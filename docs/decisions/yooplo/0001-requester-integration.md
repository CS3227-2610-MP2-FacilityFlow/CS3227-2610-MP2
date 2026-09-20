# Decision 0001: Integrate Requester creation, list, and detail

- Owner: `yooplo`
- Date: 20 September 2026
- Status: Proposed for team review; implementation not started
- Affected collaborators: `yu-sutong` (Manager), `ngkhengyang` (Technician)

## First milestone

An authenticated Requester creates a request, sees it in their own list/detail,
and can reload it after restart and login. A Manager sees that same persisted
request and assigns it through the existing service. The Requester then sees
the assigned state without gaining access to internal notes.

This implements parts of REQ-002–003, REQ-006, REQ-010–012 and AUT-018–022,
with LIF-001–005, LIF-011–012 and DAT-001–010. Search/filtering, dashboard counts,
edit/cancel, and follow-ups are subsequent increments, not dropped requirements.

## Existing foundation and gaps

The Requester preview uses `facilityflow`; the Manager foundation uses
`sg.edu.nus.facilityflow`. Both define a reported-urgency enum. The Requester
validator checks fields but does not authorize or save.

`AuthenticatedSession` currently holds only an account ID. It is not a login
implementation or evidence that an arbitrary caller has authenticated.
`ManagerAssignmentStore` has transaction callbacks, account/request reads,
request updates, and audit insertion, but no request creation or owner-scoped
query operation. `SQLiteManagerAssignmentStore.initializeSchema()` provides
initial tables, not versioned migrations. The audit model/schema must also be
reviewed against DAT-015–019 before adding Requester creation events.

## Proposed integration contract

| Area | Proposal | Review needed |
|---|---|---|
| Packages/model | Move Requester classes under `sg.edu.nus.facilityflow`; reuse shared request/status/urgency/account types; retain the input-only RequestDraft and pure validator | Both role owners; preserve all existing Manager tests |
| Session | Login supplies the shared session; each Requester service call rechecks the persisted account's active flag and role; auth owns session invalidation | Shared auth owner must be assigned; clarify password-reset invalidation |
| Services | Add createRequest(session, draft), listOwnRequests(session), getOwnRequest(session, requestId); no owner or actor supplied by the form | Names/signatures and safe error contract to confirm |
| Persistence | Evolve the existing transaction boundary for creation and owner-scoped reads against the same database, with prepared statements and rollback | Coordinate with yu-sutong; agree interface ownership/name before changing it |
| Creation | Validate with the production catalogue, derive owner/actor from session, generate identity/timestamps, set OPEN with null priority/assignee, and commit request plus audit together | Agree ID allocation, display-ID exhaustion handling, and audit representation |
| Read visibility | Check role and ownership before returning data; return only requester-visible history, even for guessed IDs | Confirm the shared history representation; UI hiding alone is insufficient |
| JavaFX | Shared login routes to Requester navigation; database work runs off the JavaFX thread; disable submit while pending, retain input on failure, refresh only after commit | Agree routing/session-expiry callbacks with the auth owner |

The application must not manufacture a session or add a permanent role picker
to make integration appear complete. Test fixtures may supply sessions/accounts
in isolated tests; they do not replace real login or demo seeding.

## Decisions to settle before dependent implementation

- [ ] Both role owners review the package/model and transaction-boundary proposal.
- [ ] Assign owners for shared authentication/routing, migrations, catalogue, and
  atomic demo seeding. No teammate is assigned new work by this proposal.
- [ ] Define session trust and invalidation, including deactivation, role changes,
  and password reset. Re-reading an account alone does not detect password reset.
- [ ] Resolve AUT-004 versus the 12 September requirements log's password policy
  before implementing login. Record the agreed policy in the spec deliberately.
- [ ] Agree versioned schema migration and the path to reuse the same database
  across all roles without deleting existing requests or audit records.
- [ ] Agree atomic ID allocation and audit fields (actor, target, time, action,
  safe details), including failure/rollback and display-ID uniqueness.
- [ ] Agree production category configuration format/location and startup wiring.
- [ ] Agree deterministic own-list ordering and requester-visible history fields.
  Date-filter boundaries can be settled with the later filtering increment.

Only decisions essential to a particular increment block that increment. Review
and test planning can proceed now; do not mark this proposal approved or claim
team agreement until reviewers actually confirm it.

## Implementation sequence after the relevant review

1. Consolidate Requester packages and urgency type, adjust launcher/test imports,
   and run both role suites to establish no behavior regression.
2. Implement the agreed storage extensions/migrations and create/list/detail
   services. Test authorization, generated identity, owner-scoped results, audit
   rollback, and restart against isolated SQLite databases before UI wiring.
3. Connect authenticated Requester navigation and form/list/detail. Add focused
   tests for routing, duplicate-click prevention, safe errors, and retained input.
4. Run the Requester-to-Manager handoff against one database. Update guides with
   verified behavior and record CI/test evidence before requesting PR review.

Use small PRs on topic branches; no shared API rename or schema replacement
should silently break the incoming Manager slice. The current preview remains
available while authentication integration is incomplete.

## Review record

| Reviewer | Decision/date | Notes |
|---|---|---|
| yooplo | Pending | Review generated proposal and Requester scope |
| yu-sutong | Pending | Shared storage, audit, model, and Manager handoff |
| ngkhengyang | Pending | Shared model, session, and later Technician handoff |
