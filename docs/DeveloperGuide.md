# FacilityFlow Developer Guide

Status: authenticated Requester UI, Manager assignment, and Technician backend,
updated 24 September 2026. This development build is not a complete released
maintenance application.

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

The default `run` task launches `FacilityFlowLauncher`, which starts the real
login flow. To launch the older validation-only preview instead:

```powershell
./gradlew.bat run "-PmainClass=sg.edu.nus.facilityflow.RequesterPreviewLauncher"
```

## Implemented structure

| File | Responsibility |
|---|---|
| [FacilityFlowApplication](../src/main/java/sg/edu/nus/facilityflow/FacilityFlowApplication.java) | Background workspace startup, dependency wiring, shutdown revocation |
| [AuthenticationService](../src/main/java/sg/edu/nus/facilityflow/auth/AuthenticationService.java) | Password verification, login audits, own changes and Manager resets |
| [SessionManager](../src/main/java/sg/edu/nus/facilityflow/auth/SessionManager.java) | Opaque process-local sessions and persisted active/role/version checks |
| [ApplicationRouter](../src/main/java/sg/edu/nus/facilityflow/ui/ApplicationRouter.java) | Login, logout, role routing and own-password UI |
| [RequesterDashboardView](../src/main/java/sg/edu/nus/facilityflow/ui/requester/RequesterDashboardView.java) | Own list, submission, detail, and retained drafts |
| [Workspace](../src/main/java/sg/edu/nus/facilityflow/storage/Workspace.java) | OS-user database location and category startup validation |
| [RequesterPreviewLauncher](../src/main/java/sg/edu/nus/facilityflow/RequesterPreviewLauncher.java) | Development entry point; starts JavaFX without impersonating an account |
| [RequesterPreview](../src/main/java/sg/edu/nus/facilityflow/ui/requester/RequesterPreview.java) | Window and initial-category preview fixture |
| [RequesterForm](../src/main/java/sg/edu/nus/facilityflow/ui/requester/RequesterForm.java) | Input controls and field feedback; no SQL, authentication, or persistence |
| [RequestDraft](../src/main/java/sg/edu/nus/facilityflow/model/RequestDraft.java) | Normalized, still-untrusted input; no caller-supplied ownership or status |
| [ReportedUrgency](../src/main/java/sg/edu/nus/facilityflow/model/ReportedUrgency.java) | The four reported urgency values from LIF-005 |
| [RequestValidator](../src/main/java/sg/edu/nus/facilityflow/service/RequestValidator.java) | Field validation against a supplied catalogue, independent of JavaFX |
| [RequesterRequestService](../src/main/java/sg/edu/nus/facilityflow/service/RequesterRequestService.java) | Requester authorization, validation, atomic creation/audit, and owner-only list/detail |
| [SchemaMigrations](../src/main/java/sg/edu/nus/facilityflow/storage/SchemaMigrations.java) | Ordered versions 1–4 and schema compatibility checks |
| [TechnicianRequestService](../src/main/java/sg/edu/nus/facilityflow/service/TechnicianRequestService.java) | Technician-scoped queue, filtering, progress, work-log, completion, and stale-write checks |
| [TechnicianQueueFilter](../src/main/java/sg/edu/nus/facilityflow/model/TechnicianQueueFilter.java) | Normalized TEC-002 queue search and enum/category filters |
| [WorkLog](../src/main/java/sg/edu/nus/facilityflow/model/WorkLog.java) | Immutable internal Technician work evidence |
| [RequestValidatorTest](../src/test/java/sg/edu/nus/facilityflow/service/RequestValidatorTest.java) | Boundary, missing-value, catalogue, and trimming checks |
| [RequesterFormTest](../src/test/java/sg/edu/nus/facilityflow/ui/requester/RequesterFormTest.java) | Real JavaFX controls: input retention and error correction |

Requester and Manager code now share `sg.edu.nus.facilityflow` and the same
`ReportedUrgency` enum. The Requester form supplies its own display labels;
`RequestDraft` remains input-only and `RequestValidator` remains pure validation.
Both role services share the same trusted session registry and SQLite boundary.
A valid draft is not an authorized or persisted request. `RequesterRequestService`
rechecks the session account's persisted active flag and role, derives owner/actor
from that account, validates the draft, and commits the request plus audit
atomically. `AuthenticatedSession` has no public constructor. Only verified login
issues a session, after its audit transaction commits; fabricated, foreign-process
and revoked sessions fail even if their account ID is valid. Test-only issuers
for older fixtures live in `src/test`, not in the application.
Follow [the lifecycle specification](../specs/components/request-lifecycle.md)
and [persistence contract](../specs/components/persistence-observability.md).

