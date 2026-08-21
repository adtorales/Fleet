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

package org.eclipse.edc.registry.oci.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import land.oras.ContainerRef;
import org.eclipse.edc.registry.xregistry.model.definition.GroupDefinition;
import org.eclipse.edc.registry.xregistry.model.definition.ResourceDefinition;
import org.eclipse.edc.registry.xregistry.model.typed.TypeFactory;
import org.eclipse.edc.registry.xregistry.model.typed.TypedGroup;
import org.eclipse.edc.registry.xregistry.policy.model.typed.TypedPolicyResource;
import org.eclipse.edc.registry.xregistry.policy.model.typed.TypedPolicyVersion;
import org.eclipse.edc.registry.xregistry.schema.model.typed.TypedSchemaResource;
import org.eclipse.edc.registry.xregistry.schema.model.typed.TypedSchemaVersion;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.xregistry.processor.Artifact;
import org.eclipse.edc.xregistry.processor.CompactFileSystemWalker;
import org.eclipse.edc.xregistry.processor.XRegistryVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Loads xRegistry policy and schema catalogs from one or more OCI artifacts.
 */
public class OciCatalogLoader {
    private static final String GROUP_ID = "groupId";
    private static final String RESOURCE_ID = "resourceId";
    private static final String VERSION_ID = "versionId";
    private static final String ACCESS_POLICY = "accessPolicy";
    private static final String CONTROL_POLICY = "controlPolicy";
    private static final String POLICY_DEFINITION = "policyDefinition";
    private static final String FORMAT = "format";
    private static final String SCHEMA = "schema";
    private static final String SCHEMA_BASE64 = "schemabase64";
    private static final String EDC_CONTEXT = "https://w3id.org/edc/v0.0.1/ns/";
    private static final String ODRL_CONTEXT = "http://www.w3.org/ns/odrl.jsonld";

    private final List<ContainerRef> imageReferences;
    private final OciArtifactLoader artifactLoader;
    private final GroupDefinition policyGroupDefinition;
    private final ResourceDefinition policyResourceDefinition;
    private final GroupDefinition schemaGroupDefinition;
    private final ResourceDefinition schemaResourceDefinition;
    private final TypeFactory typeFactory;
    private final ObjectMapper objectMapper;
    private final Monitor monitor;

    public OciCatalogLoader(List<ContainerRef> imageReferences,
                            OciArtifactLoader artifactLoader,
                            GroupDefinition policyGroupDefinition,
                            ResourceDefinition policyResourceDefinition,
                            GroupDefinition schemaGroupDefinition,
                            ResourceDefinition schemaResourceDefinition,
                            TypeFactory typeFactory,
                            ObjectMapper objectMapper,
                            Monitor monitor) {
        this.imageReferences = requireNonNull(imageReferences, "imageReferences");
        this.artifactLoader = requireNonNull(artifactLoader, "artifactLoader");
        this.policyGroupDefinition = requireNonNull(policyGroupDefinition, "policyGroupDefinition");
        this.policyResourceDefinition = requireNonNull(policyResourceDefinition, "policyResourceDefinition");
        this.schemaGroupDefinition = requireNonNull(schemaGroupDefinition, "schemaGroupDefinition");
        this.schemaResourceDefinition = requireNonNull(schemaResourceDefinition, "schemaResourceDefinition");
        this.typeFactory = requireNonNull(typeFactory, "typeFactory");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper");
        this.monitor = requireNonNull(monitor, "monitor");
    }

    /**
     * Pulls each configured OCI artifact, walks the compact xRegistry directory layout and returns merged groups.
     */
    public Collection<TypedGroup> loadGroups() {
        var visitor = new MergingCatalogVisitor();
        var extractedRoots = new ArrayList<Path>();

        try {
            for (var ref : imageReferences) {
                var root = artifactLoader.pullArtifact(ref);
                extractedRoots.add(root);
                var catalogRoot = findCatalogRoot(root);
                new CompactFileSystemWalker(visitor).walk(catalogRoot);
            }
            return visitor.toGroups();
        } finally {
            extractedRoots.forEach(this::deleteDirectoryQuietly);
        }
    }

