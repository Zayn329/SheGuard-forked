# SheGuard Dependency & Data-Flow Map

This document illustrates the deterministic local data-flow pipeline and infrastructure dependency graph for SheGuard (Team Aegis, CX1001).

---

## 1. Deterministic Core MVP Pipeline (Local Operation)

The core safety guarantee functions strictly on-device without Internet, backend, cloud database, external AI/LLMs, or mesh prerequisites:

```
[ User Input / Hazard Selection ]
               │
               ▼
   [ MicroReport (Domain Model) ]
   - reportId: UUID
   - anonymousReporterToken: String
   - category: ReportCategory
   - lat / lng: Double?
   - approximateArea: String
   - timestamp: Long
               │
               ▼
  [ Local Room Database v4 ] ─────────► [ Local SQLite Storage ]
  - Table: micro_reports                (Offline Persistence Guarantee)
  - Table: spatio_temporal_patterns
  - Table: rising_pattern_alerts
               │
               ▼
[ SpatioTemporalPatternEngine ]
  - Spatial threshold: <= 500m (Haversine)
  - Temporal window: <= 2 hours
  - Enforce category separation: true
  - Min reports: 2
               │
               ▼
[ SpatioTemporalPattern (Candidate) ]
  - state: PATTERN_CANDIDATE
  - trustScore: 0.0f
               │
               ▼
[ TrustAndAntiGamingEvaluator ] (Phase C)
  - Filter duplicates: time <= 5m, dist <= 50m
  - Rate/flood protection: max 2 reports / 10m
  - Reporter diversity: >= 2 unique tokens required
  - Temporal independence: span across 10m
  - Spatial consistency: centroid distance check
               │
               ├──[ Trust Criteria Satisfied ]──► [ Pattern State: PATTERN_EMERGING ]
               │                                      (Persisted to Room Database)
               │                                                  │
               └──[ Criteria Not Met ]──────────► [ Remains: PATTERN_CANDIDATE ]
                                                                  │
                                               (UI amber banner — unverified)

[ Pattern State: PATTERN_EMERGING ]
               │
               ▼
[ RisingPatternAlertEngine ] (Phase D)
  - State gate: only PATTERN_EMERGING → alert
  - Trust score gate: score >= 0.60
  - Deterministic alertId: UUID.nameUUIDFromBytes("alert_{patternId}")
  - Privacy-preserving location: lat/lng truncated to 2dp, radius to 100m
  - TrustLevel: HIGH (>=0.80) / MEDIUM (0.60-0.79)
  - Fixed disclaimer: "EARLY WARNING PATTERN ALERT. NOT A GUARANTEED EMERGENCY RESPONSE."
  - No network, no LLM, no backend
               │
               ▼
[ RisingPatternAlert (Local) ]
  - Persisted to rising_pattern_alerts (Room Database v5)
  - Displayed as alert card in UI (badge: "Generated locally")
               │
               ▼
[ SheGuardMeshAdapter ] (Phase E)
  - Serializes to SheGuardMeshAlertPayload (JSON)
  - Coarsened location preserved, zero PII, zero tokens
  - Computes SHA-256 payloadHash
  - Envelopes into MeshPacket(SHEGUARD_ALERT, maxHops=12)
  - Store-and-forward queueing if mesh disconnected
               │
               ▼
[ NearbyConnectionsMeshRelay ] ──────────► [ Peer Device B ]
                                                    │
                                                    ▼
                                     [ MeshPayloadValidator ]
                                       - Verify version, schema, enums
                                                    │
                                                    ▼
                                     [ MeshDeduplicationCache ]
                                       - Drop if already seen (LRU/TTL)
                                       - Drop if hopCount >= maxHops (12)
                                                    │
                                                    ▼
                                     [ Persist to Room (v5) ]
                                       - isRelayed = true
                                                    │
                                                    ▼
                                     [ Alert UI Card (Peer) ]
                                       - Badge: "Received via nearby device"
```

---

## 2. Communication & Supporting Infrastructure Layers

```
                                  ┌────────────────────────────────┐
                                  │   FastAPI Backend (Optional)   │
                                  │   - Incident Metadata Sync     │
                                  │   - FIR Legal Drafting Agent   │
                                  └───────────────▲────────────────┘
                                                  │ (Async sync when online)
┌─────────────────────────────────────────────────┴────────────────────────────────────────┐
│ Android Device (Trusted Local Execution Environment)                                     │
│                                                                                          │
│  ┌─────────────────────────┐          ┌──────────────────────┐   ┌────────────────────┐  │
│  │   Nearby Connections    │          │  Security & Crypto   │   │     Sensors &      │  │
│  │       Mesh Relay        │◄────────►│  - Android Keystore  │   │     Distress       │  │
│  │  - BLE / Wi-Fi Direct   │          │  - AES-256-GCM       │   │  - Audio ML TFLite │  │
│  │  - Store & Forward      │          │  - SHA-256 Merkle    │   │  - Motion / Volume │  │
│  │  - Max 12 Hops / Dedupe │          │  (Evidence Sealing)  │   │  (Panic Triggers)  │  │
│  └───────────▲─────────────┘          └──────────────────────┘   └────────────────────┘  │
│              │ (Relays SheGuard Alerts & MicroReports peer-to-peer)                      │
│  ┌───────────┴────────────────────────────────────────────────────────────────────────┐  │
│  │ Core Offline Pipeline:                                                             │  │
│  │ Report (Room) → Detect (PatternEngine) → Trust (Evaluator) → ALERT → MESH RELAY    │  │
│  └────────────────────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Component Coupling & Boundaries

1. **Local Pipeline Isolation**:
   - `MicroReportRepository`, `PatternRepository`, and `AlertRepository` interact solely with SQLite via Room (Database v5).
   - `SpatioTemporalPatternEngine`, `TrustAndAntiGamingEvaluator`, and `RisingPatternAlertEngine` are pure Kotlin domain components with zero platform, framework, or network dependencies.
2. **Mesh Communication Decoupling**:
   - Nearby Connections operates alongside the local pipeline. If mesh is unavailable or no peers are discovered, local reporting, detection, trust evaluation, and local UI are 100% operational.
3. **Backend Decoupling**:
   - Core reporting, pattern detection, trust scoring, and alert generation never make network requests to the FastAPI backend.
4. **AI / LLM Decoupling**:
   - Zero LLM or AI components participate in micro-report validation, pattern clustering, trust evaluation, or alert decisions.
5. **Alert Engine Isolation**:
   - `RisingPatternAlertEngine` does NOT re-run trust evaluation. It reads `pattern.trustScore` directly.
   - `RisingPatternAlertEngine` does NOT set `PatternState.ALERT_ACTIVE`. It produces a separate `RisingPatternAlert` object.
6. **Mesh Transport Boundary**:
   - Mesh receives already-generated alerts; it NEVER generates alerts directly or alters local pattern trust scores.
   - Received mesh alerts are persisted as `isRelayed = true` community alerts and rendered with distinct source provenance.
