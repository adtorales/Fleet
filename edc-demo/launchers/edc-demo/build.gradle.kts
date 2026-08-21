import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

plugins {
    `java-library`
    application
    id("com.gradleup.shadow") version "9.6.0"
}

val txVersion: String by project
val fleetVersion: String by project

dependencies {
    implementation("org.eclipse.tractusx.edc:edc-controlplane-postgresql-hashicorp-vault:$txVersion")
    implementation("org.eclipse.edc:reconciler-core:$fleetVersion")
    implementation("org.eclipse.edc:reconciler-policy:$fleetVersion")
    implementation(project(":extensions:reconciler-status"))
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    mergeServiceFiles()
    transform(Log4j2PluginsCacheFileTransformer::class.java)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    archiveFileName.set("edc-demo.jar")
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}