    private void addPolicy(Artifact artifact, Supplier<InputStream> ref,
                           Map<String, Map<String, TypedPolicyResource.Builder>> resourcesByGroup) {
        try (var input = ref.get()) {
            var document = objectMapper.readTree(input);
            var groupId = readString(document, GROUP_ID, artifact.group());
            var resourceId = readString(document, RESOURCE_ID, artifact.name());
            var versionId = readString(document, VERSION_ID, artifact.version());

            if (!artifact.group().equals(groupId) || !artifact.name().equals(resourceId) || !artifact.version().equals(versionId)) {
                monitor.warning("Policy coordinates mismatch for '%s'. File name and JSON coordinates must match."
                        .formatted("%s.%s.%s".formatted(artifact.group(), artifact.name(), artifact.version())));
                return;
            }

            var policyDefinitionNode = document.get(POLICY_DEFINITION);
            if (policyDefinitionNode == null || policyDefinitionNode.isNull()) {
                monitor.warning("Skipping policy without policyDefinition: " + resourceId + ":" + versionId);
                return;
            }

            var policyDefinitionString = wrapPolicyDefinition(resourceId, policyDefinitionNode);

            var version = TypedPolicyVersion.Builder.newInstance()
                    .untyped(new LinkedHashMap<>())
                    .definition(policyResourceDefinition.getVersionDefinition())
                    .typeFactory(typeFactory)
                    .id(versionId)
                    .accessPolicy(document.path(ACCESS_POLICY).asBoolean(false))
                    .controlPolicy(document.path(CONTROL_POLICY).asBoolean(false))
                    .policyDefinition(policyDefinitionString)
                    .build();

            var resourceBuilder = resourcesByGroup
                    .computeIfAbsent(groupId, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(resourceId, this::newPolicyResourceBuilder);
            resourceBuilder.version(versionId, version);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load policy artifact '%s.%s.%s'"
                    .formatted(artifact.group(), artifact.name(), artifact.version()), e);
        }
    }

    private void addSchema(Artifact artifact, Supplier<InputStream> ref,
                           Map<String, Map<String, TypedSchemaResource.Builder>> resourcesByGroup) {
        try (var input = ref.get()) {
            var document = objectMapper.readTree(input);
            var groupId = readString(document, GROUP_ID, artifact.group());
            var resourceId = readString(document, RESOURCE_ID, artifact.name());
            var versionId = readString(document, VERSION_ID, artifact.version());

            if (!artifact.group().equals(groupId) || !artifact.name().equals(resourceId) || !artifact.version().equals(versionId)) {
                monitor.warning("Schema coordinates mismatch for '%s'. File name and JSON coordinates must match."
                        .formatted("%s.%s.%s".formatted(artifact.group(), artifact.name(), artifact.version())));
                return;
            }

            var version = TypedSchemaVersion.Builder.newInstance()
                    .untyped(new LinkedHashMap<>())
                    .definition(schemaResourceDefinition.getVersionDefinition())
                    .typeFactory(typeFactory)
                    .id(versionId)
                    .format(readNodeAsString(document, FORMAT, null))
                    .schema(readNodeAsString(document, SCHEMA, null))
                    .schemaBase64(readNodeAsString(document, SCHEMA_BASE64, null))
                    .build();

            var resourceBuilder = resourcesByGroup
                    .computeIfAbsent(groupId, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(resourceId, this::newSchemaResourceBuilder);
            resourceBuilder.version(versionId, version);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load schema artifact '%s.%s.%s'"
                    .formatted(artifact.group(), artifact.name(), artifact.version()), e);
        }
    }

    private String wrapPolicyDefinition(String resourceId, JsonNode policyDefinitionNode) throws IOException {
        if (isWrappedPolicyDefinition(policyDefinitionNode)) {
            return objectMapper.writeValueAsString(policyDefinitionNode);
        }

        var wrapper = objectMapper.createObjectNode();
        var context = objectMapper.createObjectNode();
        context.put("edc", EDC_CONTEXT);
        context.put("odrl", ODRL_CONTEXT);
        wrapper.set("@context", context);
        wrapper.put("@type", "edc:PolicyDefinition");
        wrapper.put("@id", extractPolicyDefinitionId(policyDefinitionNode, resourceId));
        wrapper.set("edc:policy", policyDefinitionNode);
        return objectMapper.writeValueAsString(wrapper);
    }

    private boolean isWrappedPolicyDefinition(JsonNode node) {
        if (!node.isObject()) {
            return false;
        }
        var type = node.get("@type");
        if (type != null && type.isTextual()) {
            var typeText = type.asText();
            if (typeText.equals("edc:PolicyDefinition") || typeText.equals("PolicyDefinition")) {
                return true;
            }
        }
        return node.has("edc:policy") || node.has("policy");
    }

