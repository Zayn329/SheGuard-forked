# SheGuard (Aegis) Codebase Analysis: Cyclomatic Complexity, Stubs, and Drivers

## Executive Summary

This report presents an exhaustive structural and quality analysis of the **SheGuard (Aegis)** repository, covering both the native Android Kotlin application (`android/`) and the Python FastAPI backend service (`backend/`).

The analysis encompasses:
1. **Cyclomatic Complexity (McCabe CCN)** across all production and test source files.
2. **High-Complexity Hotspots** (functions exceeding industry thresholds $CCN \ge 10$ and $CCN \ge 15$).
3. **Exhaustive Inventory of Stubs, Fakes, and Mocks** (test doubles) across testing modules, unit test suites, and backend services.
4. **Exhaustive Inventory of Test Drivers** (JUnit suites, test runners, and pytest drivers).

---

## 1. Cyclomatic Complexity Metrics

### 1.1 Repository-Wide Metric Summary

| Metric | Measured Value |
| :--- | :--- |
| **Total Source Files Analyzed** | 91 files (83 Android Kotlin, 8 Backend Python) |
| **Total Code Volume (NLOC)** | 14,508 lines of non-comment code |
| **Total Functions / Methods Analyzed** | 611 functions |
| **Repository Average Cyclomatic Complexity (CCN)** | **2.42** |
| **High Complexity Functions ($CCN \ge 10$)** | 24 functions (3.9% of functions) |
| **Very High Complexity Functions ($CCN \ge 15$)** | 10 functions (1.6% of functions) |
| **Maximum CCN Observed** | **32** (`TrustAndAntiGamingEvaluator.evaluate`) |

### 1.2 Subsystem and Module Breakdown

| Subsystem / Module | Files | NLOC | Functions | Avg CCN | High CCN ($CCN \ge 10$) | Primary Role |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| `android/app` | 17 | 6,306 | 163 | 2.63 | 8 | UI Screens (Compose), Orchestration, Location, Export |
| `android/core/data` | 9 | 967 | 71 | 1.04 | 0 | Room Database DAOs, Entities, and Repository Impls |
| `android/core/domain` | 11 | 1,430 | 51 | 1.98 | 2 | Domain Models, Clustering & Trust Engines |
| `android/core/security` | 6 | 352 | 16 | 2.31 | 0 | AES-GCM KeyStore & Merkle Tree Integrity |
| `android/core/testing` | 2 | 72 | 11 | 1.00 | 0 | Shared in-memory DAO fakes for test harnesses |
| `android/features/incident` | 4 | 676 | 39 | 3.00 | 1 | Safety Foreground Service & Lifecycle State Machine |
| `android/features/notify-circle` | 3 | 305 | 10 | 2.40 | 1 | SMS/Dispatch manager for trusted circles |
| `android/features/panic` | 3 | 134 | 7 | 1.43 | 0 | Countdown, cancellation, and manual panic trigger |
| `android/services/detection` | 9 | 1,030 | 70 | 2.76 | 4 | Audio scream detection, TFLite classifiers, DSP |
| `android/services/evidence` | 5 | 316 | 13 | 2.46 | 1 | Bounded pre-roll audio ringbuffer & manifest sealing |
| `android/services/mesh` | 14 | 1,747 | 104 | 2.41 | 4 | BLE/Nearby mesh transport, packet wire codec & relay |
| `backend` (`FastAPI`) | 8 | 1,173 | 56 | 3.57 | 3 | REST API, AI Agents (Groq/Mock), Density Dashboard |
| **TOTAL** | **91** | **14,508** | **611** | **2.42** | **24** | **Entire SheGuard Monorepo** |

---

## 2. High Cyclomatic Complexity Hotspots ($CCN \ge 10$)

Functions with McCabe cyclomatic complexity $\ge 10$ represent multi-branching control flow that requires thorough branch coverage in test drivers.

