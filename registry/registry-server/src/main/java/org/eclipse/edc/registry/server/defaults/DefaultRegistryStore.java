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

package org.eclipse.edc.registry.server.defaults;

import org.eclipse.edc.registry.server.spi.resource.ResourceTypeStore;
import org.eclipse.edc.registry.server.spi.store.RegistryStore;
import org.eclipse.edc.registry.xregistry.model.definition.GroupDefinition;
import org.eclipse.edc.registry.xregistry.model.definition.RegistryDefinition;
import org.eclipse.edc.registry.xregistry.model.definition.RegistrySpecification;
import org.eclipse.edc.registry.xregistry.model.definition.ResourceDefinition;
import org.eclipse.edc.registry.xregistry.model.definition.VersionDefinition;
import org.eclipse.edc.registry.xregistry.model.typed.TypeFactory;
import org.eclipse.edc.registry.xregistry.model.typed.TypeFactoryImpl;
import org.eclipse.edc.registry.xregistry.model.typed.TypedGroup;
import org.eclipse.edc.registry.xregistry.model.typed.TypedResource;
import org.eclipse.edc.spi.result.ServiceResult;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation.
 */
public class DefaultRegistryStore implements RegistryStore {
    private static final String DEFAULT_REGISTRY_NAME = "edc-registry";
    private static final String REGISTRY_XID = "/registry";
    private static final String SPEC_VERSION = "0.5";
    private final RegistrySpecification specification;

    private TypeFactory typeFactory;
    private RegistryDefinition registryDefinition;

    private Instant created;

    private Map<Class<?>, ResourceTypeStore<?>> cache = new HashMap<>();

    public DefaultRegistryStore(RegistrySpecification specification) {
        this.specification = specification;
        typeFactory = new TypeFactoryImpl();
        registryDefinition = RegistryDefinition.Builder.newInstance().build();
        created = Instant.now();
    }

    @Override
    public void register(ResourceTypeStore<?> store) {
        cache.put(store.getType(), store);
    }

    @Override
    public @NotNull Map<String, Object> fetch(int offset, int maxResults) {
        var fetchedGroups = cache.values().stream()
                .flatMap(store -> store.fetchGroups(offset, maxResults).stream())
                .toList();

        registryDefinition = createRegistryDefinition(fetchedGroups);

        var registry = new LinkedHashMap<String, Object>();
        configureRegistry(registry);

        var groupedResources = new LinkedHashMap<String, Map<String, Object>>();
        fetchedGroups.stream()
                .map(this::enrichGroup)
                .forEach(typedGroup -> groupedResources
                        .computeIfAbsent(typedGroup.getDefinition().getPlural(), key -> new LinkedHashMap<>())
                        .put(typedGroup.getId(), typedGroup.asMap()));

        groupedResources.forEach((plural, groups) -> {
            registry.put(plural, groups);
            registry.put(plural + "url", "#/" + plural);
            registry.put(plural + "count", groups.size());
        });

        return registry;
    }

