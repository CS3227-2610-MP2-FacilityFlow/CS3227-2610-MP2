# Decision 0001: Begin with the Manager assignment vertical slice

- Owner: `yu-sutong`
- Date: 18 September 2026
- Status: Proposed for team review
- Human verification: Approved by `yu-sutong` on 19 September 2026

## Context

The repository contained approved specifications but no Java/JavaFX project or
application source. The Facilities Manager scope is broad and shares account,
request, lifecycle, authorization, audit, and persistence concerns with the
Requester and Technician roles.

## Decision

Build the `OPEN` to `ASSIGNED` Manager workflow first, together with the minimum
shared project foundation. The slice includes Java 25/Gradle/JavaFX, immutable
domain records, authenticated Manager checks, active-Technician validation,
Manager-priority validation, one atomic request-and-audit transaction, a
queue/detail/assignment view, and automated service and SQLite rollback tests.

The Manager view remains injectable until the team supplies shared
authentication. The application must not use a hard-coded Manager session just
to make the screen launch.

## Reasons

- E2E-005 is the first point at which the Manager and Technician work connect.
- The slice establishes reusable boundaries without attempting the entire
  Manager dashboard at once.
- Atomic audit persistence addresses a high-risk requirement early.
- The work can integrate with future Requester creation and Technician workflow
  through shared models and service/storage contracts.

## Consequences

- Search/filtering, reassignment, review/close/reopen, account administration,
  audit browsing, demo seeding, and login remain future work.
- Teammates should review the shared records and transaction boundary before
  building on them.
- Authentication integration is required before the Manager view is reachable
  in the running product.

## Alternatives considered

- Building every Manager screen first was rejected because it would delay a
  testable end-to-end lifecycle increment and create more integration risk.
- Launching with a hard-coded Manager account was rejected because it would
  undermine AUT-018–022 and could be mistaken for completed authentication.
