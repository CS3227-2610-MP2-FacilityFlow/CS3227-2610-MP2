# Decision 0001: Integrate Requester creation, list, and detail

- Owner: `yooplo`
- Date: 20 September 2026
- Status: Team-confirmed by yooplo on 20 September 2026; implementation not started
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

## Confirmed integration contract

Yooplo explicitly confirmed that all answers from the decision interview were
agreed by the team. This is a record of that report, not separate reviewer
signatures or a claim that generated code/documentation has been reviewed.

| Area | Confirmed decision |
|---|---|
| Packages/models | Use `sg.edu.nus.facilityflow` and reuse the Manager foundation's shared request, account, status, and urgency types; retain input-only RequestDraft and pure validation |
| Storage extension | Requester work may extend the shared SQLite transaction boundary for creation and owner-only reads while preserving Manager assignment tests |
| Authentication owner | yooplo owns login, session management, and role routing in addition to Requester |
| Data/configuration owner | yu-sutong owns versioned migrations, atomic demo seeding, and category configuration/startup migrations together |
| Passwords | 8–24 characters inclusive, no composition rule; salted secure hashing remains mandatory (AUT-004–005) |
| Deactivation/reset | Reject deactivated sessions on the next protected call; Manager password reset invalidates the affected session (AUT-022, AUT-030) |
| Role changes | Retain session, recheck current persisted role on every protected action, reject now-forbidden old-role actions, and route to the current role without login (AUT-032) |
| Own password change | Keep the user's current session (AUT-033) |
| Own-list ordering | Creation time descending, display ID ascending for ties (REQ-015) |
| Date filters | Inclusive start/end dates in the app's local time zone (REQ-016) |
| Visible history | Status changes, cancellation/reopening reasons, and own follow-ups; no internal work logs/private Manager notes (REQ-017) |
| IDs | Database-generated sequence, FF-000001 through FF-999999; gaps permitted; reject exhausted range without partial writes (LIF-002) |
| Creation audit | Actor, request ID, UTC timestamp, action, and existing DAT-015 identity/target metadata; exclude full title, description, and location (DAT-019) |
| Database | One shared local database for all app roles under the OS user's application-data directory; separate temporary database per integration test (DAT-001, DAT-010) |
| Catalogue | One Java .properties file beside the database with categories and rename/removal mappings, validated at startup (LIF-021–022) |

The earlier suggestion to allocate categories to ngkhengyang was not adopted.
Category configuration stays with yu-sutong's database responsibilities.

## Implementation boundaries

Start with createRequest(session, draft), listOwnRequests(session), and
getOwnRequest(session, requestId). The form must not supply owner, actor, status,
or generated identity. Login supplies a trusted session; services re-read the
persisted active flag and role and enforce ownership. Atomic creation includes
validation, ID allocation, OPEN status, null priority/assignee, timestamps, and
one creation audit event. Neither write remains after rollback.

Database work runs off the JavaFX thread; pending submission disables repeated
clicks, failed saves retain input, and successful saves refresh persisted data.
Do not manufacture sessions or introduce a permanent role picker as a login
replacement. Isolated test fixtures do not establish real authentication.

The password-policy discrepancy is resolved in AUT-004. The team decisions
above are no longer review blockers. Implementation still needs concrete schema
versions, configuration filenames/property keys, and a way to detect Manager
password-reset invalidation; reading the account's role alone cannot detect it.
Document these details as implemented and preserve existing data and tests.

## Implementation sequence

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

## Confirmation record

- Source: yooplo's explicit statement, "all are confirmed by the team", in the
  20 September 2026 decision interview.
- Scope: shared package/models and storage extension, responsibilities, password
  and session behavior, ordering/filtering/history, IDs/audit, and data/config location.
- This confirmation supersedes the earlier pending decision/reviewer placeholders.
- Human review of the resulting documentation and future code remains required;
  no individual teammate signature or implementation completion is asserted.
