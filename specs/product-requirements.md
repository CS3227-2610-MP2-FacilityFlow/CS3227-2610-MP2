# Product requirements

Status: **Draft v0.2 — requirements confirmed 12 September 2026**

## Problem statement

Facilities issues in formal settings are often reported through conversations or
scattered messages. Requesters cannot reliably see progress, technicians lack a
consistent queue, and managers cannot prove who changed a request or how long
resolution took. FacilityFlow provides a single local desktop workflow for
general-purpose facilities maintenance with explicit roles, validated state
changes, and an audit trail.

## Product goals

- **PRD-001:** The product MUST support exactly three authenticated user roles:
  Requester, Technician, and Facilities Manager.
- **PRD-002:** Each role MUST receive a separate, task-focused interface after login.
- **PRD-003:** The product MUST support the complete lifecycle from report creation
  to assignment, work, completion, and managerial closure.
- **PRD-004:** The product MUST prevent one role from performing another role's
  protected operations, both in the UI and service layer.
- **PRD-005:** Important changes MUST be persistent and auditable.
- **PRD-006:** The released application MUST remain usable after restart without
  requiring a network connection.
- **PRD-007:** The application MUST provide actionable validation and error messages
  rather than failing silently or exposing stack traces to users.

## Actors

### Requester

An employee, resident, or authorized organization member who owns facility
requests and tracks only their own requests.

### Technician

A maintenance worker who sees work assigned to their account, records progress,
and submits completed work for review.

### Facilities Manager

An operational administrator who sees all requests, makes triage and assignment
decisions, reviews completed work, manages accounts, and uses a simple
operational overview and audit view.

Team-agreed role ownership (confirmed 14 September 2026): Requester — `yooplo`;
Technician — `ngkhengyang`; Facilities Manager — `yu-sutong`.

## MVP scope

- Documented seeded demo accounts and requests for a new local database, login,
  logout, and role-based sessions
- Manager account administration, including role changes and password resets
- Request creation, Manager recording on behalf of an existing Requester, and
  Requester self-service tracking
- Manager triage, prioritization, assignment, reassignment, cancellation, and review
- Technician work queue, start-work action, work-log entries, and completion
- Explicit request states and validated transitions
- Search, filters, and useful empty states for each dashboard
- Persistent SQLite storage with schema versioning and transactional updates
- Immutable application audit trail and structured operational error logs
- A simple Manager operational overview and read-only audit view
- Automated tests, CI checks, cross-platform build verification, and formal release
- Accurate user/developer guides, product website, Agentic SE logs, and reflection

## Explicit non-goals for the MVP

- Cloud synchronization or a remote server
- Simultaneous use of one database by multiple application processes
- Email, SMS, push notifications, or calendar integration
- Maps, GPS, image/file attachments, inventory, invoicing, or payment processing
- Native mobile or web applications
- AI-generated maintenance decisions inside the released product
- Arbitrary workflow configuration or custom roles
- CSV export and advanced reports

These may be reconsidered only after every MUST requirement is complete and tested.

## Deployment assumption

- **PRD-008:** One FacilityFlow installation MUST use one local SQLite database and
  support multiple accounts taking turns on that installation.
- **PRD-009:** The application MUST reject or safely handle attempts to open the
  same database from a conflicting application process.
- **PRD-010:** Dates and times MUST be stored in UTC and rendered in the host's
  local time zone using an unambiguous display format.

## Shared business rules

- **PRD-011:** Every active account MUST have exactly one role.
- **PRD-012:** Deactivated accounts MUST NOT be able to authenticate.
- **PRD-013:** A Requester MUST NOT read requests created by another Requester.
- **PRD-014:** A Technician MUST NOT read or modify requests not assigned to them,
  except that a reassigned request remains represented in the audit trail.
- **PRD-015:** A Facilities Manager MAY read all requests but MUST use defined
  lifecycle operations rather than editing status directly.
- **PRD-016:** Users MUST NOT permanently delete requests, work logs, or audit events
  through the UI.
- **PRD-017:** Every successful protected action MUST record the acting user and time.
- **PRD-018:** Failed authorization MUST leave persistent state unchanged.

## Domain glossary

| Term | Meaning |
|---|---|
| Request | A reported facilities problem and its lifecycle data |
| Request owner | The existing Requester account to which a request belongs, including a request recorded by a Manager on that account's behalf |
| Recording actor | The authenticated account that creates a request; this may be the Request owner or a Manager acting on the owner's behalf |
| Reported urgency | The Requester's assessment at creation time |
| Priority | The Facilities Manager's operational priority |
| Category catalogue | The documented set of valid maintenance categories and explicit rename/removal mappings used to validate and safely migrate request categories |
| Assignment | The currently responsible Technician and assignment time |
| Work log | A Technician's timestamped internal note and minutes spent |
| Requester update | Follow-up information visible to the Requester and Manager |
| Audit event | Immutable security/business record of an important action |
| Operational log | Diagnostic application record intended for maintainers |

## Product-level acceptance

The MVP is product-complete only when a clean installation seeds its documented
demo workspace, executes every valid lifecycle transition, blocks representative
unauthorized operations, restarts without data loss, shows the Manager overview,
and produces diagnostic/audit evidence without a crash.
