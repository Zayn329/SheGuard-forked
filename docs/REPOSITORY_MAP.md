# SheGuard Repository Map

This document provides a comprehensive structural map of the SheGuard project repository, its modular architecture, core runtime components, and governance harness.

---

## 1. High-Level Architecture Overview

SheGuard is structured as an offline-first modular Android application (Kotlin, Jetpack Compose, Room, Coroutines) accompanied by a supporting FastAPI Python backend, BDD behavioral specifications, and architectural documentation.

```
SheGuard/
├── AGENTS.md                  # Project governance & agent rules of engagement
├── architecture.yaml          # Canonical technical architecture authority (v2.1)
├── docs/                      # Canonical PRD, specifications, API contracts, ADRs
│   ├── SHEGUARD_PRD.md        # Canonical product requirements document
│   ├── specs/                 # Focused technical specifications (trust.yaml, ai.yaml)
│   ├── adr/                   # Architecture Decision Records
│   ├── REPOSITORY_MAP.md      # Repository layout and module responsibilities
│   └── DEPENDENCY_MAP.md      # Deterministic data-flow and infrastructure map
├── bdd/                       # Gherkin behavioral specifications
│   └── features/              # Feature files (.feature)
├── android/                   # Native Kotlin Android project
│   ├── app/                   # Compose UI & Application entry points
│   ├── core/                  # Shared domain models, data/Room persistence, security
│   │   ├── domain/            # Pure Kotlin business rules, pattern engine, trust evaluator
│   │   ├── data/              # Room database, entities, DAOs, repositories
│   │   ├── security/          # Android Keystore, AES-256-GCM, Merkle tree
│   │   └── testing/           # Synthetic test data & test fixtures
│   ├── services/              # Background execution services
│   │   ├── detection/         # On-device sensor/audio distress detection (supporting)
│   │   ├── evidence/          # Incident evidence capture & sealing (supporting)
│   │   └── mesh/              # Google Nearby Connections BLE/Wi-Fi Direct mesh relay
│   └── features/              # Feature presentation modules
│       ├── incident/          # Incident management & state machine (supporting)
│       ├── panic/             # Manual panic button & trigger controllers (supporting)
│       └── notify-circle/     # Trusted contacts management (supporting)
├── backend/                   # Supporting FastAPI Python service
│   ├── app/                   # FastAPI routes, schemas, models, LLM provider abstraction
│   └── tests/                 # Pytest backend test suite
└── apk/                       # Verified debug APK artifact directory
    └── sheguard-debug.apk     # Standalone installable Android APK
```

---

## 2. Module Responsibilities & Key Files

### A. Core SheGuard MVP Pipeline (Android)

| Pipeline Stage | Module & Package | Key Classes / Files | Primary Responsibility |
|---|---|---|---|
| **REPORT** | `:android:core:domain`<br>`:android:core:data`<br>`:android:app` | `MicroReport`, `ReportCategory`<br>`MicroReportEntity`, `MicroReportDao`<br>`MicroReportRepositoryImpl`<br>`SheGuardReportingScreen.kt` | Anonymous micro-reporting UI, local Room persistence, offline queueing. |
| **DETECT** | `:android:core:domain`<br>`:android:core:data` | `SpatioTemporalPatternEngine.kt`<br>`SpatioTemporalPattern`<br>`SpatioTemporalPatternEntity`<br>`PatternRepositoryImpl.kt` | Deterministic spatial (Haversine $\le 500$m) and temporal ($\le 2$h) clustering into candidate patterns (`PATTERN_CANDIDATE`). |
| **TRUST** | `:android:core:domain`<br>`:android:core:data` | `TrustAndAntiGamingEvaluator.kt`<br>`TrustEvaluationConfig`<br>`TrustEvaluationResult`<br>`SheGuardReportingScreen.kt` | Deterministic multi-signal trust scoring (reporter diversity, duplicates, temporal/spatial coherence, anti-flooding) $\rightarrow$ `PATTERN_EMERGING`. |
| **ALERT** | `:android:core:domain`<br>`:android:core:data`<br>`:android:app` | `RisingPatternAlertEngine.kt`<br>`RisingPatternAlert`, `TrustLevel`, `AlertEngineConfig`<br>`RisingPatternAlertEntity`, `RisingPatternAlertDao`<br>`AlertRepositoryImpl`<br>`SheGuardReportingScreen.kt` | Deterministic rising-pattern early-warning alert generation from `PATTERN_EMERGING` patterns. Deterministic `alertId`, privacy-preserving coarse location, fixed disclaimer, idempotent Room persistence. No network, no LLM. |

