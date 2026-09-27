# Real Offline Mesh Implementation Plan

Status: implementation in progress; physical two-device validation pending

## Objective

Replace the current in-memory `NearbyConnectionsMeshRelay` simulation with a
minimal real Google Nearby Connections transport that can relay a verified
SheGuard early-warning alert between two physical Android devices without
Internet connectivity.

The hackathon acceptance flow is:

```
Device A: local report → detect → trust → alert
                         │
                         ▼
              Nearby Connections transport
                         │
                         ▼
Device B: validate → deduplicate → persist → display relayed alert
```

## Scope for the hackathon

### Included

- Two nearby Android devices.
- Google Nearby Connections, preferably the `P2P_CLUSTER` strategy.
- Alert payloads already produced by `SheGuardMeshAdapter`.
- One device advertising and accepting connections.
- One device discovering and requesting connections.
- Byte payload send/receive with JSON already defined by `SheGuardMeshAlertPayload`.
- Connection status, permission, Bluetooth, and failure states visible in the UI.
- Existing validation, hop limits, deduplication, local persistence, and
  store-and-forward behavior.

### Deferred

- Multi-hop routing optimization.
- Background reconnection guarantees across every Android vendor.
- Mesh-wide leader election or topology management.
- Backend synchronization changes.
- Changes to cryptographic evidence handling.
- Any dependency on LLMs or cloud services.

## Existing reusable components

| Component | Current state | Planned use |
|---|---|---|
| `SheGuardMeshAlertPayload` | JSON serialization/deserialization | Wire payload format. |
| `MeshPayloadValidator` | Deterministic validation | Reject malformed, stale, invalid, or unsafe packets. |
| `SheGuardMeshAdapter` | Queueing and incoming-packet handling | Keep as the transport-independent boundary. |
| `MeshDeduplicationCache` | In-memory deduplication | Prevent repeated processing during the demo. |
| `RisingPatternAlert` | Local verified alert model | Source object for outbound packets. |
| `RisingPatternAlertRepository` | Room persistence | Persist received alerts locally. |
| `NearbyConnectionsMeshRelay` | In-memory simulator | Refactor behind a transport interface; retain as test double. |

## Implementation phases

### Phase 1 — Transport boundary

1. Define a small `MeshTransport` interface for:
   - start advertising;
   - start discovery;
   - stop transport;
   - request connection;
   - send bytes to a connected peer;
   - observe peer and payload events.
2. Keep `SheGuardMeshAdapter` dependent on the interface rather than directly on
   Google APIs.
3. Adapt the current in-memory relay to the interface so deterministic unit tests
   remain available.

### Phase 2 — Nearby Connections implementation

1. Add the maintained Google Play Services Nearby Connections dependency.
2. Implement a `NearbyConnectionsTransport` using the chosen strategy.
3. Use a stable service identifier shared by both demo devices.
4. Implement connection callbacks:
   - connection initiated;
   - connection result accepted/rejected;
   - connected;
   - disconnected;
   - endpoint lost.
5. Implement byte payload callbacks and pass received bytes to the adapter.
6. Send only serialized `SheGuardMeshAlertPayload` packets; never send raw reports,
   reporter tokens, exact coordinates, evidence, or keys.

### Phase 3 — Android permissions and lifecycle

1. Add the minimum manifest permissions required for the supported API range.
2. Request runtime nearby-device/Bluetooth permissions before starting discovery or
   advertising.
3. Show explicit states for:
   - permissions denied;
   - Bluetooth or required capability disabled;
   - advertising/discovery active;
   - connecting;
   - connected;
   - disconnected;
   - unavailable/degraded.
4. Stop discovery/advertising and unregister callbacks when the owner is destroyed.
5. Ensure mesh startup failure never blocks local report creation, local detection,
   trust evaluation, or alert generation.

### Phase 4 — Application wiring

1. Create the transport through an application-scoped or lifecycle-safe owner.
2. Start the transport from an explicit Mesh control or demo mode.
3. When a local verified alert is generated, create a packet with
   `SheGuardMeshAdapter.createPacketForAlert()` and send it when a peer is
   connected.
