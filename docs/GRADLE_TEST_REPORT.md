# Gradle Test Execution & Mesh Diagnostic Report

## 1. Executive Summary

| Attribute | Details |
| :--- | :--- |
| **Overall Status** | **PASS** (100% Passing) |
| **Command Executed** | `.\gradlew.bat test --no-daemon` |
| **Build Artifact** | `apk/sheguard-debug.apk` (Generated via `assembleDebug`) |
| **Total Test Suites** | 19 distinct test classes across 8 Gradle modules |
| **Total Tests Executed** | 144 unit & integration tests per variant (288 total across Debug & Release) |
| **Passed Tests** | 144 / 144 (100%) |
| **Failed Tests** | 0 |
| **Skipped Tests** | 0 |
| **Legacy Sahara Tests** | All passed without regression |

---

## 2. Test Suite Breakdown by Module

### `:android:app`
- `org.sahara.app.AppMilestone8UnitTest`: 3 tests (Passed)
- `org.sahara.app.BackendIntegrationTest`: 2 tests (Passed)
- `org.sahara.app.CriticalEvidenceIntegrationTest`: 1 test (Passed)
- `org.sahara.app.EmergencyResponseIntegrationTest`: 4 tests (Passed)

### `:android:services:mesh`
- `org.sahara.services.mesh.SheGuardMeshUnitTest`: 31 tests (Passed)
  - Covers payload serialization, deserialization, privacy invariants, deduplication, hop limits, store-and-forward queueing, trust boundary, wire codec, and permission management.
- `org.sahara.services.mesh.MeshRelayUnitTest`: 6 tests (Passed)
  - Covers relay deduplication, LRU & TTL cache eviction, emergency SMS formatting, and distress delegation.

### `:android:core:domain`
- `org.sahara.core.domain.engine.RisingPatternAlertEngineTest`: 17 tests (Passed)
- `org.sahara.core.domain.engine.SpatioTemporalPatternEngineTest`: 11 tests (Passed)
- `org.sahara.core.domain.engine.TrustAndAntiGamingEvaluatorTest`: 18 tests (Passed)
- `org.sahara.core.domain.models.ModelsUnitTest`: 5 tests (Passed)

### `:android:core:data`
- `org.sahara.core.data.repository.RepositoryMappingUnitTest`: 4 tests (Passed)
- `org.sahara.core.data.repository.SheGuardDataUnitTest`: 5 tests (Passed)

### `:android:core:security`
- `org.sahara.core.security.CryptoUnitTest`: 4 tests (Passed)

### `:android:features:incident`
- `org.sahara.features.incident.IncidentStateMachineUnitTest`: 3 tests (Passed)

### `:android:features:notify-circle`
- `org.sahara.features.notifycircle.NotifyCircleUnitTest`: 3 tests (Passed)

### `:android:features:panic`
- `org.sahara.features.panic.PanicControllerUnitTest`: 2 tests (Passed)

### `:android:services:detection`
- `org.sahara.services.detection.DetectionUnitTest`: 11 tests (Passed)
- `org.sahara.services.detection.ScreamDetectorUnitTest`: 10 tests (Passed)

### `:android:services:evidence`
- `org.sahara.services.evidence.EvidenceEngineUnitTest`: 4 tests (Passed)

---

## 3. Mesh Diagnosis and Root Cause Analysis

### Identified Failure Points

1. **Permission Logic Gap on Android 12+ / 13+ (`MeshPermissionManager.kt`)**:
   - `requiredPermissions()` used an `else if` check for location permissions after matching Android 12+ (`SDK_INT >= S`).
   - On Android 12 and Android 13+ (e.g., target OPPO physical test device), `ACCESS_FINE_LOCATION` was omitted from required permissions.
   - Google Nearby Connections requires fine location on Android 12 and on Android 13 when Wi-Fi without `neverForLocation` is used, resulting in immediate permission rejection or security exceptions when initializing advertising or discovery.

2. **Asymmetric Peer Tracking on Advertising Nodes (`NearbyConnectionsTransport.kt`)**:
   - When Device A advertised and Device B discovered:
     - Device B discovered Device A via `onEndpointFound` and initiated a connection.
     - Device A received `onConnectionInitiated` and accepted the connection.
     - Upon connection success (`STATUS_OK`), Device A ran `publishPeers()`, which filtered `_peers.value` against `connectedEndpoints`.
     - Because Device A never received `onEndpointFound` (it was advertising, not discovering), its `_peers.value` list remained empty.
     - When Device A attempted to send messages or drain its store-and-forward queue via `sendToConnectedPeers`, it iterated over `peers.value` (size 0) and failed to send any packets.

3. **Silent Failures and Lack of Structured Logcat Logging**:
   - Nearby Connections failures in `addOnFailureListener` and `getOrElse` silently updated status enums without emitting any logs.
   - Android Studio Logcat only showed system battery/power logs because no messages were tagged for SheGuard mesh components.