| Rank | CCN | NLOC | Subsystem / File | Function / Method | Primary Cause of Branching |
| :---: | :---: | :---: | :--- | :--- | :--- |
| **1** | **32** | 171 | `android/core/domain` &bull; `TrustAndAntiGamingEvaluator.kt:22` | `evaluate(...)` | Multi-factor trust scoring, anti-gaming penalty trees, diversity & spatio-temporal checks |
| **2** | **31** | 94 | `android/services/detection` &bull; `Detectors.kt:106` | `processAudioChunk(...)` | DSP signal processing, RMS energy bounds, pitch/spectral analysis, hysteresis thresholds |
| **3** | **25** | 62 | `backend` &bull; `app/agents/providers.py:324` | `answer_question(...)` | Intent parsing, context-grounding checks, boundary validation, and error branches |
| **4** | **20** | 34 | `backend` &bull; `app/dashboard.py:88` | `cluster_reports(...)` | Distance-matrix calculation, radius clustering, category grouping, threshold branching |
| **5** | **18** | 90 | `android/services/mesh` &bull; `SheGuardMeshAlertPayload.kt:45` | `toJson(...)` | Manual JSON building with conditional field inclusion and privacy guards |
| **6** | **18** | 59 | `android/services/mesh` &bull; `MeshPayloadValidator.kt:49` | `validate(...)` | Multi-field schema validation, hop limits, payload size, type invariants, timestamp windows |
| **7** | **17** | 41 | `android/services/detection` &bull; `Detectors.kt:228` | `analyzeAcousticFeatures(...)` | Acoustic spectral centroid, harmonic ratio, zero-crossing rate computation loops |
| **8** | **16** | 24 | `android/services/mesh` &bull; `NearbyConnectionsTransport.kt:340` | `formatError(...)` | Nearby Connections status code error translation branching |
| **9** | **15** | 43 | `android/services/evidence` &bull; `EvidenceManifestManager.kt:65` | `verifyPackageIntegrity(...)` | Merkle proof verification, file hash checks, signature and timestamp validation |
| **10** | **15** | 54 | `android/app` &bull; `SheGuardReportingScreen.kt:1159` | `HazardCategoryChip(...)` | Jetpack Compose dynamic UI theming, category icon/color mappings, state transitions |
| **11** | **14** | 62 | `android/services/detection` &bull; `TFLiteScreamClassifier.kt:178` | `classifyAudioFrameDetailed(...)` | Model input prep, tensor inference validation, score smoothing, fallback logic |
| **12** | **13** | 65 | `android/app` &bull; `EvidenceExporter.kt:24` | `createExportPackage(...)` | ZIP archive bundling, manifest hashing, chain of custody metadata export |
| **13** | **13** | 52 | `android/features/incident` &bull; `SafetyForegroundService.kt:330` | `startAudioRecording(...)` | Android AudioRecord configuration, permissions checks, buffer allocation fallbacks |
| **14** | **12** | 62 | `android/core/domain` &bull; `SpatioTemporalPatternEngine.kt:19` | `detectCandidatePatterns(...)` | Haversine distance matrix clustering, sliding time window filtering, bucket generation |
| **15** | **12** | 84 | `android/features/notify-circle` &bull; `NotifyCircleManager.kt:69` | `dispatchAlert(...)` | SMS delivery fallback chain, phone number validation, location embedding, status tracking |
| **16** | **11** | 11 | `android/services/detection` &bull; `SignalFusionEngine.kt:69` | `evaluateConfirmationRule(...)` | Multi-sensor cross-confirmation rules, temporal correlation, confidence thresholds |
| **17** | **11** | 18 | `android/app` &bull; `DeviceLocationManager.kt:143` | `getEmergencyFix(...)` | High-priority fused location polling, timeout handling, precision downgrading |
| **18** | **11** | 25 | `android/app` &bull; `DeviceLocationManager.kt:254` | `resolveAddress(...)` | Geocoder reverse lookup, locale handling, network error fallbacks |
| **19** | **11** | 86 | `android/app` &bull; `SaharaComponents.kt:841` | `BreathingSafetyVisual(...)` | Compose canvas animation math, pulse scaling, state-driven color transitions |
| **20** | **11** | 56 | `android/app` &bull; `MainActivity.kt:377` | `SaharaAppNavigation(...)` | Navigation host route routing, deep link intent resolution, screen parameter passing |
| **21** | **10** | 25 | `backend` &bull; `app/dashboard.py:151` | `upload_reports(...)` | Batch ingestion parsing, PII sanitization, schema verification, sync persistence |
| **22** | **10** | 57 | `android/services/mesh` &bull; `SheGuardMeshAdapter.kt:217` | `handleIncomingPacket(...)` | Packet type routing (`ALERT`, `DISTRESS`, `MICRO_REPORT`), deduplication, store-and-forward |
| **23** | **10** | 36 | `android/app` &bull; `DeviceLocationManager.kt:94` | `getCurrentDeviceLocation(...)` | Provider availability checks (GPS vs Network), cached location freshness verification |
| **24** | **10** | 132 | `android/app` &bull; `SaharaScreens.kt:2106` | `AuthScreen(...)` | OTP phone input UI state machine, verification error display, countdown timers |