    private String extractPolicyDefinitionId(JsonNode node, String fallback) {
        var id = node.get("@id");
        if (id != null && id.isTextual() && !id.asText().isBlank()) {
            return id.asText();
        }
        return fallback;
    }

    private Path findCatalogRoot(Path directory) {
        try (var paths = Files.walk(directory)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(this::looksLikeCatalogRoot)
                    .findFirst()
                    .orElse(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to inspect extracted OCI directory " + directory, e);
        }
    }

    private boolean looksLikeCatalogRoot(Path candidate) {
        return Files.isDirectory(candidate.resolve("policies")) || Files.isDirectory(candidate.resolve("schemas"));
    }

    private String readString(JsonNode document, String fieldName, String fallback) {
        var node = document.get(fieldName);
        return node == null || node.isNull() || !node.isTextual() || node.asText().isBlank() ? fallback : node.asText();
    }

    private String readNodeAsString(JsonNode document, String fieldName, String fallback) {
        var node = document.get(fieldName);
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (node.isTextual()) {
            var text = node.asText();
            return text.isBlank() ? fallback : text;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize field " + fieldName, e);
        }
    }

    private TypedGroup.Builder newPolicyGroupBuilder(String groupId) {
        return TypedGroup.Builder.newInstance()
                .untyped(new LinkedHashMap<>())
                .definition(policyGroupDefinition)
                .typeFactory(typeFactory)
                .id(groupId);
    }

    private TypedGroup.Builder newSchemaGroupBuilder(String groupId) {
        return TypedGroup.Builder.newInstance()
                .untyped(new LinkedHashMap<>())
                .definition(schemaGroupDefinition)
                .typeFactory(typeFactory)
                .id(groupId);
    }

    private TypedPolicyResource.Builder newPolicyResourceBuilder(String resourceId) {
        return TypedPolicyResource.Builder.newInstance()
                .untyped(new LinkedHashMap<>())
                .definition(policyResourceDefinition)
                .typeFactory(typeFactory)
                .id(resourceId);
    }

    private TypedSchemaResource.Builder newSchemaResourceBuilder(String resourceId) {
        return TypedSchemaResource.Builder.newInstance()
                .untyped(new LinkedHashMap<>())
                .definition(schemaResourceDefinition)
                .typeFactory(typeFactory)
                .id(resourceId);
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            monitor.warning("Failed to delete temporary OCI directory: " + path);
                        }
                    });
        } catch (IOException e) {
            monitor.warning("Failed to walk temporary OCI directory: " + directory);
        }
    }

    private class MergingCatalogVisitor implements XRegistryVisitor {
        private final Map<String, TypedGroup.Builder> policyGroups = new LinkedHashMap<>();
        private final Map<String, TypedGroup.Builder> schemaGroups = new LinkedHashMap<>();
        private final Map<String, Map<String, TypedPolicyResource.Builder>> policyResourcesByGroup = new LinkedHashMap<>();
        private final Map<String, Map<String, TypedSchemaResource.Builder>> schemaResourcesByGroup = new LinkedHashMap<>();

        @Override
        public void onPolicy(Artifact artifact, Supplier<InputStream> ref) {
            addPolicy(artifact, ref, policyResourcesByGroup);
        }

        @Override
        public void onSchema(Artifact artifact, Supplier<InputStream> ref) {
            addSchema(artifact, ref, schemaResourcesByGroup);
        }

        @Override
        public void onRule(Artifact artifact, Supplier<InputStream> ref) {
            // not supported by the OCI catalog loader
        }

        @Override
        public void onError(String problem) {
            throw new IllegalStateException("Failed to walk OCI catalog: " + problem);
        }

        public Collection<TypedGroup> toGroups() {
            var groups = new ArrayList<TypedGroup>();

            policyResourcesByGroup.forEach((groupId, resources) -> {
                var groupBuilder = policyGroups.computeIfAbsent(groupId, OciCatalogLoader.this::newPolicyGroupBuilder);
                resources.values().stream()
                        .map(TypedPolicyResource.Builder::build)
                        .forEach(groupBuilder::resource);
                groups.add(groupBuilder.build());
            });

            schemaResourcesByGroup.forEach((groupId, resources) -> {
                var groupBuilder = schemaGroups.computeIfAbsent(groupId, OciCatalogLoader.this::newSchemaGroupBuilder);
                resources.values().stream()
                        .map(TypedSchemaResource.Builder::build)
                        .forEach(groupBuilder::resource);
                groups.add(groupBuilder.build());
            });

            return groups;
        }
    }
}
