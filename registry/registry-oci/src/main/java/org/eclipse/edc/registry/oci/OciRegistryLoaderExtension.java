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

package org.eclipse.edc.registry.oci;

import com.fasterxml.jackson.databind.ObjectMapper;
import land.oras.ContainerRef;
import org.eclipse.edc.registry.oci.loader.OciArtifactLoader;
import org.eclipse.edc.registry.oci.loader.OciCatalogLoader;
import org.eclipse.edc.registry.oci.store.OciBackedResourceTypeStore;
import org.eclipse.edc.registry.server.spi.store.RegistryStore;
import org.eclipse.edc.registry.xregistry.model.definition.RegistrySpecification;
import org.eclipse.edc.registry.xregistry.model.typed.TypeFactory;
import org.eclipse.edc.registry.xregistry.policy.model.typed.TypedPolicyResource;
import org.eclipse.edc.registry.xregistry.schema.model.typed.TypedSchemaResource;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.util.concurrency.LockManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static org.eclipse.edc.registry.xregistry.policy.model.definition.RegistryPolicyDefinitions.createPolicyGroupDefinition;
import static org.eclipse.edc.registry.xregistry.policy.model.definition.RegistryPolicyDefinitions.createPolicyResourceDefinition;
import static org.eclipse.edc.registry.xregistry.schema.model.definition.RegistrySchemaDefinitions.createSchemaGroupDefinition;
import static org.eclipse.edc.registry.xregistry.schema.model.definition.RegistrySchemaDefinitions.createSchemaResourceDefinition;

/**
 * Loads xRegistry policy and schema catalogs from OCI artifacts and exposes them through the registry server.
 */
public class OciRegistryLoaderExtension implements ServiceExtension {

    @Setting(key = "edc.registry.oci.images", required = false,
            description = "Comma-separated list of OCI image references containing xRegistry catalogs")
    private String ociImages;

    @Setting(key = "edc.registry.oci.username", required = false,
            description = "Username for the OCI registry")
    private String ociUsername;

    @Setting(key = "edc.registry.oci.password", required = false,
            description = "Password or token for the OCI registry")
    private String ociPassword;

    @Setting(key = "edc.registry.oci.insecure", required = false, defaultValue = "false",
            description = "Enables insecure OCI registry access for local testing")
    private boolean ociInsecure;

    @Setting(key = "edc.registry.oci.poll.seconds", required = false, defaultValue = "60",
            description = "Poll interval in seconds for re-pulling OCI artifacts; 0 disables polling")
    private int ociPollSeconds;

    @Inject
    private RegistryStore registryStore;

    @Inject
    private RegistrySpecification registrySpecification;

    @Inject
    private TypeFactory typeFactory;

    @Inject
    private Monitor monitor;

    private List<ContainerRef> imageReferences = List.of();
    private OciArtifactLoader artifactLoader;
    private OciCatalogLoader catalogLoader;
    private OciBackedResourceTypeStore<TypedPolicyResource> policyStore;
    private OciBackedResourceTypeStore<TypedSchemaResource> schemaStore;
    private ScheduledExecutorService executorService;
    private final Map<ContainerRef, String> lastKnownDigests = new LinkedHashMap<>();

