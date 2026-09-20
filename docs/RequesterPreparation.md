# Requester preparation: yooplo

Status: Requester starter, 14 September 2026. A Gradle/JavaFX form preview and
validation tests are now present; login and persistence are not implemented.
Team-agreed ownership, confirmed by yooplo on
14 September 2026: Requester — yooplo; Technician — ngkhengyang; Facilities
Manager — yu-sutong. Shared design decisions and generated documentation still
need team review.

## Start here

1. Install JDK 25, set `JAVA_HOME`, and open the repository root as a Gradle
   project in your IDE. Select JDK 25 for both the project and Gradle JVM.
2. Run `./gradlew.bat test`, `./gradlew.bat check`, then `./gradlew.bat run`
   in PowerShell. Use `sh ./gradlew` on macOS/Linux; headless Linux requires
   `xvfb-run -a sh ./gradlew test` for the focused JavaFX test.
3. Try the blank form, then enter valid details and select category/urgency.
   The preview must keep your input and explicitly say nothing was saved.
4. Read `RequestDraft`, `RequestValidator`, and their tests, then `RequesterForm`.
   The [Developer Guide](DeveloperGuide.md) links each file and explains its role.
5. Ask ngkhengyang and yu-sutong to review the shared build and proposed model/
   validator before building on it. Agree who supplies the session, category
   catalogue, and request repository. Resolve the authentication discrepancy below.
6. Your first complete feature is **create and view my own request**: implement
   the authenticated service operation and atomic request/audit transaction, test
   rollback and cross-user denial, then wire the form to it and add list/detail.
   Only then replace the preview entry point with authenticated role navigation.

The preview contains no fake save, hardcoded logged-in account, or database.
Do not treat its successful field check as permission to create a request.

## Scope and reading order

You own the complete Requester slice: JavaFX screens, service operations,
storage integration, permission checks, tests, and documentation.

Read [Requester requirements](../specs/roles/requester.md), then the shared
[lifecycle](../specs/components/request-lifecycle.md),
[authentication](../specs/components/authentication-authorization.md),
[persistence](../specs/components/persistence-observability.md), and
[UI](../specs/components/user-interface.md) contracts. Use the
[acceptance matrix](../specs/acceptance-test-matrix.md) to track verification.

## Shared preparation before feature implementation

- [x] Confirm team role ownership: yooplo (Requester), ngkhengyang (Technician),
  and yu-sutong (Facilities Manager).
- [ ] Have the team review relevant shared specifications and assign owners for
  shared setup work.
- [x] Add Java 25, JavaFX, Gradle wrapper, JUnit 5, Checkstyle, JaCoCo, and a
  three-OS build/test workflow. See the starter session log for executed checks.
- [ ] Obtain passing CI results on Windows, Linux, and macOS after pushing a PR.
- [ ] Add SQLite JDBC with reviewed migrations and transaction integration tests
  when implementing persistence. The preview has no storage dependency.
- [ ] Agree the base package, shared request/account model, session contract,
  service operations, repository schema/migrations, and atomic audit writes.
  Share these components across roles; controllers must call services, not SQL.
- [ ] Agree how login routes to your Requester screens and how protected calls
  reject inactive sessions. Use two Requester accounts in test/demo data so
  ownership isolation can be demonstrated (AUT-007, AUT-018–022).
- [ ] Agree the category configuration format/location using the existing
  [catalogue contract](CategoryCatalogue.md), plus list ordering and date-range
  boundary/time-zone semantics before implementing filters.
- [ ] Resolve the password-policy discrepancy before authentication work:
  AUT-004 requires at least 10 characters and three character classes, while the
  [12 September session log](../logs/2026-09-12-requirements-grilling-and-documentation.md)
  records 8–24 characters without composition rules. Record the team's decision
  deliberately; this checklist does not change either source.
- [ ] Assign a shared packaging spike and clarify monitoring evidence with the
  teaching team, as tracked in the specification index.

Repository setup and test-harness preparation can proceed while team decisions
are pending. Do not implement behavior whose essential contract is unresolved.
Shared setup is team work; taking Requester ownership does not automatically
assign all infrastructure and authentication work to yooplo.

## Suggested small pull requests

Create topic branches from `master`, for example `yooplo/requester-create`, and
request a teammate's review before merging. The following are planned work,
not features currently available.

| Order | Deliverable | Requirements |
|---|---|---|
| 1 | After shared scaffold: submit a validated request, atomically persist it and its audit event, show it in the owner's list/detail, and verify reload after restart | REQ-002–003, REQ-006, REQ-010–012; LIF-001–005; E2E-003–004, E2E-016 |
| 2 | Edit own `OPEN` requests and cancel with confirmation and a valid reason; recheck persisted state before writing | REQ-007–008; LIF-011–016; E2E-011 |
| 3 | Follow-up updates and requester-visible history, excluding internal notes; cover Manager-created requests owned by this Requester | REQ-009–010, REQ-014; LIF-006, LIF-008–010; E2E-025 |
| 4 | Case-insensitive search, status/category/date filters, reset and empty states, then dashboard counts and recent own requests | REQ-001, REQ-004–005; LIF-017–020; UIX-013–015 |

Enforce ownership and role checks from the first PR, including direct service
calls (AUT-018–021, REQ-013). Derive owner and recording actor from the session
for Requester creation. Filter private history before returning it to the UI.
Keep shared validation in services and domain code so Manager entry uses the
same field rules. Finish the full workflow before decorative dashboard work.

## Verification and handoff

- [ ] Test valid creation and field boundaries, trimming, enum/category validation,
  and safe handling of storage failure with preserved form input.
- [ ] Test Requester A cannot list, read, edit, cancel, or update Requester B's
  requests; unauthenticated and wrong-role service calls fail without disclosure.
- [ ] Test edits/cancellation are allowed only in `OPEN`, follow-ups are rejected
  in `CLOSED` and `CANCELLED`, and stale screens cannot bypass state checks.
- [ ] Test internal notes never reach Requester results, while Manager-recorded
  requests retain their owner's normal rights.
- [ ] Test transaction rollback with an isolated temporary SQLite database,
  persistence after restart, search/filter results, and accurate dashboard counts.
- [ ] Add focused UI/system checks for role routing, form recovery, cancellation
  confirmation, and keyboard access. Cite requirement IDs in tests and PRs.
- [ ] Before each PR, run `./gradlew.bat test` and `./gradlew.bat check`
  on Windows (or `./gradlew test` and `./gradlew check` on macOS/Linux).
- [ ] Update UserGuide.md and DeveloperGuide.md as functionality becomes real;
  record each substantial AI session under `logs/` with human review pending
  until a team member actually verifies it.

Agree a substantial team-level contribution in addition to the role slice.
One candidate is helping develop and evaluate the required role-permission test
generator skill using Requester isolation and transition cases; ownership of
that contribution is not assigned by this checklist.
