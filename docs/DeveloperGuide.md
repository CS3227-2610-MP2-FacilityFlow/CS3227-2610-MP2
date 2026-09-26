# FacilityFlow Developer Guide

Status: complete three-role release candidate, updated 27 September 2026.
Local verification is recorded separately from GitHub release and clean-machine
evidence.

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
| [RequesterDashboardView](../src/main/java/sg/edu/nus/facilityflow/ui/requester/RequesterDashboardView.java) | Requester counts, owned list/search/filters, submission, detail/history, edit/cancel and follow-up controls |
| [TechnicianDashboardController](../src/main/java/sg/edu/nus/facilityflow/ui/technician/TechnicianDashboardController.java) | Thin presentation adapter for Technician queue, work-log reads, and writes |
| [TechnicianDashboardView](../src/main/java/sg/edu/nus/facilityflow/ui/technician/TechnicianDashboardView.java) | Assigned queue, request detail, start-work action, internal work-log history and entry form, resolution summary, and completion-for-review action |
| [ManagerRequestService](../src/main/java/sg/edu/nus/facilityflow/service/ManagerRequestService.java) | Manager-wide queue, filters, summaries, history, lifecycle transitions, record-on-behalf, and corrections |
| [ManagerAccountService](../src/main/java/sg/edu/nus/facilityflow/service/ManagerAccountService.java) | Account creation, activation, role changes, and last-Manager/active-work safety rules |
| [ManagerDashboardView](../src/main/java/sg/edu/nus/facilityflow/ui/manager/ManagerDashboardView.java) | Overview, all requests, lifecycle actions, account administration, and read-only audit viewer |
| [Workspace](../src/main/java/sg/edu/nus/facilityflow/storage/Workspace.java) | OS-user database location and category startup validation |
| [RequesterPreviewLauncher](../src/main/java/sg/edu/nus/facilityflow/RequesterPreviewLauncher.java) | Development entry point; starts JavaFX without impersonating an account |
| [RequesterPreview](../src/main/java/sg/edu/nus/facilityflow/ui/requester/RequesterPreview.java) | Window and initial-category preview fixture |
| [RequesterForm](../src/main/java/sg/edu/nus/facilityflow/ui/requester/RequesterForm.java) | New/edit input controls and field feedback; no SQL, authentication, or persistence |
| [RequestDraft](../src/main/java/sg/edu/nus/facilityflow/model/RequestDraft.java) | Normalized, still-untrusted input; no caller-supplied ownership or status |
| [ReportedUrgency](../src/main/java/sg/edu/nus/facilityflow/model/ReportedUrgency.java) | The four reported urgency values from LIF-005 |
| [RequestValidator](../src/main/java/sg/edu/nus/facilityflow/service/RequestValidator.java) | Field validation against a supplied catalogue, independent of JavaFX |
| [RequesterRequestService](../src/main/java/sg/edu/nus/facilityflow/service/RequesterRequestService.java) | Requester authorization, validation, owner-only search/filter/detail, audited edits/cancellation and visible history/follow-ups |
| [SchemaMigrations](../src/main/java/sg/edu/nus/facilityflow/storage/SchemaMigrations.java) | Ordered versions 1–5 and schema compatibility checks |
| [TechnicianRequestService](../src/main/java/sg/edu/nus/facilityflow/service/TechnicianRequestService.java) | Authenticated Technician-scoped queue, dashboard-count read, filtering, progress, work-log, completion, and stale-write checks |
| [TechnicianQueueFilter](../src/main/java/sg/edu/nus/facilityflow/model/TechnicianQueueFilter.java) | Normalized TEC-002 queue search and enum/category filters |
| [TechnicianDashboardCounts](../src/main/java/sg/edu/nus/facilityflow/model/TechnicianDashboardCounts.java) | Non-negative counts for assigned, in-progress, and completed work awaiting Manager review |
| [WorkLog](../src/main/java/sg/edu/nus/facilityflow/model/WorkLog.java) | Immutable internal Technician work evidence |
| [SQLiteTechnicianCrossRoleIntegrationTest](../src/test/java/sg/edu/nus/facilityflow/storage/SQLiteTechnicianCrossRoleIntegrationTest.java) | Authenticated same-database Requester-to-Manager-to-Technician workflow, role isolation, audit sequence, and restart evidence |
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
database. Optional `renames=Old>New,...` entries migrate names to configured
categories; `removals=Old,...` entries move those categories to `Other`.
Unknown keys, duplicate/blank categories, invalid mappings, and unmapped stored
categories stop startup before data changes. Each affected request update and
`CATEGORY_MIGRATED` audit event shares the startup transaction. See
[CategoryCatalogue.md](CategoryCatalogue.md).