    @Override
    public String name() {
        return "OCI Registry Loader";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        imageReferences = parseImageReferences(ociImages);
        if (imageReferences.isEmpty()) {
            monitor.info("No OCI registry images configured. OCI registry loader will remain inactive.");
            return;
        }

        registrySpecification.registerGroup(createPolicyGroupDefinition());
        registrySpecification.registerGroup(createSchemaGroupDefinition());

        artifactLoader = new OciArtifactLoader(ociUsername, ociPassword, ociInsecure, monitor);
        catalogLoader = new OciCatalogLoader(
                imageReferences,
                artifactLoader,
                createPolicyGroupDefinition(),
                createPolicyResourceDefinition(),
                createSchemaGroupDefinition(),
                createSchemaResourceDefinition(),
                typeFactory,
                new ObjectMapper(),
                monitor
        );

        policyStore = new OciBackedResourceTypeStore<>(TypedPolicyResource.class, "policies",
                new LockManager(new ReentrantReadWriteLock()), catalogLoader);
        schemaStore = new OciBackedResourceTypeStore<>(TypedSchemaResource.class, "schemas",
                new LockManager(new ReentrantReadWriteLock()), catalogLoader);

        registryStore.register(policyStore);
        registryStore.register(schemaStore);

        var result = policyStore.reload();
        if (result.failed()) {
            monitor.warning("Initial OCI policy catalog reload failed: " + result.getFailureDetail());
        } else {
            logPolicyCatalogReload("Initial OCI policy catalog reload completed.");
        }

        result = schemaStore.reload();
        if (result.failed()) {
            monitor.warning("Initial OCI schema catalog reload failed: " + result.getFailureDetail());
        } else {
            logSchemaCatalogReload("Initial OCI schema catalog reload completed.");
        }
    }

    @Override
    public void start() {
        if (imageReferences.isEmpty() || ociPollSeconds <= 0) {
            return;
        }

        executorService = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "fleet-oci-registry");
            thread.setDaemon(true);
            return thread;
        });

        executorService.scheduleWithFixedDelay(this::pollAndReload, ociPollSeconds, ociPollSeconds, TimeUnit.SECONDS);
        monitor.info("Started OCI registry polling every " + ociPollSeconds + " second(s) for images: " +
                imageReferences.stream().map(ContainerRef::toString).collect(Collectors.joining(", ")));
    }

    @Override
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    monitor.warning("OCI registry executor did not terminate within 5 seconds");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void pollAndReload() {
        try {
            boolean changed = false;
            for (var ref : imageReferences) {
                var currentDigest = artifactLoader.fetchDigest(ref);
                var previousDigest = lastKnownDigests.get(ref);
                if (previousDigest == null || !previousDigest.equals(currentDigest)) {
                    changed = true;
                    lastKnownDigests.put(ref, currentDigest);
                }
            }

            if (changed) {
                monitor.info("OCI manifest digest changed for at least one configured image. Reloading catalogs.");
                var policyResult = policyStore.reload();
                if (policyResult.failed()) {
                    monitor.warning("OCI policy catalog reload failed: " + policyResult.getFailureDetail());
                } else {
                    logPolicyCatalogReload("OCI policy catalog reload completed.");
                }

                var schemaResult = schemaStore.reload();
                if (schemaResult.failed()) {
                    monitor.warning("OCI schema catalog reload failed: " + schemaResult.getFailureDetail());
                } else {
                    logSchemaCatalogReload("OCI schema catalog reload completed.");
                }
            }
        } catch (Throwable t) {
            monitor.warning("Unexpected error during OCI registry polling: " + formatException(t));
        }
    }

    private List<ContainerRef> parseImageReferences(String images) {
        if (images == null || images.isBlank()) {
            return List.of();
        }
        var references = new ArrayList<ContainerRef>();
        for (var raw : images.split(",")) {
            var trimmed = raw.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            references.add(ContainerRef.parse(trimmed));
        }
        return List.copyOf(references);
    }

    private void logPolicyCatalogReload(String message) {
        monitor.info("%s Loaded policy groups: %d, policies: %d."
                .formatted(message, policyStore.getTypedGroupCount(), policyStore.getTypedResourceCount()));
    }

    private void logSchemaCatalogReload(String message) {
        monitor.info("%s Loaded schema groups: %d, schemas: %d."
                .formatted(message, schemaStore.getTypedGroupCount(), schemaStore.getTypedResourceCount()));
    }

    private String formatException(Throwable exception) {
        var builder = new StringBuilder();
        var current = exception;

        while (current != null) {
            if (!builder.isEmpty()) {
                builder.append(" | caused by: ");
            }
            builder.append(current.getClass().getSimpleName());
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                builder.append(": ").append(current.getMessage());
            }
            current = current.getCause();
        }

        return builder.toString();
    }
}
