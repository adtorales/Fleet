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
 *       Metaform Systems - initial API and implementation
 *
 */

package org.eclipse.edc.registry.policy.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import land.oras.ContainerRef;
import land.oras.Layer;
import land.oras.Registry;
import org.eclipse.edc.registry.xregistry.model.definition.GroupDefinition;
import org.eclipse.edc.registry.xregistry.model.definition.ResourceDefinition;
import org.eclipse.edc.registry.xregistry.model.typed.TypeFactory;
import org.eclipse.edc.registry.xregistry.model.typed.TypedGroup;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Downloads a compact xRegistry policy catalog artifact from an OCI registry and converts it to typed groups.
 */
public class OciPolicyGroupLoader implements PolicyGroupLoader {
    private final ContainerRef containerRef;
    private final String username;
    private final String password;
    private final boolean insecure;
    private final GroupDefinition groupDefinition;
    private final ResourceDefinition resourceDefinition;
    private final TypeFactory typeFactory;
    private final ObjectMapper objectMapper;
    private final Monitor monitor;

    public OciPolicyGroupLoader(String artifactReference, String username, String password, boolean insecure,
                                GroupDefinition groupDefinition, ResourceDefinition resourceDefinition, TypeFactory typeFactory,
                                ObjectMapper objectMapper, Monitor monitor) {
        this.containerRef = ContainerRef.parse(requireNonNull(artifactReference, "artifactReference"));
        this.username = username;
        this.password = password;
        this.insecure = insecure;
        this.groupDefinition = requireNonNull(groupDefinition, "groupDefinition");
        this.resourceDefinition = requireNonNull(resourceDefinition, "resourceDefinition");
        this.typeFactory = requireNonNull(typeFactory, "typeFactory");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper");
        this.monitor = requireNonNull(monitor, "monitor");
    }

    @Override
    public Collection<TypedGroup> loadGroups() {
        var pullDirectory = createWorkingDirectory();
        try {
            monitor.info("Pulling policy catalog artifact from OCI: " + containerRef);
            pullArtifact(createRegistry(), pullDirectory);
            monitor.info("OCI pull completed. Pulled content snapshot:%n%s".formatted(describeDirectory(pullDirectory)));

            var archive = findArchive(pullDirectory);
            if (archive != null) {
                monitor.info("Detected packaged policy catalog archive after OCI pull: " + archive);
                var groups = new ArchivePolicyGroupLoader(archive, groupDefinition, resourceDefinition, typeFactory, objectMapper, monitor)
                        .loadGroups();
                monitor.info("Loaded %d policy group(s) from OCI archive %s".formatted(groups.size(), archive));
                return groups;
            }

            var catalogRoot = findCatalogRoot(pullDirectory);
            if (catalogRoot != null) {
                monitor.info("Using unpacked OCI policy catalog directory: " + catalogRoot);
                var groups = new DirectoryPolicyGroupLoader(catalogRoot, groupDefinition, resourceDefinition, typeFactory, objectMapper, monitor)
                        .loadGroups();
                monitor.info("Loaded %d policy group(s) from unpacked OCI directory %s".formatted(groups.size(), catalogRoot));
                return groups;
            }

            monitor.warning("No .tar.gz policy catalog archive or unpacked catalog directory found after OCI pull from " + containerRef);
            return java.util.List.of();
        } finally {
            deleteDirectoryQuietly(pullDirectory);
        }
    }

    private void pullArtifact(Registry registry, Path pullDirectory) {
        try {
            var manifest = registry.getManifest(containerRef);
            var layers = manifest.getLayers();
            if (layers == null || layers.isEmpty()) {
                monitor.warning("OCI manifest does not contain any layers for " + containerRef);
                return;
            }

            for (var layer : layers) {
                var destination = resolveLayerDestination(layer, pullDirectory);
                var parent = destination.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                monitor.info("Downloading OCI layer to " + destination);
                registry.fetchBlob(containerRef.withDigest(layer.getDigest()), destination);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare local files for OCI artifact " + containerRef, e);
        }
    }

    private Registry createRegistry() {
        var builder = Registry.builder();
        if (insecure) {
            builder.insecure();
        }
        if (username != null && !username.isBlank() && password != null) {
            builder.defaults(username, password);
        }
        return builder.build();
    }

    private Path createWorkingDirectory() {
        try {
            return Files.createTempDirectory("fleet-policy-oci-");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create temp directory for OCI pull", e);
        }
    }

    private Path findArchive(Path directory) {
        try (var paths = Files.walk(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".tar.gz"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to inspect OCI pull directory " + directory, e);
        }
    }

    private Path findCatalogRoot(Path directory) {
        try (var paths = Files.walk(directory)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(this::looksLikeCatalogRoot)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to inspect OCI pull directory " + directory, e);
        }
    }

    private boolean looksLikeCatalogRoot(Path candidate) {
        return Files.isDirectory(candidate.resolve("policies")) || Files.isDirectory(candidate.resolve("schemas"));
    }

    private Path resolveLayerDestination(Layer layer, Path pullDirectory) {
        var annotations = layer.getAnnotations();
        var title = annotations == null ? null : annotations.get("org.opencontainers.image.title");
        var filename = title == null || title.isBlank() ? layer.getDigest() : title;
        return pullDirectory.resolve(filename).normalize();
    }

    private String describeDirectory(Path directory) {
        try (var paths = Files.walk(directory)) {
            return paths.limit(50)
                    .map(path -> directory.relativize(path).toString())
                    .map(path -> path.isBlank() ? "." : path)
                    .collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            return "<failed to inspect pulled OCI directory: " + e.getMessage() + ">";
        }
    }

    private void deleteDirectoryQuietly(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            monitor.warning("Failed to delete temporary OCI file: " + path);
                        }
                    });
        } catch (IOException e) {
            monitor.warning("Failed to clean temporary OCI directory: " + directory);
        }
    }
}
