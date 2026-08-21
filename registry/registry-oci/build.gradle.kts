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

plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    implementation(project(":registry:registry-spi"))
    implementation(project(":common:xregistry:xregistry-lib"))
    implementation(project(":common:xregistry:xregistry-model"))
    implementation(project(":common:xregistry:xregistry-policy"))
    implementation(project(":common:xregistry:xregistry-schema"))
    implementation(project(":common:xregistry:xregistry-processor"))
    implementation("land.oras:oras-java-sdk:0.2.15")
    implementation(libs.jackson.databind)
    implementation(libs.edc.spi.core)
    implementation(libs.edc.lib.util)
    implementation("org.apache.commons:commons-compress:1.28.0")
}