4. When bytes arrive, call `handleIncomingPacket()`.
5. Persist accepted alerts with `isRelayed = true` and render the existing
   “Received via nearby device” provenance badge.
6. Queue packets while disconnected and drain them after reconnection.

### Phase 5 — Demo hardening

1. Add a visible mesh status indicator and a “last packet” diagnostic that excludes
   PII and raw payload contents.
2. Add a demo checklist for two physical devices.
3. Verify local alert generation with Bluetooth disabled or permissions denied.
4. Verify duplicate packets create only one alert.
5. Verify malformed packets are rejected without crashing.
6. Verify the app remains usable when the peer disappears.

## Testing plan

### Unit tests

- Preserve existing payload, validation, deduplication, hop-limit, and queue tests.
- Add transport-interface tests using the in-memory relay.
- Test send failure and reconnection queue draining.
- Test that received mesh alerts do not enter pattern detection or alter trust.

### Instrumented/device tests

- Device A discovers Device B.
- Device B accepts the connection.
- Device A sends one verified alert.
- Device B receives, validates, persists, and displays it.
- Repeat the same packet and confirm it is ignored.
- Disable Bluetooth or revoke permission and confirm local operation remains intact.

### Manual acceptance checklist

- Both devices have the same debug build and service identifier.
- Internet is disabled on both devices.
- Nearby permissions are granted.
- Device A generates a verified alert.
- Device B shows the alert as relayed.
- Device B shows no exact location, reporter token, or private evidence.
- Repeated delivery does not duplicate the alert.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Nearby permissions differ by Android API level | Centralize permission checks and show a degraded state. |
| Device vendor blocks discovery or background operation | Keep the demo foreground/lifecycle-scoped and test on known compatible devices. |
| Peer disconnects during transfer | Keep packets queued and retry after reconnection. |
| Duplicate or looped delivery | Reuse packet IDs, deduplication cache, and hop limits. |
| Transport implementation breaks core safety flow | Keep the adapter asynchronous and fail mesh independently. |
| Payload leaks private data | Serialize only the existing relay-safe payload and validate on receipt. |

## Definition of done

- A real Nearby Connections transport is present and used by the demo build.
- Two physical devices exchange a verified SheGuard alert offline.
- Received alerts are validated, deduplicated, persisted, and visibly marked as
  relayed.
- Local Report → Detect → Trust → Alert continues working with mesh unavailable.
- Unit tests pass for the transport-independent logic.
- The device demo is manually verified and limitations are documented.

## Expected effort

- Transport boundary and Nearby implementation: 4–8 hours.
- Permissions, lifecycle, and UI states: 3–6 hours.
- Two-device testing and hardening: 4–8 hours.

Expected hackathon scope: approximately 1–2 focused days, assuming two physical
Android devices with compatible Google Play Services are available.

## Architectural impact

No change to `architecture.yaml` is required. The implementation adds the missing
transport under the existing `mesh_relay` boundary and preserves the mandatory
local-operation guarantee.

## Detailed file-by-file change plan

This section is the implementation checklist for the current repository. File
names below are proposed additions or existing files that should be changed.

### 1. Dependency and platform setup

#### `gradle/libs.versions.toml`

- Add a version entry for the Google Play Services Nearby Connections library
  after checking the version against compile SDK 34, min SDK 26, and the existing
  Kotlin/AGP versions.
- Add a named library alias rather than placing an anonymous dependency directly
  in a module build file.
- Do not add a second Bluetooth or transport library; Nearby Connections owns the
  BLE/Wi‑Fi Direct transport boundary for this feature.

#### `android/services/mesh/build.gradle.kts`

- Add the Nearby Connections implementation dependency through the version-catalog
  alias.
- Keep the mesh module responsible for transport and protocol behavior; do not
  move transport code into `android/app`.
- Keep JUnit/coroutines test dependencies unchanged.

#### `android/app/src/main/AndroidManifest.xml`

The manifest already declares the relevant Bluetooth and nearby-device permissions.
Confirm the declarations against the selected Nearby library and API range rather
than adding duplicates. Keep permissions as follows where required by the chosen
API path:

