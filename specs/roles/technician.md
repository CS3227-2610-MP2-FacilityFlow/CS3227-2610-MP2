# Technician role specification

Status: **Draft v0.2 — requirements confirmed 12 September 2026**
Team owner: **ngkhengyang** (team agreement confirmed 14 September 2026)

## Role objective

The Technician works from a focused personal queue, records accountable progress,
and submits completed work for managerial review without seeing unrelated work.

## Required screens

1. Technician dashboard with assigned/in-progress/completed-awaiting-review counts
2. Personal work queue with search, filters, and ordering
3. Assigned-request detail
4. Start-work action
5. Work-log entry form
6. Completion form

## Requirements

- **TEC-001:** The dashboard and queue MUST show only requests currently assigned
  to the logged-in Technician.
- **TEC-002:** The queue MUST support case-insensitive search by display ID, title,
  and location and filtering by status, category, and priority.
- **TEC-003:** The default queue order MUST include only the logged-in Technician's
  assigned requests and sort by Manager priority from `Critical` through `Low`, then
  reported urgency from `Emergency` through `Low`, then oldest assignment time.
  A request upgraded from a schema that did not store assignment time MUST retain
  an unknown assignment time rather than deriving one from `updatedAt`; unknown
  times sort after known times, with display ID as the deterministic tie-breaker.
  Its next assignment or reassignment establishes a known assignment time.
- **TEC-004:** A Technician MAY transition an assigned request from `ASSIGNED` to
  `IN_PROGRESS` using an explicit start-work action.
- **TEC-005:** A Technician MAY add a work log only to a request currently assigned
  to them and in `IN_PROGRESS`.
- **TEC-006:** Each work log MUST contain a non-blank note and minutes spent within
  the limits in `../components/request-lifecycle.md`.
- **TEC-007:** Work logs MUST be append-only through the UI and MUST retain author
  and timestamp.
- **TEC-008:** A Technician MAY transition their `IN_PROGRESS` request to
  `COMPLETED` only after at least one work log exists and a valid resolution
  summary is provided.
- **TEC-009:** Completion MUST preserve the current assignment so the Manager knows
  which Technician performed the work.
- **TEC-010:** A Technician MUST NOT assign/reassign, reprioritize, close, reopen,
  cancel, or edit the Requester's original report.
- **TEC-011:** A Technician MUST NOT read another Technician's assigned request or
  work logs unless that request is later assigned to them; historical authorship
  remains visible after reassignment.
- **TEC-012:** If a request is reassigned while open in the UI, the next attempted
  write by the former Technician MUST be rejected and the view MUST refresh.
- **TEC-013:** Successful progress actions MUST immediately show the resulting
  persisted state and confirmation.

## Acceptance scenarios

### TEC-A01 — start assigned work

Given a request in `ASSIGNED` is assigned to the logged-in Technician, when they
start work, then the request becomes `IN_PROGRESS` and one audit event records the
actor, old state, new state, and time.

### TEC-A02 — block unrelated request access

Given a request is assigned to Technician B, when Technician A attempts to read
or modify it through any service operation, then access is denied without
disclosing request details and no state changes.

### TEC-A03 — add valid work

Given an assigned request is `IN_PROGRESS`, when the Technician submits a valid
note and time, then one append-only work log appears in chronological history and
the request's updated time changes.

### TEC-A04 — require evidence before completion

Given an `IN_PROGRESS` request has no work log, when the Technician attempts to
complete it, then completion is rejected with guidance and status remains
`IN_PROGRESS`.

### TEC-A05 — complete for review

Given an `IN_PROGRESS` assigned request has at least one work log, when the
Technician supplies a valid resolution summary, then status becomes `COMPLETED`,
the summary and completion time are stored, and the transition is audited.

### TEC-A06 — reject stale reassigned update

Given the Manager reassigns a request after the former Technician opened it,
when the former Technician submits a work log, then the service rejects it,
no log is stored, and the UI explains that the assignment changed.