The cross-role integration test uses the real authentication boundary and one
temporary SQLite database. It proves that a Requester-created request can be
assigned by a Manager, progressed by its assigned Technician, and reloaded after
database reopen with its work log and request-scoped audit sequence intact. The
same test checks that another Technician and a wrong-role session cannot read or
write the request, while the old process-local session is rejected after restart.
This is local automated evidence for the shared service/storage workflow, not a
claim that the full release matrix or cross-platform packaging is complete.

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
session serialization exists. Manager account administration exposes account
creation, activation/deactivation, role changes, and password reset through the
authenticated JavaFX route.

`UiTasks` executes JDBC/password work off the JavaFX thread and reports outcomes
on it. Pending work disables header navigation; saving disables the form and its
back action. Success opens persisted detail, and returning to the list reloads
committed data. Failures retain entered input and display safe messages. A draft
can survive an expired session in memory for the same account's next login;
explicit logout, another account's login, or process shutdown discards it.
Role changes reroute on the next denied protected action or **Refresh account**.
The Technician dashboard uses a thin presentation adapter over
`TechnicianRequestService`; it does not issue SQL or reproduce lifecycle rules.
Selecting an assigned request loads internal work-log history asynchronously.
The entry form submits a trimmed note and whole minutes through the service,
retains both fields after failure, clears them only after a committed write, and
refreshes the queue and history after success. The completion form requires
loaded work-log evidence and a 10–2,000-code-point trimmed resolution summary;
it retains the summary after failure, clears it after a committed write, and
refreshes the queue and history after success. A selection-version token
prevents an older asynchronous history response from replacing the currently
selected request. The UI enables progress writes only for `IN_PROGRESS`; the
service still rechecks role, assignment, status, validation limits, and
conditional ownership inside the transaction.

## Manager architecture

The package boundaries follow `AGENTS.md`:

- `model` contains immutable request, account, audit, status, urgency, and
  priority values.
- `auth` contains the authenticated-session identity passed to protected use
  cases.
- `service` owns Manager authorization, validation, queue ordering, account
  safety, and named lifecycle transitions.
- `storage` owns the transaction interface, schema foundation, and SQLite/JDBC
  implementation. It does not depend on JavaFX.
- `ui.manager` contains the Manager JavaFX view and thin presentation adapter.
  It contains no SQL or lifecycle rules.

`ManagerRequestService` validates the persisted actor and current state inside
the storage transaction for assignment, reassignment, close, return, reopen,
cancellation, record-on-behalf, and correction. Each write uses an optimistic
expected-state condition and appends its audit event before commit. Return and
reopen retain completion metadata; correction cannot change ownership, workflow
state, assignment, work logs, or resolution evidence.

Manager reads cover deterministic MGR-021 queue ordering, combined search and
filters, status/priority summaries, active Technician workloads, full request
history, and enriched audit records. `ManagerAccountService` enforces validation,
self/last-Manager protection, and the active-Technician-work role-change rule.
The JavaFX workspace exposes these operations in separate Overview, Requests,
Accounts, and Audit tabs while keeping SQL and lifecycle rules outside the view.

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
- Version 5 adds append-only Requester follow-up updates and its chronological
  history index.
- Versions newer than 5, missing required columns, and missing/invalid sequence
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
never reseeded, even when empty. A new workspace contains six representative
requests covering `OPEN`, `ASSIGNED`, `IN_PROGRESS`, `COMPLETED`, `CLOSED`, and
`CANCELLED`, plus representative work logs and lifecycle audits. `initializeSchema`
remains an unseeded migration entry point for integration tests.

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

`TechnicianRequestService.getDashboardCounts(session)` uses a parameterized
aggregate scoped to the logged-in Technician and returns separate counts for
`ASSIGNED`, `IN_PROGRESS`, and `COMPLETED` requests. The Technician UI exposes
those counts plus queue search and status/category/priority filters. Its combined
history contains internal work logs and Requester follow-up updates for the
current assignee, satisfying LIF-009 without exposing unrelated requests.

`MaintenanceRequest.assignedAt` records the current assignment time separately
from `updatedAt`; Manager assignment sets both, while later work-log writes change
only `updatedAt`. Work-log history retains its original Technician author after
reassignment. Requester queries do not join or expose internal work logs.

`RequesterRequestService.createRequest(session, draft)`, `listOwnRequests(session)`
and `getOwnRequest(session, requestId)` reuse that transaction boundary. Filtered
list reads also support case-insensitive search, enum/category filters, and local
inclusive date ranges. Owner reads
are scoped in SQL and checked in the service. Lists order by creation Instant
descending, then display ID ascending; absent and inaccessible details have the
same safe error. Returned records contain no audit details or internal notes.
Edit, cancellation, and follow-up writes recheck owner and current status inside
the transaction. Visible history reads only approved status audit actions and
Requester updates; it does not return work logs or private Manager notes. The
configured category set is supplied through `RequestValidator`; the authenticated
Requester view calls these operations through background tasks.