- `BLUETOOTH_SCAN` with `neverForLocation` where valid;
- `BLUETOOTH_CONNECT`;
- `BLUETOOTH_ADVERTISE`;
- `NEARBY_WIFI_DEVICES` for Android versions that require it.

Do not make Bluetooth or nearby hardware a required install-time feature. A device
without the capability must continue to run local reporting and alerting.

### 2. Transport abstraction

#### New: `android/services/mesh/src/main/java/org/sahara/services/mesh/transport/MeshTransport.kt`

Define the transport-independent contract. Keep it small and testable. It should
expose:

```kotlin
interface MeshTransport {
    val status: StateFlow<MeshTransportStatus>
    val peers: StateFlow<List<MeshPeer>>
    val incomingPayloads: Flow<ByteArray>
    suspend fun startAdvertising(): TransportResult
    suspend fun startDiscovery(): TransportResult
    suspend fun connect(peerId: String): TransportResult
    suspend fun send(peerId: String, payload: ByteArray): TransportResult
    fun stop()
}
```

Use explicit result/status types for permission denied, unavailable, failed,
connecting, connected, and disconnected. Do not throw from ordinary Bluetooth or
peer-loss conditions.

#### New: `.../transport/MeshTransportModels.kt`

Add small data types such as:

- `MeshTransportStatus`: `STOPPED`, `STARTING`, `ADVERTISING`, `DISCOVERING`,
  `CONNECTING`, `CONNECTED`, `DISCONNECTED`, `PERMISSION_REQUIRED`, `UNAVAILABLE`,
  `ERROR`;
- `MeshPeer`: endpoint ID plus safe display name, never raw device identifiers in
  logs or UI;
- `TransportResult`: success or a classified failure.

### 3. Preserve the in-memory implementation for tests

#### Existing: `android/services/mesh/src/main/java/org/sahara/services/mesh/relay/NearbyConnectionsMeshRelay.kt`

- Rename or adapt the current in-memory behavior as a deterministic test transport,
  for example `InMemoryMeshTransport`.
- Preserve deduplication and hop-limit behavior in tests.
- Do not keep the class name `NearbyConnectionsMeshRelay` for a simulator after the
  real implementation is added; that name would be misleading.
- Keep `MeshDeduplicationCache` reusable by both the real transport adapter and the
  test implementation.

#### New or changed: `.../relay/MeshRelayCoordinator.kt`

Move transport-independent orchestration here:

1. Receive a `MeshPacket` from `SheGuardMeshAdapter`.
2. Serialize it to UTF-8 bytes.
3. Send it to connected peers through `MeshTransport`.
4. Receive bytes from `MeshTransport`.
5. Decode a `MeshPacket` safely.
6. Pass it to `SheGuardMeshAdapter.handleIncomingPacket()`.
7. Return classified outcomes to the UI/status layer.

The coordinator must never generate a pattern, increase trust, or create an alert
from raw report volume.

### 4. Real Nearby Connections implementation

#### New: `android/services/mesh/src/main/java/org/sahara/services/mesh/transport/NearbyConnectionsTransport.kt`

Implement the actual Google Nearby Connections client here.

Initialization:

- Accept `Context`, a stable service ID, and a coroutine scope/lifecycle owner.
- Use the selected Nearby strategy, preferably `P2P_CLUSTER` for the two-device
  hackathon demo.
- Register one `ConnectionLifecycleCallback` and one `PayloadCallback`.
- Keep endpoint IDs internal; expose only a safe peer count/name to UI.

Advertising:

1. Verify required runtime permissions.
2. Verify Bluetooth/nearby capability is enabled.
3. Start advertising with the service ID and strategy.
4. On connection initiation, accept only the expected service/protocol.
5. On connection result success, add the endpoint to the connected-peer set.
6. On disconnect, remove it and notify the coordinator to retain queued packets.

Discovery:

1. Verify permissions and capability state.
2. Start discovery with the same service ID and strategy.
3. On endpoint discovery, expose a safe peer entry.
4. Request a connection only from an explicit user/demo action or controlled
   auto-connect policy.
5. Stop discovery after connection if the demo only needs one peer, or keep it
   active if testing multiple peers.

