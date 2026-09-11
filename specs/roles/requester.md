# Requester role specification

Status: **Draft v0.2 — requirements confirmed 12 September 2026**
Team owner: **TBD**

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

## Acceptance scenarios

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
