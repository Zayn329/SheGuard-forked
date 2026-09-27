# Keyword Detection Disconnect and Removal Plan

Status: runtime disconnected; permanent removal deferred

## Goal

Stop legacy keyword detection from executing or appearing in live detection logs,
while preserving the SheGuard offline MVP pipeline and keeping the change easy to
reverse until legacy contracts are reviewed.

## Repository map

| Area | Relevant files | Role |
|---|---|---|
| Runtime owner | `android/features/incident/.../SafetyForegroundService.kt` | Creates detectors, reads audio, forwards signals, and owns live detection logging. |
| Detector implementation | `android/services/detection/.../detectors/Detectors.kt` | Contains `KeywordDetector`, `ScreamDetector`, and `MotionDetector`. |
| Keyword model | `android/services/detection/.../tflite/TFLiteSpeechCommandsClassifier.kt` and `android/app/src/main/assets/models/speech_commands*` | Legacy speech-command model and labels. |
| Fusion | `android/services/detection/.../fusion/SignalFusionEngine.kt` | Consumes detector signals and applies legacy incident rules. |
| Log store/UI | `android/services/detection/.../log/DetectionLogManager.kt`, `android/app/.../DetectionLogScreen.kt` | Stores and renders signal events. |
| Legacy contracts | `android/services/detection/src/test/.../DetectionUnitTest.kt`, `backend/tests/`, `docs/api/`, `docs/adr/0002-*`, `bdd/features/monitoring.feature` | Historical tests, examples, and behavior documentation that still mention keywords. |

## Dependency map

Before this change:

```
AudioRecord → KeywordDetector → SignalFusionEngine → IncidentStateMachine
                      └──────→ DetectionLogManager → DetectionLogScreen
AudioRecord → ScreamDetector ─┘
Sensor data → MotionDetector ─┘
```

After this change:

```
AudioRecord → ScreamDetector ─┐
Sensor data → MotionDetector ──┴→ SignalFusionEngine → IncidentStateMachine
AudioRecord → Evidence pre-roll
Scream/Motion signals ───────────────────────────────→ DetectionLogManager → DetectionLogScreen

KeywordDetector / speech-command model: disconnected and dormant
```

The SheGuard core pipeline remains independent:

```
Anonymous report → Room → pattern detection → trust evaluation → local alert → mesh
```

## Implemented now

- Removed keyword detector construction from `SafetyForegroundService`.
- Removed speech-command TFLite initialization and cleanup from the service.
- Removed keyword flow collection, audio processing, confidence diagnostics, and
  runtime log emission from the service.
- Updated monitoring notifications and UI copy so they do not claim keyword support.
- Retained dormant implementation and legacy tests to avoid silently changing old
  public/internal contracts in the same change.

## Permanent removal plan

1. Audit all remaining keyword references and classify them as runtime, test,
   historical documentation, or public contract.
2. Confirm with product/architecture owners whether the legacy incident feature may
   remove `DetectorType.KEYWORD`, keyword fusion rules, and model assets.
3. Update or delete the dedicated keyword tests and historical BDD/API fixtures.
4. Remove the dormant detector, classifier, labels, config fields, fusion branches,
   and UI enum rendering in one focused change.
5. Run detection, incident, app, and backend test suites; verify live logs contain no
   keyword events and that scream/motion behavior is unchanged.

## Validation criteria

- No runtime reference from `SafetyForegroundService` to `KeywordDetector` or the
  speech-command classifier.
- No runtime keyword confidence diagnostic.
- No keyword event emitted to `DetectionLogManager` by the service.
- Scream and motion detection paths remain wired and compile.
- SheGuard Report → Detect → Trust → Alert behavior is unchanged.

## Known limitation

The dormant class, model assets, fusion branches, enum value, and historical test
fixtures still exist. A complete repository-wide removal requires a separate
contract review because those references belong to the legacy incident feature.
