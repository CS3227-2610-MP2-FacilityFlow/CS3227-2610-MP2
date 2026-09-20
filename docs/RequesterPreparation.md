# Requester preparation: yooplo

Status: integration planning, 20 September 2026. A Gradle/JavaFX form preview and
validation tests are now present. The merged Manager foundation supplies SQLite
storage and session identity; Requester persistence and shared login are not integrated.
Team-agreed ownership, confirmed by yooplo on
14 September 2026: Requester — yooplo; Technician — ngkhengyang; Facilities
Manager — yu-sutong. Integration decisions were confirmed on
20 September 2026. Generated documentation and future code still need human review.

## Start here

The next milestone is **authenticated create/list/detail with Manager handoff**.
Start with the [confirmed integration decision](decisions/yooplo/0001-requester-integration.md).
Yooplo owns authentication/session/routing as well as Requester. Yu-sutong owns
migrations, demo seeding, and category configuration together. The decisions are
agreed; feature implementation is still outstanding.

1. Install JDK 25, set `JAVA_HOME`, and open the repository root as a Gradle
   project in your IDE. Select JDK 25 for both the project and Gradle JVM.
2. Run `./gradlew.bat test`, `./gradlew.bat check`, then `./gradlew.bat run`
   in PowerShell. Use `sh ./gradlew` on macOS/Linux; headless Linux requires
   `xvfb-run -a sh ./gradlew test` for the focused JavaFX test.
3. Try the blank form, then enter valid details and select category/urgency.
   The preview must keep your input and explicitly say nothing was saved.
4. Read `RequestDraft`, `RequestValidator`, and their tests, then `RequesterForm`.
   The [Developer Guide](DeveloperGuide.md) links each file and explains its role.
5. Consolidate Requester packages/models under `sg.edu.nus.facilityflow` and
   coordinate storage changes with yu-sutong. Follow the confirmed session and
   password contract; preserve the existing Manager tests.
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
- [x] Confirm integration decisions and shared owners, as reported by yooplo.
- [ ] Human-review the resulting documentation and implementation changes.
- [x] Add Java 25, JavaFX, Gradle wrapper, JUnit 5, Checkstyle, JaCoCo, and a
  three-OS build/test workflow. See the starter session log for executed checks.
- [ ] Obtain passing CI results on Windows, Linux, and macOS after pushing a PR.
- [ ] Integrate Requester persistence with the incoming SQLite foundation;
  JDBC and Manager rollback tests now exist, but versioned migrations and
  Requester transaction tests still need implementation.
- [x] Confirm shared package/models and creation/owner-read storage extensions.
- [x] Confirm login/session/routing ownership and session rules (AUT-032–033).
- [x] Confirm category properties-file format/location, owner, and startup rules.
- [x] Confirm own-list ordering, inclusive local dates, and visible history
  (REQ-015–017).
- [x] Resolve password discrepancy: AUT-004 now requires 8–24 characters and no
  composition rule; AUT-005 hashing still applies.
- [x] Confirm database location, sequential six-digit IDs, and creation audit fields.
- [ ] Implement the agreed schema migrations, catalogue loader, and session-reset
  detection; record concrete filenames/property keys and schema versions.
- [ ] Assign a shared packaging spike and clarify monitoring evidence with the
  teaching team, as tracked in the specification index.

Begin the package/model consolidation next, then implement the agreed services
and storage integration. Shared responsibilities follow the confirmed ownership
above; packaging and monitoring questions remain separate release work.

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

Your confirmed team-level contribution is login, session management, and role
routing. The required agent-skill development/evaluation work still needs to be
tracked separately; this decision does not assign all three skills to you.