Manager service tests cover queue search/filter/order, summaries and history,
valid and forbidden lifecycle transitions, account safety, record-on-behalf,
corrections, authorization, and invalid inputs. SQLite tests verify Manager
persistence and that injected audit failures roll back request state and audit
writes.

`SQLiteRequesterRequestServiceTest` covers valid/invalid creation, role checks,
live deactivation/role changes, owner isolation and ordering, search, filters,
inclusive local dates, edit/cancel/follow-up state rules, visible-history privacy,
reopening the database, Manager handoff, request/audit failure rollback, ID
exhaustion, foreign keys, and safe read errors. `SchemaMigrationsTest` covers
fresh/repeated startup, legacy preservation, seed identity advancement, rollback,
and incompatible schemas. Every integration test uses a temporary database;
legacy session fixtures remain isolated from the new real-authentication tests.
`AuthenticationServiceTest` covers login, generic failures, password boundaries,
revocation, role changes, reset invalidation, audit rollback and secret clearing.
`AuthenticatedWorkflowTest` uses real JavaFX controls with a controlled background
executor and temporary SQLite database to exercise all role routes, logout,
submission, repeated-click prevention, safe recovery, draft restoration, and
Requester-to-Manager handoff. `WorkspaceTest` checks seeding and category startup.
`TechnicianRequestServiceTest` and `SQLiteTechnicianRequestServiceTest` cover
Technician dashboard-count ownership/role checks and persisted queue search,
filtering, and ordering. The Requester dashboard presents its owner-scoped
counts and exposes the corresponding search and filters in JavaFX.

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
distributions. `releaseZip` produces the current operating system's release
archive. The 27 September 2026 local release-candidate run executed 241 tests
with no failures or skips and measured 87.3% line coverage across model, service,
auth, and storage packages. Reports are written to:

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
applies. The suite covers the complete named lifecycle, authentication, role and
object authorization, rollback, migrations, demo seeding, role UIs, and responsive
content layout. Native accessibility and visual layout still need the recorded
clean-machine acceptance pass on each target operating system.

The current classpath-based JavaFX test emits an upstream warning that JavaFX
classes are loaded from an unnamed module; the control test passes. The release
uses platform-specific Gradle application distributions, each containing the
application JAR, dependency JARs, JavaFX native libraries, and launch scripts.

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

All three role slices now share the authenticated transaction boundary and
complete the specified request lifecycle. Category mappings, Manager review and
administration, cross-role history visibility, and representative demo data are
integrated in the release candidate.

## Release, website, and monitoring

`releaseZip` builds `FacilityFlow-1.0.0-<platform>.zip` from Gradle's installed
application distribution. The tag-triggered [release workflow](../.github/workflows/release.yml)
runs `clean check releaseZip` on Windows, macOS, and Linux, uploads each package,
creates `SHA256SUMS.txt`, and publishes the artifacts to a formal GitHub release.
This configuration is not proof that a tag run or clean-machine smoke test passed;
record those external results in [SubmissionChecklist.md](SubmissionChecklist.md).

The [Pages workflow](../.github/workflows/pages.yml) builds the `docs/` Jekyll site
after documentation changes reach `master`. Repository Pages settings must use
GitHub Actions. The website, guides, reflection, and session summaries are all
versioned with the release candidate.

`OperationalLog` writes UTF-8 structured records under the workspace `logs/`
directory, using `INFO`, `WARNING`, and `SEVERE` severity names. Records include
UTC timestamp, component, event, safe metadata, and build version at startup.
Startup/shutdown, schema/category migration outcomes, login failures, handled
storage failures, and unexpected exceptions are covered. Files rotate at 1 MB
with five retained generations. Passwords, hashes, full request descriptions,
and raw free text must never be supplied to this logger. Unexpected errors include
exception types and bounded stack frames without exception messages; the JavaFX
boundary also shows a stable recovery message. A user sharing diagnostics should
close FacilityFlow and send only the relevant rotated log after checking it for
organisation-specific information.

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
actions/upload-artifact, actions/configure-pages, actions/jekyll-build-pages,
actions/upload-pages-artifact, and actions/deploy-pages projects. These tools
and libraries retain their respective upstream licenses. No MP1 source or assets
were reused.

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

Manager UI review used the locally installed `ui-ux-pro-max` guidance as design
advice. The team retained desktop-relevant accessibility recommendations such as
visible labels, focus, keyboard access, form grouping, and responsive density;
web landing-page typography, animation, icons, and font recommendations were not
copied into the JavaFX product. AI-generated changes and documentation remain
subject to human review as recorded in the logs and pull request.
