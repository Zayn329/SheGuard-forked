# SheGuard Implementation Roadmap

This document outlines the coherent milestones for building **SheGuard** (Team Aegis, Problem Statement CX1001) according to `architecture.yaml`, `docs/SHEGUARD_PRD.md`, and `AGENTS.md`.

---

## Active Target Architecture & Product Intent
- **Product:** SheGuard
- **Team:** Aegis
- **Problem Statement:** CX1001
- **Domain:** Women Safety & Social Impact — Preventive & Community Safety
- **Canonical Product Contract:** `docs/SHEGUARD_PRD.md`
- **Technical Architecture Contract:** `architecture.yaml`
- **Legacy Context:** Sahara represents legacy repository context. Reusable infrastructure (Room, Nearby Connections mesh relay, AES-256-GCM, FastAPI) is retained, while SheGuard MVP core logic governs implementation.

---

## Hackathon Core Demo Path Overview
The critical path prioritized across the milestones is the deterministic pipeline:
**Report → Local Persistence → Detect → Trust → Alert (with BLE / Wi-Fi Direct Mesh Propagation)**

- **Local Operation = Fundamental Guarantee:** Local reporting, local Room persistence, local spatio-temporal pattern detection, local trust evaluation, and local early-warning alerting function deterministically on-device without Internet, backend, or mesh.
- **Mesh Communication = Core MVP Capability:** BLE / Wi-Fi Direct store-and-forward relay allows nearby participating devices to exchange compact reports and pattern signals without Internet. Mesh failure or absence of peers does NOT break local operation.
- **Backend Synchronization = Supporting Capability:** Asynchronous synchronization when Internet connectivity returns.

---

## Phase 0: Contract, Architecture & Governance Migration [COMPLETED]
- **Goal:** Establish canonical SheGuard contracts, technical architecture, coding-agent governance rules, and complete an inspection-only audit of existing repository infrastructure.
- **Status:** COMPLETED. Created `docs/SHEGUARD_PRD.md`, updated `architecture.yaml` (v2.1), updated `AGENTS.md`, and completed the implementation audit.

---

## Phase 1: Report — Low-Friction Anonymous Micro-Reporting
- **Goal:** Build the low-friction anonymous micro-reporting UI, domain/data models (`MicroReport`), Room persistence (`MicroReportEntity`), and store-and-forward local queuing.
- **Relevant Architecture Components:** `micro_reporter`, `data_models.MicroReport`, `persistence.primary`.
- **Relevant Specs & BDD:** `docs/SHEGUARD_PRD.md` (Section 6 & 7), `bdd/features/sheguard_micro_report.feature` (when created).
- **Protected Modules Touched:** `micro_reporter` [PROTECTED].
- **Acceptance Criteria:**
  - User can create an anonymous micro-report selecting hazard category, approximate location, and timestamp.
  - Micro-report persists locally in SQLite / Room database.
  - Operates completely offline without network calls.

---

## Phase 2: Detect — Spatio-Temporal Pattern Engine
- **Goal:** Implement the deterministic `SpatioTemporalPatternEngine` that aggregates local and mesh-received micro-reports within spatial proximity and temporal windows to identify emerging risk patterns.
- **Relevant Architecture Components:** `spatio_temporal_pattern_engine`, `data_models.SpatioTemporalPattern`, `reporting_pattern_state_machine`.
- **Relevant Specs & BDD:** `architecture.yaml` (Section `spatio_temporal_pattern_engine`), `docs/SHEGUARD_PRD.md` (Section 6).
- **Protected Modules Touched:** `spatio_temporal_pattern_engine` [PROTECTED].
- **Acceptance Criteria:**
  - Deterministically clusters micro-reports using configurable spatial proximity and temporal window thresholds.
  - Evaluates pattern candidate state transitions on-device without cloud dependencies.

---

## Phase 3: Trust — Multi-Signal Trust & Anti-Gaming Evaluator
- **Goal:** Implement the deterministic `TrustAndAntiGamingEvaluator` that scores pattern confidence using reporter diversity, temporal independence, spatial consistency, duplicate filtering, and rate limiting.
- **Relevant Architecture Components:** `trust_and_anti_gaming_evaluator`, `invariants.anti_gaming_trust_rule`.
- **Protected Modules Touched:** `trust_and_anti_gaming_evaluator` [PROTECTED].
- **Acceptance Criteria:**
  - Enforces the non-negotiable rule: **Raw report volume alone MUST NOT determine pattern confidence or trigger an alert.**
  - Filters out spam/duplicate reports from a single source while escalating patterns supported by diverse reporters.

---

## Phase 4: Alert — Actionable Rising-Pattern Early Warning
- **Goal:** Build the `RisingPatternAlertEngine` and Jetpack Compose UI card displaying verified rising risk patterns with location, time window, risk context, and non-emergency disclaimers.
- **Relevant Architecture Components:** `rising_pattern_alert_engine`, `data_models.RisingPatternAlert`.
- **Protected Modules Touched:** `rising_pattern_alert_engine` [PROTECTED].
- **Acceptance Criteria:**
  - Displays actionable early-warning alerts for emerging patterns that pass trust validation.
  - Clearly includes the mandatory disclaimer: `"EARLY WARNING PATTERN ALERT. NOT A GUARANTEED EMERGENCY RESPONSE."`

---

## Phase 5: Mesh — BLE / Wi-Fi Direct Peer Communication Capability
- **Goal:** Adapt existing Nearby Connections mesh relay infrastructure (`NearbyConnectionsMeshRelay`, `MeshDeduplicationCache`) for compact `MicroReport` and `PatternAlert` payloads over BLE / Wi-Fi Direct, including physical Google Play Services Nearby Connections API binding.
- **Relevant Architecture Components:** `mesh_relay`, `data_models.MeshPacket`, `technology.android.mesh`.
- **Protected Modules Touched:** `mesh_relay` [PROTECTED].
- **Acceptance Criteria:**
  - Nearby physical devices exchange compact micro-report packets peer-to-peer without Internet connectivity.
  - Enforces deduplication cache and max hop count (max 12 hops) to prevent relay loops.
  - Degrades gracefully if no peer devices are discovered, preserving local report creation and local alerting.

---

## Phase 6: Demo Hardening & End-to-End Verification
- **Goal:** Wire the complete SheGuard MVP flow into a Jetpack Compose dashboard (`SheGuardDashboardScreen`) with an offline "Simulate Nearby Reports" toggle for live hackathon demonstration.
- **Relevant Architecture Components:** All SheGuard core components.
- **Acceptance Criteria:**
  - Demonstrates the end-to-end flow completely offline on physical devices or emulator: **Report → Local Storage → Detect → Trust → Early Warning Alert (with BLE Mesh Relay)**.
  - Verifies that spam/duplicate reports do not trigger false pattern alerts, while diverse reports generate valid early warnings.