### B. MVP Mesh Relay Capability

| Module | Key Files | Responsibility |
|---|---|---|
| `:android:services:mesh` | `NearbyConnectionsMeshRelay.kt`<br>`MeshDeduplicationCache.kt`<br>`MeshPacket`<br>`SheGuardMeshAdapter.kt`<br>`SheGuardMeshAlertPayload.kt`<br>`MeshPayloadValidator.kt` | Peer-to-peer packet propagation over BLE / Wi-Fi Direct using Google Nearby Connections abstractions. Handles payload serialization, deterministic validation, deduplication, hop limits (max 12), store-and-forward queuing, and safe community alert relay. |

### C. Supporting & Security Modules

| Module | Key Files | Responsibility |
|---|---|---|
| `:android:core:security` | `KeyStorageManagerImpl.kt`<br>`AesGcmFileStorage.kt`<br>`MerkleTree.kt` | Hardware-backed Android Keystore key management, AES-256-GCM file encryption, SHA-256 Merkle tree evidence integrity. |
| `:android:services:evidence` | `EvidenceCollectionManager.kt`<br>`EncryptedChunkManager.kt` | Incident evidence capture, chunking, cryptographic hashing, and local sealing. |
| `:android:services:detection` | `KeywordDetector.kt`<br>`ScreamDetector.kt`<br>`MotionDetector.kt` | Optional on-device ML distress signal fusion using TensorFlow Lite. |
| `:android:features:panic` | `PanicController.kt` | Manual SOS trigger and hardware volume-button sequence monitor. |
| `:android:features:incident` | `IncidentStateMachine.kt` | Controlled 11-state incident lifecycle management. |
| `:android:features:notify-circle` | `NotifyCircleManager.kt`<br>`EscalationFallbackManager.kt` | Trusted emergency contacts and fallback SMS delivery. |

### D. Backend Service

| Directory | Key Files | Responsibility |
|---|---|---|
| `backend/app/` | `main.py`<br>`routers/incident.py`<br>`agents/legal_agent.py`<br>`providers/llm_provider.py` | Optional asynchronous synchronization when online; AI legal draft FIR generator with mandatory legal review disclaimers. |

---

## 3. Specifications & Governance

- `AGENTS.md`: Codebase rules, decision boundaries, protected module conventions, and anti-gaming non-negotiables.
- `architecture.yaml`: Authoritative technical architecture contract defining invariants, system boundaries, and state machines.
- `docs/SHEGUARD_PRD.md`: Authoritative product contract defining the offline-first three-tier hierarchy and MVP boundaries.
- `docs/specs/trust.yaml`: Focused technical specification for Phase C Trust & Anti-Gaming evaluation.
- `docs/specs/alert.yaml`: Focused technical specification for Phase D Rising Pattern Alert Engine.
- `docs/specs/mesh.yaml`: Focused technical specification for Phase E Device-to-Device Mesh Relay.
- `docs/specs/ai.yaml`: Focused technical specification for backend AI assistance and legal drafting boundaries.
- `bdd/features/`: Behavioral specifications including `sheguard_trust_and_anti_gaming.feature`, `sheguard_alert.feature`, and `sheguard_mesh.feature`.
