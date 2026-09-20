AGENTS.md

Project Governance

- Product / Project Name: SheGuard
- Team Name: Aegis
- Problem Statement Identifier: CX1001
- Domain / Theme: Women Safety & Social Impact — Preventive & Community Safety
- Canonical Product Contract: "docs/SHEGUARD_PRD.md"

SheGuard is an offline-first intelligent safety system that converts low-friction, anonymous micro-reports into verified spatio-temporal risk patterns and actionable community-level early warnings using local operation and BLE / Wi-Fi Direct mesh relay without relying on cloud connectivity or LLMs.

This file defines how coding agents must work in this repository. It does not replace the technical architecture.

"architecture.yaml" is the highest technical authority in this repository and must remain unchanged unless the user explicitly approves an architectural modification. "docs/SHEGUARD_PRD.md" is the authoritative product contract for SheGuard. "Sahara" represents legacy repository context; existing Sahara infrastructure may be reused where technically appropriate, but legacy Sahara product requirements are not automatically SheGuard requirements.

The repository should remain agent-legible. Important decisions, contracts, specifications, and limitations must exist in versioned repository files rather than only in conversation context.

Instruction Precedence

When implementing or modifying code, follow this order:

1. Direct system, developer, and user instructions.
2. The repository's "architecture.yaml".
3. The repository's "docs/SHEGUARD_PRD.md".
4. More specific referenced specifications under "docs/specs/".
5. Applicable nested "AGENTS.md" files.
6. Existing stable implementation conventions.
7. This file.

If "architecture.yaml", "docs/SHEGUARD_PRD.md", a specification, BDD scenario, or existing implementation conflicts with another source, do not silently choose one. Stop and clearly report the conflict.

Do not modify "architecture.yaml" silently. You may propose an architectural change, explain why it is necessary, and wait for approval.

More specific "AGENTS.md" files may be introduced later for subprojects such as "android/" or "backend/". Their rules apply only within their directory scope unless explicitly stated otherwise.

Operating Principle

Act as a pragmatic hackathon builder with strict safety and security boundaries.

The objective is to build a genuinely functional demonstration rather than a visually convincing prototype. Make reasonable implementation decisions autonomously when details are missing, but do not silently cross architectural, security, cryptographic, public-contract, or protected-module boundaries.

Prefer the simplest implementation that satisfies the architecture and produces real behavior.

Do not introduce unnecessary abstraction, infrastructure, microservices, optimization, or speculative features.

The project is allowed to be less optimized than a production system. It is not allowed to be careless with evidence integrity, cryptography, key handling, privacy, or safety-critical behavior.

Mandatory Startup Procedure

Before implementing any task:

1. Read "architecture.yaml".
2. Read "docs/SHEGUARD_PRD.md".
3. Read every specification directly relevant to the requested task under "docs/specs/".
4. Check for nested "AGENTS.md" files affecting the files you may modify.
5. Inspect the relevant existing implementation and tests.
6. Identify whether the task touches a protected module.
7. Create or update the short task plan or progress record.
8. Implement only after the above context is understood.

Use the following execution cycle:

"plan -> implement -> test -> review -> report"

Continue autonomously until the requested task is complete unless one of the explicit stop conditions in this file applies.

Do not repeatedly interrupt the user with small questions. If clarification is required, consolidate the unresolved questions and ask once.

Decision Authority

You may implement a clearly defined feature end-to-end without asking for approval.

When implementation details are missing, choose a reasonable implementation and document the decision in the appropriate place. Use code comments only when they explain non-obvious constraints or behavior. Record meaningful decisions as ADRs.

You must stop and ask before proceeding if the required decision would change:

- an architectural boundary;
- a security boundary;
- a behavior contract;
- a cryptographic design;
- a public interface;
- an event contract;
- a protected module's defined security or behavior semantics.

Protected modules are not "ask before touching" modules. Clearly specified behavior inside a protected module may be implemented autonomously.

Do not change public APIs, shared contracts, event names, event payload semantics, or externally consumed interfaces without explicit approval.

Do not remove or replace an existing dependency without approval.

New dependencies may be introduced only when justified and when they:

