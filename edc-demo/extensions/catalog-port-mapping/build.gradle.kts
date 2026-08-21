plugins {
    `java-library`
}

val edcVersion: String by project

dependencies {
    implementation("org.eclipse.edc:web-spi:$edcVersion")
    implementation("org.eclipse.edc:boot-spi:$edcVersion")
}
