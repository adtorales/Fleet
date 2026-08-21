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

package org.eclipse.edc.registry.xregistry.validation.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Default implementation that validates a policy document against a list of JSON Schemas.
 */
public class PolicyContentValidatorImpl implements PolicyContentValidator {

    private final ObjectMapper objectMapper;

    public PolicyContentValidatorImpl() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<Violation> validate(String policyJson, boolean accessPolicy, boolean controlPolicy, List<Map.Entry<String, String>> tiers) {
        var violations = new ArrayList<Violation>();
        JsonNode policyNode;
        String policyDocument;

        try {
            policyNode = objectMapper.readTree(policyJson);
            policyNode = unwrapEdcPolicyDefinition(policyNode);
            policyDocument = objectMapper.writeValueAsString(policyNode);
        } catch (IOException e) {
            violations.add(new Violation("policy", "INVALID_JSON", "Policy document is not valid JSON: " + e.getMessage()));
            return violations;
        }

        for (var tier : tiers) {
            var tierName = tier.getKey();
            var schemaJson = tier.getValue();

            try {
                var schemaNode = objectMapper.readTree(schemaJson);
                var schemaId = schemaNode.path("$id").asText();
                if (schemaId == null || schemaId.isBlank()) {
                    schemaId = "urn:tap74:" + tierName;
                }

                var schemas = Collections.singletonMap(schemaId, schemaJson);
                var registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                        builder -> builder.schemas(schemas));
                var schema = registry.getSchema(SchemaLocation.of(schemaId));

                List<Error> errors = schema.validate(policyDocument, InputFormat.JSON);
                for (var error : errors) {
                    violations.add(new Violation(tierName, "VALIDATION_ERROR", error.getMessage()));
                }
            } catch (IOException e) {
                violations.add(new Violation(tierName, "INVALID_SCHEMA", "Schema document is not valid JSON: " + e.getMessage()));
            } catch (Exception e) {
                violations.add(new Violation(tierName, "INVALID_SCHEMA", "Schema could not be loaded: " + e.getMessage()));
            }
        }

        if (controlPolicy && !containsUsagePurpose(policyNode)) {
            violations.add(new Violation("tier-1", "MISSING_USAGE_PURPOSE", "Control policies must declare a UsagePurpose constraint."));
        }

        return violations;
    }

    /**
     * The OCI loader wraps an ODRL policy in an EDC PolicyDefinition so that it can be transformed
     * by the control plane. Content schemas describe the contained ODRL policy, not that EDC wrapper.
     */
    private JsonNode unwrapEdcPolicyDefinition(JsonNode policyNode) {
        var edcPolicy = policyNode.get("edc:policy");
        if (edcPolicy != null && !edcPolicy.isNull()) {
            return edcPolicy;
        }

        var policy = policyNode.get("policy");
        return policy != null && !policy.isNull() ? policy : policyNode;
    }

    private boolean containsUsagePurpose(JsonNode policyNode) {
        for (var permission : policyNode.path("permission")) {
            for (var constraint : permission.path("constraint")) {
                if ("UsagePurpose".equals(constraint.path("leftOperand").asText())) {
                    return true;
                }
            }
        }
        return false;
    }
}
