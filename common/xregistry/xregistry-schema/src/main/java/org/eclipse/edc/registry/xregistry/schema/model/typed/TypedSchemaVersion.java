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

import org.eclipse.edc.registry.xregistry.model.definition.VersionDefinition;
import org.eclipse.edc.registry.xregistry.model.typed.TypeFactory;
import org.eclipse.edc.registry.xregistry.model.typed.TypedVersion;

import java.util.Map;

import static org.eclipse.edc.registry.xregistry.schema.model.definition.RegistrySchemaDefinitions.FORMAT;
import static org.eclipse.edc.registry.xregistry.schema.model.definition.RegistrySchemaDefinitions.SCHEMA;
import static org.eclipse.edc.registry.xregistry.schema.model.definition.RegistrySchemaDefinitions.SCHEMA_BASE64;

/**
 * A typed view of a schema resource version.
 */
public class TypedSchemaVersion extends TypedVersion {

    public String getFormat() {
        return getString(FORMAT);
    }

    public String getSchema() {
        return getString(SCHEMA);
    }

    public String getSchemaBase64() {
        return getString(SCHEMA_BASE64);
    }

    protected TypedSchemaVersion(Map<String, Object> untyped, VersionDefinition definition, TypeFactory typeFactory) {
        super(untyped, definition, typeFactory);
    }

    public Builder toBuilder() {
        return Builder.newInstance()
                .untyped(untyped)
                .definition(definition)
                .typeFactory(typeFactory);
    }

    public static class Builder extends TypedVersion.Builder<VersionDefinition, Builder> {

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder format(String format) {
            checkModifiableState();
            untyped.put(FORMAT, format);
            return this;
        }

        public Builder schema(String schema) {
            checkModifiableState();
            untyped.put(SCHEMA, schema);
            return this;
        }

        public Builder schemaBase64(String schemaBase64) {
            checkModifiableState();
            untyped.put(SCHEMA_BASE64, schemaBase64);
            return this;
        }

        public TypedSchemaVersion build() {
            validate();
            return new TypedSchemaVersion(untyped, definition, typeFactory);
        }

        private Builder() {
            super();
        }
    }
}
