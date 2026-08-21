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

package org.eclipse.edc.demo.reconciler.status.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.registry.spi.reconciler.ReconciliationReport;
import org.eclipse.edc.registry.spi.reconciler.ResourceReconciler;
import org.eclipse.edc.registry.spi.reconciler.ResourceReconcilerRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.ok;

/**
 * Exposes the last reconciliation reports via a REST endpoint.
 */
@Path("/reconciler")
public class ReconcilerStatusApiController {

    private final ResourceReconcilerRegistry reconcilerRegistry;

    public ReconcilerStatusApiController(ResourceReconcilerRegistry reconcilerRegistry) {
        this.reconcilerRegistry = reconcilerRegistry;
    }

    @GET
    @Path("/status")
    @Produces(APPLICATION_JSON)
    public Response getStatus() {
        var reports = new ArrayList<Map<String, Object>>();
        reconcilerRegistry.getReconcilers().stream()
                .map(this::toReport)
                .forEach(reports::add);
        return ok(Map.of("reconcilers", reports)).build();
    }

    private Map<String, Object> toReport(ResourceReconciler reconciler) {
        var report = reconciler.lastReport();
        if (report == null) {
            report = new ReconciliationReport();
            report.setResourceType(reconciler.resourceType());
        }
        var map = new LinkedHashMap<String, Object>();
        map.put("resourceType", report.getResourceType());
        map.put("created", report.getCreated());
        map.put("updated", report.getUpdated());
        map.put("deleted", report.getDeleted());
        map.put("retained", report.getRetained());
        map.put("unchanged", report.getUnchanged());
        map.put("invalid", report.getInvalid());
        map.put("failed", report.getFailed());
        map.put("errors", new ArrayList<>(report.getErrors()));
        return map;
    }
}
