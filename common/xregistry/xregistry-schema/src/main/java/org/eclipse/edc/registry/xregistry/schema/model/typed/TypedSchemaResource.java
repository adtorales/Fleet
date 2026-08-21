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

package org.eclipse.edc.registry.xregistry.schema.model.typed;

import org.eclipse.edc.registry.xregistry.model.definition.ResourceDefinition;
import org.eclipse.edc.registry.xregistry.model.typed.TypeFactory;
import org.eclipse.edc.registry.xregistry.model.typed.TypedResource;

import java.util.Map;

/**
 * Typed view of schema resources.
 */
public class TypedSchemaResource extends TypedResource<TypedSchemaVersion> {

    public Builder toBuilder() {
        return Builder.newInstance()
                .untyped(untyped)
                .definition(definition)
                .typeFactory(typeFactory);
    }

    public TypedSchemaResource(Map<String, Object> untyped, ResourceDefinition definition, TypeFactory typeFactory) {
        super(untyped, definition, typeFactory);
    }

    @Override
    protected TypedSchemaVersion createVersion(Map<String, Object> untypedVersion) {
        return new TypedSchemaVersion(untypedVersion, definition.getVersionDefinition(), typeFactory);
    }

    public static class Builder extends TypedResource.Builder<TypedSchemaVersion, Builder> {

        public static Builder newInstance() {
            return new Builder();
        }

        public TypedSchemaResource build() {
            validate();
            return new TypedSchemaResource(untyped, definition, typeFactory);
        }
    }
}
