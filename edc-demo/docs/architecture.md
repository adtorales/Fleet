# Architecture Notes

The repository holds the runtime-side pieces of the TAP 7.4 demonstrator.

## Main Components

- `edc-demo` launcher: custom Tractus-X control-plane runtime
- Fleet reconciler modules: consumed from published artifacts
- Tractus-X control-plane runtime modules: consumed from published artifacts

## Intended Flow

1. The policy catalog is published to GHCR.
2. Fleet Registry pulls and exposes the merged `/registry` document.
3. Each EDC control plane runs the embedded reconciler and polls the registry.
4. The local policy state converges inside each connector runtime.

## Runtime Boundary

`edc-demo` is the custom control-plane assembly for the demonstrator. It does not replace Fleet and it does not replace the policy catalog repository.

- Fleet remains the registry server and the source of the embedded reconciler modules.
- The policy catalog repository remains the source of the OCI artifact published to GHCR.
- `edc-demo` is the runtime that consumes both pieces and is meant to be deployed as the connector control plane used in the demo scenario.
- Local composite builds of Fleet or `tractusx-edc` are development-only overrides and are not part of the default architecture.
