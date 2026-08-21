# Setup Guide

This repository is meant to be used together with:

- the Fleet fork that provides the reconciler and registry work;
- the policy catalog repository that publishes OCI artifacts to GHCR;
- the local Tractus-X umbrella environment.

## Local Inputs

- GitHub Packages credentials for Maven artifact resolution
- local access to the umbrella deployment

Optional development-only inputs:

- local access to the Fleet fork
- local access to the `tractusx-edc` workspace

Credentials can be provided in one of these ways:

- Gradle properties `githubPackagesUsername` and `githubPackagesToken`
- environment variables `GITHUB_PACKAGES_USERNAME` and `GITHUB_PACKAGES_TOKEN`
- local file `local-secrets.properties`
- optional Fleet local secrets file when using a local Fleet checkout

## Current Deliverable

The current deliverable in this repository is a runnable launcher assembly:

- `launchers/edc-demo/build.gradle.kts` builds the final shaded `edc-demo.jar`;
- `launchers/edc-demo/config/edc-demo.properties.example` documents the runtime properties required by the reconciler and the Tractus-X control plane;
- `launchers/edc-demo/start-local-edc-demo.ps1` starts the runtime and can optionally build it through a local `tractusx-edc` checkout;
- `local-secrets.properties.example` documents the non-versioned GitHub Packages credentials file.

## Package Resolution

By default the launcher resolves Fleet and `tractusx-edc` dependencies from published package repositories.

Local composite builds are optional and must be enabled explicitly through Gradle properties such as:

```properties
useLocalFleetBuild=true
fleetLocalPath=../Fleet
useLocalTxBuild=true
txLocalPath=../tractusx-edc
```

For local TAP 7.4 image builds, the helper scripts pass these overrides explicitly so the runtime can be assembled from local checkouts without requiring local Maven publication.

## Local Run

1. Copy `launchers/edc-demo/config/edc-demo.properties.example` to `launchers/edc-demo/config/edc-demo.local.properties`.
2. Replace the placeholder values with the URLs, credentials, and participant identity for your environment.
3. Start the runtime:

```powershell
.\launchers\edc-demo\start-local-edc-demo.ps1
```

If you want to use a different file, pass `-ConfigFile <absolute-or-relative-path>`.
