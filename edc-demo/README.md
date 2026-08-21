# TAP 7.4 Demonstrator

Source project for the TAP 7.4 GitOps Policy Flow demonstrator. It assembles
and publishes the `edc-demo` connector image; the separate `demo` deployment
kit installs that published image in a local Umbrella environment.

## Output

The project produces `ghcr.io/<owner>/edc-demo`. The image contains:

- `org.eclipse.tractusx.edc:edc-controlplane-postgresql-hashicorp-vault`;
- `org.eclipse.edc:reconciler-core` and `reconciler-policy`, published by
  `tap74-registry`;
- local extension modules.

These artifacts are merged into `edc-demo.jar` and run in every provider and
consumer control-plane pod.

## Structure

| Path | Purpose |
|---|---|
| `launchers/edc-demo/` | Connector build, Dockerfile, and local runtime configuration examples |
| `extensions/reconciler-status/` | Demo-only reconciliation status extension |
| `charts/tap74-registry/` | Helm chart for the Registry image |
| `.github/workflows/publish.yml` | Builds and publishes `edc-demo` to GHCR |
| `docs/` | Implementation notes |

## Deployment

This source project does not deploy a local cluster. Use the separate
[demo deployment kit](../../demo/README.md) to deploy the published Registry
and connector images with Tractus-X Umbrella.

On a new cluster, components start asynchronously. PostgreSQL, Vault, the
Registry, the SSI DIM wallet, and EDC control planes can restart once while
waiting for dependencies. Wait until the current pod state is `Running` and
ready before seeding or executing tests; a historical restart count is normal
after recovery.

## Related repositories

| Repository | Published content |
|---|---|
| `tap74-registry` | Reconciler/xRegistry Maven libraries and the Registry image/chart |
| `tap74-policy-catalog` | `xregistry-policies` and `xregistry-dataspace-schemas` OCI artifacts |
| `demo` | End-user Minikube deployment, seed, and test scripts |

## Version alignment

`txVersion=0.12.1` uses upstream EDC `0.15.1`. Fleet artifacts and local
extensions must remain aligned with that upstream version. A non-composite
build requires `fleetVersion=0.15.1-tap74.0-SNAPSHOT` to be published to
GitHub Packages.
