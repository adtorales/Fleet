/*
 *  Copyright (c) 2025 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package org.eclipse.edc.registry.reconciler.policy;

import jakarta.json.Json;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.services.spi.policydefinition.PolicyDefinitionService;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.registry.spi.reconciler.ReconciliationContext;
import org.eclipse.edc.registry.spi.reconciler.ReconciliationReport;
import org.eclipse.edc.registry.spi.reconciler.ResourceReconciler;
import org.eclipse.edc.registry.xregistry.model.typed.TypedGroup;
import org.eclipse.edc.registry.xregistry.model.typed.TypedRegistry;
import org.eclipse.edc.registry.xregistry.policy.model.typed.TypedPolicyResource;
import org.eclipse.edc.registry.xregistry.policy.model.typed.TypedPolicyVersion;
import org.eclipse.edc.registry.xregistry.schema.model.typed.TypedSchemaResource;
import org.eclipse.edc.registry.xregistry.schema.model.typed.TypedSchemaVersion;
import org.eclipse.edc.registry.xregistry.validation.content.PolicyContentValidator;
import org.eclipse.edc.registry.xregistry.validation.content.Violation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.eclipse.edc.registry.xregistry.policy.model.PolicyConstants.GROUPS_NAME;
import static org.eclipse.edc.spi.result.ServiceResult.success;

/**
 * Reconciles policy objects against the EDC {@link PolicyDefinitionService} using JSON-LD transforms.
 */
public class PolicyResourceReconciler implements ResourceReconciler {
    private static final String MANAGED_PROPERTY = "xregistry:managed";
    private static final String CONTENT_HASH_PROPERTY = "xregistry:contentHash";
    private static final String SCHEMA_GROUPS_NAME = "schemagroups";
    private static final String TIER_1_GROUP = "cx";
    private static final String TIER_2_GROUP = "mb";

    private final PolicyDefinitionService policyService;
    private final JsonLd jsonLd;
    private final TypeTransformerRegistry typeTransformerRegistry;
    private final PolicyContentValidator contentValidator;
    private final Monitor monitor;
    private ReconciliationReport lastReport;
    private List<Map.Entry<String, String>> currentSchemas;

    public PolicyResourceReconciler(PolicyDefinitionService policyService,
                                    JsonLd jsonLd,
                                    TypeTransformerRegistry typeTransformerRegistry,
                                    PolicyContentValidator contentValidator,
                                    Monitor monitor) {
        this.policyService = policyService;
        this.jsonLd = jsonLd;
        this.typeTransformerRegistry = typeTransformerRegistry;
        this.contentValidator = contentValidator;
        this.monitor = monitor;
    }

    @Override
    public String resourceType() {
        return "policy";
    }

    @Override
    public ServiceResult<Void> reconcile(TypedRegistry registry, ReconciliationContext context) {
        lastReport = new ReconciliationReport();
        lastReport.setResourceType(resourceType());

        currentSchemas = resolveSchemas(registry);

        var groups = registry.getGroups(GROUPS_NAME);
        var desiredPolicyIds = new HashSet<String>();

        groups.values().forEach(group -> group.getResourcesOfType(TypedPolicyResource.class)
                .forEach(resource -> resource.getVersions().values()
                        .forEach(version -> processVersion(group, resource, version, desiredPolicyIds))));

        sweepDeletedPolicies(desiredPolicyIds);

        monitor.info("Policy reconciliation completed: %s".formatted(lastReportSummary()));
        return success();
    }

    @Override
    public ReconciliationReport lastReport() {
        return lastReport;
    }

    private List<Map.Entry<String, String>> resolveSchemas(TypedRegistry registry) {
        var schemas = new ArrayList<Map.Entry<String, String>>();

        var schemaGroups = registry.getGroups(SCHEMA_GROUPS_NAME);
        schemaGroups.values().stream()
                .filter(group -> TIER_1_GROUP.equals(group.getId()))
                .forEach(group -> collectSchemas(group, "tier-1", schemas));

        schemaGroups.values().stream()
                .filter(group -> TIER_2_GROUP.equals(group.getId()))
                .forEach(group -> collectSchemas(group, "tier-2", schemas));

        return schemas;
    }

