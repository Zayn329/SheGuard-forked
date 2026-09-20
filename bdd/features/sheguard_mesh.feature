Feature: SheGuard Mesh Relay (Phase E)
  As a SheGuard user in an offline or network-denied environment
  I want nearby devices to relay verified rising-pattern alerts peer-to-peer
  So that community members receive early warnings without relying on internet connectivity

  Background:
    Given the SheGuard pipeline operates offline on-device
    And local operation is the fundamental guarantee

  # --- Alert Relay ---

  Scenario: Alert is relayed to a nearby device
    Given Device A generates a verified RisingPatternAlert locally
    When Device A serializes the alert into a SheGuardMeshAlertPayload
    And Device A transmits the packet to Device B over mesh
    Then Device B validates the schema and required fields
    And Device B persists the alert with isRelayed flag set to true
    And Device B displays the alert with badge "Received via nearby device"

  # --- Offline Resilience ---

  Scenario: Mesh unavailable does not block local alerts
    Given mesh connectivity is unavailable or no peer devices are discovered
    When local micro-reports trigger pattern detection and trust evaluation
    Then a local RisingPatternAlert is successfully generated
    And the alert is displayed to the user
    And the alert is queued locally for later store-and-forward relay

  # --- Deduplication ---

  Scenario: Duplicate mesh messages are ignored
    Given Device B has already received and persisted an alert with alertId "11111111-1111-1111-1111-111111111111"
    When Device B receives another mesh packet with the same alertId from a different peer
    Then Device B identifies the message as a duplicate via MeshDeduplicationCache
    And Device B drops the packet without creating a second database entry
    And the total count of persisted alerts remains exactly one

  # --- Message Validation ---

  Scenario: Invalid mesh payload is rejected
    Given Device B receives a corrupted or malformed mesh packet
    When the MeshPayloadValidator inspects the payload text
    Then the packet is rejected with a descriptive rejection reason
    And the application does not crash
    And no alert is persisted to local storage

  # --- Privacy Invariants ---

  Scenario: Mesh payload does not expose reporter identity
    Given Device A creates a mesh packet for an alert
    When the serialized JSON payload is inspected
    Then reporter tokens are completely absent
    And user names, phone numbers, and hardware identifiers are completely absent

  Scenario: Mesh payload preserves coarse location
    Given an alert has an approximate location coarsened to approximately 1km resolution
    When the alert is serialized into a mesh payload
    Then the payload contains only the coarsened location string
    And raw high-precision GPS coordinates are not included
    And location precision is not increased

  # --- Trust Boundary ---

  Scenario: Received alert does not bypass trust
    Given Device B receives a valid relayed alert from Device A
    When the alert is processed by the SheGuardMeshAdapter
    Then Device B persists the alert as a community alert
    And Device B does NOT modify any local trust scores
    And Device B does NOT inject new patterns into its SpatioTemporalPatternEngine
    And Device B does NOT bypass Phase C trust evaluation

  # --- Loop & Hop Bounding ---

  Scenario: Relay loop is bounded
    Given a mesh packet traverses in a cycle: Node A -> Node B -> Node C -> Node A
    When Node A receives the packet for the second time
    Then Node A identifies the packet as already processed
    And Node A drops the packet to prevent infinite circulation

  Scenario: Hop count limit prevents indefinite relay
    Given a mesh packet has reached its maximum hop count of 12
    When the packet is received by another peer device
    Then the mesh relay flags the packet as HOP_LIMIT_EXCEEDED
    And the packet is not forwarded further