---

## 3. Exhaustive Inventory of Available Stubs, Fakes, and Mocks

Test doubles in the repository isolate external systems (database engines, network APIs, hardware sensors, and cloud LLMs) for reliable, deterministic offline testing.

### 3.1 Explicit Stub / Fake / Mock Classes (14 implementations)

| # | Double Name | File Path | Target Interface / Superclass | Behavior / Purpose |
| :---: | :--- | :--- | :--- | :--- |
| **1** | `FakeIncidentDao` | `android/core/testing/.../fakes/Fakes.kt` | `IncidentDao` | In-memory `MutableMap` tracking incident entities and state updates. |
| **2** | `FakeEvidenceDao` | `android/core/testing/.../fakes/Fakes.kt` | `EvidenceDao` | In-memory `MutableList` emitting Room flow streams of evidence entries. |
| **3** | `FakeAuditEventDao` | `android/core/testing/.../fakes/Fakes.kt` | `AuditEventDao` | In-memory audit log list for event verification without SQLite. |
| **4** | `FakeNotifyContactDao` | `android/core/testing/.../fakes/Fakes.kt` | `NotifyContactDao` | In-memory contact map with CRUD flow emissions for trust circle. |
| **5** | `FakeIncidentDao` | `android/core/data/.../RepositoryMappingUnitTest.kt` | `IncidentDao` | Local test fake verifying repository-to-DAO domain model mapping. |
| **6** | `FakeEvidenceDao` | `android/core/data/.../RepositoryMappingUnitTest.kt` | `EvidenceDao` | Local test fake verifying evidence repository roundtrips and ordering. |
| **7** | `FakeAuditEventDao` | `android/core/data/.../RepositoryMappingUnitTest.kt` | `AuditEventDao` | Local test fake verifying append-only audit event logging semantics. |
| **8** | `FakeNotifyContactDao` | `android/core/data/.../RepositoryMappingUnitTest.kt` | `NotifyContactDao` | Local test fake verifying emergency contact persistence mapping. |
| **9** | `FakeMicroReportDao` | `android/core/data/.../SheGuardDataUnitTest.kt` | `MicroReportDao` | In-memory Room DAO fake verifying unsynced micro-report queries and updates. |
| **10** | `FakePatternDao` | `android/core/data/.../SheGuardDataUnitTest.kt` | `PatternDao` | In-memory Room DAO fake verifying spatio-temporal candidate persistence. |
| **11** | `FakeAlertDao` | `android/core/data/.../SheGuardDataUnitTest.kt` | `AlertDao` | In-memory Room DAO fake verifying rising early-warning alert caching. |
| **12** | `FakeAlertRepository` | `android/services/mesh/.../SheGuardMeshUnitTest.kt` | `AlertRepository` | In-memory repository fake verifying mesh incoming alert persistence and deduplication. |
| **13** | `DeterministicMockAIProvider` | `backend/app/agents/providers.py` | `BaseAIProvider` | Deterministic offline AI provider delivering rule-based responses without external LLM calls. |
| **14** | `MockHTTPResponse` | `backend/tests/test_ai_agent.py` | `urllib.request.urlopen` mock | Mock HTTP response object simulating Groq Cloud completions and error codes. |