- are actively maintained;
- are compatible with the target Android, Kotlin, Python, or backend runtime;
- have an acceptable license;
- have no known high or critical vulnerability relevant to the intended use;
- do not unnecessarily duplicate an existing dependency.

Record the reason for every meaningful new dependency.

SheGuard Core MVP & Pipeline Rules

The primary hackathon MVP pipeline is:

"Report -> Detect -> Trust -> Alert"

1. REPORT: Low-friction, anonymous safety micro-report (hazard category, approximate location, timestamp, local Room persistence & queueing, offline capable).
2. DETECT: Deterministic spatio-temporal grouping (spatial proximity, temporal windows, recurring/emerging safety patterns).
3. TRUST: Multi-signal evaluation (reporter diversity, temporal independence, spatial consistency, duplicate filtering, rate limiting / anti-flooding).
4. ALERT: Actionable rising-pattern early warning (location/time/risk context; explicitly NOT a guaranteed emergency response).

Non-Negotiable Anti-Gaming Rule

Raw report volume alone MUST NOT determine pattern confidence, cause pattern escalation, or trigger an early-warning alert. Trust evaluation requires reporter diversity, temporal independence, spatial consistency, and duplicate/anti-flooding verification as defined by "architecture.yaml".

Offline-First and Mesh Communication Hierarchy

"Offline-first" means SheGuard is capable of creating, persisting, processing, evaluating trust, and generating local early-warning alerts without Internet or backend connectivity.

The operational hierarchy is:

1. LOCAL OPERATION — Fundamental Guarantee:
   - A user can create an anonymous micro-report offline.
   - Micro-reports are persisted locally in Room storage.
   - Spatio-temporal pattern detection and trust evaluation run deterministically on-device.
   - Local rising-pattern early warnings generate directly without backend or peer access.
   - Failure of external networks, backends, or peer devices MUST NOT break local operation.

2. MESH COMMUNICATION — Core MVP Capability:
   - BLE / Wi-Fi Direct peer-to-peer relay allows nearby participating devices to exchange compact micro-reports and pattern signals without Internet.
   - Mesh enables nearby disconnected devices to contribute to a shared local safety picture using store-and-forward semantics.
   - Mesh operates alongside local persistence. Mesh failure or absence of nearby peer devices MUST NOT break local report creation, local persistence, local pattern detection, or local early-warning alerting.

3. BACKEND SYNCHRONIZATION — Optional Supporting Capability:
   - Queued metadata and pattern signals synchronize asynchronously with the backend when Internet connectivity returns.

Infrastructure Reuse Rule

Before creating new infrastructure, inspect existing reusable implementation in the repository.

Prefer adapting and reusing existing:
- Room database and local storage entities;
- Google Nearby Connections BLE / Wi-Fi Direct mesh relay modules;
- FastAPI backend framework and provider abstractions;
- Cryptographic primitives (AES-256-GCM, SHA-256, Android Keystore);
- Existing event and policy enforcement structures.

Do not duplicate infrastructure merely because its original implementation was created for Sahara. If an existing module's contract or implementation is incompatible with SheGuard, document the incompatibility and make the smallest architecturally compliant change.

Protected Modules

The following areas require additional care:

SheGuard Core MVP Protected Modules:
- micro_reporter;
- spatio_temporal_pattern_engine;
- trust_and_anti_gaming_evaluator;
- rising_pattern_alert_engine;
- mesh_relay (Google Nearby Connections / BLE / Wi-Fi Direct).

Supporting & Security Protected Modules:
- evidence encryption;
- Android Keystore integration;
- evidence signing and hashing;
- Merkle tree logic;
- SMS escalation;
- distress detection & panic controllers (legacy supporting).

Before modifying a protected module, read its complete relevant specification, PRD section, and existing implementation.

For every protected-module change:

- add or update tests;
- run the relevant tests;
- run a focused review after implementation;
- explain meaningful behavior changes;
- document architectural or security limitations;
- run the mandatory security/privacy review before completion.

Warnings in protected modules are treated as failures.

Never weaken a protected module merely to make a demo easier.

Non-Negotiable Security Rules

The following are prohibited:

