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

package org.eclipse.edc.registry.policy.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.registry.server.spi.store.RegistryStore;
import org.eclipse.edc.registry.xregistry.model.typed.TypeFactory;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.util.concurrency.LockManager;

import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.eclipse.edc.registry.xregistry.policy.model.definition.RegistryPolicyDefinitions.createPolicyGroupDefinition;
import static org.eclipse.edc.registry.xregistry.policy.model.definition.RegistryPolicyDefinitions.createPolicyResourceDefinition;

/**
 * Contributes an in-memory implementation of the policy resource store.
 */
public class RegistryPolicyMemoryExtension implements ServiceExtension {
    @Setting(key = "edc.registry.policy.catalog.oci.reference", required = false,
            description = "OCI reference for the packaged compact xRegistry policy catalog, for example ghcr.io/org/xregistry-policies:current")
    private String policyCatalogOciReference;

    @Setting(key = "edc.registry.policy.catalog.oci.username", required = false,
            description = "Username for the OCI registry hosting the policy catalog artifact")
    private String policyCatalogOciUsername;

    @Setting(key = "edc.registry.policy.catalog.oci.password", required = false,
            description = "Password or token for the OCI registry hosting the policy catalog artifact")
    private String policyCatalogOciPassword;

    @Setting(key = "edc.registry.policy.catalog.oci.insecure", required = false,
            description = "Enables insecure OCI registry access for local testing")
    private boolean policyCatalogOciInsecure;

    @Setting(key = "edc.registry.policy.catalog.archive", required = false,
            description = "Path to the packaged compact xRegistry policy catalog tar.gz artifact")
    private String policyCatalogArchive;

    @Setting(key = "edc.registry.policy.catalog.directory", required = false,
            description = "Directory containing the unpacked compact xRegistry policy catalog")
    private String policyCatalogDirectory;

    @Inject
    private RegistryStore registryStore;

    @Inject
    private TypeFactory typeFactory;

    @Inject
    private Monitor monitor;

    private InMemoryPolicyResourceTypeStore store;

    @Override
    public String name() {
        return "Policy Registry Memory";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        store = new InMemoryPolicyResourceTypeStore(new LockManager(new ReentrantReadWriteLock()), createLoader());
        registryStore.register(store);
        var result = store.reload();
        if (result.failed()) {
            monitor.warning("Initial policy catalog reload failed: " + result.getFailureDetail());
        } else {
            monitor.info("Initial policy catalog reload completed. Loaded policy group count: " + store.size());
        }
    }

    @Provider
    public InMemoryPolicyResourceTypeStore policyResourceTypeStore() {
        if (store == null) {
            store = new InMemoryPolicyResourceTypeStore(new LockManager(new ReentrantReadWriteLock()), createLoader());
        }
        return store;
    }

    private PolicyGroupLoader createLoader() {
        if (policyCatalogOciReference != null && !policyCatalogOciReference.isBlank()) {
            monitor.info("Using OCI policy catalog reference: " + policyCatalogOciReference);
            return new OciPolicyGroupLoader(
                    policyCatalogOciReference,
                    policyCatalogOciUsername,
                    policyCatalogOciPassword,
                    policyCatalogOciInsecure,
                    createPolicyGroupDefinition(),
                    createPolicyResourceDefinition(),
                    typeFactory,
                    new ObjectMapper(),
                    monitor
            );
        }

        if (policyCatalogArchive != null && !policyCatalogArchive.isBlank()) {
            monitor.info("Using packaged policy catalog archive: " + policyCatalogArchive);
            return new ArchivePolicyGroupLoader(
                    Path.of(policyCatalogArchive),
                    createPolicyGroupDefinition(),
                    createPolicyResourceDefinition(),
                    typeFactory,
                    new ObjectMapper(),
                    monitor
            );
        }

        if (policyCatalogDirectory == null || policyCatalogDirectory.isBlank()) {
            monitor.info("No policy catalog directory configured. Policy registry memory store will stay empty until a loader is configured.");
            return PolicyGroupLoader.EMPTY;
        }

        monitor.info("Using unpacked policy catalog directory: " + policyCatalogDirectory);
        return new DirectoryPolicyGroupLoader(
                Path.of(policyCatalogDirectory),
                createPolicyGroupDefinition(),
                createPolicyResourceDefinition(),
                typeFactory,
                new ObjectMapper(),
                monitor
        );
    }
}
