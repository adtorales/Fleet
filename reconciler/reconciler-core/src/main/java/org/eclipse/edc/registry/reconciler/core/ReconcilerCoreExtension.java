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

package org.eclipse.edc.registry.reconciler.core;

import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.registry.reconciler.core.manager.ReconciliationManager;
import org.eclipse.edc.registry.reconciler.core.registry.ResourceReconcilerRegistryImpl;
import org.eclipse.edc.registry.spi.reconciler.ResourceReconcilerRegistry;
import org.eclipse.edc.registry.xregistry.model.definition.RegistrySpecification;
import org.eclipse.edc.registry.xregistry.model.typed.TypeFactory;
import org.eclipse.edc.registry.xregistry.model.typed.TypeFactoryImpl;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Loads core services that handle resource reconciliation.
 */
public class ReconcilerCoreExtension implements ServiceExtension {
    @Setting(description = "Registry location", key = "edc.registry")
    private String registryUrl;

    @Setting(description = "Interval in seconds between reconciliation cycles", key = "edc.reconciler.interval.seconds", defaultValue = "60")
    private int intervalSeconds;

    @Setting(description = "Initial delay in seconds before the first reconciliation cycle", key = "edc.reconciler.initial.delay.seconds", defaultValue = "10")
    private int initialDelaySeconds;

    @Inject
    private EdcHttpClient httpClient;

    @Inject
    private TypeManager typeManager;

    @Inject
    private Monitor monitor;

    private RegistrySpecification specification;
    private TypeFactory typeFactory;
    private ResourceReconcilerRegistry resourceRegistry;
    private ScheduledExecutorService scheduler;
    private ReconciliationManager reconciliationManager;

    @Override
    public String name() {
        return "Core Reconciler";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        reconciliationManager = new ReconciliationManager(
                getResourceRegistry(),
                getSpecification(),
                typeFactory(),
                httpClient,
                typeManager,
                monitor
        );
    }

    @Override
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r);
            thread.setName("fleet-reconciler");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                reconciliationManager.run();
            } catch (Throwable throwable) {
                monitor.severe("Reconciliation cycle failed unexpectedly.", throwable);
            }
        }, initialDelaySeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @Provider
    public TypeFactory typeFactory() {
        if (typeFactory == null) {
            typeFactory = new TypeFactoryImpl();
        }
        return typeFactory;
    }

    @Provider
    public ResourceReconcilerRegistry getResourceRegistry() {
        if (resourceRegistry == null) {
            resourceRegistry = new ResourceReconcilerRegistryImpl();
        }
        return resourceRegistry;
    }

    @Provider
    public RegistrySpecification getSpecification() {
        if (specification == null) {
            specification = new RegistrySpecification(registryUrl);
        }
        return specification;
    }

}
