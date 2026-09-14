# Facilities Manager role specification

Status: **Draft v0.2 — requirements confirmed 12 September 2026**
Team owner: **yu-sutong** (team agreement confirmed 14 September 2026)

## Role objective

The Facilities Manager maintains the operational overview, controls assignment
and priority, reviews outcomes, manages accounts, records reports on behalf of
existing Requesters, and can explain past decisions through audit history.

## Required screens

1. Manager dashboard with workload and status summaries
2. All-request queue with search, filters, and sorting
3. Request triage/detail view and record-on-behalf action
4. Assignment/reassignment action
5. Completed-work review action
6. Account-management screen
7. Read-only audit-event viewer

## Requirements

- **MGR-001:** The Manager MUST be able to view every request and its internal and
  requester-visible history.
- **MGR-002:** The all-request queue MUST support search by display ID, title,
  location, Requester, and assigned Technician.
- **MGR-003:** The queue MUST support status, category, priority, Technician, and
  creation-date filters and MUST provide a reset action.
- **MGR-004:** The Manager MUST set an operational priority before first assignment.
- **MGR-005:** The Manager MAY assign an `OPEN` request to one active Technician,
  changing the request to `ASSIGNED`.
- **MGR-006:** The Manager MAY reassign an `ASSIGNED` or `IN_PROGRESS` request to
  another active Technician, with a mandatory reason, returning it to `ASSIGNED`.
- **MGR-007:** The Manager MAY close a `COMPLETED` request after reviewing its work
  logs and resolution summary.
- **MGR-008:** The Manager MAY return a `COMPLETED` request to `ASSIGNED` with a
  mandatory reason and an active Technician assignment.
- **MGR-009:** The Manager MAY reopen a `CLOSED` request to `ASSIGNED` with a
  mandatory reason and an active Technician assignment.
- **MGR-010:** The Manager MAY cancel an `OPEN`, `ASSIGNED`, or `IN_PROGRESS`
  request with a mandatory reason.
- **MGR-011:** The Manager MUST NOT directly set a status outside the lifecycle
  operations defined in `../components/request-lifecycle.md`.
- **MGR-012:** The Manager MUST be able to create accounts for any of the three
  roles, deactivate/reactivate accounts, change another account's role subject to
  AUT-028–029, reset another account's password subject to AUT-030, and view account
  status.
- **MGR-013:** The Manager MUST NOT deactivate their own active session or leave
  the system without at least one active Manager account.
- **MGR-014:** Assignment MUST reject inactive users and users whose role is not
  Technician.
- **MGR-015:** The dashboard MUST show counts by status and priority plus the number
  of active assignments per Technician.
- **MGR-016:** Withdrawn. Advanced reports are outside the MVP scope.
- **MGR-017:** Withdrawn. CSV export is outside the MVP scope.
- **MGR-018:** The Manager MUST be able to search/filter audit events by request,
  actor, action type, and date range but MUST NOT edit or delete them.
- **MGR-019:** The Manager MAY record a valid `OPEN` request on behalf of an existing
  Requester account. The selected Requester owns the request and receives the usual
  Requester rights; the audit event identifies the Manager as the recording actor.
- **MGR-020:** The Manager MAY make the corrections permitted by LIF-023 in every
  request state. The interface MUST distinguish a correction from a lifecycle action.
- **MGR-021:** The default all-request queue order MUST place `OPEN` requests first,
  sorting them by reported urgency from `Emergency` through `Low` and then oldest
  creation time. Active assigned work follows, sorted by Manager priority and oldest
  update time; `COMPLETED`, `CLOSED`, and `CANCELLED` requests follow by newest
  update time. Display ID breaks ties.

## Acceptance scenarios

### MGR-A01 — assign an open request

Given an `OPEN` request, an active Technician, and a selected priority, when the
Manager assigns it, then assignment and priority are stored, status becomes
`ASSIGNED`, and one transition audit event is committed atomically.

### MGR-A02 — reject invalid assignee

Given an inactive account or a non-Technician account, when the Manager attempts
assignment, then a clear validation message is shown and the request and audit
trail remain unchanged.

### MGR-A03 — reassign in-progress work

Given an `IN_PROGRESS` request, when the Manager selects another active Technician
and gives a valid reason, then the assignee changes, status becomes `ASSIGNED`,
existing work logs are preserved, and the reason is audited.

### MGR-A04 — review and close

Given a `COMPLETED` request with a resolution summary, when the Manager accepts
the work, then status becomes `CLOSED`, closure time is stored, and subsequent
role writes are rejected except for a Manager correction permitted by LIF-023 or
a Manager reopen action.

### MGR-A05 — return incomplete work

Given a `COMPLETED` request, when the Manager enters a valid return reason and
selects an active Technician, then status becomes `ASSIGNED`, completion metadata
is retained historically, and the reason appears in audit history.

### MGR-A06 — preserve an active manager

Given only one active Manager account exists, when that Manager attempts to
deactivate it, then the operation is rejected and the account remains active.

### MGR-A07 — record a request on behalf

Given an existing Requester account, when the Manager records valid request
details on that account's behalf, then one audited `OPEN` request belongs to that
Requester and appears in the Requester's own list.

### MGR-A08 — correct terminal request fields

Given a `CLOSED` or `CANCELLED` request, when the Manager corrects a permitted
field, then the correction is saved and audited without changing status, ownership,
history, or completion data.
