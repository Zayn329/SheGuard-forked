# SheGuard — Comprehensive Integration Gaps Audit Report

**Date:** March 2026
**Authors:** Lead Mobile Architect & UX Engineering Team
**Scope:** Native Android Application, Background Foreground Services, Local Persistence (Room), Mesh Transport (Google Nearby Connections), FastAPI Backend API, and User Experience (UX) Flows.
**Authority:** Aligned with `architecture.yaml` (v2.1) and `docs/SHEGUARD_PRD.md`.

---

## Executive Summary

SheGuard is designed as an offline-first intelligent safety system operating on a 3-tier operational hierarchy:
1. **Local Operation (Fundamental Guarantee):** On-device micro-reporting, Room persistence, spatio-temporal risk clustering, trust evaluation, and early-warning alert generation.
2. **Mesh Communication (MVP Capability):** Peer-to-peer relay over BLE / Wi-Fi Direct via Google Nearby Connections.
3. **Backend Synchronization (Supporting Capability):** Asynchronous sync to cloud endpoints upon connectivity restoration.

While the core MVP components (Phase 1–5) are fully implemented and verified via unit tests, an in-depth end-to-end integration audit reveals critical architectural, lifecycle, state management, UI/UX, and synchronization gaps that must be addressed for production readiness and seamless user experience.

This document details all **Major** (blocking/high-impact) and **Minor** (degraded experience/edge-case) integration gaps across five core operational subsystems.

---

## 1. Domain Pipeline & Pattern Engine Lifecycle Gaps

### Major Gaps

#### Gap 1.1: Event-Driven Background Trigger for Spatio-Temporal Pattern Engine
- **Severity:** Major
- **Component / Path:** `android/features/incident/src/main/java/org/sahara/features/incident/service/SafetyForegroundService.kt` & `android/app/src/main/java/org/sahara/app/ui/SheGuardReportingScreen.kt`
- **Description:** When automated sensor fusion detects screams or impact signals, `SafetyForegroundService` persists a `MicroReport` and triggers `SpatioTemporalPatternEngine.detectCandidatePatterns()` in the background. However, when a user manually submits a micro-report from `SheGuardReportingScreen`, the report is saved directly to Room, but there is no background observer or broadcast trigger notifying `SafetyForegroundService` to immediately re-evaluate candidate patterns across the surrounding spatio-temporal window. Pattern re-evaluation on manually created reports only occurs when `SheGuardReportingScreen` is composed in the foreground.
- **Impact:** Micro-reports created while the app UI is closed or backgrounded do not contribute to risk pattern detection until the user re-opens the reporting UI.
- **Recommended Resolution:** Introduce a Room DAO `Flow<List<MicroReport>>` observer in `SafetyForegroundService` or emit an internal Kotlin `SharedFlow<DomainEvent>` to trigger pattern engine evaluation automatically whenever a new micro-report is persisted in Room.

#### Gap 1.2: Unautomated Background Dispatch of Early-Warning Alerts to BLE Mesh
- **Severity:** Major
- **Component / Path:** `android/app/src/main/java/org/sahara/app/ui/SheGuardReportingScreen.kt` & `android/services/mesh/src/main/java/org/sahara/services/mesh/relay/SheGuardMeshAdapter.kt`
- **Description:** `RisingPatternAlertEngine` generates early-warning pattern alerts when an emerging pattern achieves a trust score $\ge 0.60$. Currently, alert queueing for mesh transmission (`sheGuardMeshAdapter.queueAlertForRelay()`) is invoked within UI `LaunchedEffect` blocks inside `SheGuardReportingScreen`. If an emerging pattern condition is satisfied while the user is on the Home Dashboard or Safety Watch screen, the alert is not automatically queued for mesh relay.
- **Impact:** Nearby participating devices will fail to receive early-warning alerts over BLE mesh unless the originating user actively views the Reporting screen.
- **Recommended Resolution:** Move pattern alert evaluation and mesh adapter queueing into a centralized repository or `SafetyForegroundService` background worker.

