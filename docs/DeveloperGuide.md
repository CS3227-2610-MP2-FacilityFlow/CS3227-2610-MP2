# FacilityFlow Developer Guide

Status: Requester backend and Manager foundation, updated 22 September 2026. This guide describes
the scaffold in this branch, not a released maintenance-management application.

## Setup and commands

Use JDK 25. Set `JAVA_HOME` to its directory (not `bin`) and select that JDK in
your IDE's Gradle settings. Obtain it from the
[Eclipse Temurin downloads](https://adoptium.net/temurin/releases/?version=25).
In PowerShell, for a JDK installed at a path you supply:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
& "$env:JAVA_HOME/bin/java.exe" -version
./gradlew.bat test
./gradlew.bat check
./gradlew.bat run
```

On macOS/Linux, set `JAVA_HOME` for your installed JDK and use `sh ./gradlew`
with the same task names. A desktop display is required for the UI test. On
headless Linux use `xvfb-run -a sh ./gradlew check` with Xvfb installed.
Gradle and JavaFX download automatically on first use, which requires network
access. Do not add local JDK paths or IDE state to the repository.

The build pins Gradle 9.1.0, JavaFX 25.0.2, the OpenJFX plugin 0.1.0,
JUnit 5.13.4, SQLite JDBC 3.50.3.0, Checkstyle 10.26.1, and JaCoCo 0.8.14. The wrapper verifies the
Gradle distribution SHA-256. Gradle's
[compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)
lists Java 25 support from Gradle 9.1.0.

The incoming Foojay resolver is retained to download a Java 25 toolchain when
needed. A compatible Java runtime is still required to start Gradle itself.

The default `run` task launches the Requester preview. To launch the incoming
application shell (an integration notice, not an authenticated Manager screen):

```powershell
./gradlew.bat run "-PmainClass=sg.edu.nus.facilityflow.FacilityFlowApplication"
```

## Implemented structure

| File | Responsibility |
|---|---|
| [RequesterPreviewLauncher](../src/main/java/sg/edu/nus/facilityflow/RequesterPreviewLauncher.java) | Development entry point; starts JavaFX without impersonating an account |
| [RequesterPreview](../src/main/java/sg/edu/nus/facilityflow/ui/requester/RequesterPreview.java) | Window and initial-category preview fixture |
| [RequesterForm](../src/main/java/sg/edu/nus/facilityflow/ui/requester/RequesterForm.java) | Input controls and field feedback; no SQL, authentication, or persistence |
| [RequestDraft](../src/main/java/sg/edu/nus/facilityflow/model/RequestDraft.java) | Normalized, still-untrusted input; no caller-supplied ownership or status |
| [ReportedUrgency](../src/main/java/sg/edu/nus/facilityflow/model/ReportedUrgency.java) | The four reported urgency values from LIF-005 |
| [RequestValidator](../src/main/java/sg/edu/nus/facilityflow/service/RequestValidator.java) | Field validation against a supplied catalogue, independent of JavaFX |
| [RequesterRequestService](../src/main/java/sg/edu/nus/facilityflow/service/RequesterRequestService.java) | Requester authorization, validation, atomic creation/audit, and owner-only list/detail |
| [SchemaMigrations](../src/main/java/sg/edu/nus/facilityflow/storage/SchemaMigrations.java) | Ordered versions 1–2 and schema compatibility checks |
| [RequestValidatorTest](../src/test/java/sg/edu/nus/facilityflow/service/RequestValidatorTest.java) | Boundary, missing-value, catalogue, and trimming checks |
| [RequesterFormTest](../src/test/java/sg/edu/nus/facilityflow/ui/requester/RequesterFormTest.java) | Real JavaFX controls: input retention and error correction |

Requester and Manager code now share `sg.edu.nus.facilityflow` and the same
`ReportedUrgency` enum. The Requester form supplies its own display labels;
`RequestDraft` remains input-only and `RequestValidator` remains pure validation.
The Manager foundation provides session identity and storage components, which
are not yet wired to the Requester preview.
A valid draft is not an authorized or persisted request. `RequesterRequestService`
rechecks the session account's persisted active flag and role, derives owner/actor
from that account, validates the draft, and commits the request plus audit
atomically. The session record is still only an identity carrier: login issuance,
logout and Manager-reset invalidation are not implemented. Do not construct
sessions from UI-supplied account IDs to bypass that integration gap.
Follow [the lifecycle specification](../specs/components/request-lifecycle.md)
and [persistence contract](../specs/components/persistence-observability.md).

The preview category list is a fixture, not a configuration implementation.
The validator accepts the catalogue as input to allow later integration without
duplicating field rules. The [category catalogue contract](CategoryCatalogue.md)
still governs production startup, rename/removal mapping, and atomic migration.

## Manager foundation architecture

The package boundaries follow `AGENTS.md`:

- `model` contains immutable request, account, audit, status, urgency, and
  priority values.
- `auth` contains the authenticated-session identity passed to protected use
  cases.
- `service` owns Manager authorization, validation, queue ordering, and the
  named assignment transition.
- `storage` owns the transaction interface, schema foundation, and SQLite/JDBC
  implementation. It does not depend on JavaFX.
- `ui.manager` contains the Manager JavaFX view and thin presentation adapter.
  It contains no SQL or lifecycle rules.

`ManagerRequestService.assignOpenRequest` implements the first complete use
case. It validates the persisted actor, request state, priority, and assignee
inside one storage transaction. It then updates the request and appends one
`REQUEST_ASSIGNED` audit event. Any runtime or database failure rolls the
transaction back.

The Manager queue uses the deterministic ordering in MGR-021. The initial view
shows the queue, request detail, active-Technician selection, Manager-priority
selection, actionable feedback, visible identity/role, and logout action. It is
an injectable component rather than a route that bypasses authentication.

## Database foundation

`SQLiteManagerAssignmentStore.initializeSchema()` runs `SchemaMigrations` in one
transaction, using SQLite `PRAGMA user_version`:

- Version 1 creates/adopts the original `user_accounts`, `maintenance_requests`,
  and `audit_events` tables. Existing unversioned Manager databases upgrade in place.
- Version 2 adds a singleton `request_identity_sequence`, an owner index, and
  request audit target metadata. The sequence starts above existing internal and
  display IDs; an insert trigger advances it for explicitly seeded IDs too.
- Versions newer than 2, missing required columns, and missing/invalid sequence
  rows stop initialization with a safe error. Failed migrations roll back schema,
  data, and version together; repeated initialization does not reset data.

Creation increments the database sequence in the same transaction as request and
audit insertion. SQLite formats `FF-000001` through `FF-999999`; exhaustion fails
without partial writes. New requests use `OPEN`, null priority/assignee, and the
injected clock's UTC Instant for both timestamps. Creation audit details contain
only structured status metadata, not title, description, or location.

The existing request-only audit schema exposes `target_type = REQUEST` and
`target_id = request_id` as generated columns. Existing audit IDs/details remain
unchanged. Account/login/category audit targets will require a later migration;
this increment does not implement all of DAT-016. Yu-sutong remains the migration
owner and should review these schema changes before merging. Demo seeding,
application-data path resolution, and category startup configuration are pending.

The transaction interface is deliberately callback-based. Services re-read the
persisted state within the callback before writing, while SQLite controls the
commit or rollback. Other role services should reuse or evolve this shared
boundary rather than adding SQL to controllers.

`RequesterRequestService.createRequest(session, draft)`, `listOwnRequests(session)`
and `getOwnRequest(session, requestId)` reuse that transaction boundary. Owner reads
are scoped in SQL and checked in the service. Lists order by creation Instant
descending, then display ID ascending; absent and inaccessible details have the
same safe error. Returned records contain no audit details or internal notes.
Visible activity history under REQ-017 is not implemented yet. The configured
category set is supplied through `RequestValidator`; the production loader remains
separate work. No JavaFX controller calls these new operations yet.


Manager service tests cover valid assignment, invalid/inactive assignees,
unauthorized actors, invalid state, and missing priority. SQLite tests verify
that an injected audit failure rolls back request state and audit writes.

`SQLiteRequesterRequestServiceTest` covers valid/invalid creation, all three
operations' role checks, live deactivation/role changes, owner isolation and
ordering, reopening the database, Manager handoff, request/audit failure rollback,
ID exhaustion, foreign keys, and safe read errors. `SchemaMigrationsTest` covers
fresh/repeated startup, legacy preservation, seed identity advancement, rollback,
and incompatible schemas. Every integration test uses a temporary database;
session fixtures do not claim successful login or password-reset invalidation.

## Verification and CI

Request title, description, and location validation counts Unicode code points
after trimming (LIF-005), not UTF-16 units or visual grapheme clusters. Tests
cover supplementary emoji boundaries and combining marks without normalization.

JavaFX tests share `sg.edu.nus.facilityflow.ui.JavaFxTestSupport.runOnFxThread`. It starts
or reuses the toolkit, disables implicit exit, and returns assertion failures to
JUnit. Do not call `Platform.exit()` in individual tests; the Gradle test-worker
JVM owns shutdown. Tests should close their own windows without ending JavaFX.

`test` runs domain/service and focused JavaFX tests and generates coverage.
`check` also runs Checkstyle. `build` adds compilation and development
distributions. Reports are written to:

- `build/reports/tests/test/index.html`
- `build/reports/jacoco/test/html/index.html`
- `build/reports/checkstyle/main.html` and `test.html`

The [build workflow](../.github/workflows/build.yml) runs on PRs and pushes to
`master` with Windows, macOS, and Linux jobs and uploads reports. Linux uses a
virtual display. Configuring CI is not evidence that all OS jobs have passed;
record actual run links before claiming cross-platform verification.

Checkstyle currently enforces a small baseline (imports, braces, tabs, and file
structure). Coverage is reported, not threshold-gated. Extend the checks with
the team as the codebase grows; the QLT-007 service/domain coverage target still
applies. The form smoke test does not verify login, storage recovery, the full
workflow, accessibility, or visual layout across operating systems.

The current classpath-based JavaFX test emits an upstream warning that JavaFX
classes are loaded from an unnamed module; the control test passes. Module and
runtime-image packaging choices remain part of the shared release spike.

## Confirmed integration responsibilities and behavior

Team agreement reported by yooplo on 20 September 2026:

- yooplo owns login, session management, and routing; yu-sutong owns versioned
  migrations, demo seeding, and category configuration/migrations.
- Passwords use 8–24 characters without composition rules and remain salted/hashed.
- Role changes retain sessions, reauthorize each operation, and reroute; own-password
  changes retain the current session. Deactivation and Manager resets require login
  on the next protected operation (AUT-022, AUT-030, AUT-032–033).
- All app roles share one SQLite database in the OS user's application-data
  directory. Each integration test gets a separate temporary database.
- The category catalogue is one Java .properties file beside that database.
  See [CategoryCatalogue.md](CategoryCatalogue.md) for validation/migration rules.
- Display IDs are database-generated FF-000001 through FF-999999, with gaps allowed
  and safe rejection at exhaustion. Audit records omit full request text/location.
- Requester lists use creation descending/display ID ascending; date filters are
  inclusive local dates; history includes status/reasons/follow-ups but no private notes.

These are implementation contracts, not claims of implemented or released features.
Exact configuration filenames/keys and schema/session-version details are to be
recorded when implemented, consistently with the confirmed requirements.

## Next integration and release work

The [confirmed Requester integration decision](decisions/yooplo/0001-requester-integration.md)
defines the next create/list/detail milestone and team-agreed shared contracts.
In particular, the existing session record does not implement authentication,
while the shared storage interface now supports creation and owner-only reads.
The backend handoff is tested; the authenticated UI milestone remains incomplete.

Follow [RequesterPreparation.md](RequesterPreparation.md) for the ordered tasks.
Implement the confirmed account/session contract, then wire create/list/detail
to authenticated navigation. Add edit/cancel, visible history, and filters next.
The Manager slice still needs authenticated routing, search/filter/reset,
the remaining transitions, account administration, summaries, and audit browsing.

Operational logging, production error boundaries, demo accounts,
release installers, and cross-platform launch verification are not implemented.
Gradle development distributions use host-specific JavaFX libraries and require
Java; they are not the final universal release artifact. Resolve packaging and
monitoring evidence with the team under REL-005–007 and OBS-001–007.

## Acknowledgements

The Gradle wrapper is generated by Gradle and retains its upstream license
headers. Build configuration follows the official
[Gradle documentation](https://docs.gradle.org/9.1.0/userguide/userguide.html),
[OpenJFX Gradle plugin guide](https://github.com/openjfx/javafx-gradle-plugin),
[JUnit 5 guide](https://docs.junit.org/5.13.4/user-guide/), and
[JaCoCo documentation](https://www.jacoco.org/jacoco/trunk/doc/).
JavaFX control usage was checked against the
[JavaFX 25 API](https://openjfx.io/javadoc/25/).
CI uses the official actions/checkout, actions/setup-java, gradle/actions,
and actions/upload-artifact projects. These tools and libraries retain their
respective upstream licenses. No MP1 source or assets were reused.

SQLite migration/transaction behavior was checked through Context7 and the
official [generated-column](https://www.sqlite.org/gencol.html) and
[transaction](https://www.sqlite.org/lang_transaction.html) documentation.
The legacy migration test fixture preserves this repository's pre-migration
Manager schema; it is not imported from MP1.
