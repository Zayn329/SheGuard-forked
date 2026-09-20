# SheGuard — Product Requirements Document (PRD)

## 1. Product Identity
- **Product / Project Name:** SheGuard
- **Team Name:** Aegis
- **Problem Statement Identifier:** CX1001 (`teamAegis_CX1001_codex2026`)
- **Domain:** Women Safety & Social Impact
- **Theme:** Preventive & Community Safety
- **Legacy Repository Context:** "Sahara" represents legacy repository implementation context. SheGuard is the canonical target product identity for this migration.

---

## 2. Problem
Personal safety applications frequently suffer from critical operational limitations in real emergency or distress situations:
- **Unable to Actively Operate Phone:** During sudden danger or intimidation, a person may be unable to unlock their phone, navigate a complex UI, or trigger a panic button.
- **Delayed Emergency Response:** Traditional emergency alerts are reactive, triggering only after an incident occurs, leading to delayed assistance.
- **Connectivity & Network Blackouts:** Mobile data and cellular service are unreliable in remote, crowded, or signal-jammed areas.
- **Fragmented Evidence & Misinformation:** Evidence captured during safety events is often lost, deleted, or hard to verify, while false/unverified reports can cause panic or system gaming.
- **Isolated Interventions:** Existing solutions address isolated capabilities (e.g., individual location sharing) rather than combining proactive pattern detection, offline resilience, community-level early warning, and integrity-protected evidence preservation.

---

## 3. Product Thesis
**Turn individual, small safety signals into trusted spatio-temporal patterns and actionable community-level early warnings.**

SheGuard moves beyond reactive individual SOS alerts by converting low-friction safety signals and micro-reports into verified, community-wide preventive insights. By combining offline resilience with a multi-signal Trust & Anti-Gaming Layer, SheGuard identifies emerging risk patterns early—even in network-denied environments—while protecting user privacy and evidence integrity.

---

## 4. Target Users
1. **Primary Users (Women & Individuals Navigating Public Spaces):**
   - Individuals seeking proactive safety awareness during daily commutes, late-night travel, or movement through unfamiliar/high-risk areas.
   - Users requiring low-friction anonymous micro-reporting and offline safety support.
2. **Community Safety Circles & Responders:**
   - Trusted personal contacts, nearby community members, or local safety organizers receiving verified rising-pattern alerts and early warnings.
3. **Evidence Consumers (Post-Incident):**
   - Legal advisors, authorities, or support organizations consuming integrity-protected, verifiable incident data packages after an event.

---

## 5. Offline-First Architectural Hierarchy
Offline-first in SheGuard explicitly means that **Internet connectivity is not required** to create, propagate, process, trust, or act on safety signals. SheGuard defines a strict three-tier offline-first operational hierarchy:

1. **LOCAL OPERATION — Fundamental Guarantee:**
   - A user can create an anonymous micro-report without Internet or network access.
   - The micro-report is persisted locally in device storage.
   - Local spatio-temporal pattern detection operates on locally available micro-reports.
   - Local trust / anti-gaming evaluation runs deterministically on-device.
   - A local rising-pattern early-warning alert can be generated directly without cloud/backend access.
   - *Failure Boundary:* Failure of external networks, backends, or peer devices MUST NOT break local operation.

2. **MESH COMMUNICATION — Core MVP Capability:**
   - BLE / Wi-Fi Direct peer-to-peer relay allows nearby participating devices to exchange compact micro-reports and pattern metadata without Internet.
   - Mesh enables disconnected users and devices in proximity to contribute to a shared local safety picture.
   - Reports propagate hop-by-hop using store-and-forward semantics.
   - Mesh MUST NOT require Internet or backend connectivity.
   - *Failure Boundary:* Mesh failure or absence of nearby peer devices MUST NOT degrade local report creation, local persistence, or local pattern detection.

3. **BACKEND SYNCHRONIZATION — Supporting Capability:**
   - When Internet or mobile data connectivity becomes available, queued micro-report metadata and aggregated pattern information synchronize asynchronously with the backend service.
   - Backend connectivity remains optional for the core MVP experience.

---

## 6. Core User Flow
```
[ Anonymous Micro-Report / Signal ]
                │
                ▼
      [ Local Persistence ]
                │
                ▼
 [ Local Detect / Trust / Alert ] ◄───► [ Mesh Propagation Between Nearby Devices (BLE / Wi-Fi Direct) ]
                │
                ▼
 [ Optional Backend Synchronization when Connectivity Returns ]
```
*Note:* A micro-report does NOT need to pass through mesh before local detection/trust/alerting occurs. Mesh operates alongside local persistence as an MVP peer communication capability.

---

