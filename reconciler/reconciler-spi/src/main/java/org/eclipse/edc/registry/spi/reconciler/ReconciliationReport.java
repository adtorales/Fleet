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

package org.eclipse.edc.registry.spi.reconciler;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the outcome of a single reconciliation run.
 */
public class ReconciliationReport {
    private String resourceType;
    private int created;
    private int updated;
    private int deleted;
    private int retained;
    private int unchanged;
    private int invalid;
    private int failed;
    private final List<String> errors = new ArrayList<>();

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public int getCreated() {
        return created;
    }

    public void incrementCreated() {
        created++;
    }

    public int getUpdated() {
        return updated;
    }

    public void incrementUpdated() {
        updated++;
    }

    public int getDeleted() {
        return deleted;
    }

    public void incrementDeleted() {
        deleted++;
    }

    public int getRetained() {
        return retained;
    }

    public void incrementRetained() {
        retained++;
    }

    public int getUnchanged() {
        return unchanged;
    }

    public void incrementUnchanged() {
        unchanged++;
    }

    public int getInvalid() {
        return invalid;
    }

    public void incrementInvalid() {
        invalid++;
    }

    public int getFailed() {
        return failed;
    }

    public void incrementFailed() {
        failed++;
    }

    public void addError(String error) {
        errors.add(error);
        failed++;
    }

    public List<String> getErrors() {
        return errors;
    }
}
