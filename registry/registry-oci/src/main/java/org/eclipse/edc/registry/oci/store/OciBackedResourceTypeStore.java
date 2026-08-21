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

package org.eclipse.edc.registry.oci.store;

import org.eclipse.edc.registry.oci.loader.OciCatalogLoader;
import org.eclipse.edc.registry.server.spi.resource.ResourceTypeStore;
import org.eclipse.edc.registry.xregistry.model.typed.TypedGroup;
import org.eclipse.edc.registry.xregistry.model.typed.TypedResource;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.util.concurrency.LockManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.eclipse.edc.spi.result.ServiceResult.success;
import static org.eclipse.edc.spi.result.ServiceResult.unexpected;

/**
 * A read-only {@link ResourceTypeStore} backed by OCI xRegistry catalogs.
 *
 * @param <T> the resource type managed by this store.
 */
public class OciBackedResourceTypeStore<T extends TypedResource<?>> implements ResourceTypeStore<T> {
    private final Class<T> type;
    private final String resourcePlural;
    private final LockManager lockManager;
    private final OciCatalogLoader catalogLoader;
    /**
     * Replaced as one immutable snapshot after every successful OCI reload. Readers always see
     * either the previous complete catalog or the next complete catalog, never a partial update.
     */
    private volatile List<TypedGroup> groups = List.of();

    public OciBackedResourceTypeStore(Class<T> type, String resourcePlural, LockManager lockManager, OciCatalogLoader catalogLoader) {
        this.type = type;
        this.resourcePlural = resourcePlural;
        this.lockManager = lockManager;
        this.catalogLoader = catalogLoader;
    }

    @Override
    public Class<T> getType() {
        return type;
    }

    @Override
    public @NotNull Collection<TypedGroup> fetchGroups(int offset, int maxResults) {
        return lockManager.readLock(() -> {
            var safeOffset = Math.max(offset, 0);
            if (safeOffset >= groups.size()) {
                return List.of();
            }
            var endIndex = Math.min(groups.size(), safeOffset + Math.max(maxResults, 0));
            return List.copyOf(groups.subList(safeOffset, endIndex));
        });
    }

    public int size() {
        return lockManager.readLock(groups::size);
    }

    /**
     * Returns the number of groups loaded by this store. Groups are filtered by resource type
     * before they are stored.
     */
    public int getTypedGroupCount() {
        return size();
    }

    /**
     * Returns the number of resources of this store's type across all loaded groups.
     */
    public int getTypedResourceCount() {
        return lockManager.readLock(() -> groups.stream()
                .mapToInt(this::getRawResourceCount)
                .sum());
    }

    private boolean containsStoreResourceType(TypedGroup group) {
        return group.getDefinition().getResources().values().stream()
                .anyMatch(resource -> resource.getPlural().equals(resourcePlural));
    }

    private int getRawResourceCount(TypedGroup group) {
        var resources = group.asMap().get(resourcePlural);
        return resources instanceof Map<?, ?> resourceMap ? resourceMap.size() : 0;
    }

    @Override
    public ServiceResult<Void> createResource(T resource) {
        throw new UnsupportedOperationException("OCI-backed registry is read-only");
    }

    @Override
    public ServiceResult<Void> updateResource(T resource) {
        throw new UnsupportedOperationException("OCI-backed registry is read-only");
    }

    @Override
    public ServiceResult<Void> deleteResource(String id) {
        throw new UnsupportedOperationException("OCI-backed registry is read-only");
    }

    @Override
    public ServiceResult<Void> reload() {
        try {
            var loadedGroups = catalogLoader.loadGroups();
            replaceGroups(loadedGroups);
            return success();
        } catch (Exception e) {
            return unexpected("Failed to reload OCI catalog: " + formatException(e));
        }
    }

    public void replaceGroups(Collection<TypedGroup> newGroups) {
        var filteredGroups = newGroups.stream()
                .filter(this::containsStoreResourceType)
                .toList();

        lockManager.writeLock(() -> {
            // OciCatalogLoader loads the configured policy and schema artifacts together.
            // Keep only this store's resource type so a Policy store can never expose a Schema
            // group (and vice versa) through fetchGroups(), pagination, or count operations.
            groups = filteredGroups;
            return null;
        });
    }

    private String formatException(Exception exception) {
        var builder = new StringBuilder();
        var current = exception;

        while (current != null) {
            if (!builder.isEmpty()) {
                builder.append(" | caused by: ");
            }
            builder.append(current.getClass().getSimpleName());
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                builder.append(": ").append(current.getMessage());
            }
            current = current.getCause() instanceof Exception cause ? cause : null;
        }

        return builder.toString();
    }
}