## 7. Hackathon MVP
The minimum demonstrable scope for the hackathon focuses on the core **Report → Detect → Trust → Alert** loop backed by local operation and BLE / Wi-Fi Direct mesh relay:

### REPORT
- **Low-Friction Micro-Reporting:** Quick, anonymous safety reporting with minimal required interactions.
- **Categorization:** Incident and hazard selection (e.g., poor lighting, harassment, feeling followed, unsafe gathering).
- **Spatio-Temporal Context:** Automatic local capture of approximate location and timestamp.
- **Offline Queueing:** Local persistence and store-and-forward mechanism when network connectivity is unavailable.

### DETECT
- **Spatio-Temporal Pattern Engine:** Aggregates micro-reports based on spatial proximity and time windows locally or across nearby mesh peers.
- **Pattern Identification:** Detects recurring and emerging safety anomalies (e.g., multiple micro-reports within a specific radius and time frame).

### TRUST
- **Multi-Signal Trust Evaluation:** Evaluates pattern confidence based on reporter diversity, temporal independence, and spatial consistency.
- **Anti-Gaming & Spam Protection:** Implements duplicate prevention, rate limiting, and anti-flooding checks.
- **Anti-Gaming Rule:** Raw report volume alone MUST NOT determine pattern confidence; trust metrics and reporter independence govern signal escalation.

### ALERT
- **Rising-Pattern Early Warning:** Surfaces detected emerging risk patterns in real-time or via local relay.
- **Contextual Action:** Provides actionable information including location, time window, and risk context.
- **Clear Boundaries:** Clearly distinguishes early warning/community pattern alerts from guaranteed emergency services response.

---

## 8. Supporting Capabilities
The following capabilities support the primary product experience and may be integrated using reusable repository infrastructure:
- **Privacy-Preserving Data Handling:** Anonymous reporting without exposing user identity or continuous exact location history.
- **Secure Local Evidence Capture:** Optional local capture and cryptographic sealing (AES-256-GCM, SHA-256, Merkle tree) of incident evidence on-device.
- **Backend Synchronization:** Optional synchronization of aggregated, verified metadata when internet connectivity returns.

---

## 9. Explicit Non-Goals / Claims Boundaries
SheGuard explicitly DOES NOT claim to:
- Guarantee immediate emergency service or police response.
- Guarantee crime prevention or absolute physical protection.
- Guarantee continuous mobile network or internet availability.
- Guarantee court admissibility of generated reports or evidence.
- Perfectly detect danger without user or community input.
- Replace official emergency services (e.g., 112/911).

---

## 10. Demo Acceptance Criteria
The Hackathon MVP demonstration is complete when the system successfully demonstrates:
1. **Anonymous Report Creation:** A user can submit a low-friction micro-report (category, location, timestamp) completely offline.
2. **Local Storage & Processing:** The report is saved locally in room storage and queued without network failure.
3. **BLE / Wi-Fi Direct Mesh Relay:** Micro-reports are relayed peer-to-peer between disconnected nearby devices without Internet connectivity.
4. **Pattern Detection:** Submission of multiple spatio-temporal micro-reports (local or received via mesh) triggers the Spatio-Temporal Pattern Engine to identify an emerging pattern.
5. **Trust & Anti-Gaming Verification:** Spam/duplicate reports from a single source are filtered out, while diverse reports increase pattern trust confidence.
6. **Actionable Early Warning Alert:** A verified rising pattern generates an early-warning alert containing location, time, and risk context.

---

## 11. Product vs Existing Repository
*Note on Legacy Context:*
The existing repository contains implementation code from the legacy "Sahara" project. Existing technical infrastructure (e.g., cryptographic sealing, Room storage, Nearby Connections mesh, FastAPI structure) may be reused where applicable. However, legacy Sahara product requirements (such as Notify Circle, FIR legal drafting, or specific distress detection rules) are **not automatically SheGuard requirements**. Product relevance is established strictly by this PRD (`docs/SHEGUARD_PRD.md`).

---

## 12. MVP Priority

### MUST DEMO
- Anonymous micro-reporting (category, location, timestamp)
- Local offline report storage & queuing
- BLE / Wi-Fi Direct mesh relay for disconnected nearby devices
- Spatio-Temporal Pattern Detection (spatial/temporal grouping)
- Trust & Anti-Gaming Layer (reporter diversity, duplicate filtering)
- Actionable Rising-Pattern Early Warning Alert

### SUPPORTING
- Optional cryptographic evidence capture & Merkle sealing
- Backend metadata synchronization upon network recovery

### FUTURE / EXTENDED
- Full community-wide risk heatmaps & predictive routing
- Fine-grained legal aid & institutional reporting workflows
- Automated external authority integration
