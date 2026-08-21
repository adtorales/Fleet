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

package org.eclipse.edc.registry.reconciler.policy;

import org.eclipse.edc.connector.controlplane.services.spi.policydefinition.PolicyDefinitionService;
import org.eclipse.edc.connector.controlplane.transform.edc.policy.to.JsonObjectToPolicyDefinitionTransformer;
import org.eclipse.edc.connector.controlplane.transform.odrl.OdrlTransformersFactory;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.registry.spi.reconciler.ResourceReconcilerRegistry;
import org.eclipse.edc.registry.xregistry.model.definition.RegistrySpecification;
import org.eclipse.edc.registry.xregistry.model.typed.TypeFactory;
import org.eclipse.edc.registry.xregistry.policy.model.typed.TypedPolicyResource;
import org.eclipse.edc.registry.xregistry.schema.model.typed.TypedSchemaResource;
import org.eclipse.edc.registry.xregistry.validation.content.PolicyContentValidator;
import org.eclipse.edc.registry.xregistry.validation.content.PolicyContentValidatorImpl;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;

import static org.eclipse.edc.registry.xregistry.policy.model.definition.RegistryPolicyDefinitions.createPolicyGroupDefinition;
import static org.eclipse.edc.registry.xregistry.schema.model.definition.RegistrySchemaDefinitions.createSchemaGroupDefinition;

/**
 * Loads XRegistry policy extensions.
 */
public class ReconcilerPolicyExtension implements ServiceExtension {

    @Inject
    private PolicyDefinitionService policyService;

    @Inject
    private RegistrySpecification specification;

    @Inject
    private ResourceReconcilerRegistry reconcilerRegistry;

    @Inject
    private TypeFactory typeFactory;

    @Inject
    private JsonLd jsonLd;

    @Inject
    private TypeTransformerRegistry typeTransformerRegistry;

    @Inject
    private Monitor monitor;

    @Override
    public String name() {
        return "Policy Reconciler";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        specification.registerGroup(createPolicyGroupDefinition());
        specification.registerGroup(createSchemaGroupDefinition());
        typeFactory.registerResource("policy", TypedPolicyResource::new);
        typeFactory.registerResource("schema", TypedSchemaResource::new);

        ParticipantIdMapper identityMapper = new ParticipantIdMapper() {
            @Override
            public String toIri(String id) {
                return id;
            }

            @Override
            public String fromIri(String iri) {
                return iri;
            }
        };
        OdrlTransformersFactory.jsonObjectToOdrlTransformers(identityMapper)
                .forEach(typeTransformerRegistry::register);
        typeTransformerRegistry.register(new JsonObjectToPolicyDefinitionTransformer());

        PolicyContentValidator contentValidator = new PolicyContentValidatorImpl();
        reconcilerRegistry.registerReconciler(new PolicyResourceReconciler(policyService, jsonLd, typeTransformerRegistry, contentValidator, monitor));
    }


}
