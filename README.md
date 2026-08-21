# EDC Fleet

This repository contains components implementing an EDC Registry:

- A registry server and tooling based on [xRegsitry](https://xregistry.io/).
- A reconciler for synchronizing EDC management domains with resource states specified in a hierarchy of registry servers. 

> :warning: This codebase is under active development and is not yet ready for production use.

## TAP 7.4 OCI catalog mode

For the demonstrator, the Registry loads policy and schema groups from OCI
artifacts published by `tap74-policy-catalog`. It replaces each resource-type
snapshot atomically after a successful reload, so requests observe a complete
catalog version. Registry logs report policy-group/policy counts and
schema-group/schema counts separately; schema groups are not policy groups.

The published Registry image is deployed by the separate
[demo deployment kit](../../demo/README.md). On a fresh Minikube cluster, wait
until the Registry pod is `Running` and ready before expecting EDC
reconciliation to succeed. A transient restart while dependencies initialize
is expected.
 
Build and run the xRegistry server:

```
./gradlew clean shadowJar
java -Dedc.participant.id="test" -jar registry/launcher/build/libs/registry-server.jar
```

Run the local registry against a policy catalog published to GitHub Packages / GHCR:

```
./registry/launcher/start-local-registry.ps1
```

The launcher script will:

- build `registry-server.jar`
- load settings from `registry/launcher/config/registry-local.properties`
- load OCI catalog credentials from `registry/launcher/config/registry-local-secrets.properties`

Override the OCI source explicitly when needed:

```
./registry/launcher/start-local-registry.ps1 -CatalogOciReference "ghcr.io/owner/tap74-policy-catalog:current"
```

For local GitHub Packages credentials, copy:

```
registry/launcher/config/registry-local-secrets.properties.example
```

to:

```
registry/launcher/config/registry-local-secrets.properties
```

and fill in your GitHub username and token there. The real secrets file is ignored by git.

Local endpoints:

- `GET http://localhost:8181/registry`
- `POST http://localhost:8181/registry/reload`

For the deployed demo, follow Registry startup and OCI reloads with:

```
kubectl logs -n umbrella deployment/tap74-registry -f
```
