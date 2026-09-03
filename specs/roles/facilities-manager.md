# Facilities Manager role specification

Status: **Draft v0.1**  
Team owner: **TBD**

## Role objective

The Facilities Manager maintains the operational overview, controls assignment
and priority, reviews outcomes, manages accounts, and can explain past decisions
through reports and audit history.

## Required screens

1. Manager dashboard with workload and status summaries
2. All-request queue with search, filters, and sorting
3. Request triage/detail view
4. Assignment/reassignment action
5. Completed-work review action
6. Account-management screen
7. Reports and CSV export screen
8. Read-only audit-event viewer

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
- **MGR-012:** The Manager MUST be able to create Requester and Technician accounts,
  deactivate/reactivate accounts, and view account status.
- **MGR-013:** The Manager MUST NOT deactivate their own active session or leave
  the system without at least one active Manager account.
- **MGR-014:** Assignment MUST reject inactive users and users whose role is not
  Technician.
- **MGR-015:** The dashboard MUST show counts by status and priority plus the number
  of active assignments per Technician.
- **MGR-016:** Reports MUST support a date range and include request counts, median
  resolution time when available, and per-Technician completed counts.
- **MGR-017:** CSV export MUST use the currently selected report date range, include
  a header row, escape values correctly, and avoid internal passwords or secrets.
- **MGR-018:** The Manager MUST be able to search/filter audit events by request,
  actor, action type, and date range but MUST NOT edit or delete them.

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
role writes are rejected unless a Manager reopens it.

### MGR-A05 — return incomplete work

Given a `COMPLETED` request, when the Manager enters a valid return reason and
selects an active Technician, then status becomes `ASSIGNED`, completion metadata
is retained historically, and the reason appears in audit history.

### MGR-A06 — preserve an active manager

Given only one active Manager account exists, when that Manager attempts to
deactivate it, then the operation is rejected and the account remains active.

### MGR-A07 — export a filtered report

Given a valid date range and matching requests, when the Manager exports the
report, then a readable CSV containing only the range's report rows is produced
without credentials or internal diagnostic data.
