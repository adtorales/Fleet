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

package org.eclipse.edc.demo.reconciler.status;

import org.eclipse.edc.demo.reconciler.status.api.ReconcilerStatusApiController;
import org.eclipse.edc.registry.spi.reconciler.ResourceReconcilerRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;

/**
 * Registers the reconciler status API.
 */
public class ReconcilerStatusExtension implements ServiceExtension {
    private static final String API_CONTEXT = "default";

    @Inject
    private WebService webService;

    @Inject
    private ResourceReconcilerRegistry reconcilerRegistry;

    @Override
    public String name() {
        return "Reconciler Status";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        webService.registerResource(API_CONTEXT, new ReconcilerStatusApiController(reconcilerRegistry));
    }
}
