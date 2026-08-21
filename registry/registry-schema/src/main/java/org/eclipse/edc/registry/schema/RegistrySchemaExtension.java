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

package org.eclipse.edc.registry.schema;

import org.eclipse.edc.registry.xregistry.model.definition.RegistrySpecification;
import org.eclipse.edc.registry.xregistry.model.typed.TypeFactory;
import org.eclipse.edc.registry.xregistry.schema.model.typed.TypedSchemaResource;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import static org.eclipse.edc.registry.xregistry.schema.model.definition.RegistrySchemaDefinitions.createSchemaGroupDefinition;

/**
 * Contributes base schema extensions.
 */
public class RegistrySchemaExtension implements ServiceExtension {

    @Inject
    private RegistrySpecification specification;

    @Inject
    private TypeFactory typeFactory;

    @Override
    public String name() {
        return "Schema Registry";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        specification.registerGroup(createSchemaGroupDefinition());
        typeFactory.registerResource("schema", TypedSchemaResource::new);
    }
}
