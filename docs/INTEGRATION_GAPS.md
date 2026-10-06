# SheGuard — Comprehensive Integration Gaps & Architecture Analysis Report

**Product:** SheGuard
**Team:** Aegis (CX1001)
**Authors:** Senior App Architect & Lead UX Engineer
**Date:** March 2026
**Document Authority:** Aligned with `architecture.yaml` and `docs/SHEGUARD_PRD.md`

---

## Executive Summary

SheGuard is an offline-first intelligent safety system that converts low-friction, anonymous micro-reports into verified spatio-temporal risk patterns and actionable community-level early warnings.

This document presents an exhaustive expert audit of the current codebase (`android/` client, `backend/` FastAPI services, Room persistence, BLE mesh transport, and UX state workflows). It identifies **14 major and minor integration gaps** spanning core offline pipeline execution, UI/UX state synchronization, mesh background lifecycle, sensor fusion, evidence security, and backend synchronization, along with concrete engineering and UX remediation paths for each gap.

---

## Summary Matrix of Identified Integration Gaps

| ID | Severity | Domain | Title | Impact |
|:---|:---|:---|:---|:---|
| **GAP-01** | **MAJOR** | Core Pipeline | Room Pattern Observation Disconnect in Compose UI | Background-detected patterns/alerts are not reactively reflected in UI without manual recalculation. |
| **GAP-02** | **MAJOR** | Core Pipeline | Local Compose Pattern Recalculation vs Database Authority | UI recalculates candidate patterns in Compose memory rather than observing Room database state. |
| **GAP-03** | **MINOR** | Core Pipeline | Hardcoded Coarse Location Context | Micro-reports populate static fallback strings ("Dadated Street") when GPS is unacquired. |
| **GAP-04** | **MINOR** | Core Pipeline | Startup Outbound Mesh Queue Invalidation | Queued offline reports/alerts in Room DB are not restored to the in-memory mesh outbound queue upon process restart. |
| **GAP-05** | **MAJOR** | Mesh & Background | Mesh Transport Lifecycle Tied to Activity Context | BLE discovery/advertising stops when `MainActivity` is closed, crippling background mesh relay. |
| **GAP-06** | **MAJOR** | Mesh & Background | Fragile Regex Json Parsing for Mesh Micro-Reports | Special characters or quotes in report descriptions cause regex parsing failures on peer devices. |
| **GAP-07** | **MINOR** | Mesh & Background | Generic Notification Intent Routing | Tapping a mesh alert system notification opens a generic screen rather than focusing the specific alert ID. |
| **GAP-08** | **MAJOR** | UX & Interaction | Monitoring Switch Disconnect from Background Detection | Toggling "Monitoring Active" on the Home Dashboard does not pause background audio/sensor threads. |
| **GAP-09** | **MAJOR** | UX & Interaction | Passive Alert Cards Lacking Actionable Pathways | Early-warning alert cards display information but lack 1-tap actions (Share to Circle, Avoid Area, View Directory). |
| **GAP-10** | **MINOR** | UX & Interaction | Static Palette Visual Contrast in System Dark Mode | Custom `SheGuardColors` use fixed light container values that do not adapt to system dark themes. |
| **GAP-11** | **MINOR** | UX & Interaction | Missing Actionable degraded Mode Banners | UI displays "LOCAL_ONLY" status but lacks a 1-tap "Enable Bluetooth / Permissions" prompt. |
| **GAP-12** | **MAJOR** | Security & Sensors | Dual Pre-Roll Audio Buffer Instance Synchronization | `SafetyForegroundService` and `MainActivity` maintain independent pre-roll buffers, risking audio loss. |
| **GAP-13** | **MINOR** | Security & Sensors | Deprecated `SmsManager` API Invocations | Fallback manager uses deprecated `SmsManager.getDefault()`, triggering API compatibility warnings. |
| **GAP-14** | **MAJOR** | Backend & Sync | One-Way Micro-Report Sync without Pattern/Alert Endpoints | Backend sync uploads micro-reports but cannot pull or push aggregated spatial patterns across nodes. |

---

## Detailed Gap Analysis & Strategic Remediation

### 1. CORE SHEGUARD PIPELINE & ROOM PERSISTENCE

