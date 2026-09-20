# FacilityFlow Developer Guide

Status: Requester development starter, 14 September 2026. This guide describes
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
JUnit 5.13.4, Checkstyle 10.26.1, and JaCoCo 0.8.14. The wrapper verifies the
Gradle distribution SHA-256. Gradle's
[compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)
lists Java 25 support from Gradle 9.1.0.

## Implemented structure

| File | Responsibility |
|---|---|
| [RequesterPreviewLauncher](../src/main/java/facilityflow/RequesterPreviewLauncher.java) | Development entry point; starts JavaFX without impersonating an account |
| [RequesterPreview](../src/main/java/facilityflow/ui/requester/RequesterPreview.java) | Window and initial-category preview fixture |
| [RequesterForm](../src/main/java/facilityflow/ui/requester/RequesterForm.java) | Input controls and field feedback; no SQL, authentication, or persistence |
| [RequestDraft](../src/main/java/facilityflow/model/RequestDraft.java) | Normalized, still-untrusted input; no caller-supplied ownership or status |
| [ReportedUrgency](../src/main/java/facilityflow/model/ReportedUrgency.java) | The four reported urgency values from LIF-005 |
| [RequestValidator](../src/main/java/facilityflow/service/RequestValidator.java) | Field validation against a supplied catalogue, independent of JavaFX |
| [RequestValidatorTest](../src/test/java/facilityflow/service/RequestValidatorTest.java) | Boundary, missing-value, catalogue, and trimming checks |
| [RequesterFormTest](../src/test/java/facilityflow/ui/requester/RequesterFormTest.java) | Real JavaFX controls: input retention and error correction |

`facilityflow` is the starter package; review this shared choice with teammates.
There is no `auth` or `storage` implementation yet. A valid draft is not an
authorized or persisted request. The eventual create service must enforce
AUT-018–022, derive owner/actor from the authenticated session, run validation,
generate the ID/status/timestamps, and commit request plus audit atomically.
Follow [the lifecycle specification](../specs/components/request-lifecycle.md)
and [persistence contract](../specs/components/persistence-observability.md).

The preview category list is a fixture, not a configuration implementation.
The validator accepts the catalogue as input to allow later integration without
duplicating field rules. The [category catalogue contract](CategoryCatalogue.md)
still governs production startup, rename/removal mapping, and atomic migration.

## Verification and CI

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

## Next integration and release work

Follow [RequesterPreparation.md](RequesterPreparation.md) for the ordered tasks.
Agree shared account/session and repository contracts before adding protected
operations. Add isolated SQLite transaction tests, then wire create/list/detail
to authenticated navigation. Add edit/cancel, visible history, and filters next.

Operational logging, migrations, production error boundaries, demo accounts,
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