Payload handling:

- Use `Payload.fromBytes(packetJson.toByteArray(Charsets.UTF_8))` for alert packets.
- Reject payloads over the protocol max size before deserialization.
- Never log the JSON payload, exact location, reporter token, evidence, or keys.
- Pass received bytes to `MeshRelayCoordinator`, which performs packet validation.

### 5. Packet serialization boundary

#### Existing: `android/services/mesh/src/main/java/org/sahara/services/mesh/models/MeshPacket.kt`

- Add deterministic `toWireBytes()` and `fromWireBytes()` helpers, or keep this
  responsibility in the coordinator if changing the model would affect tests.
- Validate packet type, packet ID, hop count, max hops, payload hash, and payload
  size before forwarding.
- Keep `SHEGUARD_ALERT` as the only real transport type for the hackathon.
- Do not transmit `DISTRESS_ALERT` or legacy SMS payloads through this new path.

#### Existing: `.../models/SheGuardMeshAlertPayload.kt`

- Keep the current relay-safe JSON schema.
- Add a maximum serialized-size check before creating the Nearby payload.
- Verify the SHA-256 hash over the canonical JSON on receipt if the current protocol
  contract requires it; reject mismatches without crashing.

#### Existing: `.../relay/MeshPayloadValidator.kt`

- Keep validation before deduplication and persistence.
- Add explicit rejection reasons for oversized payloads and hash mismatches.
- Ensure rejection reasons are safe to show in diagnostics and contain no payload
  contents.

### 6. Adapter and queue behavior

#### Existing: `android/services/mesh/src/main/java/org/sahara/services/mesh/relay/SheGuardMeshAdapter.kt`

Change the adapter from directly owning the in-memory relay to receiving a
`MeshRelayCoordinator` or `MeshTransport` dependency.

Outbound logic:

1. `queueAlertForRelay()` creates the existing `MeshPacket`.
2. Persist/retain the packet in a bounded local outbound queue before sending.
3. If no connected peer exists, return a queued result without error.
4. If peers exist, send the packet through the coordinator.
5. Remove the packet from the queue only after a successful transport handoff.
6. On disconnect or send failure, keep it queued for retry.
7. Deduplicate queued packets by `packetId`.

Inbound logic:

1. Receive a packet from the coordinator.
2. Check packet type.
3. Validate JSON, schema, hash, timestamps, score, and privacy constraints.
4. Apply packet deduplication and hop-limit checks.
5. Convert only accepted payloads into `RisingPatternAlert(isRelayed = true)`.
6. Persist through `AlertRepository`.
7. Never submit received alerts to `SpatioTemporalPatternEngine` or trust
   evaluation.

Mesh failure must not propagate as an exception into the report submission path.

### 7. Application lifecycle and ownership

#### Existing: `android/app/src/main/java/org/sahara/app/MainActivity.kt`

- Stop constructing the in-memory relay directly for SheGuard mesh behavior.
- Create a lifecycle-safe mesh controller/coordinator using `applicationContext`.
- Keep the existing `NearbyConnectionsMeshRelay` construction used by legacy SMS
  fallback separate until that path is migrated; do not accidentally replace its
  dependency in the same change.
- Start/stop advertising and discovery from explicit mesh lifecycle events.
- Stop the transport in the appropriate lifecycle shutdown path.
- Expose a `StateFlow` of mesh status and peer count to Compose.

Prefer an application-scoped owner or a dedicated `MeshService` only if the demo
needs mesh to continue while the Activity is backgrounded. For a hackathon,
foreground/lifecycle-scoped operation is acceptable and easier to verify, but the
UI must label it accordingly.

#### New, recommended: `android/services/mesh/src/main/java/org/sahara/services/mesh/MeshController.kt`

Own:

- permission checks;
- transport startup/shutdown;
- advertising/discovery mode;
- coordinator subscription;
- outbound queue draining;
- status and peer state exposed as `StateFlow`.

It should provide methods such as `startHost()`, `startDiscovering()`,
`connect(peerId)`, `stop()`, and `sendAlert(packet)`.

### 8. Report and alert integration

