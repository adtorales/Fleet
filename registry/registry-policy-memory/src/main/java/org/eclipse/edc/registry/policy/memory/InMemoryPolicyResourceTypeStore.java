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

package org.eclipse.edc.registry.policy.memory;

import org.eclipse.edc.registry.server.spi.resource.ResourceTypeStore;
import org.eclipse.edc.registry.xregistry.model.typed.TypedGroup;
import org.eclipse.edc.registry.xregistry.policy.model.typed.TypedPolicyResource;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.util.concurrency.LockManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.eclipse.edc.spi.result.ServiceResult.success;
import static org.eclipse.edc.spi.result.ServiceResult.unexpected;

/**
 * An In-memory store for policy resources.
 */
public class InMemoryPolicyResourceTypeStore implements ResourceTypeStore<TypedPolicyResource> {
    private final LockManager lockManager;
    private final PolicyGroupLoader loader;
    private final List<TypedGroup> groups = new CopyOnWriteArrayList<>();

    public InMemoryPolicyResourceTypeStore(LockManager lockManager, PolicyGroupLoader loader) {
        this.lockManager = lockManager;
        this.loader = loader;
    }

    @Override
    public Class<TypedPolicyResource> getType() {
        return TypedPolicyResource.class;
    }

    @Override
    public @NotNull Collection<TypedGroup> fetchGroups(int offset, int maxResults) {
        return lockManager.readLock(() -> {
            var safeOffset = Math.max(offset, 0);
            if (safeOffset >= groups.size()) {
                return List.of();
            }
            var endIndex = Math.min(groups.size(), safeOffset + Math.max(maxResults, 0));
            var result = List.copyOf(groups.subList(safeOffset, endIndex));
            return result;
        });
    }

    public int size() {
        return lockManager.readLock(groups::size);
    }

    @Override
    public ServiceResult<Void> createResource(TypedPolicyResource resource) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceResult<Void> updateResource(TypedPolicyResource resource) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceResult<Void> deleteResource(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceResult<Void> reload() {
        try {
            var loadedGroups = loader.loadGroups();
            replaceGroups(loadedGroups);
            return success();
        } catch (Exception e) {
            return unexpected("Failed to reload policy groups: " + formatException(e));
        }
    }

    public void replaceGroups(Collection<TypedGroup> newGroups) {
        lockManager.writeLock(() -> {
            groups.clear();
            groups.addAll(newGroups);
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