### Minor Gaps

#### Gap 1.3: Spatial Clustering Distance Metric vs Coarse Geohash Truncation Discrepancy
- **Severity:** Minor
- **Component / Path:** `android/core/domain/src/main/java/org/sahara/core/domain/engine/SpatioTemporalPatternEngine.kt`
- **Description:** `SpatioTemporalPatternEngine` uses precise Haversine formulas to compute distance radii between micro-report coordinates. Conversely, privacy-minimized mesh packets and backend sync endpoints truncate coordinates to 2 decimal places (~1.1 km precision) or geohash string prefixes. This causes potential boundary mismatches when evaluating mesh-received reports against exact local reports.
- **Impact:** Mesh-received micro-reports with coarse coordinates may fall outside precise Haversine distance thresholds during local pattern clustering.
- **Recommended Resolution:** Standardize spatial matching by supporting dual-mode spatial clustering: exact Haversine distance for local high-accuracy reports, and cell-based geohash matching for privacy-minimized mesh reports.

---

## 2. Mesh Transport & Peer Networking Gaps

### Major Gaps

#### Gap 2.1: In-Memory Outbound Mesh Queue Persistence Deficit
- **Severity:** Major
- **Component / Path:** `android/services/mesh/src/main/java/org/sahara/services/mesh/relay/SheGuardMeshAdapter.kt`
- **Description:** `SheGuardMeshAdapter` manages outbound store-and-forward mesh packets via an in-memory queue (`outboundQueue: ArrayDeque<MeshPacket>`). If the Android OS terminates the process or the phone reboots while disconnected from mesh peers, all queued `MicroReport` and `RisingPatternAlert` mesh packets waiting for transport are permanently lost.
- **Impact:** Violates store-and-forward guarantees for disconnected mesh propagation across device restart cycles.
- **Recommended Resolution:** Store outbound mesh packets in Room DB (`micro_reports` table with `SyncStatus.MESH_QUEUED` and dedicated `outbound_mesh_packets` table), and hydrate `outboundQueue` from Room upon service initialization.

#### Gap 2.2: Absence of Mesh Peer Discovery & Transport State UX Controls
- **Severity:** Major
- **Component / Path:** `android/services/mesh/src/main/java/org/sahara/services/mesh/transport/NearbyConnectionsTransport.kt` & `android/app/src/main/java/org/sahara/app/ui/SaharaScreens.kt`
- **Description:** `NearbyConnectionsTransport` handles BLE/Wi-Fi Direct advertising and discovery automatically, but the user interface lacks active peer count indicators, mesh connection status badges (e.g. "3 Peers Connected via BLE"), and manual mesh discovery toggle controls.
- **Impact:** Users are unable to verify whether peer-to-peer mesh networking is operational or if nearby participating devices are present.
- **Recommended Resolution:** Expose a `StateFlow<MeshTransportState>` from `NearbyConnectionsTransport` to Compose UI, and render a mesh status panel on `HomeDashboardScreen` displaying connected peer count and radio transport mode (BLE / Wi-Fi Direct).

### Minor Gaps

#### Gap 2.3: System Clock Skew Vulnerability in Mesh Deduplication Window
- **Severity:** Minor
- **Component / Path:** `android/services/mesh/src/main/java/org/sahara/services/mesh/relay/MeshPayloadValidator.kt`
- **Description:** Mesh packet validation enforces a temporal window check against system clock time (`abs(now - timestamp) <= maxAge`). If a receiving device's system clock is out of sync by several minutes, valid mesh packets relayed from nearby peers may be rejected as expired.
- **Impact:** Clock drift on disconnected devices can prevent valid mesh alert propagation.
- **Recommended Resolution:** Use monotonic elapsed realtime clock offsets or relax temporal validation windows for peer-relayed mesh packets up to 24 hours.

---

## 3. Background Services & Service Lifecycle Gaps

### Major Gaps

