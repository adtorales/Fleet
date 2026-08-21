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

import java.util.List;
import java.util.Map;

/**
 * Validates a policy JSON-LD string against an ordered list of JSON Schemas (tiers).
 */
public interface PolicyContentValidator {

    /**
     * Validates the given policy document without policy-type-specific checks.
     *
     * @param policyJson the policy JSON-LD document
     * @param tiers ordered list of (tier name, JSON Schema document)
     * @return a list of violations; empty if valid
     */
    default List<Violation> validate(String policyJson, List<Map.Entry<String, String>> tiers) {
        return validate(policyJson, false, false, tiers);
    }

    /**
     * Validates the given policy document against all provided tiers.
     *
     * @param policyJson the policy JSON-LD document
     * @param accessPolicy whether the xRegistry version is an access policy
     * @param controlPolicy whether the xRegistry version is a control policy
     * @param tiers ordered list of (tier name, JSON Schema document)
     * @return a list of violations; empty if valid
     */
    List<Violation> validate(String policyJson, boolean accessPolicy, boolean controlPolicy, List<Map.Entry<String, String>> tiers);
}
