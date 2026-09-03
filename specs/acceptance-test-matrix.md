# Acceptance test matrix

Status: **Draft v0.1**

This file defines release-level scenarios. Detailed unit and integration cases
are derived from the role/component requirements and tracked in test names or
test metadata using the listed requirement identifiers.

| ID | Scenario | Principal requirements | Expected result |
|---|---|---|---|
| E2E-001 | First launch and account setup | AUT-007–010 | First Manager is created atomically; setup cannot run again |
| E2E-002 | Create one account per remaining role | MGR-012, AUT-023–027 | Active Requester and Technician can authenticate to their own dashboards |
| E2E-003 | Requester submits a valid report | REQ-002–003, LIF-001–005 | One audited `OPEN` request with a generated display ID is persisted |
| E2E-004 | Requester validation failure | REQ-012, UIX-006–009 | No partial data; field values and actionable guidance remain visible |
| E2E-005 | Manager triages and assigns | MGR-004–005, LIF-011–012 | Priority, assignee, `ASSIGNED` status, and audit event commit atomically |
| E2E-006 | Technician begins and logs work | TEC-004–007 | Request becomes `IN_PROGRESS`; append-only work log is stored |
| E2E-007 | Technician submits completion | TEC-008–009 | Valid work becomes `COMPLETED` with resolution metadata and audit history |
| E2E-008 | Manager returns work | MGR-008, LIF-014 | Request returns to `ASSIGNED`; reason and prior history are retained |
| E2E-009 | Manager accepts work | MGR-007, LIF-015 | Request becomes `CLOSED` and normal writes are rejected |
| E2E-010 | Manager reopens closed work | MGR-009, LIF-014–015 | Request becomes `ASSIGNED` with valid assignee and recorded reason |
| E2E-011 | Requester cancels open request | REQ-008, LIF-015 | Own request becomes terminal `CANCELLED`; later edits are rejected |
| E2E-012 | Manager reassigns active work | MGR-006, TEC-012 | New assignee and `ASSIGNED` state persist; former Technician write is rejected |
| E2E-013 | Block Requester cross-access | PRD-013, REQ-003, AUT-019–021 | No inaccessible details or state changes are returned |
| E2E-014 | Block Technician cross-access | PRD-014, TEC-011, AUT-019–021 | Unassigned request cannot be read or modified |
| E2E-015 | Block role-forged Manager action | PRD-004, AUT-018–021 | Direct service invocation fails even without UI controls |
| E2E-016 | Survive restart | PRD-006, DAT-001–004 | Accounts, requests, history, and audit events reload correctly; session does not |
| E2E-017 | Roll back injected transition failure | LIF-012, DAT-007 | Neither state nor audit event remains after failure |
| E2E-018 | Deactivate an account safely | AUT-022–027 | Login is rejected; history remains; last active Manager cannot be removed |
| E2E-019 | Filter and export Manager report | MGR-015–017, DAT-013–014 | Accurate range-limited UTF-8 CSV is produced without secrets |
| E2E-020 | Inspect audit history | MGR-018, DAT-015–019 | Authorized filters work; events cannot be edited or deleted |
| E2E-021 | Recover from database lock/failure | DAT-009, OBS-002–007 | No crash or raw exception; safe diagnostic event and recovery guidance exist |
| E2E-022 | Verify separate role UIs | PRD-002, UIX-001–005 | Each login reaches only its role navigation and dashboard |
| E2E-023 | Keyboard/accessibility smoke test | UIX-017–023 | Critical workflows are keyboard reachable and do not rely on color alone |
| E2E-024 | Cross-platform clean install | REL-003, REL-005–008 | Tagged artifact launches and completes a smoke workflow on all target OSs |

## Test naming convention

Automated tests SHOULD include at least one requirement identifier in the test
name or display name, for example:

```java
@Test
@DisplayName("LIF-012 rolls back the state change when audit insertion fails")
void transitionRollsBackWhenAuditInsertFails() {
    // ...
}
```

## Required evidence before release

- CI link and commit SHA for the release candidate
- Coverage and static-analysis reports
- Cross-platform smoke-test record
- Completed E2E matrix with pass/fail and issue links
- Verification that User Guide behavior matches the built artifact
- Verified AI-session summaries supporting the reflection
