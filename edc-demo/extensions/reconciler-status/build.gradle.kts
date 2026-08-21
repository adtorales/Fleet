plugins {
    `java-library`
}

val fleetVersion: String by project
val edcVersion: String by project

dependencies {
    implementation("org.eclipse.edc:reconciler-spi:$fleetVersion")
    implementation("org.eclipse.edc:web-spi:$edcVersion")
    implementation("jakarta.ws.rs:jakarta.ws.rs-api:4.0.0")
}
