# Persistence, audit, and observability specification

Status: **Draft v0.2 — requirements confirmed 12 September 2026**

## SQLite persistence

- **DAT-001:** Production data MUST be stored in a local SQLite database outside
  the packaged application artifact.
- **DAT-002:** The application MUST enable SQLite foreign-key enforcement for every
  connection.
- **DAT-003:** Schema changes MUST be applied through ordered, versioned migrations;
  ad hoc table creation inside repositories is prohibited.
- **DAT-004:** Startup MUST validate the schema version and either migrate safely or
  stop with an actionable error. It MUST NOT continue against an unknown newer schema.
- **DAT-005:** Repository interfaces MUST isolate service/domain logic from JDBC.
- **DAT-006:** SQL parameters MUST use prepared statements. User input MUST never be
  concatenated into SQL.
- **DAT-007:** Multi-write business operations, including every lifecycle transition,
  MUST use a transaction with rollback on failure.
- **DAT-008:** Internal database IDs MUST NOT be treated as authorization evidence;
  service-level ownership/assignment checks remain mandatory.
- **DAT-009:** The application MUST handle an unavailable, locked, or corrupt database
  without displaying a raw exception or silently discarding data.
- **DAT-010:** Automated repository tests MUST use isolated temporary databases and
  must never depend on a developer's real application data.

## Data retention

- **DAT-011:** Requests, work logs, requester updates, and audit events MUST NOT be
  hard-deleted through application UI.
- **DAT-012:** Account deactivation MUST preserve historical authorship.
- **DAT-013:** Withdrawn. CSV export is outside the MVP scope.
- **DAT-014:** Withdrawn. CSV export failure handling is outside the MVP scope.

## Audit trail

- **DAT-015:** An audit event MUST include immutable ID, UTC timestamp, actor account
  ID, action type, target type, target ID, and safe structured details.
- **DAT-016:** Audited actions MUST include login success, account creation/state,
  role changes, password changes/resets, request creation including Manager
  record-on-behalf actions, request edits/cancellation, priority/assignment changes,
  every lifecycle transition, work-log creation, and category migrations.
- **DAT-017:** Failed login attempts MUST be recorded only in operational logs; do
  not create permanent audit events containing attempted passwords or usernames
  that are not known accounts.
- **DAT-018:** Audit events MUST be append-only through the application and ordered
  deterministically by time then immutable ID.
- **DAT-019:** Audit detail MUST describe changed field names and workflow reasons
  without duplicating password data or full sensitive free-text fields.

## Operational logs and monitoring

- **OBS-001:** The application MUST write structured operational logs containing
  timestamp, severity, component, event name, and correlation/request ID when relevant.
- **OBS-002:** Logs MUST include application startup/shutdown, migration results,
  category-catalogue migration outcomes, handled persistence failures, unexpected
  exceptions, and build version.
- **OBS-003:** Passwords, password hashes, raw database connection secrets, and full
  request descriptions MUST NOT be logged.
- **OBS-004:** Expected validation and authorization failures SHOULD avoid noisy
  stack traces; unexpected failures MUST retain enough diagnostic context for developers.
- **OBS-005:** Logs MUST rotate or cap retained file size so normal usage cannot
  consume unbounded disk space.
- **OBS-006:** The Developer Guide MUST explain log locations, severity meanings,
  safe diagnostic collection, and how monitoring evidence satisfies the assignment.
- **OBS-007:** A global JavaFX exception boundary MUST record unexpected UI errors
  and show a stable recovery message instead of terminating silently.

The team must confirm with the teaching staff whether local operational logs,
audit events, CI status, and release smoke-test results are sufficient monitoring
evidence for this desktop application.