### 3.2 Anonymous / In-line Test Doubles and Callbacks (7 implementations)

| # | Anonymous Double / Callback | File Path | Target Component | Purpose |
| :---: | :--- | :--- | :--- | :--- |
| **1** | `object : TFLiteScreamClassifier` | `android/services/detection/.../ScreamDetectorUnitTest.kt:39` | `TFLiteScreamClassifier` | Negative dummy classifier forcing DSP fallback evaluation. |
| **2** | `object : TFLiteScreamClassifier` | `android/services/detection/.../ScreamDetectorUnitTest.kt:147` | `TFLiteScreamClassifier` | Stub classifier returning positive scream probability (0.95). |
| **3** | `object : TFLiteScreamClassifier` | `android/services/detection/.../ScreamDetectorUnitTest.kt:180` | `TFLiteScreamClassifier` | Stub classifier returning borderline score (0.60) to test hysteresis. |
| **4** | `object : PayloadCallback` | `android/services/mesh/.../NearbyConnectionsTransport.kt:118` | Google Nearby API | Internal listener stub for BLE byte stream reception. |
| **5** | `object : ConnectionLifecycleCallback` | `android/services/mesh/.../NearbyConnectionsTransport.kt:156` | Google Nearby API | Internal listener stub for peer handshake and connection state. |
| **6** | `object : EndpointDiscoveryCallback` | `android/services/mesh/.../NearbyConnectionsTransport.kt:195` | Google Nearby API | Internal listener stub for peer discovery in physical mesh. |
| **7** | `object : ServiceConnection` | `android/app/.../MainActivity.kt:105` | Android OS | Android OS IPC service connection binding `SafetyForegroundService`. |

---

## 4. Exhaustive Inventory of Available Test Drivers

The codebase contains **22 test driver suites** executing a total of **169 automated test cases** across Android and Backend subsystems.

### 4.1 Android Client Test Drivers (20 Suites, 147 Test Cases)

| Suite File Path | Subsystem | Test Count | Key Invariants & Scenarios Verified |
| :--- | :--- | :---: | :--- |
| `android/core/domain/.../TrustAndAntiGamingEvaluatorTest.kt` | `core/domain` | **18** | Anti-gaming rules, volume $\ne$ trust, multi-reporter diversity, temporal/spatial consistency, deduplication. |
| `android/core/domain/.../RisingPatternAlertEngineTest.kt` | `core/domain` | **17** | State gates (`PATTERN_EMERGING` only), trust threshold $\ge 0.60$, deterministic alert IDs, privacy coarsening. |
| `android/core/domain/.../SpatioTemporalPatternEngineTest.kt` | `core/domain` | **11** | Haversine clustering, sliding time window filtering, input/order idempotency, missing location tolerance. |
| `android/core/domain/.../ModelsUnitTest.kt` | `core/domain` | **5** | Domain model constructors, enum states, and entity integrity. |
| `android/core/data/.../SheGuardDataUnitTest.kt` | `core/data` | **5** | Room mapping for MicroReports, SpatioTemporalPatterns, and Early-Warning Alerts. |
| `android/core/data/.../RepositoryMappingUnitTest.kt` | `core/data` | **4** | Incident, Evidence, Audit, and Contact repository mapping to DAO entities. |
| `android/core/security/.../CryptoUnitTest.kt` | `core/security` | **4** | AES-GCM encryption/decryption roundtrip, key wrapping, persistent fallback keystore, Merkle root integrity. |
| `android/services/mesh/.../SheGuardMeshUnitTest.kt` | `services/mesh` | **31** | Wire codec serialization, hop limits, loop prevention, deduplication, PII stripping, distress alert relay. |
| `android/services/mesh/.../MeshRelayUnitTest.kt` | `services/mesh` | **5** | Mesh relay cache eviction, TTL expiry, SMS fallback formatting, distress packet queuing. |
| `android/services/detection/.../DetectionUnitTest.kt` | `services/detection` | **11** | TFLite scream/speech classifiers, DSP acoustic analysis, multi-sensor signal fusion, detection audit log. |
| `android/services/detection/.../ScreamDetectorUnitTest.kt` | `services/detection` | **10** | Frequency filtering, transient noise rejection (claps, impacts), speech vs scream discrimination. |
| `android/services/evidence/.../EvidenceEngineUnitTest.kt` | `services/evidence` | **4** | Pre-roll audio ringbuffer pruning, manifest cryptographic sealing, tamper detection. |
| `android/features/notify-circle/.../NotifyCircleUnitTest.kt` | `features/notify-circle` | **3** | Max contacts limit enforcement, SMS permission fallback, incident trigger alert dispatch. |
| `android/features/panic/.../PanicControllerUnitTest.kt` | `features/panic` | **2** | Immediate emergency panic triggering, countdown cancellation window. |
| `android/features/incident/.../IncidentStateMachineUnitTest.kt` | `features/incident` | **3** | Incident state transitions (`MONITORING` $\to$ `CANDIDATE` $\to$ `ACTIVE` $\to$ `SEALED`), history preservation. |
| `android/app/.../EmergencyResponseIntegrationTest.kt` | `app` | **4** | End-to-end incident activation, emergency notification dispatch, mesh distress relay. |
| `android/app/.../SheGuardLocationUnitTest.kt` | `app` | **4** | Multi-device coordinate isolation, location accuracy buckets, live coordinate clustering. |
| `android/app/.../AppMilestone8UnitTest.kt` | `app` | **3** | Offline help directory lookup, evidence export ZIP verification, tampered archive rejection. |
| `android/app/.../BackendIntegrationTest.kt` | `app` | **2** | Legal draft payload serialization without raw PII/audio, offline fallback behavior. |
| `android/app/.../CriticalEvidenceIntegrationTest.kt` | `app` | **1** | End-to-end sensor detection $\to$ incident creation $\to$ evidence sealing pipeline. |

