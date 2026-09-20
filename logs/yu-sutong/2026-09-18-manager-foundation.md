# AI session log: Facilities Manager foundation

- Date: 18 September 2026
- Owner: `yu-sutong`
- Branch: `feature/manager-foundation`
- Human verification: **Approved by `yu-sutong` on 19 September 2026**

## Goal and prompt summary

The user stated that their role is Facilities Manager and asked the AI to follow
the repository instructions, decide what to build next, and keep separate AI
reflection and decision records.

## Instructions and skill used

- Read the updated `AGENTS.md` and the Manager, lifecycle, authentication, UI,
  quality, and acceptance-test specifications before implementation.
- Used the local `ui-ux-pro-max` guidance for a JavaFX operations interface.
- Followed the specification-first, package-boundary, authorization,
  transaction, testing, and evidence rules in `AGENTS.md`.

## Decisions and changes

- Selected E2E-005, Manager assignment, as the first vertical slice.
- Added the Gradle 9.1 wrapper, Java 25 toolchain resolution, JavaFX 25, SQLite
  JDBC, JUnit 5, JaCoCo, Checkstyle, and ignore rules.
- Added immutable account, request, audit, lifecycle, urgency, priority, role,
  and session types.
- Added `ManagerRequestService` with persisted active-Manager authorization,
  `OPEN` state validation, required Manager priority, and active-Technician role
  validation.
- Added a callback-based storage transaction and SQLite implementation so the
  request update and audit insert commit or roll back together.
- Added an injectable Manager queue/detail/assignment JavaFX view and thin
  controller. The application entry point shows an integration notice until the
  shared authentication flow exists; it does not hard-code a privileged session.
- Added a developer guide, updated current status in the README, and created
  separate personal decision and reflection files.

## Verification actually performed

1. The initial `./gradlew test --stacktrace` compiled main and test code but
   failed before executing tests because Gradle 9 required an explicit JUnit
   Platform launcher at runtime.
2. Added `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'`.
3. Reran `./gradlew test`: **passed**.
4. Ran `./gradlew check`: **passed** Java 25 compilation, nine JUnit tests,
   Checkstyle for main/test sources, and JaCoCo report generation.
5. After adding the deterministic queue-order test, successful SQLite commit
   test, and Java 25 native-access setting, ran `./gradlew clean check`:
   **passed** all nine tests and all checks from a clean build.

The SQLite integration test uses an isolated temporary database and an injected
trigger failure to verify rollback of both the request mutation and audit event.
The JavaFX screen was compiled but was not manually exercised through a login
flow because authentication is not yet implemented.

## Errors, corrections, and open risks

- Corrected the missing JUnit Platform launcher dependency after the first test
  run.
- The development machine's default Java is 21 and has no system Gradle. The
  wrapper and Foojay resolver downloaded the pinned Gradle distribution and
  Java 25 toolchain successfully.
- Java 25 initially reported that SQLite JDBC uses restricted native access.
  The test and application JVM settings were updated to allow native access for
  the unnamed SQLite JDBC module, and the final clean check ran without the warning.
- No authentication, full demo seeding, versioned migration framework,
  search/filter UI, cross-role integration, or UI automation exists yet.
- The password rules and Manager-recorded ownership wording inconsistencies
  already present in the specifications were not changed because they do not
  block this assignment slice.

## Outcome

The Manager assignment foundation is implemented and locally verified on this
branch. The owner reviewed and approved this log and the accompanying personal
decision/reflection records on 19 September 2026. Teammates still need to review
the shared model/transaction boundary before merge.
