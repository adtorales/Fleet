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

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.eclipse.edc.registry.xregistry.model.definition.GroupDefinition;
import org.eclipse.edc.registry.xregistry.model.definition.ResourceDefinition;
import org.eclipse.edc.registry.xregistry.model.typed.TypeFactory;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * Loads policy groups from a compact xRegistry tar.gz artifact.
 */
public class ArchivePolicyGroupLoader extends DirectoryPolicyGroupLoader {
    private final Path archivePath;
    private final Monitor monitor;

    public ArchivePolicyGroupLoader(Path archivePath, GroupDefinition groupDefinition, ResourceDefinition resourceDefinition,
                                    TypeFactory typeFactory, com.fasterxml.jackson.databind.ObjectMapper objectMapper, Monitor monitor) {
        super(createWorkingDirectory(archivePath, monitor), groupDefinition, resourceDefinition, typeFactory, objectMapper, monitor);
        this.archivePath = archivePath;
        this.monitor = monitor;
    }

    @Override
    public java.util.Collection<org.eclipse.edc.registry.xregistry.model.typed.TypedGroup> loadGroups() {
        if (!Files.exists(archivePath)) {
            monitor.warning("Policy catalog archive does not exist: " + archivePath);
            return java.util.List.of();
        }

        var workingDirectory = rootDirectory();
        cleanDirectory(workingDirectory);
        extractArchive(archivePath, workingDirectory);
        return super.loadGroups();
    }

    private static Path createWorkingDirectory(Path archivePath, Monitor monitor) {
        try {
            var directory = Files.createTempDirectory("fleet-policy-catalog-");
            directory.toFile().deleteOnExit();
            return directory;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create temp directory for archive " + archivePath, e);
        }
    }

    private void extractArchive(Path sourceArchive, Path targetDirectory) {
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
            throw new IllegalStateException("Failed to extract policy catalog archive " + sourceArchive, e);
        }
    }

    private void cleanDirectory(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                    .filter(path -> !path.equals(directory))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to clean temp directory " + directory, e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to walk temp directory " + directory, e);
        }
    }
}