### 4.2 Backend Service Test Drivers (2 Suites, 22 Test Cases)

| Suite File Path | Subsystem | Test Count | Key Invariants & Scenarios Verified |
| :--- | :--- | :---: | :--- |
| `backend/tests/test_backend.py` | `backend/app` | **11** | OTP authentication flow, token refresh, batch report sync, raw audio rejection, AI endpoints, rate limits. |
| `backend/tests/test_ai_agent.py` | `backend/agents` | **11** | Legal draft generation, timeline summaries, grounded Q&A, mock Groq provider HTTP error handling. |

---

## 5. Architectural Quality Observations & Testing Observations

1. **Low Overall Complexity ($CCN = 2.42$) with Isolated Algorithmic Hotspots**:
   - The vast majority of components (DAOs, Repositories, Domain models) exhibit low complexity ($CCN \le 2$).
   - High complexity is concentrated in algorithmic decision points:
     - `TrustAndAntiGamingEvaluator.evaluate` ($CCN = 32$): Implements multi-variable heuristics to prevent alert gaming.
     - `Detectors.processAudioChunk` ($CCN = 31$): Implements real-time DSP audio frequency bandpass filtering and envelope tracking.
     - `providers.answer_question` ($CCN = 25$): Implements AI grounded response guardrails.
     - `dashboard.cluster_reports` ($CCN = 20$): Implements spatio-temporal Haversine radius grouping.

2. **Decoupled Architecture Through Interface Fakes**:
   - The Room persistence tier in `android/core/data` and `android/core/testing` provides comprehensive fakes (`FakeIncidentDao`, `FakeEvidenceDao`, etc.) that allow the domain engines and mesh services to run unit tests in standard JVM environments without Robolectric or an Android emulator.

3. **Strong Verification Coverage of Critical Safety Invariants**:
   - **Privacy Guard**: Tests in `SheGuardMeshUnitTest.kt` explicitly assert that PII and raw high-precision GPS coordinates are never serialized into mesh envelopes.
   - **Anti-Gaming Invariant**: Tests in `TrustAndAntiGamingEvaluatorTest.kt` explicitly assert that high volume from a single user cannot escalate a pattern to an alert.
   - **Offline-First Resilience**: Tests in `BackendIntegrationTest.kt` and `MeshRelayUnitTest.kt` verify graceful fallback when network or mesh transport is unavailable.