The optional preview retains its category fixture. The real app loads the
`categories` comma-separated key from UTF-8 `categories.properties` beside the
database. Duplicate/blank categories, missing `Other`, and unknown keys fail
before database changes. Startup also rejects a catalogue that omits a category
already stored on a request. Rename/removal mappings remain unimplemented and
are rejected rather than ignored. See [CategoryCatalogue.md](CategoryCatalogue.md).

## Authentication and UI tasks

Passwords use the JDK `PBKDF2WithHmacSHA256` provider with 600,000 iterations,
independent 16-byte salts and 256-bit keys. Stored values are
`pbkdf2-sha256$600000$<base64-salt>$<base64-key>`. Comparisons use
`MessageDigest.isEqual`; malformed/unsupported hashes cannot authenticate.
Lengths are 8–24 Unicode code points with no composition rule or trimming.
Password inputs are masked, cleared immediately on UI submission, and supplied
char arrays/PBE key specifications are cleared after use. Unknown usernames still
perform PBKDF2 verification against a dummy value. Failed login logs contain no
username, password or hash, and create no audit event.

Services recheck `UserAccount.sessionVersion` alongside active status and role
within the protected operation's transaction. `resetPassword` is Manager-only,
targets another existing account, and increments that version atomically with
hash update and audit. `changePassword` verifies the current password and retains
the version/session. Role changes also retain the version. Logout removes the
session; application shutdown permanently closes the registry. No auto-login or
session serialization exists. The Manager account-administration UI is pending;
reset is currently a tested service API.

`UiTasks` executes JDBC/password work off the JavaFX thread and reports outcomes
on it. Pending work disables header navigation; saving disables the form and its
back action. Success opens persisted detail, and returning to the list reloads
committed data. Failures retain entered input and display safe messages. A draft
can survive an expired session in memory for the same account's next login;
explicit logout, another account's login, or process shutdown discards it.
Role changes reroute on the next denied protected action or **Refresh account**.
The Technician has a separate placeholder view. The authorized backend operations
for personal queue/detail reads, search and filters, start work, append work logs,
and completion now exist, but they are not yet connected to that view.

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

`ManagerRequestService.reassignRequest` now provides the backend transition for
`ASSIGNED` or `IN_PROGRESS` work. It requires a different active Technician and
a 5–500 character reason, returns the request to `ASSIGNED`, starts a new current
assignment timestamp, preserves work logs and completion metadata, and appends a
`REQUEST_REASSIGNED` audit. The Manager UI does not expose this operation yet.

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
- Version 3 adds `user_accounts.session_version` and rebuilds `audit_events` with
  ordinary `target_type`/`target_id` fields and nullable `request_id`, preserving
  all event IDs and details. `REQUEST` events still reference a request; `ACCOUNT`
  events support login, password operations, and initial account creation.
- Version 4 adds nullable assignment/completion metadata to `maintenance_requests`,
  the append-only application-facing `work_logs` table, deterministic work-log
  history indexes, and a Technician queue index. Legacy assignment times remain
  null rather than being inferred from the mutable request update time. The shared
  choices are recorded in [ADR 0002](adr/0002-technician-shared-data-contract.md).
- Versions newer than 4, missing required columns, and missing/invalid sequence
  rows stop initialization with a safe error. Failed migrations roll back schema,
  data, and version together; repeated initialization does not reset data.

Creation increments the database sequence in the same transaction as request and
audit insertion. SQLite formats `FF-000001` through `FF-999999`; exhaustion fails
without partial writes. New requests use `OPEN`, null priority/assignee, and the
injected clock's UTC Instant for both timestamps. Creation audit details contain
only structured status metadata, not title, description, or location.

`initializeWorkspace` applies migrations and initial account seeding in one
transaction only when no application tables exist. It creates two accounts per
role with independently salted hashes and account audits. Existing schemas are
never reseeded, even when empty. Representative lifecycle requests under AUT-008
remain pending; the initial request list is empty. `initializeSchema` remains an
unseeded migration entry point for integration tests. Yu-sutong remains the shared
migration/seeding owner and must review these changes before merge.

`Workspace.defaultDirectory()` uses `%LOCALAPPDATA%/FacilityFlow` on Windows,
`~/Library/Application Support/FacilityFlow` on macOS, and
`$XDG_DATA_HOME/FacilityFlow` (fallback `~/.local/share/FacilityFlow`) on Linux.
The database filename is `facilityflow.db`. Tests supply isolated temporary paths.
No app code stores database files in the repository.

The transaction interface is deliberately callback-based. Services re-read the
persisted state within the callback before writing, while SQLite controls the
commit or rollback. Other role services should reuse or evolve this shared
boundary rather than adding SQL to controllers.