    private void collectSchemas(TypedGroup group, String tierName, List<Map.Entry<String, String>> schemas) {
        group.getResourcesOfType(TypedSchemaResource.class).forEach(resource -> {
            var version = resource.getLatestVersion();
            if (version == null) {
                return;
            }
            var schema = getSchema(version);
            if (schema != null && !schema.isBlank()) {
                schemas.add(Map.entry(tierName, schema));
            }
        });
    }

    private String getSchema(TypedSchemaVersion version) {
        var schema = version.getSchema();
        if (schema != null && !schema.isBlank()) {
            return schema;
        }
        var base64 = version.getSchemaBase64();
        if (base64 != null && !base64.isBlank()) {
            return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        }
        return null;
    }

    private void processVersion(TypedGroup group, TypedPolicyResource resource, TypedPolicyVersion version, Set<String> desiredPolicyIds) {
        var policyDefinition = version.getPolicyDefinition();
        // Register the id BEFORE any early return: a broken or blank version must
        // freeze (stay out of the sweep), never be deleted from running connectors.
        var policyId = buildPolicyId(group, resource, version);
        desiredPolicyIds.add(policyId);

        if (policyDefinition == null || policyDefinition.isBlank()) {
            monitor.warning("Skipping policy resource '%s' in group '%s' because version '%s' has no policy definition."
                    .formatted(resource.getId(), group.getId(), version.getId()));
            lastReport.addError("No policy definition: %s/%s/%s".formatted(group.getId(), resource.getId(), version.getId()));
            return;
        }

        var violations = contentValidator.validate(policyDefinition, version.isAccessPolicy(), version.isControlPolicy(), currentSchemas);
        if (!violations.isEmpty()) {
            monitor.warning("Policy '%s' failed content validation; freezing it. Violations: %s"
                    .formatted(policyId, violations.stream().map(Violation::toString).toList()));
            violations.forEach(v -> lastReport.addError("Validation %s for '%s': %s".formatted(v.getTier(), policyId, v.getMessage())));
            lastReport.incrementInvalid();
            return;
        }

        var parsed = parsePolicyDefinition(policyId, policyDefinition);
        if (parsed == null) {
            return;
        }

        var contentHash = computeContentHash(policyDefinition);
        var existing = policyService.findById(policyId);

        if (existing != null) {
            updatePolicyIfNeeded(policyId, contentHash, parsed, existing);
        } else {
            createPolicy(policyId, contentHash, parsed);
        }
    }

    private String buildPolicyId(TypedGroup group, TypedPolicyResource resource, TypedPolicyVersion version) {
        return "%s/%s/%s".formatted(group.getId(), resource.getId(), version.getId());
    }

    private PolicyDefinition parsePolicyDefinition(String policyId, String policyDefinition) {
        try {
            var json = Json.createReader(new StringReader(policyDefinition)).readObject();

            var expanded = jsonLd.expand(json);
            if (expanded.failed()) {
                monitor.warning("Failed to expand JSON-LD for policy '%s': %s".formatted(policyId, expanded.getFailureDetail()));
                lastReport.addError("JSON-LD expansion failed for '%s': %s".formatted(policyId, expanded.getFailureDetail()));
                return null;
            }

            var transformResult = typeTransformerRegistry.transform(expanded.getContent(), PolicyDefinition.class);
            if (transformResult.failed()) {
                monitor.warning("Failed to transform JSON-LD for policy '%s': %s".formatted(policyId, transformResult.getFailureDetail()));
                lastReport.addError("JSON-LD transform failed for '%s': %s".formatted(policyId, transformResult.getFailureDetail()));
                return null;
            }

            return transformResult.getContent();
        } catch (Exception exception) {
            monitor.warning("Skipping policy '%s' because its policy definition could not be parsed: %s"
                    .formatted(policyId, exception.getMessage()));
            lastReport.addError("Parse error for '%s': %s".formatted(policyId, exception.getMessage()));
            return null;
        }
    }

