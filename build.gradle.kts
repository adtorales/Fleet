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
 *       Metaform Systems - initial implementation
 *
 */

import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.plugins.signing.Sign

plugins {
    `java-library`
    alias(libs.plugins.edc.build)
}

val edcScmConnection: String by project
val edcScmUrl: String by project
val edcVersion = libs.versions.edc

buildscript {
    dependencies {
        val edcVersion: String = libs.versions.edc.asProvider().get()
        classpath("org.eclipse.edc.autodoc:org.eclipse.edc.autodoc.gradle.plugin:$edcVersion")
    }
}

val edcBuildId = libs.plugins.edc.build.get().pluginId
val publishingToGitHubPackages = providers.gradleProperty("githubPackagesToken").isPresent

allprojects {
    // Skip edc-build for the Gradle plugin module to avoid a duplicate plainJavadocJar task
    // until the upstream edc-build plugin is compatible with java-gradle-plugin + Gradle 9.6
    if (path != ":tooling:xregistry-oci-plugin") {
        apply(plugin = edcBuildId)
    }
    apply(plugin = "org.eclipse.edc.autodoc")

    configure<org.eclipse.edc.plugins.autodoc.AutodocExtension> {
        processorVersion.set(edcVersion.asProvider())
        outputDirectory.set(project.layout.buildDirectory.asFile.get())
    }

    if (path != ":tooling:xregistry-oci-plugin") {
        configure<org.eclipse.edc.plugins.edcbuild.extensions.BuildExtension> {
            pom {
                scmConnection.set(edcScmConnection)
                scmUrl.set(edcScmUrl)
            }
        }
    }

    if (path != ":tooling:xregistry-oci-plugin") {
        configure<CheckstyleExtension> {
            configFile = rootProject.file("resources/checkstyle-config.xml")
            configDirectory.set(rootProject.file("resources"))
        }
    }

    afterEvaluate {
        plugins.withType<PublishingPlugin> {
            configure<PublishingExtension> {
                repositories {
                    maven {
                        name = "GitHubPackages"
                        val owner = providers.gradleProperty("githubPackagesOwner").orNull
                                ?: System.getenv("GITHUB_REPOSITORY_OWNER")
                                ?: "adtorales"
                        val repo = providers.gradleProperty("githubPackagesRepo").orNull
                                ?: System.getenv("GITHUB_REPOSITORY_NAME")
                                ?: "Fleet"
                        url = uri("https://maven.pkg.github.com/$owner/$repo")
                        credentials {
                            username = providers.gradleProperty("githubPackagesUsername").orNull
                                    ?: System.getenv("GITHUB_ACTOR")
                                    ?: ""
                            password = providers.gradleProperty("githubPackagesToken").orNull
                                    ?: System.getenv("GITHUB_TOKEN")
                                    ?: ""
                        }
                    }
                }
            }
        }
    }
}

// edc-build configures GPG signatures for Maven Central. GitHub Packages does
// not require them, and CI has no GPG key. Remove signature artifacts only
// when the GitHub Packages publishing credentials are explicitly supplied.
gradle.projectsEvaluated {
    if (publishingToGitHubPackages) {
        allprojects {
            tasks.withType<Sign>().configureEach {
                enabled = false
            }
            extensions.findByType(PublishingExtension::class.java)
                    ?.publications
                    ?.withType(MavenPublication::class.java)
                    ?.configureEach {
                        artifacts.removeIf { artifact -> artifact.file.name.endsWith(".asc") }
                    }
        }
    }
}
