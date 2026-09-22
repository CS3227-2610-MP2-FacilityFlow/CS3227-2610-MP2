# Acceptance test matrix

Status: **Baseline with team-confirmed integration amendments, 20 September 2026**

This file defines release-level scenarios. Detailed unit and integration cases
are derived from the role/component requirements and tracked in test names or
test metadata using the listed requirement identifiers.

| ID | Scenario | Principal requirements | Expected result |
|---|---|---|---|
| E2E-001 | Create initial demo workspace | AUT-007–010 | A new database atomically contains two active accounts per role and representative requests across every lifecycle state; no reseed occurs later |
| E2E-002 | Safely administer accounts | MGR-012–013, AUT-023–031 | The Manager can create, deactivate/reactivate, reset, and change eligible accounts without exposing passwords, leaving zero Managers, or stranding active Technician work |
| E2E-003 | Requester submits a valid report | REQ-002–003, LIF-001–005 | One audited `OPEN` request with a generated display ID is persisted |
| E2E-004 | Requester validation failure | REQ-012, UIX-006–009 | No partial data; field values and actionable guidance remain visible |
| E2E-005 | Manager triages and assigns | MGR-004–005, LIF-011–012 | Priority, assignee, `ASSIGNED` status, and audit event commit atomically |
| E2E-006 | Technician begins and logs work | TEC-004–007 | Request becomes `IN_PROGRESS`; append-only work log is stored |
| E2E-007 | Technician submits completion | TEC-008–009 | Valid work becomes `COMPLETED` with resolution metadata and audit history |
| E2E-008 | Manager returns work | MGR-008, LIF-014 | Request returns to `ASSIGNED`; reason and prior history are retained |
| E2E-009 | Manager accepts work | MGR-007, LIF-015, LIF-023 | Request becomes `CLOSED`; normal role writes are rejected while only audited Manager corrections remain available |
| E2E-010 | Manager reopens closed work | MGR-009, LIF-014–015 | Request becomes `ASSIGNED` with valid assignee and recorded reason |
| E2E-011 | Requester cancels open request | REQ-008, LIF-015 | Own request becomes terminal `CANCELLED`; later Requester edits are rejected |
| E2E-012 | Manager reassigns active work | MGR-006, TEC-012 | New assignee and `ASSIGNED` state persist; former Technician write is rejected |
| E2E-013 | Block Requester cross-access | PRD-013, REQ-003, AUT-019–021 | No inaccessible details or state changes are returned |
| E2E-014 | Block Technician cross-access | PRD-014, TEC-011, AUT-019–021 | Unassigned request cannot be read or modified |
| E2E-015 | Block role-forged Manager action | PRD-004, AUT-018–021 | Direct service invocation fails even without UI controls |
| E2E-016 | Survive restart | PRD-006, DAT-001–004 | Accounts, requests, history, and audit events reload correctly; session does not |
| E2E-017 | Roll back injected transition failure | LIF-012, DAT-007 | Neither state nor audit event remains after failure |
| E2E-018 | Deactivate an account safely | AUT-022–027 | Login is rejected; history remains; last active Manager cannot be removed |
| E2E-019 | View Manager operational overview | MGR-015 | Accurate counts by status and priority and active assignments per Technician are displayed |
| E2E-020 | Inspect audit history | MGR-018, DAT-015–019 | Authorized filters work; events cannot be edited or deleted |
| E2E-021 | Recover from database lock/failure | DAT-009, OBS-002–007 | No crash or raw exception; safe diagnostic event and recovery guidance exist |
| E2E-022 | Verify separate role UIs | PRD-002, UIX-001–005 | Each login reaches only its role navigation and dashboard |
| E2E-023 | Keyboard/accessibility smoke test | UIX-017–023 | Critical workflows are keyboard reachable and do not rely on color alone |
| E2E-024 | Cross-platform clean install | REL-003, REL-005–008 | Tagged artifact launches and completes a smoke workflow on all target OSs |
| E2E-025 | Manager records on behalf | MGR-019, REQ-014, LIF-003 | A valid `OPEN` request is owned by the selected existing Requester, who can view, edit, or cancel it while open; the Manager is audited as recording actor |
| E2E-026 | Apply category catalogue migration | LIF-021–022 | A valid rename/removal mapping updates affected records atomically; malformed catalogue data leaves the database unchanged and stops startup safely |
| E2E-027 | Correct request fields safely | MGR-020, LIF-023 | The Manager can correct permitted fields in every state without overwriting workflow state, ownership, history, or audit records |

## Test naming convention

Additional team-confirmed integration checks (20 September 2026; not yet executed):

| ID | Scenario | Principal requirements | Expected result |
|---|---|---|---|
| E2E-028 | Password boundaries and own-password change | AUT-004–005, AUT-033 | Lengths 8/24 pass without composition rules, 7/25 fail; stored password is hashed and current session survives own-password change |
| E2E-029 | Live role change | AUT-018–021, AUT-028–029, AUT-032 | Eligible change keeps the session, rejects now-forbidden old-role action, and routes to current role without login |
| E2E-030 | Manager password reset | AUT-030 | Next protected call rejects the target's old session and returns to login |
| E2E-031 | Requester order and date boundaries | REQ-015–016 | Newest creation first with ascending display-ID ties; both selected local dates included, next day's midnight excluded |
| E2E-032 | Requester history visibility | REQ-010, REQ-017 | Status changes, cancellation/reopening reasons, and own updates are returned; internal logs/private notes never reach Requester results |
| E2E-033 | ID exhaustion and safe creation audit | LIF-002, DAT-007, DAT-015–019 | Generated IDs stay unique and six-digit; beyond FF-999999 fails atomically; creation audit includes identity/actor/target/time/action but no full title, description, or location |
| E2E-034 | Shared data and catalogue location | DAT-001, DAT-010, LIF-021–022 | Roles use one OS-user application-data database and adjacent properties catalogue; invalid configuration changes nothing; tests use separate temporary databases |

Requester integration milestone (backend and authenticated JavaFX evidence added
22 September 2026; release acceptance and visible history remain outstanding):

| Role acceptance | Existing release scenarios | Integration evidence required |
|---|---|---|
| REQ-A07 | E2E-013, E2E-016, E2E-018 | Owner-only list/detail, safe denial for other users and inactive sessions, and reload after restart/login |
| REQ-A08 | E2E-003, E2E-005 | Requester-created record reaches Manager assignment in the same database; owner sees assigned state and only permitted history |
| REQ-A09 | E2E-003, E2E-004, E2E-017 | Creation and audit roll back together on injected failure; input survives, pending submission cannot duplicate, and retry after rollback creates one record |

See the [Requester acceptance scenarios](roles/requester.md) and
[confirmed integration decision](../docs/decisions/yooplo/0001-requester-integration.md).

`SQLiteRequesterRequestServiceTest` verifies backend portions of REQ-A07–A09:
owner-scoped reads, role/deactivation checks, database reopening, Manager handoff,
creation rollback/retry, deterministic own-list order, and ID exhaustion with safe
audit metadata. `SchemaMigrationsTest` verifies versioned startup and preservation
of legacy Manager data. `AuthenticationServiceTest` adds real login/session,
password change/reset and audit rollback evidence. `AuthenticatedWorkflowTest`
covers each role route, login/logout, one pending submission, recovery, same-owner
draft restoration, and Requester-to-Manager handoff through real controls.
`WorkspaceTest` covers initial account seeding and category startup validation.
These are local automated results, not signed release-level E2E passes. Visible
history, date filtering, representative lifecycle demo requests, category mappings,
and the complete Technician/Manager workflow remain outstanding.

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