    @Override
    public ServiceResult<Void> createResource(TypedResource<?> resource) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceResult<Void> updateResource(TypedResource<?> resource) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceResult<Void> deleteResource(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceResult<Void> reload() {
        for (var store : cache.values()) {
            var result = store.reload();
            if (result.failed()) {
                return result;
            }
        }
        return ServiceResult.success();
    }

    private RegistryDefinition createRegistryDefinition(List<TypedGroup> groups) {
        var builder = RegistryDefinition.Builder.newInstance();
        groups.stream()
                .map(TypedGroup::getDefinition)
                .map(GroupDefinition::getPlural)
                .distinct()
                .forEach(plural -> builder.group(findGroupDefinition(plural, groups)));
        return builder.build();
    }

    private GroupDefinition findGroupDefinition(String plural, List<TypedGroup> groups) {
        return groups.stream()
                .map(TypedGroup::getDefinition)
                .filter(definition -> definition.getPlural().equals(plural))
                .findFirst()
                .orElseThrow();
    }

    private void configureRegistry(Map<String, Object> registry) {
        registry.put("registryid", DEFAULT_REGISTRY_NAME);
        registry.put("specversion", SPEC_VERSION);
        registry.put("self", specification.getUrl());
        registry.put("url", specification.getUrl());
        registry.put("epoch", 1);
        registry.put("createdat", created.toString());
        registry.put("modifiedat", created.toString());
        registry.put("xid", REGISTRY_XID);
    }

    private TypedGroup enrichGroup(TypedGroup group) {
        var copy = mutableCopy(group.asMap());
        var groupPath = pathFor(group.getDefinition().getPlural(), group.getId());
        applyCommonMetadata(copy, groupPath, xidFor(groupPath));

        for (var resourceDefinition : group.getDefinition().getResources().values()) {
            enrichResources(copy, groupPath, resourceDefinition);
        }

        return TypedGroup.Builder.newInstance()
                .untyped(copy)
                .definition(group.getDefinition())
                .typeFactory(typeFactory)
                .build();
    }

    @SuppressWarnings("unchecked")
    private void enrichResources(Map<String, Object> groupMap, String groupPath, ResourceDefinition resourceDefinition) {
        var resourceCollectionName = resourceDefinition.getPlural();
        var resources = (Map<String, Map<String, Object>>) groupMap.get(resourceCollectionName);
        groupMap.put(resourceCollectionName + "url", groupPath + "/" + resourceCollectionName);
        groupMap.put(resourceCollectionName + "count", resources == null ? 0 : resources.size());

        if (resources == null) {
            return;
        }

        var defaultVersionId = resources.values().stream()
                .map(resource -> (Map<String, Map<String, Object>>) resource.get("versions"))
                .filter(versions -> versions != null && !versions.isEmpty())
                .findFirst()
                .map(this::resolveDefaultVersionId)
                .orElse(null);

        resources.forEach((resourceId, resourceMap) -> {
            var resourcePath = groupPath + "/" + resourceCollectionName + "/" + resourceId;
            resourceMap.put("self", resourcePath);
            resourceMap.put("xid", xidFor(resourcePath));
            resourceMap.put("metaurl", resourcePath + "/meta");
            resourceMap.put("versionsurl", resourcePath + "/versions");

            var versions = (Map<String, Map<String, Object>>) resourceMap.get("versions");
            resourceMap.put("versionscount", versions == null ? 0 : versions.size());

            if (versions == null) {
                return;
            }

            var resourceDefaultVersionId = resolveDefaultVersionId(versions);
            versions.forEach((versionId, versionMap) -> enrichVersion(versionMap, resourceDefinition.getVersionDefinition(), resourceId, versionId, resourcePath, versionId.equals(resourceDefaultVersionId)));
        });
    }

    private void enrichVersion(Map<String, Object> versionMap, VersionDefinition versionDefinition, String resourceId, String versionId, String resourcePath, boolean isDefault) {
        versionMap.put(versionDefinition.getResourceName() + "id", resourceId);
        versionMap.put(versionDefinition.getSingular() + "id", versionId);
        versionMap.put("self", resourcePath + "/versions/" + versionId);
        versionMap.put("xid", xidFor(resourcePath + "/versions/" + versionId));
        versionMap.put("epoch", 1);
        versionMap.put("isdefault", isDefault);
        versionMap.put("createdat", created.toString());
        versionMap.put("modifiedat", created.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutableCopy(Map<String, Object> source) {
        var copy = new LinkedHashMap<String, Object>();
        source.forEach((key, value) -> {
            if (value instanceof Map<?, ?> mapValue) {
                copy.put(key, mutableCopy((Map<String, Object>) mapValue));
            } else {
                copy.put(key, value);
            }
        });
        return copy;
    }

    private void applyCommonMetadata(Map<String, Object> map, String self, String xid) {
        map.put("self", self);
        map.put("xid", xid);
        map.put("epoch", 1);
        map.put("createdat", created.toString());
        map.put("modifiedat", created.toString());
    }

    @SuppressWarnings("unchecked")
    private String resolveDefaultVersionId(Map<String, Map<String, Object>> versions) {
        return versions.keySet().stream()
                .sorted()
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private String pathFor(String collectionName, String id) {
        return "#/" + collectionName + "/" + id;
    }

    private String xidFor(String selfPath) {
        return selfPath.startsWith("#") ? selfPath.substring(1) : selfPath;
    }
}