#### Existing: `android/app/src/main/java/org/sahara/app/ui/SheGuardReportingScreen.kt`

- Replace the current default `SheGuardMeshAdapter(alertRepository = ...)` creation
  with an injected adapter/controller from `MainActivity` or a view-model layer.
- Keep alert generation and persistence synchronous with the local pipeline before
  attempting mesh transmission.
- After saving a locally generated alert, call the adapter/coordinator to queue it.
- Do not block the submit button on peer discovery or connection.
- Observe mesh status and show `Queued`, `Connected`, `Sent`, `Disconnected`, or
  `Unavailable` states.
- Keep the existing relayed-alert provenance badge.

#### Existing: `android/app/src/main/java/org/sahara/app/ui/SaharaScreens.kt`

- Replace static “Nearby Devices (Mesh)” copy with live status from the controller.
- Add explicit controls for `Start hosting`, `Discover peers`, `Connect`, and
  `Stop`, or a single clearly labeled demo-mode control.
- Show permission denied and unavailable hardware states.
- Do not imply that a connected peer guarantees emergency response.

### 9. Permission handling

#### New: `android/services/mesh/src/main/java/org/sahara/services/mesh/permissions/MeshPermissionManager.kt`

- Centralize API-level checks for scan, connect, advertise, and nearby Wi‑Fi
  permissions.
- Return missing permissions instead of throwing.
- Keep permission request UI in the app module, since only the Activity can request
  permissions interactively.

#### Existing: `android/app/src/main/java/org/sahara/app/MainActivity.kt`

- Request missing permissions before invoking mesh start methods.
- Handle denial as a visible degraded state, not as a crash or silent no-op.
- Do not infer that location permission grants nearby permission on newer Android
  versions.

### 10. Tests to add or update

#### Existing: `android/services/mesh/src/test/.../SheGuardMeshUnitTest.kt`

Retain and extend tests for:

- adapter behavior with a fake `MeshTransport`;
- queue retention on send failure;
- queue drain after reconnection;
- duplicate outbound packet suppression;
- malformed/oversized/hash-invalid inbound payload rejection;
- no pattern/trust mutation from a received alert.

#### Existing: `android/services/mesh/src/test/.../MeshRelayUnitTest.kt`

- Rename/reframe tests around the in-memory transport.
- Keep hop-limit and TTL tests as deterministic unit coverage.
- Add fake peer connect/disconnect behavior.

#### New: `android/services/mesh/src/androidTest/.../NearbyConnectionsTransportTest.kt`

Only add if the project’s device-test setup can run with two devices. Cover lifecycle
and callback behavior; do not pretend a single emulator proves peer-to-peer radio
transport.

#### Existing: `android/app/src/androidTest/...`

Add a manual/instrumented checklist or test harness for permission states and mesh
status UI. Physical two-device validation remains a manual acceptance test.

### 11. Documentation updates after implementation

Update these files after the transport is actually working:

- `docs/specs/mesh.yaml`: replace the current in-memory limitation with the actual
  transport status and documented Android limitations.
- `docs/DEPENDENCY_MAP.md`: show `NearbyConnectionsTransport` between the adapter
  and the peer device.
- `docs/REPOSITORY_MAP.md`: list the transport, controller, and permission manager.
- `PROGRESS.md`: record unit-test and two-device results separately.
- Add an ADR under `docs/adr/` if the selected Nearby strategy, lifecycle owner, or
  transport boundary is a meaningful architectural decision.

### 12. Recommended implementation order

1. Add the dependency and compile the mesh module.
2. Extract the `MeshTransport` interface without changing behavior.
3. Move current tests to the in-memory transport.
4. Implement `NearbyConnectionsTransport` with status callbacks only.
5. Add permission checks and a visible status screen.
6. Send a small synthetic test packet between two devices.
7. Connect the real `SheGuardMeshAdapter` packet path.
8. Add queue retry and disconnect handling.
9. Verify received alerts persist and display as relayed.
10. Run the full relevant test suite and complete the two-device acceptance checklist.

Do not delete the in-memory transport until the physical two-device test has passed;
it remains the deterministic fallback and unit-test implementation.
