# EDC Demo Launcher

Custom Tractus-X control-plane launcher for the TAP 7.4 demo.

## Composition

- `org.eclipse.tractusx.edc:edc-controlplane-postgresql-hashicorp-vault`
- `org.eclipse.edc:reconciler-core`
- `org.eclipse.edc:reconciler-policy`
- `:extensions:reconciler-status`

## Inputs

- `txVersion`
- `fleetVersion`
- GitHub Packages credentials:
  - `githubPackagesUsername` or `GITHUB_PACKAGES_USERNAME`
  - `githubPackagesToken` or `GITHUB_PACKAGES_TOKEN`

Optional development-only inputs:

- `useLocalFleetBuild=true` with `fleetLocalPath` for a local Fleet fork composite build
- `useLocalTxBuild=true` with `txLocalPath` for a local `tractusx-edc` composite build

## Runtime Files

- `config/edc-demo.properties.example`: versioned runtime configuration template
- `config/edc-demo.local.properties`: local non-versioned runtime configuration
- `start-local-edc-demo.ps1`: local run helper; local build support is optional and requires an explicit `tractusx-edc` path

## Output

The final launcher artifact is written to:

`build/libs/edc-demo.jar`

## Deployment and startup

This launcher is packaged into the published `edc-demo` image. Deploy that
image through the separate [demo deployment kit](../../../../demo/README.md),
not by running this local launcher in the Umbrella environment.

On a clean cluster, the control plane can restart while PostgreSQL, Vault, the
Registry, or the SSI DIM wallet finishes starting. Wait for the control-plane
pod to be currently `Running` and ready before executing seed or acceptance
test scripts. A non-zero historical restart count is normal after recovery.