- hardcoded secrets or cryptographic keys;
- fake cryptography presented as real cryptography;
- plaintext evidence or report storage;
- plaintext persistence of incident encryption keys;
- silent replacement of real security mechanisms with mocks;
- reuse of AES-GCM nonces;
- logging private keys, authentication tokens, encryption keys, raw evidence, exact location history, or phone numbers;
- silently uploading raw evidence or unredacted reports;
- silently disabling cryptographic verification;
- silently disabling tests;
- temporarily disabling failing tests to claim completion.

If cryptographic or security functionality required by the current task cannot be implemented correctly, stop. Do not downgrade security to continue.

If a hardware-backed Keystore capability is unavailable on a device, implement only the architecture-compliant degraded behavior permitted by the platform and document the limitation. Do not pretend hardware-backed protection exists when it does not.

Test evidence must always be synthetic or generated. Never add real personal evidence, real victim data, private phone numbers, or real sensitive recordings to the repository.

Secrets may exist only in local secret configuration such as ".env" or "local.properties", which must be gitignored. Secrets must never be committed.

Fallback Rules

Fallback behavior must always be explicit.

If a real implementation is technically unavailable, a safe fallback may be used only when it does not weaken required security or violate the architecture.

If an external service is unavailable, use an architecture-compliant local fallback or degraded behavior where possible.

If hardware functionality is unavailable, expose a clearly labelled degraded state.

If required safety behavior cannot be implemented as specified, stop and flag the limitation rather than silently substituting a different safety behavior.

If tests pass only because a fallback implementation was used, explicitly report:

"PASSED USING FALLBACK"

Mocks are permitted only for clearly separated development, emulator, or demo-only integrations.

A mock must never silently execute in a production or release path.

Demo-only transport mocks, such as a mock SMS provider, must be impossible to enable in a release build.

Required Environment Variables

Only environment variables required for the current task block implementation.

For example, missing LLM credentials must not block offline Android reporting or pattern detection work.

However, if the current task requires integration with a real LLM provider, SMS gateway, backend, blockchain RPC, or other configured external service and its required environment variable is missing, stop and ask for the required configuration.

Never silently substitute an empty string, default credential, fake secret, or undeclared provider.

Never invent environment variable values.

External Documentation and Platform APIs

Before implementing or materially changing platform-sensitive or security-sensitive functionality, consult the relevant official documentation when network access is available.

This requirement applies particularly to:

- Android foreground services;
- Android background execution restrictions;
- Android location permissions;
- Android Keystore;
- Android cryptographic APIs;
- Google Nearby Connections;
- BLE and Wi-Fi Direct behavior;
- "SmsManager";
- TensorFlow Lite;
- authentication providers;
- FastAPI security mechanisms;
- cryptographic libraries and APIs.

Do not rely on memory for platform behavior when the implementation depends on current restrictions or API semantics.

If official documentation conflicts with the repository architecture, do not silently alter the architecture. Report the conflict.

Android Implementation Rules

The primary application is native Android using Kotlin.

Respect the API range and compatibility decisions defined by the project. Prefer a balance between useful modern Android APIs and broad device compatibility.

The minimum supported Android API should remain aligned with the repository architecture and existing project configuration. Do not change it without approval.

Prefer real physical-device behavior for:

- Nearby Connections;
- Bluetooth-related discovery;
- microphone capture;
- accelerometer behavior;
- SMS delivery.

Emulator behavior may be used for development and testing but must be clearly identified when it differs from physical-device behavior.

Android platform restrictions must be respected. Do not claim that background monitoring, foreground services, sensor access, or notification behavior can be guaranteed beyond what Android permits.

When the platform prevents a requested guarantee, implement the closest compliant behavior and document the limitation.

Deterministic Pipeline Rules

The core SheGuard MVP pipeline must remain deterministic and on-device.

No LLM, generative AI, or agent component may participate in:

- micro-report validation;
- spatio-temporal pattern detection;
- trust evaluation;
- anti-gaming evaluation;
- rising-pattern early-warning alert generation;
- mesh forwarding decisions;
- evidence capture or sealing;
- emergency escalation decisions.

The backend must not become a dependency of the core local safety/reporting path.

Core Demonstration Requirements

The following capabilities are core to the SheGuard hackathon demonstration and must be genuinely implemented rather than silently mocked:

- Anonymous micro-reporting (hazard selection, location/time context, Room persistence);
- Local offline report storage and queuing;
- BLE / Wi-Fi Direct mesh relay using Google Nearby Connections between physical devices;
- Spatio-temporal pattern detection;
- Deterministic trust and anti-gaming evaluation;
- Actionable rising-pattern early-warning alerts.

For Nearby mesh relay, a valid demonstration may involve two physical devices in the same room.

The following may use controlled and explicitly reported fallbacks or supporting implementations during the hackathon:

- SMS delivery fallback;
- AI legal complaint drafting;
- Evidence audio capture & encryption (optional supporting);
- Blockchain anchoring.

"Fallback" does not mean invisible substitution. Every fallback must be observable in implementation, configuration, logs, UI where appropriate, and final reporting.

Evidence and Cryptography

Evidence integrity is more important than convenience.

Follow "architecture.yaml" exactly for:

- local-first evidence handling;
- per-incident encryption;
- Android Keystore key protection;
- SHA-256 hashing;
- Merkle integrity;
- signed evidence manifests;
- export verification.

Raw evidence must remain local by default.

No external service may receive raw evidence unless an explicitly authorized architecture-compliant feature permits it.

Every cryptographic operation must use maintained platform or library primitives. Do not implement custom cryptography.

Canonicalization and serialization rules must remain deterministic where hashes or signatures depend on them.

Any change to evidence format, hash input, Merkle leaf construction, manifest semantics, signature semantics, or key lifecycle is a protected-module decision and must be treated accordingly.

UI Rules

No dummy or placeholder frontend code is permitted for completed functionality.

Every visible safety control must be connected to real behavior.

Do not create buttons that only exist visually.

Where relevant, the UI must provide appropriate states for:

- loading;
- success;
- error;
- empty data;
- permission denied;
- unavailable capability;
- degraded or fallback operation.

Safety-related degraded behavior must be visible to the user when it affects what the application can actually do.

Implement basic accessibility, including meaningful labels or content descriptions and clear interaction states.

Do not freely redesign unrelated UX while implementing a feature.

Small supporting UI changes required for the requested feature are allowed.

Scope Control

Stay focused on the requested task.

You may modify files outside the immediate task when necessary to keep the implementation consistent, compileable, testable, or architecturally correct.

Report such changes explicitly.

Do not:

- expand into unrelated features;
- redesign unrelated screens;
- introduce speculative infrastructure;
- refactor unrelated modules;
- fix unrelated bugs unless necessary for the requested feature.

Refactoring should be minimal and tightly scoped.

Prefer improving the module currently being touched rather than broad codebase cleanup.

If unrelated tests already fail, report them but continue when they are genuinely unrelated to the current task.

Backend and Agent Rules

The FastAPI backend is optional for core safety and reporting operation.

Core offline functionality must remain usable without backend authentication.

Backend-dependent features require authentication according to the repository's authentication implementation.

Development-only authentication shortcuts may be used only when explicitly marked as development-only.

Release or demo backend paths must not silently bypass authentication.

Runtime LLM agents may draft, summarize, and suggest.

They may not autonomously:

- file legal documents;
- submit complaints;
- contact police or authorities;
- contact employers;
- export raw evidence;
- upload raw evidence;
- perform irreversible actions without the required policy and user authorization.

Every legal output must retain the required draft and legal-review disclaimer defined by the architecture.

Policy and External Actions

Before any agent or backend workflow performs an action that leaves the device or affects another person, evaluate the applicable policy and user authorization.

This includes:

- sending notifications;
- sharing location;
- backend synchronization;
- legal drafting requests;
- evidence export;
- blockchain anchoring;
- IPFS upload.

Deny by default when required authorization is absent.

Do not infer user consent from convenience.

Testing

Prioritize tests for critical behavior rather than arbitrary coverage percentages.

Every completed feature should include documentation updates and appropriate tests.

Protected modules require tests.

Use unit tests for deterministic logic and integration tests where component boundaries interact.

BDD scenarios are required for the most important user flows, not for every internal function.

Before considering work complete, run all relevant available checks, including applicable:

- unit tests;
- integration tests;
- Android instrumentation tests when appropriate;
- backend tests;
- linting;
- formatting;
- type checks;
- protected-module security checks.

Do not claim a check passed if it was not run.

