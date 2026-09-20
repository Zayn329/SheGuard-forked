Feature: SheGuard Rising Pattern Alert Engine (Phase D)
  As a SheGuard user in an unsafe area
  I want to receive an actionable early-warning alert
  So that I can make informed safety decisions based on trust-verified pattern evidence

  Background:
    Given the SheGuard pipeline has completed phases REPORT → DETECT → TRUST
    And at least one pattern is in state PATTERN_EMERGING with a trust score >= 0.60

  # ─── Core alert generation ──────────────────────────────────────────────────

  Scenario: Emerging pattern produces a rising-pattern alert
    Given a PATTERN_EMERGING pattern with trust score 0.82 and category HARASSMENT
    When the RisingPatternAlertEngine processes the pattern
    Then a RisingPatternAlert is generated
    And the alert contains the correct category, approximate location, and time window
    And the alert trust level is HIGH
    And the disclaimer is "EARLY WARNING PATTERN ALERT. NOT A GUARANTEED EMERGENCY RESPONSE."

  Scenario: Candidate pattern does not produce an alert
    Given a PATTERN_CANDIDATE pattern with trust score 0.75
    When the RisingPatternAlertEngine processes the pattern
    Then no RisingPatternAlert is generated
    And the pipeline does not escalate the pattern

  Scenario: Pattern below trust threshold does not produce an alert
    Given a PATTERN_EMERGING pattern with trust score 0.55
    When the RisingPatternAlertEngine processes the pattern
    Then no RisingPatternAlert is generated
    And the reason is that the trust score is below the minimum threshold of 0.60

  # ─── Trust level classification ─────────────────────────────────────────────

  Scenario Outline: Trust level is correctly classified from score
    Given a PATTERN_EMERGING pattern with trust score <score>
    When the RisingPatternAlertEngine generates the alert
    Then the alert trust level is <level>

    Examples:
      | score | level  |
      | 0.55  | (none) |
      | 0.60  | MEDIUM |
      | 0.70  | MEDIUM |
      | 0.79  | MEDIUM |
      | 0.80  | HIGH   |
      | 0.95  | HIGH   |

  # ─── Idempotency / determinism ───────────────────────────────────────────────

  Scenario: Alert ID is deterministic for the same pattern
    Given a PATTERN_EMERGING pattern with a fixed patternId
    When the RisingPatternAlertEngine generates alerts twice for the same pattern
    Then both alerts have the same alertId
    And the alert is safe to persist idempotently (REPLACE on conflict)

  # ─── Privacy: location coarsening ────────────────────────────────────────────

  Scenario: Approximate location does not expose sub-1km precision
    Given a PATTERN_EMERGING pattern with exact centroid 19.07605678°N, 72.87776543°E
    When the alert is generated
    Then the approximateLocation string contains coordinates truncated to 2 decimal places
    And the location radius is rounded to the nearest 100m
    And the raw exact coordinates are not present in the alert

  # ─── Pipeline isolation ───────────────────────────────────────────────────────

  Scenario: Alert engine does not re-evaluate trust
    Given a PATTERN_EMERGING pattern (trust already evaluated by TrustAndAntiGamingEvaluator)
    When the RisingPatternAlertEngine generates an alert
    Then the engine uses the pattern's existing trustScore directly
    And no new trust or anti-gaming evaluation is performed inside the alert engine

  Scenario: Alert engine operates entirely on-device without network
    Given the device has no Internet connection
    And the device has no backend connectivity
    When an emerging pattern is available locally
    Then the RisingPatternAlertEngine still generates a rising-pattern alert
    And no network call is made

  # ─── Persistence ─────────────────────────────────────────────────────────────

  Scenario: Generated alert is persisted to Room database
    Given a PATTERN_EMERGING pattern produces a RisingPatternAlert
    When the alert is saved via AlertRepository
    Then the alert can be retrieved by alertId from Room storage
    And the retrieved alert has the same category, trustLevel, trustScore, and disclaimer
    And the alert table is separate from micro_reports and spatio_temporal_patterns

  Scenario: Alert persistence survives database migration from version 3 to version 4
    Given the device has an existing Room database at version 3
    When the app is upgraded and MIGRATION_3_4 runs
    Then the rising_pattern_alerts table is created
    And existing micro_reports and spatio_temporal_patterns data is preserved