4. **Incomplete Manifest Flag for Android 13 Nearby Wi-Fi (`AndroidManifest.xml`)**:
   - `NEARBY_WIFI_DEVICES` was declared without `android:usesPermissionFlags="neverForLocation"`, causing strict Android 13 OS permission mismatches when location was not concurrently verified.

---

## 4. Exact Files Modified

1. [`android/app/src/main/AndroidManifest.xml`](file:///d:/SheGuard/android/app/src/main/AndroidManifest.xml):
   - Added `android:usesPermissionFlags="neverForLocation"` to `NEARBY_WIFI_DEVICES` permission.
2. [`android/services/mesh/src/main/java/org/sahara/services/mesh/transport/MeshPermissionManager.kt`](file:///d:/SheGuard/android/services/mesh/src/main/java/org/sahara/services/mesh/transport/MeshPermissionManager.kt):
   - Fixed permission aggregation logic so `ACCESS_FINE_LOCATION` is included on Android 10+ (including Android 12 & 13+).
   - Integrated `MeshLogger` for structured permission logs (`PERMISSIONS_GRANTED`, `PERMISSIONS_MISSING`).
3. [`android/services/mesh/src/main/java/org/sahara/services/mesh/util/MeshLogger.kt`](file:///d:/SheGuard/android/services/mesh/src/main/java/org/sahara/services/mesh/util/MeshLogger.kt):
   - Created safe logging utility using unified tag `SheGuardMesh` with JVM unit test fallback to avoid unmocked Log exceptions.
4. [`android/services/mesh/src/main/java/org/sahara/services/mesh/transport/NearbyConnectionsTransport.kt`](file:///d:/SheGuard/android/services/mesh/src/main/java/org/sahara/services/mesh/transport/NearbyConnectionsTransport.kt):
   - Added bidirectional peer tracking (`endpointNames` cache updated in both `onEndpointFound` and `onConnectionInitiated`).
   - Fixed connection state cleanup on disconnects and failures.
   - Added structured `SheGuardMesh` logs for `MESH_INITIALIZED`, `ADVERTISING_STARTED`/`FAILED`, `DISCOVERY_STARTED`/`FAILED`, `PEER_DISCOVERED`, `CONNECTION_REQUESTED`, `CONNECTION_ESTABLISHED`/`FAILED`, `MESSAGE_SENT`/`FAILED`, `MESSAGE_RECEIVED`, and `PEER_DISCONNECTED`.
5. [`android/services/mesh/src/main/java/org/sahara/services/mesh/relay/SheGuardMeshAdapter.kt`](file:///d:/SheGuard/android/services/mesh/src/main/java/org/sahara/services/mesh/relay/SheGuardMeshAdapter.kt):
   - Added structured `SheGuardMesh` logs for `MESSAGE_QUEUED`, `MESSAGE_SENT`/`FAILED`, `MESSAGE_RECEIVED`, `DUPLICATE_IGNORED`, `HOP_LIMIT_EXCEEDED`, and `MESSAGE_RELAYED`.
6. [`android/app/src/main/java/org/sahara/app/MainActivity.kt`](file:///d:/SheGuard/android/app/src/main/java/org/sahara/app/MainActivity.kt):
   - Integrated `MeshLogger` into runtime permission handling and mesh startup.
7. [`android/services/mesh/src/test/java/org/sahara/services/mesh/SheGuardMeshUnitTest.kt`](file:///d:/SheGuard/android/services/mesh/src/test/java/org/sahara/services/mesh/SheGuardMeshUnitTest.kt):
   - Added unit tests for permission manager and store-and-forward queue drainage.
8. [`android/services/mesh/build.gradle.kts`](file:///d:/SheGuard/android/services/mesh/build.gradle.kts) & [`android/app/build.gradle.kts`](file:///d:/SheGuard/android/app/build.gradle.kts):
   - Added `testOptions { unitTests.isReturnDefaultValues = true }`.

---

## 5. Verification Status

### Automated Test Verification
- **Verified in Tests:**
  - Micro-report creation and Room persistence
  - Deterministic spatio-temporal pattern grouping
  - Trust and anti-gaming evaluation
  - Rising-pattern alert generation
  - Mesh payload serialization, JSON encoding, and schema validation
  - SHA-256 payload integrity hashing and verification
  - Wire codec encoding/decoding (`MeshPacketWireCodec`)
  - Deduplication cache LRU/TTL expiration and idempotency
  - Hop count incrementing and loop drop (`maxHops = 12`)
  - Store-and-forward queueing when offline and drainage when available
  - Permission manager requirements across Android versions

### Operations Requiring Physical Multi-Device Validation
- Real BLE and Wi-Fi Direct radio handshakes between two physical Android devices using Google Play Services Nearby Connections.
- Physical RF packet transmission over BLE/Wi-Fi Direct.
- Real-time Logcat observation filtered with `tag:SheGuardMesh`.