    private void createPolicy(String policyId, String contentHash, PolicyDefinition parsedPolicy) {
        var policyToCreate = buildManagedPolicyDefinition(policyId, contentHash, parsedPolicy);
        var result = policyService.create(policyToCreate);
        if (result.failed()) {
            monitor.warning("Failed to create policy '%s': %s".formatted(policyId, result.getFailureDetail()));
            lastReport.addError("Create failed for '%s': %s".formatted(policyId, result.getFailureDetail()));
        } else {
            monitor.debug("Created reconciled policy '%s'.".formatted(policyId));
            lastReport.incrementCreated();
        }
    }

    private void updatePolicyIfNeeded(String policyId, String contentHash, PolicyDefinition parsedPolicy, PolicyDefinition existingPolicy) {
        var existingHash = existingPolicy.getPrivateProperty(CONTENT_HASH_PROPERTY);
        if (contentHash.equals(existingHash)) {
            monitor.debug("Policy '%s' is already up to date.".formatted(policyId));
            lastReport.incrementUnchanged();
            return;
        }

        var policyToUpdate = buildManagedPolicyDefinition(policyId, contentHash, parsedPolicy);
        var result = policyService.update(policyToUpdate);
        if (result.failed()) {
            monitor.warning("Failed to update policy '%s': %s".formatted(policyId, result.getFailureDetail()));
            lastReport.addError("Update failed for '%s': %s".formatted(policyId, result.getFailureDetail()));
        } else {
            monitor.debug("Updated reconciled policy '%s'.".formatted(policyId));
            lastReport.incrementUpdated();
        }
    }

    private void sweepDeletedPolicies(Set<String> desiredPolicyIds) {
        var searchResult = policyService.search(QuerySpec.max());
        if (searchResult.failed()) {
            monitor.warning("Failed to list existing policies during sweep: %s".formatted(searchResult.getFailureDetail()));
            lastReport.addError("Sweep failed: %s".formatted(searchResult.getFailureDetail()));
            return;
        }

        // Locked-room enforcement (AD-01): the sweep covers ALL policy definitions,
        // not only reconciler-managed ones, so manually created policies are
        // reverted on the next cycle. Bound policies degrade to retained + warn.
        searchResult.getContent().stream()
                .filter(policy -> !desiredPolicyIds.contains(policy.getId()))
                .forEach(policy -> deletePolicy(policy.getId()));
    }

    private void deletePolicy(String policyId) {
        var result = policyService.deleteById(policyId);
        if (result.failed()) {
            if (result.reason() == org.eclipse.edc.spi.result.ServiceFailure.Reason.CONFLICT) {
                monitor.warning("Policy '%s' retained: bound by a contract definition or agreement.".formatted(policyId));
                lastReport.incrementRetained();
                return;
            }
            monitor.warning("Failed to delete policy '%s': %s".formatted(policyId, result.getFailureDetail()));
            lastReport.addError("Delete failed for '%s': %s".formatted(policyId, result.getFailureDetail()));
        } else {
            monitor.debug("Deleted reconciled policy '%s' because it is no longer part of the registry.".formatted(policyId));
            lastReport.incrementDeleted();
        }
    }

    private PolicyDefinition buildManagedPolicyDefinition(String policyId, String contentHash, PolicyDefinition parsedPolicy) {
        var builder = PolicyDefinition.Builder.newInstance()
                .id(policyId)
                .policy(parsedPolicy.getPolicy());

        var privateProperties = parsedPolicy.getPrivateProperties();
        if (privateProperties != null && !privateProperties.isEmpty()) {
            builder.privateProperties(privateProperties);
        }

        builder.privateProperty(MANAGED_PROPERTY, true)
                .privateProperty(CONTENT_HASH_PROPERTY, contentHash);

        return builder.build();
    }

    private String computeContentHash(String policyDefinition) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hashed = digest.digest(policyDefinition.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available on this runtime.", exception);
        }
    }

    private String lastReportSummary() {
        return "created=%d, updated=%d, deleted=%d, retained=%d, unchanged=%d, invalid=%d, failed=%d".formatted(
                lastReport.getCreated(), lastReport.getUpdated(), lastReport.getDeleted(), lastReport.getRetained(),
                lastReport.getUnchanged(), lastReport.getInvalid(), lastReport.getFailed());
    }
}
