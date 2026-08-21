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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.services.spi.policydefinition.PolicyDefinitionService;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.registry.spi.reconciler.ReconciliationContext;
import org.eclipse.edc.registry.xregistry.model.definition.RegistrySpecification;
import org.eclipse.edc.registry.xregistry.model.typed.TypeFactoryImpl;
import org.eclipse.edc.registry.xregistry.model.typed.TypedRegistry;
import org.eclipse.edc.registry.xregistry.policy.model.typed.TypedPolicyResource;
import org.eclipse.edc.registry.xregistry.validation.content.PolicyContentValidator;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.eclipse.edc.registry.reconciler.policy.Definitions.POLICY_REGISTRY;
import static org.eclipse.edc.registry.xregistry.policy.model.definition.RegistryPolicyDefinitions.createPolicyGroupDefinition;
import static org.eclipse.edc.spi.result.ServiceResult.success;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PolicyResourceReconcilerTest {
    private PolicyResourceReconciler reconciler;
    private ObjectMapper mapper;
    private TypeFactoryImpl typeFactory;
    private RegistrySpecification specification;
    private PolicyDefinitionService policyService;

    @Test
    void verify_reconciliation() throws JsonProcessingException {
        var untyped = mapper.readValue(POLICY_REGISTRY, Map.class);
        @SuppressWarnings("unchecked")
        var typed = TypedRegistry.Builder.newInstance()
                .untyped(untyped)
                .definition(specification.getRegistryDefinition())
                .typeFactory(typeFactory)
                .build();

        reconciler.reconcile(typed, new ReconciliationContext());
    }

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        policyService = mock(PolicyDefinitionService.class);
        when(policyService.search(any())).thenReturn(success(List.of()));
        reconciler = new PolicyResourceReconciler(
                policyService,
                mock(JsonLd.class),
                mock(TypeTransformerRegistry.class),
                mock(PolicyContentValidator.class),
                mock(Monitor.class));
        typeFactory = new TypeFactoryImpl();
        specification = new RegistrySpecification("https://test.com");
        specification.registerGroup(createPolicyGroupDefinition());
        typeFactory.registerResource("policy", TypedPolicyResource::new);
    }
}