`TechnicianRequestService` reuses this transaction interface. Every operation
revalidates the persisted session account and role, then scopes request access to
the current assignee. `TechnicianQueueFilter` supplies optional normalized search,
status, category, and priority criteria; queue results retain the TEC-003 priority,
urgency, assignment-time, and display-ID ordering. Technician state writes use a conditional request update
that includes the expected status and assignee, so a stale screen cannot write
after reassignment. Starting work commits `IN_PROGRESS` plus one request audit;
adding work commits the request timestamp, one work log, and one request-targeted
audit; completion requires an existing work log and commits `COMPLETED`, a trimmed
resolution summary, completion time, and one audit. Any failure rolls the whole
operation back. Work-log audit details contain the generated log ID and minutes,
not the free-text note.

`MaintenanceRequest.assignedAt` records the current assignment time separately
from `updatedAt`; Manager assignment sets both, while later work-log writes change
only `updatedAt`. Work-log history retains its original Technician author after
reassignment. Requester queries do not join or expose internal work logs.

`RequesterRequestService.createRequest(session, draft)`, `listOwnRequests(session)`
and `getOwnRequest(session, requestId)` reuse that transaction boundary. Owner reads
are scoped in SQL and checked in the service. Lists order by creation Instant
descending, then display ID ascending; absent and inaccessible details have the
same safe error. Returned records contain no audit details or internal notes.
Visible activity history under REQ-017 is not implemented yet. The configured
category set is supplied through `RequestValidator`; the authenticated Requester
view now calls these operations through background tasks.


Manager service tests cover valid assignment, invalid/inactive assignees,
unauthorized actors, invalid state, and missing priority. SQLite tests verify
that an injected audit failure rolls back request state and audit writes.

`SQLiteRequesterRequestServiceTest` covers valid/invalid creation, all three
operations' role checks, live deactivation/role changes, owner isolation and
ordering, reopening the database, Manager handoff, request/audit failure rollback,
ID exhaustion, foreign keys, and safe read errors. `SchemaMigrationsTest` covers
fresh/repeated startup, legacy preservation, seed identity advancement, rollback,
and incompatible schemas. Every integration test uses a temporary database;
legacy session fixtures remain isolated from the new real-authentication tests.
`AuthenticationServiceTest` covers login, generic failures, password boundaries,
revocation, role changes, reset invalidation, audit rollback and secret clearing.
`AuthenticatedWorkflowTest` uses real JavaFX controls with a controlled background
executor and temporary SQLite database to exercise all role routes, logout,
submission, repeated-click prevention, safe recovery, draft restoration, and
Requester-to-Manager handoff. `WorkspaceTest` checks seeding and category startup.

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
applies. Tests now cover login and storage recovery; the full lifecycle,
accessibility and visual layout across operating systems still need acceptance work.

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

The implemented subset is described above. Date filtering, visible activity
history, category mappings, and the complete lifecycle remain outstanding.

## Next integration and release work

The [confirmed Requester integration decision](decisions/yooplo/0001-requester-integration.md)
defines the next create/list/detail milestone and team-agreed shared contracts.
Login/session handling and authenticated create/list/detail are integrated with
the existing Manager assignment route. This does not complete visible history or
the remainder of the cross-role lifecycle.

Follow [RequesterPreparation.md](RequesterPreparation.md) for the ordered tasks.
Add edit/cancel, visible history, and filters next.
The Manager slice still needs search/filter/reset,
the remaining transitions, account administration, summaries, and audit browsing.

Full structured/rotated operational logging and a global UI exception boundary,
representative demo requests, release installers, and cross-platform launch
verification remain unimplemented. Current login-failure/startup messages alone
do not satisfy OBS-001–007.
Gradle development distributions use host-specific JavaFX libraries and require
Java; they are not the final universal release artifact. Resolve packaging and
monitoring evidence with the team under REL-005–007 and OBS-001–007.

## Responsive presentation (UIX-017–021)

`facilityflow.css` styles the login, shared account header, role views and preview.
Native `FlowPane` action bars wrap without rebuilding controls. The Manager
`SplitPane` changes orientation below 1,100 pixels of available width; its
assignment panel scrolls independently, and the table retains horizontal
scrolling so columns remain readable. Requester forms are capped at 800 pixels
and scroll vertically. Layout changes preserve existing control instances and
input. `AuthenticatedWorkflowTest` loads the production stylesheet and exercises
760 × 600, 1,024 × 700 and 1,440 × 900 content sizes.
These tests explicitly size an unmanaged application root inside a scene, avoiding
native window-size limits and asynchronous resize events on CI runners. They
verify JavaFX content layout; native window resizing still needs platform checks.
Set `FACILITYFLOW_UI_SNAPSHOTS` to a temporary output directory when running
these UI tests to export PNGs containing only test-fixture data for visual review.

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

Authentication uses the JDK's [PBEKeySpec](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/spec/PBEKeySpec.html)
and the PBKDF2-HMAC-SHA256 work factor documented by
[OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
Background UI work follows [JavaFX Task](https://openjfx.io/javadoc/25/javafx.graphics/javafx/concurrent/Task.html),
also checked through Context7. No additional runtime dependency was added.
