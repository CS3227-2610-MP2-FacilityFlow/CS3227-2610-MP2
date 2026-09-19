# FacilityFlow Developer Guide

Status: implementation in progress; this guide documents only code currently in
the repository.

## Build baseline

FacilityFlow uses Java 25, Gradle 9.1, JavaFX 25, SQLite JDBC, JUnit 5,
Checkstyle, and JaCoCo. The committed Gradle wrapper and Foojay toolchain
resolver allow `./gradlew check` to obtain the required JDK on a development
machine that does not already have Java 25.

## Current architecture

The package boundaries follow `AGENTS.md`:

- `model` contains immutable request, account, audit, status, urgency, and
  priority values.
- `auth` contains the authenticated-session identity passed to protected use
  cases.
- `service` owns Manager authorization, validation, queue ordering, and the
  named assignment transition.
- `storage` owns the transaction interface, schema foundation, and SQLite/JDBC
  implementation. It does not depend on JavaFX.
- `ui.manager` contains the Manager JavaFX view and thin presentation adapter.
  It contains no SQL or lifecycle rules.

`ManagerRequestService.assignOpenRequest` implements the first complete use
case. It validates the persisted actor, request state, priority, and assignee
inside one storage transaction. It then updates the request and appends one
`REQUEST_ASSIGNED` audit event. Any runtime or database failure rolls the
transaction back.

The Manager queue uses the deterministic ordering in MGR-021. The initial view
shows the queue, request detail, active-Technician selection, Manager-priority
selection, actionable feedback, visible identity/role, and logout action. It is
an injectable component rather than a route that bypasses authentication.

## Database foundation

`SQLiteManagerAssignmentStore.initializeSchema()` currently creates the three
tables required by the assignment slice: `user_accounts`,
`maintenance_requests`, and `audit_events`. This is a schema foundation, not the
final migration or AUT-007–010 demo-seeding implementation.

The transaction interface is deliberately callback-based. Services re-read the
persisted state within the callback before writing, while SQLite controls the
commit or rollback. Other role services should reuse or evolve this shared
boundary rather than adding SQL to controllers.

## Verification

Run:

```shell
./gradlew test
./gradlew check
```

Service tests cover valid assignment, invalid assignee role, inactive
Technician, unauthorized actor, invalid state, and missing priority. The SQLite
integration test installs a trigger that deliberately rejects the audit insert
and verifies that request state, priority, assignee, and audit rows all remain
unchanged.

## Known integration work

- Implement authentication and account session invalidation, then construct
  `ManagerDashboardController` only for an authenticated active Manager.
- Replace the application-shell notice with role routing.
- Implement atomic demo workspace seeding and versioned migrations.
- Extend the queue with MGR-002–003 search/filter/reset behavior.
- Complete Manager transitions, account administration, dashboard summaries,
  audit browsing, and role-level UI/system tests.
- Add the cross-platform CI and release pipeline required by REL-001–008.
