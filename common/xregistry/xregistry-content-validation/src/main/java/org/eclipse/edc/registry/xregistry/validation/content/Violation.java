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

/**
 * A single content-validation violation.
 */
public class Violation {
    private final String tier;
    private final String code;
    private final String message;

    public Violation(String tier, String code, String message) {
        this.tier = tier;
        this.code = code;
        this.message = message;
    }

    public String getTier() {
        return tier;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "%s[%s]: %s".formatted(tier, code, message);
    }
}