#### Gap 3.1: Service Binder Nullability & Unbound Callback Detachment
- **Severity:** Major
- **Component / Path:** `android/app/src/main/java/org/sahara/app/MainActivity.kt` & `android/features/incident/src/main/java/org/sahara/features/incident/service/SafetyForegroundService.kt`
- **Description:** `MainActivity` binds `SafetyForegroundService` via `ServiceConnection` and assigns local state machine and mesh adapter instances. However, if the Activity is re-created (e.g., orientation change or dark mode toggle), `foregroundService` becomes temporarily null or unbound, detaching UI event listeners from background service execution.
- **Impact:** Critical events like panic activation or incoming mesh alert notifications during Activity re-creation may fail to propagate to the state machine or UI.
- **Recommended Resolution:** Shift state machine lifecycle ownership and mesh adapter singleton management into an Application-scoped container or Hilt dependency injection module.

#### Gap 3.2: Notification Deep Link Intent State Synchronization in Compose
- **Severity:** Major
- **Component / Path:** `android/app/src/main/java/org/sahara/app/MainActivity.kt`
- **Description:** Tapping an incoming high-priority mesh notification launches `MainActivity` with `TARGET_SCREEN` extra (`Screen.SHEGUARD_REPORTING` or `Screen.TRUSTED_ALERT`). When the app is running in the background, `onNewIntent(intent)` updates the activity intent, but Compose `SaharaAppNavigation` state `currentScreen` was not re-evaluating deep links dynamically unless `intent` state triggered a recomposition.
- **Impact:** Tapping a notification while the app is backgrounded brings the app to the foreground but fails to navigate directly to the target alert screen.
- **Recommended Resolution:** Wrap intent extras in a `MutableStateFlow<Intent>` or observe `onNewIntent` updates inside Compose via `LaunchedEffect(intent)` to force instant navigation state updates.

### Minor Gaps

#### Gap 3.1: Audio Mic Contention & Silent Stalling in Pre-Roll Buffer
- **Severity:** Minor
- **Component / Path:** `android/features/incident/src/main/java/org/sahara/features/incident/service/SafetyForegroundService.kt`
- **Description:** `SafetyForegroundService` records 16kHz MONO PCM audio continuously via `AudioRecord`. If an external app requests exclusive microphone access (e.g., incoming phone call), `audioRecord.read()` returns `ERROR_INVALID_OPERATION` silently without restarting the audio buffer thread when mic access returns.
- **Impact:** Screams or speech commands will not be detected following microphone contention until the service is restarted.
- **Recommended Resolution:** Implement an audio record status check loop that attempts automatic `AudioRecord` re-initialization upon read errors.

---

## 4. Backend Synchronization & API Contract Gaps

### Major Gaps

#### Gap 4.1: Missing Aggregated SpatioTemporal Pattern & Alert Sync Endpoints
- **Severity:** Major
- **Component / Path:** `android/app/src/main/java/org/sahara/app/sync/SheGuardSyncManager.kt` & `backend/app/main.py`
- **Description:** `SheGuardSyncManager` syncs `MicroReport` domain models to backend via `POST /api/v1/sync/batch`. However, there are no sync paths or FastAPI endpoints for transmitting locally generated `SpatioTemporalPattern` or `RisingPatternAlert` records to the backend.
- **Impact:** Cloud backend services cannot aggregate community-level spatio-temporal risk patterns across multi-city boundaries or surface macro-analytics.
- **Recommended Resolution:** Add `PATTERN_EMERGING` and `RISING_ALERT_GENERATED` event payloads to `BatchSyncRequest` Pydantic schemas and implement pattern aggregation handlers in FastAPI backend.

