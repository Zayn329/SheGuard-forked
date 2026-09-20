@core @protected @offline
Feature: SheGuard Trust and Anti-Gaming Evaluation

  SheGuard deterministically evaluates candidate spatio-temporal patterns to determine
  whether contributing micro-reports provide independent, corroborative evidence.
  The evaluation layer sits strictly between candidate pattern detection and early-warning
  alert generation, ensuring raw report volume alone never escalates pattern confidence.

  Background:
    Given SheGuard is operational offline on the local device
    And candidate spatio-temporal patterns have been detected locally

  @core @offline
  Scenario: Diverse independent reports elevate candidate pattern to emerging
    Given a candidate pattern exists with 3 contributing micro-reports
    And the reports originate from distinct anonymous reporter tokens
    And the reports are temporally separated by at least 5 minutes
    And the reports are geographically consistent with the pattern centroid
    When the Trust and Anti-Gaming Evaluator processes the candidate pattern
    Then the calculated trust score satisfies the emerging threshold of 0.60
    And the pattern transitions from PATTERN_CANDIDATE to PATTERN_EMERGING
    And the pattern does not trigger an active emergency alert

  @core @offline @anti_gaming
  Scenario: High volume submissions from a single reporter fail trust escalation
    Given a candidate pattern exists with 20 contributing micro-reports
    And all 20 reports originate from the same anonymous reporter token
    When the Trust and Anti-Gaming Evaluator processes the candidate pattern
    Then the evaluator recognizes an insufficient reporter diversity of 1
    And duplicate and burst flood penalties are applied
    And the pattern remains in PATTERN_CANDIDATE state
    And the pattern is not promoted to PATTERN_EMERGING
    And no emergency alert is generated

  @core @offline @anti_gaming
  Scenario: Duplicate submissions from the same session are filtered and penalized
    Given a candidate pattern has contributing reports from the same reporter
    And multiple reports share the same category within 5 minutes and 50 meters
    When the Trust and Anti-Gaming Evaluator processes the candidate pattern
    Then the duplicate submissions are identified and filtered from positive evidence
    And a duplicate penalty is deducted from the trust score
    And repeated evaluation produces identical deterministic results

  @core @offline
  Scenario: Temporal burst reports receive minimal temporal independence credit
    Given multiple reports originate from distinct reporters within 2 seconds
    When the Trust and Anti-Gaming Evaluator evaluates temporal independence
    Then the temporal independence score is bounded at the minimum baseline
    And simultaneous submission does not count as strong multi-temporal corroboration

  @core @offline
  Scenario: Reports with missing location coordinates are evaluated safely
    Given contributing micro-reports have null latitude and longitude coordinates
    When the Trust and Anti-Gaming Evaluator processes the pattern
    Then the evaluation completes safely without crashing
    And no artificial coordinates are fabricated
    And neutral spatial consistency credit is assigned

  @core @offline @state_boundary
  Scenario: Trust evaluation preserves state boundaries and never activates alerts
    Given a candidate pattern is evaluated with high multi-signal trust
    When the pattern achieves PATTERN_EMERGING state
    Then the resulting state is strictly PATTERN_EMERGING
    And the state is not ALERT_ACTIVE
    And rising-pattern alert generation is deferred to Phase D
