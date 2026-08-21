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

package org.eclipse.edc.registry.xregistry.validation.content;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolicyContentValidatorImplTest {
    private static final String PERMISSIVE_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "urn:tap74:test:tier-1",
              "type": "object"
            }
            """;
    private static final String ODRL_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "urn:tap74:test:odrl",
              "type": "object",
              "required": ["@type", "permission"],
              "properties": {
                "@type": {"const": "odrl:Set"},
                "permission": {"type": "array", "minItems": 1}
              }
            }
            """;
    private static final String CONTROL_POLICY_WITHOUT_USAGE_PURPOSE = """
            {
              "@id": "cx/test/1.0",
              "@type": "odrl:Set",
              "permission": [{
                "action": "use",
                "constraint": [{
                  "leftOperand": "FrameworkAgreement",
                  "operator": "eq",
                  "rightOperand": "CX-2026"
                }]
              }]
            }
            """;

    @Test
    void rejectsControlPolicyWithoutUsagePurpose() {
        var violations = new PolicyContentValidatorImpl().validate(
                CONTROL_POLICY_WITHOUT_USAGE_PURPOSE,
                false,
                true,
                List.of(Map.entry("tier-1", PERMISSIVE_SCHEMA)));

        assertEquals(1, violations.size());
        assertEquals("MISSING_USAGE_PURPOSE", violations.get(0).getCode());
    }

    @Test
    void validatesTheOdrlPolicyInsideAnEdcPolicyDefinition() {
        var wrappedPolicyDefinition = """
                {
                  "@type": "edc:PolicyDefinition",
                  "edc:policy": {
                    "@id": "cx/test/1.0",
                    "@type": "odrl:Set",
                    "permission": [{
                      "action": "use",
                      "constraint": [{
                        "leftOperand": "UsagePurpose",
                        "operator": "eq",
                        "rightOperand": "analysis"
                      }]
                    }]
                  }
                }
                """;

        var violations = new PolicyContentValidatorImpl().validate(
                wrappedPolicyDefinition,
                false,
                true,
                List.of(Map.entry("tier-1", ODRL_SCHEMA)));

        assertEquals(List.of(), violations);
    }
}
