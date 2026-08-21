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

package org.eclipse.edc.registry.oci.loader;

import land.oras.ContainerRef;
import land.oras.Layer;
import land.oras.Registry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.GZIPInputStream;

import static java.util.Objects.requireNonNull;

/**
 * Pulls OCI artifacts containing xRegistry compact catalogs and extracts their layers to a local directory.
 */
public class OciArtifactLoader {
    private final String username;
    private final String password;
    private final boolean insecure;
    private final Monitor monitor;

    public OciArtifactLoader(String username, String password, boolean insecure, Monitor monitor) {
        this.username = username;
        this.password = password;
        this.insecure = insecure;
        this.monitor = requireNonNull(monitor, "monitor");
    }

    /**
     * Fetches the manifest digest for the referenced artifact.
     */
    public String fetchDigest(ContainerRef containerRef) {
        var registry = createRegistry();
        var manifest = registry.getManifest(containerRef);
        return manifest.getDigest();
    }

    /**
     * Pulls the artifact layers to a temporary directory and returns the extracted root path.
     */
    public Path pullArtifact(ContainerRef containerRef) {
        var pullDirectory = createWorkingDirectory(containerRef);
        var registry = createRegistry();
        try {
            monitor.info("Pulling OCI artifact: " + containerRef);
            var manifest = registry.getManifest(containerRef);
            var layers = manifest.getLayers();
            if (layers == null || layers.isEmpty()) {
                monitor.warning("OCI manifest does not contain any layers for " + containerRef);
                return pullDirectory;
            }

            for (var layer : layers) {
                var destination = resolveLayerDestination(layer, pullDirectory);
                var parent = destination.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                monitor.info("Downloading OCI layer to " + destination);
                registry.fetchBlob(containerRef.withDigest(layer.getDigest()), destination);

                if (destination.getFileName().toString().endsWith(".tar.gz")) {
                    extractTarGz(destination, pullDirectory);
                }
            }

            monitor.info("OCI pull completed for " + containerRef);
            return pullDirectory;
        } catch (Exception e) {
            deleteDirectoryQuietly(pullDirectory);
            throw new IllegalStateException("Failed to pull OCI artifact " + containerRef, e);
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

    private Path createWorkingDirectory(ContainerRef containerRef) {
        try {
            var name = containerRef.getRepository().replaceAll("[^a-zA-Z0-9]", "-");
            return Files.createTempDirectory("fleet-oci-registry-" + name + "-");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create temp directory for OCI pull " + containerRef, e);
        }
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
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
            monitor.warning("Failed to walk temporary OCI directory: " + directory);
        }
    }

    private void extractTarGz(Path sourceArchive, Path targetDirectory) {
        try (InputStream fileStream = Files.newInputStream(sourceArchive);
                InputStream gzipStream = new GZIPInputStream(fileStream);
                TarArchiveInputStream tarStream = new TarArchiveInputStream(gzipStream)) {

            TarArchiveEntry entry;
            while ((entry = tarStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                var targetFile = targetDirectory.resolve(entry.getName()).normalize();
                if (!targetFile.startsWith(targetDirectory)) {
                    throw new IllegalStateException("Archive entry escapes target directory: " + entry.getName());
                }

                var parent = targetFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                Files.deleteIfExists(targetFile);
                try (var output = Files.newOutputStream(targetFile)) {
                    IOUtils.copy(tarStream, output);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract OCI layer archive " + sourceArchive, e);
        }
    }

    private Path resolveLayerDestination(Layer layer, Path pullDirectory) {
        var annotations = layer.getAnnotations();
        var title = annotations == null ? null : annotations.get("org.opencontainers.image.title");
        var filename = title == null || title.isBlank() ? layer.getDigest() : title;

        // guard against path traversal
        var target = pullDirectory.resolve(filename).normalize();
        if (!target.startsWith(pullDirectory)) {
            throw new IllegalStateException("OCI layer entry escapes target directory: " + filename);
        }
        return target;
    }
}
