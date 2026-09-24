# Requester role specification

Status: **Baseline with team-confirmed integration amendments, 20 September 2026**
Team owner: **yooplo** (team agreement confirmed 14 September 2026)

## Development starter scope

The default application now provides real login and Requester create/list/detail
through the shared SQLite services (22 September 2026). The optional validation-only
preview remains available separately and must never describe validation as a save.
Backend and JavaFX tests cover role isolation, rollback/retry, retained input,
duplicate-click prevention, and Manager handoff. Own `OPEN` edit/cancel and saved
cancellation-reason display are implemented in the 24 September increment,
with local automated tests and team review pending. Broader visible history, follow-ups,
filtering, and dashboard summaries remain outstanding. The requirements
and complete product acceptance criteria below remain unchanged.

## Role objective

The Requester reports a facilities problem and follows its progress without
gaining access to other requesters' data or internal maintenance notes.

## Required screens

1. Requester dashboard with counts and recent own requests
2. Own-request list with search and filters
3. New-request form
4. Own-request detail and visible activity history
5. Edit form for an eligible request

## Requirements

- **REQ-001:** The dashboard MUST show only requests owned by the logged-in
  Requester.
- **REQ-002:** The Requester MUST be able to create a request using the fields and
  validation rules in `../components/request-lifecycle.md`.
- **REQ-003:** A successfully created request MUST receive a stable display ID,
  status `OPEN`, creation time, and the logged-in Requester as owner and recording
  actor.
- **REQ-004:** The own-request list MUST support case-insensitive search across
  display ID, title, and location.
- **REQ-005:** The list MUST support filtering by status, category, and creation-date
  range, and MUST provide a clear way to reset filters.
- **REQ-006:** The detail view MUST show current status, reported urgency, manager
  priority when assigned, creation/update times, and requester-visible activity.
- **REQ-007:** The Requester MAY edit title, description, location, category, and
  reported urgency only while the request is `OPEN`.
- **REQ-008:** The Requester MAY cancel only their own `OPEN` request and MUST enter
  a cancellation reason.
- **REQ-009:** The Requester MAY add a follow-up update while the request is not
  `CLOSED` or `CANCELLED`.
- **REQ-010:** Internal technician work notes and private manager notes MUST NOT be
  displayed to the Requester.
- **REQ-011:** After a save succeeds, the interface MUST show the persisted result
  and a clear success confirmation.
- **REQ-012:** If validation, authorization, or persistence fails, the form MUST
  preserve the user's unsaved input and explain what can be corrected or retried.
- **REQ-013:** The Requester MUST NOT assign, reprioritize, start, complete, close,
  reopen, or directly change the status of a request.
- **REQ-014:** A request recorded by a Manager on behalf of the logged-in Requester
  MUST appear in that Requester's list and have the same permitted `OPEN` edit and
  cancellation rights as a request the Requester recorded personally.
- **REQ-015:** The own-request list MUST order by creation time descending, then
  display ID ascending for timestamp ties.
- **REQ-016:** Creation-date filters MUST include both selected dates using the
  app's local time zone. Implement the interval from the start date's local start
  of day up to, but excluding, the day after the end date's local start of day.
- **REQ-017:** Requester-visible history MUST include status changes,
  cancellation/reopening reasons, and the owner's follow-up updates. Internal
  Technician work logs and private Manager notes MUST remain excluded from
  service results, not merely hidden by the UI.

## Acceptance scenarios

Authenticated creation, own-request list/detail and Manager assignment handoff
are implemented, with eligible edit/cancel added in the 24 September increment. The
[confirmed integration decision](../../docs/decisions/yooplo/0001-requester-integration.md)
records implementation dependencies; it does not supersede these requirements.

### REQ-A01 — create a valid request

Given an active Requester is logged in, when they submit every required field
with valid values, then exactly one `OPEN` request is stored under their identity,
an audit event is recorded, and the new request appears in their list.

### REQ-A02 — reject incomplete data

Given a Requester is completing the form, when a required field is blank or a
length rule is violated, then field-level guidance is shown and no request or
audit event is stored.

### REQ-A03 — prevent cross-user access

Given Requester A knows Requester B's display ID, when A attempts to open B's
request through any service operation, then access is denied without revealing
the request details and persistent state is unchanged.

### REQ-A04 — edit only while open

Given the Requester's request is `OPEN`, editing valid request details succeeds
and is audited. Given the same request is `ASSIGNED` or later, editing is rejected
and the original data remains unchanged.

### REQ-A05 — cancel an open request

Given the Requester's request is `OPEN`, when they confirm cancellation with a
valid reason, then the status becomes `CANCELLED`, the reason is visible in the
activity history, and the action is audited.

### REQ-A06 — preserve form input after failure

Given valid unsaved form data and a simulated storage failure, when saving fails,
then no partial request exists, all entered values remain visible, and the user
receives a retryable error message without a stack trace.

### REQ-A07 — persist and isolate own-request reads

For REQ-003, REQ-006, AUT-018–022 and DAT-001–010: given Requesters A and B each
own stored requests, A's list contains only A's records, and requesting B's
record by identifier fails without exposing its details. Unauthenticated,
wrong-role and deactivated callers are rejected. After restart and a new login,
A can still retrieve their committed request with the same display ID.

### REQ-A08 — hand a created request to the Manager

For REQ-003, REQ-006, REQ-010 and LIF-011–012: given a Requester has committed a
new request, an authorized Manager sees the same record in the shared database
and assigns it through the existing assignment service. Refreshing the owner's
detail shows ASSIGNED and the manager priority without exposing internal notes.
The creation and assignment audit events retain their respective actors.

### REQ-A09 — roll back failed creation

For REQ-011–012 and DAT-007: given an injected audit-insert failure during
creation, neither the request nor its creation audit event remains committed.
The UI reports failure, retains the draft, and does not show save success.
Retrying after that rolled-back failure creates exactly one request and its
audit event. While a submission is pending, repeated clicks cannot start another
submission (UIX-009).

### REQ-A10 — reject stale edit and cancellation screens

For REQ-007–008, REQ-012 and LIF-016: given the owner opened an edit or
cancellation form while a request was `OPEN`, when a Manager assigns it before
the owner submits, then the service rejects the operation, preserves the
assignment and audit history, and the form retains the unsaved input.

### REQ-A11 — confirm cancellation and reload its reason

For REQ-008, REQ-014, LIF-004–005, LIF-012 and UIX-011/022: cancellation requires
a trimmed reason of 5–500 characters and a confirmation dialog identifying the
request and terminal result. The safe action is the default; declining makes no
write and retains the reason. Successful cancellation and its audit commit
together. The owner can reload the saved reason from the detail view after
restart, including for a Manager-recorded request. Other owners and roles cannot
retrieve the reason through the Requester service. An audit failure rolls back
the cancellation and leaves the entered reason available for retry.
