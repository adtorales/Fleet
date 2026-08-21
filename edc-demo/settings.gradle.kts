import java.util.Properties

rootProject.name = "tap74-demonstrator"

fun optionalPath(path: String?) = path?.takeIf { it.isNotBlank() }

val fleetLocalPath = providers.gradleProperty("fleetLocalPath")
    .orNull
    ?.let(::optionalPath)
val txLocalPath = providers.gradleProperty("txLocalPath")
    .orNull
    ?.let(::optionalPath)
val useLocalFleetBuild = providers.gradleProperty("useLocalFleetBuild")
    .orElse("false")
    .get()
    .toBoolean()
val useLocalTxBuild = providers.gradleProperty("useLocalTxBuild")
    .orElse("false")
    .get()
    .toBoolean()
val localSecretsPath = providers.gradleProperty("localSecretsPath")
    .orElse("local-secrets.properties")
    .get()

val fleetBuildDir = fleetLocalPath?.let(::file)
val txBuildDir = txLocalPath?.let(::file)
val localSecretsFile = file(localSecretsPath)
val fleetSecretsFile = fleetBuildDir?.resolve("registry/launcher/config/registry-local-secrets.properties")

fun loadPropertiesIfExists(path: File?): Properties {
    val properties = Properties()
    if (path?.exists() == true) {
        path.inputStream().use(properties::load)
    }
    return properties
}

val localSecrets = loadPropertiesIfExists(localSecretsFile)
val fleetSecrets = loadPropertiesIfExists(fleetSecretsFile)

val githubPackagesUsername = providers.gradleProperty("githubPackagesUsername").orNull
    ?: providers.environmentVariable("GITHUB_PACKAGES_USERNAME").orNull
    ?: localSecrets.getProperty("githubPackagesUsername")
    ?: fleetSecrets.getProperty("edc.registry.policy.catalog.oci.username")

val githubPackagesToken = providers.gradleProperty("githubPackagesToken").orNull
    ?: providers.environmentVariable("GITHUB_PACKAGES_TOKEN").orNull
    ?: localSecrets.getProperty("githubPackagesToken")
    ?: fleetSecrets.getProperty("edc.registry.policy.catalog.oci.password")

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenLocal()
        mavenCentral()

        if (!githubPackagesUsername.isNullOrBlank() && !githubPackagesToken.isNullOrBlank()) {
            maven {
                name = "GitHubFleetPackages"
                url = uri("https://maven.pkg.github.com/${providers.gradleProperty("fleetPackagesOwner").orNull ?: "adtorales"}/${providers.gradleProperty("fleetPackagesRepo").orNull ?: "Fleet"}")
                credentials {
                    username = githubPackagesUsername
                    password = githubPackagesToken
                }
            }

            maven {
                name = "GitHubTractusxEdcPackages"
                url = uri("https://maven.pkg.github.com/eclipse-tractusx/tractusx-edc")
                credentials {
                    username = githubPackagesUsername
                    password = githubPackagesToken
                }
            }
        }
    }
}

if (useLocalFleetBuild && fleetBuildDir?.exists() == true) {
    includeBuild(fleetBuildDir) {
        dependencySubstitution {
            substitute(module("org.eclipse.edc:reconciler-core")).using(project(":reconciler:reconciler-core"))
            substitute(module("org.eclipse.edc:reconciler-policy")).using(project(":reconciler:reconciler-policy"))
            substitute(module("org.eclipse.edc:reconciler-spi")).using(project(":reconciler:reconciler-spi"))
            substitute(module("org.eclipse.edc:xregistry-lib")).using(project(":common:xregistry:xregistry-lib"))
            substitute(module("org.eclipse.edc:xregistry-policy")).using(project(":common:xregistry:xregistry-policy"))
            substitute(module("org.eclipse.edc:xregistry-model")).using(project(":common:xregistry:xregistry-model"))
        }
    }
}

if (useLocalTxBuild && txBuildDir?.exists() == true) {
    includeBuild(txBuildDir) {
        dependencySubstitution {
            substitute(module("org.eclipse.tractusx.edc:edc-controlplane-postgresql-hashicorp-vault"))
                .using(project(":edc-controlplane:edc-controlplane-postgresql-hashicorp-vault"))
        }
    }
}

include(":extensions:reconciler-status")
include(":launchers:edc-demo")
