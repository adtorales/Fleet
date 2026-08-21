# Reconciler Status Extension

Optional demo-only extension that exposes lightweight reconciliation state.

The extension is implemented and bundled into `edc-demo`. It exposes the
latest `ReconciliationReport` through the connector's reconciler-status API.
The control-plane logs remain the primary end-to-end evidence because they
also show Registry connectivity and reconciliation failures.