#### GAP-01 (MAJOR): Room Pattern Observation Disconnect in Compose UI
- **Subsystem:** `android/app/src/main/java/org/sahara/app/ui/SheGuardReportingScreen.kt`, `PatternRepositoryImpl`
- **Root Cause:** In `SheGuardReportingScreen.kt`, micro-reports are reactively observed via `repository.getAllReports().collectAsState()`. However, `patternRepository` is NOT collected as a Flow state. Instead, patterns are computed synchronously in Compose memory using `remember(reportsState)`.
- **System & UX Impact:** When `SafetyForegroundService` creates a micro-report from background sensor fusion (audio scream or motion impact) and persists evaluated patterns into Room DB, the active `SheGuardReportingScreen` UI does NOT reflect these new background patterns until the user manually submits a new report.
- **Recommended Engineering Solution:** Modify `SheGuardReportingScreen` to observe `patternRepository.getAllPatterns().collectAsState(initial = emptyList())` and `alertRepository.getAllAlerts().collectAsState(initial = emptyList())` as the single sources of truth.

#### GAP-02 (MAJOR): Local Compose Pattern Recalculation vs Database Authority
- **Subsystem:** `SheGuardReportingScreen.kt` vs `SpatioTemporalPatternEngine.kt`
- **Root Cause:** Duplicate pattern evaluation logic exists in the UI layer. `SheGuardReportingScreen` executes `patternEngine.detectCandidatePatterns(reportsState)` and `trustEvaluator.evaluatePattern(...)` inside Compose `remember` blocks, bypassing the Room database's persistent pattern state.
- **System & UX Impact:** Divergence between what is stored in the local database (used for mesh packet queuing and backend sync) and what is rendered on screen.
- **Recommended Engineering Solution:** Delegate all pattern detection and trust evaluation to a unified domain coordinator or ViewModel that writes directly to `PatternRepository` and `AlertRepository`, allowing UI components to exclusively consume database Flows.

#### GAP-03 (MINOR): Hardcoded Coarse Location Context
- **Subsystem:** `SheGuardReportingScreen.kt`, `SafetyForegroundService.kt`
- **Root Cause:** `approximateArea` is hardcoded as `"Dadated Street / Mumbai Central"` in `SheGuardReportingScreen.kt` (containing a typo) and `"Bandra West / Mumbai Central"` in `SafetyForegroundService.kt`.
- **System & UX Impact:** When GPS fixes are unavailable or delayed offline, reports created by users or sensor signals display static placeholder location text rather than actual coarse network/cell location or last-known fused location.
- **Recommended Engineering Solution:** Integrate Android's `FusedLocationProviderClient` with coarse location permission (`ACCESS_COARSE_LOCATION`) to fall back to rounded lat/lng (~1km resolution) and dynamic coarse geocoding.

#### GAP-04 (MINOR): Startup Outbound Mesh Queue Invalidation
- **Subsystem:** `org/sahara/services/mesh/relay/SheGuardMeshAdapter.kt`
- **Root Cause:** `SheGuardMeshAdapter` stores pending store-and-forward mesh packets in an in-memory `outboundQueue: MutableList<MeshPacket>`. If the app process is terminated or recreated by Android OS, unsent mesh packets in memory are lost.
- **System & UX Impact:** Micro-reports or alerts marked with `SyncStatus.MESH_QUEUED` in Room DB are not automatically re-enqueued for mesh propagation when the app restarts and reconnects to peer devices.
- **Recommended Engineering Solution:** Implement an initialization bootstrapper in `SheGuardMeshAdapter` that queries `MicroReportRepository` and `AlertRepository` for `MESH_QUEUED` entities upon service startup and populates `outboundQueue`.

---

### 2. MESH TRANSPORT & BACKGROUND SERVICE EXECUTION