#### Gap 4.2: Authentication Token Persistence & Fallback Vulnerability
- **Severity:** Major
- **Component / Path:** `android/app/src/main/java/org/sahara/app/ui/SaharaScreens.kt` & `android/app/src/main/java/org/sahara/app/sync/SheGuardSyncManager.kt`
- **Description:** `SaharaApiClient` stores access and refresh tokens in memory (`savedAccessToken`). Upon app restart, `savedAccessToken` resets to `null`. When background sync runs, `SheGuardSyncManager` falls back to `"bearer_demo_token"`, causing 401 Unauthorized errors against a live backend requirement.
- **Impact:** Automated background sync fails after app process restarts until the user manually logs in via OTP screen.
- **Recommended Resolution:** Persist JWT tokens securely using Android `EncryptedSharedPreferences` or `KeyStorageManagerImpl` and implement automatic token refresh flows in `SaharaApiClient`.

### Minor Gaps

#### Gap 4.3: Granular Error Reporting for Partial Batch Sync Failures
- **Severity:** Minor
- **Component / Path:** `android/app/src/main/java/org/sahara/app/sync/SheGuardSyncManager.kt`
- **Description:** FastAPI backend returns `BatchSyncResponse` containing lists of `accepted_event_ids` and `rejected_events`. `SheGuardSyncManager` iterates over reports and updates status, but does not persist rejection reasons or error codes into `MicroReportEntity` when individual items are rejected.
- **Impact:** Rejected reports remain in limbo or continuously attempt sync without developer diagnostics.
- **Recommended Resolution:** Add an `errorMessage: String?` field to `MicroReportEntity` and update Room records with backend rejection reasons.

---

## 5. User Interface (UI) & User Experience (UX) Integration Gaps

### Major Gaps

#### Gap 5.1: Legacy Product Identity Branding Inconsistencies
- **Severity:** Major
- **Component / Path:** `android/app/src/main/java/org/sahara/app/ui/SaharaScreens.kt` & `android/app/src/main/java/org/sahara/app/ui/SaharaTheme.kt`
- **Description:** While domain models and Phase 1–5 feature modules reference "SheGuard", several user-facing UI screens, top app bars, notification channel titles, and welcome text still display legacy "Sahara Safety Companion" product branding.
- **Impact:** Causes brand confusion and inconsistent user experience during hackathon evaluation and user testing.
- **Recommended Resolution:** Unify product branding across strings, top app bars, theme composables (`SheGuardTheme`), and notification channel names under the canonical product title: **SheGuard**.

#### Gap 5.2: Absence of Unified 3-Tier Offline Hierarchy Dashboard Banner
- **Severity:** Major
- **Component / Path:** `android/app/src/main/java/org/sahara/app/ui/SaharaScreens.kt` (`HomeDashboardScreen`)
- **Description:** `SHEGUARD_PRD.md` defines an offline-first 3-tier operational hierarchy (Tier 1: Local Operation, Tier 2: BLE/Wi-Fi Direct Mesh, Tier 3: Cloud Sync). The current Home Dashboard displays individual status widgets, but lacks an explicit visual banner communicating the active operational tier to the user.
- **Impact:** Users are unaware whether their safety reports are relying solely on local storage, actively propagating via mesh, or syncing to cloud servers.
- **Recommended Resolution:** Add a high-visibility `OfflineHierarchyBanner` at the top of `HomeDashboardScreen` displaying the active operational tier (e.g. `🟢 Local Guarantee Active | 🔵 Mesh: 2 Peers Connected | 🟠 Offline (Sync Pending)`).

### Minor Gaps

#### Gap 5.3: Hardcoded Color Values in Custom Compose UI Components
- **Severity:** Minor
- **Component / Path:** `android/app/src/main/java/org/sahara/app/ui/SaharaComponents.kt`
- **Description:** Several Compose components (e.g. cards, status badges, buttons) use hardcoded `Color(0xFF1E293B)` or `Color(0xFF0F172A)` hex values instead of referencing `MaterialTheme.colorScheme` semantic tokens.
- **Impact:** Switching between Dark Mode and Light Mode produces inconsistent contrast ratios and text legibility issues on specific screens.
- **Recommended Resolution:** Replace hardcoded hex colors in `SaharaComponents.kt` with `MaterialTheme.colorScheme.surface`, `onSurface`, `primaryContainer`, and `errorContainer`.

