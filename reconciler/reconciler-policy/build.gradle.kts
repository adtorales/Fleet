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

plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    implementation(project(":common:xregistry:xregistry-lib"))
    implementation(project(":reconciler:reconciler-spi"))
    implementation(project(":common:xregistry:xregistry-policy"))
    implementation(project(":common:xregistry:xregistry-schema"))
    implementation(project(":common:xregistry:xregistry-content-validation"))
    implementation(libs.edc.boot)
    implementation(libs.edc.spi.policy)
    implementation(libs.edc.spi.controlplane)
    implementation(libs.edc.spi.jsonld)
    implementation(libs.edc.spi.transform)
    implementation(libs.edc.spi.transaction)
    implementation(libs.edc.controlplane.transform)
    implementation(libs.edc.spi.participant)
    runtimeOnly(libs.edc.controlplane.aggregate)
    runtimeOnly(libs.edc.jsonld)
    implementation(libs.jackson.databind)
    testImplementation(libs.edc.junit)
}