#### GAP-05 (MAJOR): Mesh Transport Lifecycle Tied to Activity Context
- **Subsystem:** `org/sahara/app/MainActivity.kt` vs `SafetyForegroundService.kt`
- **Root Cause:** `NearbyConnectionsTransport` and `SheGuardMeshAdapter` are initialized and controlled inside `MainActivity`. `MainActivity.onDestroy()` explicitly calls `meshTransport.stop()`.
- **System & UX Impact:** When the user closes `MainActivity` or locks their phone screen, BLE discovery and advertising are terminated. `SafetyForegroundService`, which continues running in the background, can no longer relay incoming or outgoing emergency distress mesh packets.
- **Recommended Engineering Solution:** Move `NearbyConnectionsTransport` and `SheGuardMeshAdapter` ownership into `SafetyForegroundService`. `MainActivity` should bind to the service to access mesh status and trigger manual relays.

#### GAP-06 (MAJOR): Fragile Regex JSON Parsing for Mesh Micro-Reports
- **Subsystem:** `SheGuardMeshAdapter.kt` (`parseReportFromJson`)
- **Root Cause:** `parseReportFromJson()` uses manual regular expressions (`Regex("\"contextDescription\"\\s*:\\s*\"([^\"]+)\"")`) to deserialize incoming `MICRO_REPORT` mesh payloads.
- **System & UX Impact:** If a user submits a context description containing quotes, newlines, or escaped characters, the regex match fails and the mesh packet is rejected silently without being saved to Room DB on receiving peer devices.
- **Recommended Engineering Solution:** Replace manual regex parsing with robust Kotlinx Serialization or standard `org.json.JSONObject` parsing with safe fallback defaults.

#### GAP-07 (MINOR): Generic Notification Intent Routing
- **Subsystem:** `MainActivity.kt` (`showIncomingMeshNotification`)
- **Root Cause:** System notifications generated for incoming BLE mesh distress or early warning alerts attach an intent extra `putExtra("TARGET_SCREEN", screen.name)`.
- **System & UX Impact:** Tapping the system notification opens the app to `Screen.TRUSTED_ALERT` or `Screen.SHEGUARD_REPORTING`, but fails to pass the specific `alertId` or `incidentId`. The user must manually scroll to find the alert that triggered the notification.
- **Recommended Engineering Solution:** Extend intent extras to include `ALERT_ID` or `INCIDENT_ID` and add auto-scroll/highlight state handling in `SheGuardReportingScreen`.

---

### 3. USER EXPERIENCE (UX) & MOBILE INTERACTION DESIGN

#### GAP-08 (MAJOR): Dashboard Safety Watch Toggle Disconnect from Background Detection
- **Subsystem:** `MainActivity.kt` (`HomeDashboardScreen`), `SafetyForegroundService.kt`
- **Root Cause:** On `HomeDashboardScreen`, the "Monitoring Active" switch toggles local Compose state `isMonitoringActive` and calls `stateMachine.startMonitoring()` / `stopMonitoring()`. However, `SafetyForegroundService`'s internal audio recording thread and accelerometer sensor listeners are not paused or stopped.
- **System & UX Impact:** Users expect turning off "Monitoring Active" to stop microphone and sensor usage. Currently, background audio recording continues silently, leading to privacy concerns and unexpected battery drain.
- **Recommended Engineering Solution:** Implement binder commands `pauseDetection()` and `resumeDetection()` on `SafetyForegroundService.LocalBinder` and invoke them when the dashboard toggle is flipped.

#### GAP-09 (MAJOR): Passive Early-Warning Alert Cards Lacking Actionable Pathways
- **Subsystem:** `SheGuardReportingScreen.kt`
- **Root Cause:** Early-warning alert cards render risk category, coarse location, trust level, and disclaimer, but contain no interactive buttons or actionable quick responses.
- **System & UX Impact:** When a user receives a high-trust rising pattern alert in their area, they are given no immediate UX options to protect themselves or inform others.
- **Recommended UX Solution:** Add direct 1-tap action buttons on `RisingPatternAlert` UI cards:
  1. **"Share Alert to Circle"** → Opens `NotifyCircle` dispatch.
  2. **"Avoid Area / View Safe Directory"** → Opens `OfflineHelpDirectory`.
  3. **"Mark Resolved / False Alarm"** → Submits feedback signal for trust evaluation.