#### Gap 5.4: Default Coordinates Fallback during Manual Emergency Panic Activation
- **Severity:** Minor
- **Component / Path:** `android/app/src/main/java/org/sahara/app/MainActivity.kt` & `android/features/panic/src/main/java/org/sahara/features/panic/controller/PanicController.kt`
- **Description:** When a user triggers manual emergency panic, the app seeds a local `MicroReport` to contribute to spatio-temporal risk clustering. If GPS location fix is disabled or unavailable, default fallback coordinates (Bandra West, Mumbai) are assigned without explicitly flagging location precision quality to the user.
- **Impact:** Synthetic or default coordinates may contaminate local spatio-temporal risk clusters if GPS permissions are denied.
- **Recommended Resolution:** Include an explicit `locationQuality: LocationQuality` enum (`EXACT_GPS`, `APPROXIMATE_CELL`, `DEFAULT_UNAVAILABLE`) in `MicroReport` models.

---

## Audit Matrix & Action Plan Summary

| Ref ID | Subsystem | Gap Title | Severity | Primary Component | Recommended Resolution Phase |
|---|---|---|---|---|---|
| **1.1** | Domain Engine | Event-Driven Background Trigger for Pattern Engine | **Major** | `SafetyForegroundService.kt` | Phase 6 Demo Hardening |
| **1.2** | Domain Engine | Unautomated Background Dispatch of Mesh Alerts | **Major** | `SheGuardReportingScreen.kt` | Phase 6 Demo Hardening |
| **1.3** | Domain Engine | Spatial Metric vs Geohash Truncation Discrepancy | Minor | `SpatioTemporalPatternEngine.kt` | Future Hardening |
| **2.1** | Mesh Transport | In-Memory Outbound Mesh Queue Persistence Deficit | **Major** | `SheGuardMeshAdapter.kt` | Phase 6 Demo Hardening |
| **2.2** | Mesh Transport | Absence of Mesh Peer Discovery & Transport UX | **Major** | `SaharaScreens.kt` | Phase 6 Demo Hardening |
| **2.3** | Mesh Transport | System Clock Skew Vulnerability in Mesh Deduplication | Minor | `MeshPayloadValidator.kt` | Future Hardening |
| **3.1** | Foreground Service | Service Binder Nullability & Callback Detachment | **Major** | `MainActivity.kt` | Phase 6 Demo Hardening |
| **3.2** | Foreground Service | Notification Deep Link Intent Synchronization | **Major** | `MainActivity.kt` | Phase 6 Demo Hardening |
| **3.3** | Foreground Service | Audio Mic Contention & Silent Stalling in Pre-Roll | Minor | `SafetyForegroundService.kt` | Future Hardening |
| **4.1** | Backend Sync | Missing Pattern & Alert Aggregation Endpoints | **Major** | `SheGuardSyncManager.kt` / FastAPI | Phase 6 Demo Hardening |
| **4.2** | Backend Sync | Auth Token Persistence & Fallback Vulnerability | **Major** | `SaharaApiClient.kt` | Phase 6 Demo Hardening |
| **4.3** | Backend Sync | Granular Error Reporting for Partial Batch Sync | Minor | `SheGuardSyncManager.kt` | Future Hardening |
| **5.1** | UI / UX | Legacy Product Identity Branding Inconsistencies | **Major** | `SaharaScreens.kt` | Phase 6 Demo Hardening |
| **5.2** | UI / UX | Absence of 3-Tier Offline Hierarchy Banner | **Major** | `HomeDashboardScreen.kt` | Phase 6 Demo Hardening |
| **5.3** | UI / UX | Hardcoded Color Values in Custom Compose UI | Minor | `SaharaComponents.kt` | Phase 6 Demo Hardening |
| **5.4** | UI / UX | Default Coordinates Fallback in Manual Panic | Minor | `PanicController.kt` | Future Hardening |

---
*End of SheGuard Integration Gaps Audit Report.*
