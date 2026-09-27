# 10. Nearby Connections Transport Boundary

* Status: accepted
* Date: 2026-09-27

## Context

SheGuard's mesh protocol, validation, deduplication, hop limits, and local alert
handling existed as deterministic in-memory logic. The hackathon requires a real
offline two-device relay without making the local Report → Detect → Trust → Alert
pipeline depend on nearby devices.

## Decision

Add a `MeshTransport` boundary in `:android:services:mesh`:

- `NearbyConnectionsTransport` owns Google Nearby Connections advertising,
  discovery, connection callbacks, and byte payload transfer.
- `MeshPacketWireCodec` creates a bounded wire envelope without transmitting raw
  reports, evidence, keys, or exact location history.
- `SheGuardMeshAdapter` remains responsible for SheGuard packet creation,
  validation, deduplication, persistence, and store-and-forward queueing.
- The existing in-memory relay remains the deterministic unit-test implementation.
- Mesh permission, transport, or peer failures degrade to local operation and must
  not block report creation or local alert generation.

## Alternatives considered

- Direct Google Nearby calls from Compose/UI: rejected because it would couple UI
  lifecycle and permission handling to the protected mesh protocol.
- Replacing the in-memory relay immediately: rejected because deterministic tests
  and offline fallback need a transport-independent implementation.
- Sending raw micro-reports: rejected because the MVP mesh contract permits only
  compact privacy-minimized alerts at this boundary.

## Consequences

- The app can advertise, discover, connect, and send alert bytes through Nearby
  Connections when permissions and Google Play Services are available.
- Physical two-device validation remains required before claiming the transport is
  demo-verified.
- Connection authentication and lifecycle behavior remain device-level validation
  work before production use.
