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
 *       Metaform Systems - initial API and implementation
 *
 */

package org.eclipse.edc.registry.policy.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.registry.xregistry.model.definition.GroupDefinition;
import org.eclipse.edc.registry.xregistry.model.definition.ResourceDefinition;
import org.eclipse.edc.registry.xregistry.model.typed.TypeFactory;
import org.eclipse.edc.registry.xregistry.model.typed.TypedGroup;
import org.eclipse.edc.registry.xregistry.policy.model.typed.TypedPolicyResource;
import org.eclipse.edc.registry.xregistry.policy.model.typed.TypedPolicyVersion;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.xregistry.processor.CompactFileSystemWalker;
import org.eclipse.edc.xregistry.processor.XRegistryVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Loads policy groups from an unpacked compact xRegistry directory.
 */
public class DirectoryPolicyGroupLoader implements PolicyGroupLoader {
    private static final String GROUP_ID = "groupId";
    private static final String RESOURCE_ID = "resourceId";
    private static final String VERSION_ID = "versionId";
    private static final String ACCESS_POLICY = "accessPolicy";
    private static final String CONTROL_POLICY = "controlPolicy";
    private static final String POLICY_DEFINITION = "policyDefinition";

    private final Path rootDirectory;
    private final GroupDefinition groupDefinition;
    private final ResourceDefinition resourceDefinition;
    private final TypeFactory typeFactory;
    private final ObjectMapper objectMapper;
    private final Monitor monitor;

    public DirectoryPolicyGroupLoader(Path rootDirectory, GroupDefinition groupDefinition, ResourceDefinition resourceDefinition,
                                      TypeFactory typeFactory, ObjectMapper objectMapper, Monitor monitor) {
        this.rootDirectory = requireNonNull(rootDirectory, "rootDirectory");
        this.groupDefinition = requireNonNull(groupDefinition, "groupDefinition");
        this.resourceDefinition = requireNonNull(resourceDefinition, "resourceDefinition");
        this.typeFactory = requireNonNull(typeFactory, "typeFactory");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper");
        this.monitor = requireNonNull(monitor, "monitor");
    }

    @Override
    public Collection<TypedGroup> loadGroups() {
        if (!Files.exists(rootDirectory)) {
            monitor.warning("Policy catalog directory does not exist: " + rootDirectory);
            return java.util.List.of();
        }

        var visitor = new PolicyCatalogVisitor();
        new CompactFileSystemWalker(visitor).walk(rootDirectory);
        var groups = visitor.toGroups();
        monitor.info("Loaded %d policy group(s) from catalog directory %s".formatted(groups.size(), rootDirectory));
        return groups;
    }

    protected Path rootDirectory() {
        return rootDirectory;
    }

    private void addPolicy(org.eclipse.edc.xregistry.processor.Artifact artifact, Supplier<InputStream> ref,
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

            var version = TypedPolicyVersion.Builder.newInstance()
                    .untyped(new LinkedHashMap<>())
                    .definition(resourceDefinition.getVersionDefinition())
                    .typeFactory(typeFactory)
                    .id(versionId)
                    .accessPolicy(document.path(ACCESS_POLICY).asBoolean(false))
                    .controlPolicy(document.path(CONTROL_POLICY).asBoolean(false))
                    .policyDefinition(objectMapper.writeValueAsString(policyDefinitionNode))
                    .build();

            var resourceBuilder = resourcesByGroup
                    .computeIfAbsent(groupId, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(resourceId, this::newResourceBuilder);
            resourceBuilder.version(versionId, version);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load policy artifact '%s.%s.%s'"
                    .formatted(artifact.group(), artifact.name(), artifact.version()), e);
        }
    }

    private TypedGroup.Builder newGroupBuilder(String groupId) {
        return TypedGroup.Builder.newInstance()
                .untyped(new LinkedHashMap<>())
                .definition(groupDefinition)
                .typeFactory(typeFactory)
                .id(groupId);
    }

    private TypedPolicyResource.Builder newResourceBuilder(String resourceId) {
        return TypedPolicyResource.Builder.newInstance()
                .untyped(new LinkedHashMap<>())
                .definition(resourceDefinition)
                .typeFactory(typeFactory)
                .id(resourceId);
    }

    private String readString(JsonNode document, String fieldName, String fallback) {
        var node = document.get(fieldName);
        return node == null || node.isNull() || node.asText().isBlank() ? fallback : node.asText();
    }

    private class PolicyCatalogVisitor implements XRegistryVisitor {
        private final Map<String, TypedGroup.Builder> groups = new LinkedHashMap<>();
        private final Map<String, Map<String, TypedPolicyResource.Builder>> resourcesByGroup = new LinkedHashMap<>();

        @Override
        public void onPolicy(org.eclipse.edc.xregistry.processor.Artifact artifact, Supplier<InputStream> ref) {
            addPolicy(artifact, ref, resourcesByGroup);
        }

        @Override
        public void onSchema(org.eclipse.edc.xregistry.processor.Artifact artifact, Supplier<InputStream> ref) {
        }

        @Override
        public void onRule(org.eclipse.edc.xregistry.processor.Artifact artifact, Supplier<InputStream> ref) {
        }

        @Override
        public void onError(String problem) {
            throw new IllegalStateException("Failed to walk policy catalog: " + problem);
        }

        public Collection<TypedGroup> toGroups() {
            resourcesByGroup.forEach((groupId, resources) -> {
                var groupBuilder = groups.computeIfAbsent(groupId, DirectoryPolicyGroupLoader.this::newGroupBuilder);
                resources.values().stream()
                        .map(TypedPolicyResource.Builder::build)
                        .forEach(groupBuilder::resource);
            });
            return groups.values().stream()
                    .map(TypedGroup.Builder::build)
                    .toList();
        }
    }
}
