# Request lifecycle and data specification

Status: **Baseline with team-confirmed integration amendments, 20 September 2026**

## Request identity and fields

- **LIF-001:** Each request MUST have an immutable internal identifier and a unique,
  immutable display ID in the form `FF-` followed by six digits, for example
  `FF-000123`.
- **LIF-002:** The system MUST generate display IDs; users MUST NOT choose or edit them.
  IDs MUST use a database-generated sequence displayed as `FF-000001` through
  `FF-999999`. Gaps after failures are permitted. Creation beyond this range MUST
  fail safely without a committed request or creation audit event; IDs MUST NOT
  wrap around or collide. Allocate identity within the creation transaction.
- **LIF-003:** A request MUST store its Requester owner, title, description,
  location, category, reported urgency, manager priority, status, current assignee,
  creation time, last-updated time, and applicable completion/closure metadata. The
  audit trail identifies the authenticated recording actor when a Manager records
  the request on behalf of the owner.
- **LIF-004:** User-entered text MUST be trimmed before validation and storage while
  preserving meaningful internal whitespace and line breaks.
- **LIF-005:** Request creation MUST satisfy all field rules below.
  Title, description, and location lengths count Unicode code points after
  trimming, not UTF-16 code units or grapheme clusters. A supplementary emoji
  counts as one code point; combining marks and joined emoji sequences may
  contain multiple code points. Validation MUST preserve the entered text.

| Field | Rule |
|---|---|
| Title | Required; 5–100 characters after trimming |
| Description | Required; 10–2,000 characters after trimming |
| Location | Required; 2–120 characters after trimming |
| Category | Required value from the configured category catalogue; the initial catalogue is Electrical, Plumbing, HVAC, Structural, Cleaning, Safety, Other |
| Reported urgency | Required enum: Low, Normal, High, Emergency |
| Manager priority | Null until triage; then Low, Medium, High, Critical |
| Assignment | Null in `OPEN`; active Technician required in assigned/work/review states |
| Resolution summary | Required for transition to `COMPLETED`; 10–2,000 characters |
| Transition/cancellation reason | Required when specified below; 5–500 characters |

The category catalogue is a confirmed developer-managed configuration decision.
Reported urgency is the Requester's input; priority is the Manager's operational
decision. The UI MUST label them distinctly.

## Supporting records

- **LIF-006:** A Requester update MUST store request ID, author, 1–1,000 characters
  of text, and creation time.
- **LIF-007:** A work log MUST store request ID, Technician author, 1–1,000
  characters of text, whole minutes from 1 through 1,440, and creation time.
- **LIF-008:** Work logs MUST be internal and MUST NOT appear in Requester views.
- **LIF-009:** Requester updates MUST be visible to the owning Requester, assigned
  Technician, and Manager.
- **LIF-010:** User-facing chronological history MUST distinguish status events,
  requester updates, and work logs according to role visibility.

## States

| State | Meaning | Assignment rule |
|---|---|---|
| `OPEN` | Submitted and awaiting managerial triage | No assignee |
| `ASSIGNED` | Prioritized and assigned; work not currently started | Active Technician required |
| `IN_PROGRESS` | Assigned Technician is actively working | Active Technician required |
| `COMPLETED` | Technician submitted work for review | Last assignee retained |
| `CLOSED` | Manager accepted completed work | Last assignee retained; read-only |
| `CANCELLED` | Work should no longer proceed | Historical assignee may be retained; read-only |

## Allowed transitions

Any transition not listed here MUST be rejected.

| From | To | Actor/action | Additional conditions |
|---|---|---|---|
| none | `OPEN` | Requester creates | Valid request data |
| none | `OPEN` | Manager records on behalf | Valid request data and an existing Requester owner |
| `OPEN` | `CANCELLED` | Owning Requester cancels | Valid reason |
| `OPEN` | `ASSIGNED` | Manager assigns | Priority and active Technician required |
| `OPEN` | `CANCELLED` | Manager cancels | Valid reason |
| `ASSIGNED` | `IN_PROGRESS` | Current Technician starts | Assignment unchanged |
| `ASSIGNED` | `ASSIGNED` | Manager reassigns | Different active Technician and valid reason |
| `ASSIGNED` | `CANCELLED` | Manager cancels | Valid reason |
| `IN_PROGRESS` | `ASSIGNED` | Manager reassigns | Active Technician and valid reason |
| `IN_PROGRESS` | `COMPLETED` | Current Technician completes | Work log and resolution required |
| `IN_PROGRESS` | `CANCELLED` | Manager cancels | Valid reason |
| `COMPLETED` | `CLOSED` | Manager accepts | Completion data remains present |
| `COMPLETED` | `ASSIGNED` | Manager returns work | Active Technician and valid reason |
| `CLOSED` | `ASSIGNED` | Manager reopens | Active Technician and valid reason |

- **LIF-011:** Every transition MUST be performed through a named service operation,
  validate the actor and current persisted state, and write one audit event.
- **LIF-012:** The state update and corresponding audit event MUST commit in one
  database transaction or both roll back.
- **LIF-013:** An edit that does not change status MUST update `updatedAt` and create
  an audit event describing the action without storing passwords or unnecessary
  full text values. This includes a permitted Manager correction.
- **LIF-014:** A return/reopen operation MUST retain prior completion metadata for
  audit and reporting while allowing a later completion attempt.
- **LIF-015:** A closed or cancelled request MUST reject Requester updates, work
  logs, Requester edits or cancellation, and normal transitions except the defined
  Manager reopen action from `CLOSED` and the Manager correction in LIF-023.
- **LIF-016:** Service operations MUST re-read and validate the current state before
  writing so stale screens cannot bypass transition or assignment rules.

## Ordering and search

- **LIF-017:** Status and enum filters MUST use stable enum values rather than
  matching display text.
- **LIF-018:** Text search MUST be case-insensitive and treat surrounding whitespace
  in the query as insignificant.
- **LIF-019:** Empty search/filter results MUST be a valid result, not an error.
- **LIF-020:** List ordering MUST be deterministic; ties MUST fall back to display ID.

## Category catalogue and Manager corrections

- **LIF-021:** The User Guide and Developer Guide MUST document the category
  catalogue and its rename/removal mapping contract; the current contract is also
  recorded in [`docs/CategoryCatalogue.md`](../../docs/CategoryCatalogue.md). A
  configured category change is applied at application startup, not through a
  Manager screen.
  The catalogue MUST use one Java `.properties` file alongside the shared database,
  containing the category list and explicit rename/removal mappings, validated
  at startup before applying changes.
- **LIF-022:** A valid category rename mapping MUST migrate affected requests to the
  replacement category. A configured category removal without a replacement MUST
  migrate affected requests to `Other`. The migration and its audit events MUST be
  atomic. A malformed catalogue, duplicate category, or invalid mapping MUST stop
  startup with an actionable error and leave persistent data unchanged.
- **LIF-023:** A Manager MAY correct title, description, location, category,
  reported urgency, and Manager priority in every request state. Display ID,
  Requester owner, status, assignment history, timestamps, work logs, resolution
  summaries, and audit events MUST NOT be directly overwritten. Corrections MUST
  be audited and MUST NOT bypass named lifecycle operations.

## Transactional acceptance

If an injected storage failure occurs between a state update and audit insert,
neither change may remain after rollback. Repeating a rejected or failed command
must not create duplicate request, work-log, or audit records.