#### GAP-10 (MINOR): Static Palette Visual Contrast in System Dark Mode
- **Subsystem:** `SheGuardReportingScreen.kt` (`SheGuardColors`)
- **Root Cause:** `SheGuardReportingScreen` utilizes hardcoded light background colors (e.g., `#FFF5F7` for `PrimaryContainer`, `#FAFAFC` for `SurfaceCard`) rather than leveraging Jetpack Compose `MaterialTheme.colorScheme`.
- **System & UX Impact:** When system Dark Mode is enabled on Android, the reporting screen displays bright white/pink cards, creating high visual glare and inconsistency with the rest of the dark-themed app.
- **Recommended UX Solution:** Refactor `SheGuardColors` to map dynamically to `MaterialTheme.colorScheme` tokens with dark/light variants in `SaharaTheme.kt`.

#### GAP-11 (MINOR): Missing Actionable Degraded Mode Banners
- **Subsystem:** `SheGuardReportingScreen.kt` (Status Bar)
- **Root Cause:** When Bluetooth is disabled or location permissions are missing, the status bar displays `"Mesh: LOCAL_ONLY"`. However, no actionable prompt or recovery button is presented.
- **System & UX Impact:** Users are left unaware of why mesh relay is inactive or how to enable peer-to-peer warnings.
- **Recommended UX Solution:** Introduce an interactive warning banner when mesh status is `LOCAL_ONLY`: **"Mesh Disabled: Tap to Enable Bluetooth & Nearby Permissions"**.

---

### 4. SECURITY, EVIDENCE INTEGRITY & SENSOR FUSION

#### GAP-12 (MAJOR): Dual Pre-Roll Audio Buffer Instance Synchronization
- **Subsystem:** `SafetyForegroundService.kt` vs `MainActivity.kt`
- **Root Cause:** `SafetyForegroundService` and `MainActivity` instantiate separate instances of `BoundedAudioPreRollBuffer`. While `SafetyForegroundService` attempts to copy chunks upon setter assignment, late initialization or service re-binding can leave pre-roll audio chunks uncollected.
- **System & UX Impact:** If a distress scream triggers an emergency incident while the activity is in the background, audio chunks captured during the 10-second pre-roll window right before activation may fail to seal into the cryptographic manifest.
- **Recommended Engineering Solution:** Singletonize `BoundedAudioPreRollBuffer` within the application context or expose it exclusively through `SafetyForegroundService`.

#### GAP-13 (MINOR): Deprecated `SmsManager` API Invocations
- **Subsystem:** `org/sahara/services/mesh/fallback/EscalationFallbackManager.kt`
- **Root Cause:** `EscalationFallbackManager` calls `SmsManager.getDefault()`, which is deprecated in modern Android SDKs (API 31+).
- **System & UX Impact:** Potential runtime exceptions or unexpected behavior on multi-SIM Android 12+ physical devices.
- **Recommended Engineering Solution:** Update SMS initialization to use `context.getSystemService(SmsManager::class.java)` with multi-SIM subscription context.

---

### 5. BACKEND & CLOUD SYNCHRONIZATION

#### GAP-14 (MAJOR): One-Way Micro-Report Sync without Pattern/Alert Endpoints
- **Subsystem:** `org/sahara/app/sync/SheGuardSyncManager.kt`, `backend/app/main.py`
- **Root Cause:** `SheGuardSyncManager` pushes `MicroReport` records to `/api/v1/sync/batch`. However, neither the client nor backend includes endpoints for syncing verified `SpatioTemporalPattern` or `RisingPatternAlert` domain entities.
- **System & UX Impact:** When network connectivity is restored, individual report events are backed up, but cross-device cloud pattern aggregation or city-wide pattern broadcasting across disconnected nodes cannot occur.
- **Recommended Engineering Solution:** Implement `/api/v1/patterns/sync` and `/api/v1/alerts/feed` endpoints on the FastAPI backend and integrate bi-directional sync in `SheGuardSyncManager`.

---

## Conclusion & Action Plan

This comprehensive audit highlights that SheGuard's core algorithmic pipeline (**Report → Detect → Trust → Alert**) is fully functional, deterministic, and offline-resilient. Addressing the **14 identified major and minor integration gaps**—specifically decoupling mesh lifecycle from Activity contexts, binding UI state directly to Room DB Flows, and adding actionable UX pathways—will elevate SheGuard into an enterprise-grade preventive safety system.
