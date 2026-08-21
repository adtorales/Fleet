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

import org.eclipse.edc.registry.xregistry.model.typed.TypedGroup;

import java.util.Collection;
import java.util.List;

/**
 * Loads policy groups from an external source.
 */
@FunctionalInterface
public interface PolicyGroupLoader {

    PolicyGroupLoader EMPTY = () -> List.of();

    Collection<TypedGroup> loadGroups();
}