Clearly report checks that could not be run and why.

High or critical dependency vulnerabilities relevant to the module block completion until resolved or explicitly approved.

Warnings in protected modules are failures.

Warnings elsewhere must be reported.

Review Passes

Use one general coding workflow by default.

Specialized review passes may be invoked when useful:

- Security reviewer;
- Test/QA reviewer.

Parallel work is allowed only for clearly independent modules with no conflicting file ownership or contract changes.

A security review may block completion.

A final review pass is mandatory for any task touching:

- protected modules;
- security;
- privacy;
- cryptography;
- evidence integrity;
- authentication.

The final review must check that the implementation matches "architecture.yaml", relevant specifications, and the actual behavior demonstrated by tests.

Documentation and ADRs

Meaningful architectural or implementation decisions must be recorded as individual ADRs.

Store ADRs under:

"docs/adr/"

Use sequential filenames such as:

"0001-use-nearby-connections-for-mesh-relay.md"

An ADR should briefly state:

- context;
- decision;
- alternatives considered when relevant;
- consequences;
- status.

Do not create ADRs for trivial implementation details.

Documentation must be updated when behavior, setup, dependencies, environment requirements, limitations, or operational instructions change.

Progress Tracking

Maintain a simple task or progress record for substantial work.

The progress record should help an agent or developer understand:

- the current task;
- current phase;
- completed work;
- remaining work;
- blockers;
- validation status.

Do not create unnecessary project-management bureaucracy.

Remove or close completed temporary task records when the repository's established workflow requires it.

Git Workflow

Create Git commits automatically after coherent milestones when the environment permits it.

Use conventional commit messages.

Examples:

"feat(android): add anonymous micro-report storage"

"test(core): cover spatio-temporal pattern trust rules"

"fix(mesh): prevent duplicate relay loops in report forwarding"

"docs(adr): record pattern detection trust model decision"

Do not commit:

- secrets;
- ".env" files containing credentials;
- "local.properties";
- build artifacts;
- generated APKs;
- local databases;
- temporary recordings;
- generated evidence;
- unnecessary model binaries.

Do not amend unrelated commits.

Keep commits coherent and reviewable.

If Git operations cannot be performed because of the environment, report that clearly.

Definition of Done

A task is complete only when all applicable conditions are satisfied:

- the architecture and relevant specifications were followed;
- the requested functionality is genuinely implemented;
- completed UI is real and interactive rather than placeholder code;
- critical logic has appropriate tests;
- applicable tests pass;
- fallback-based passing results are explicitly identified as "PASSED USING FALLBACK";
- linting, formatting, and type checks pass where applicable;
- no secrets were introduced;
- security and privacy constraints were respected;
- documentation was updated;
- required ADRs were added;
- known limitations are documented;
- protected or security-sensitive changes received the required final review.

Do not mark incomplete functionality as complete merely because the application compiles.

Completion Report

At the end of each completed task, report concisely:

1. What was implemented.
2. Files changed.
3. Tests and checks run.
4. Tests or checks not run and why.
5. Any fallbacks used.
6. Any "PASSED USING FALLBACK" results.
7. Known limitations.
8. Any architectural deviations or proposed deviations.
9. Relevant commits created.

Do not hide failures, skipped validation, degraded behavior, unavailable hardware, unavailable credentials, or mocked integrations.

Accuracy about the implementation state is more important than presenting an optimistic completion report.

Stop Conditions

Stop and ask the user when:

- "architecture.yaml" conflicts with another specification;
- a required architectural change is needed;
- a security boundary must change;
- a cryptographic design must change;
- a public interface or event contract must change;
- required safety behavior cannot be implemented as specified;
- a required environment variable for the current task is missing;
- a protected-module decision crosses its defined behavior or security contract;
- an existing dependency must be removed or replaced.

Otherwise, make reasonable decisions, document important ones, and continue autonomously until the requested task is complete.

Final Principle

Build the smallest real implementation that satisfies the requested feature and the architecture.

Optimize for a working hackathon demonstration without compromising the integrity of evidence, cryptographic protections, user privacy, or deterministic safety behavior.

When uncertain, prefer explicitness over silent substitution, real behavior over simulated behavior, and a documented limitation over a misleading claim.
