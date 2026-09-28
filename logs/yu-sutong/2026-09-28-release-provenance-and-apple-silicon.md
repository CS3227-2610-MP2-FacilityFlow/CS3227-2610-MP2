# Release provenance and Apple silicon packaging, 28 September 2026

## Goal and prompts

The user asked why the `v1.0.0` release workflow failed, then requested a fix
and push. They clarified that two JARs are preferable: retain the existing
64-bit Windows/Linux/Intel Mac JAR and add an Apple silicon Mac JAR.

## Evidence and decisions

- Tag `v1.0.0` points to `6637171`, while the runnable fat-JAR build exists on
  the later `release-prep` branch. The official release JAR was therefore not
  reproducible from its tag.
- The tag workflow packaged ZIPs on three operating systems, then failed at
  `gh release create` because the release already existed. The package jobs
  themselves succeeded.
- The published `v1.0.0` JAR contained x86-64 macOS JavaFX native libraries.
  Its `libprism_es2.dylib` was verified with `file`, and launching it on an
  Apple silicon Mac with Java 25 failed with an incompatible-architecture error.
- REL-006 in the team-authored specification allows platform-specific artifacts
  when a universal JAR is not viable. It is not an independent teaching-staff
  ruling. The replacement release uses two JARs because macOS x86-64 and ARM64
  native libraries have colliding resource names inside one fat JAR.

## Changes prepared for review

- Version 1.0.1, with an Apple silicon Shadow build target and a default that
  selects ARM64 on an Apple silicon development Mac.
- Release workflow builds the x86-64 JAR on Linux and checks that same JAR on
  Windows and Intel macOS. It builds and checks an ARM64 JAR on an Apple silicon
  runner. The publish job runs only for a version tag, attaches both JARs and
  `SHA256SUMS.txt`, and can upload to an existing release on rerun.
- Smoke script checks process survival, startup errors, database creation,
  six seeded accounts and requests, and SQLite integrity.
- User, developer, website, and submission documentation updated for the two
  artifacts and Java 25 requirement. The user-guide reviewer checked the guide.

## Verification so far

- `sh ./gradlew clean check shadowJar -PreleaseTarget=macos-aarch64` passed
  locally with Java 25 after the added REL-008 test: 257 Java tests passed,
  no failures, errors, or skips. Checkstyle passed. Four Python smoke-gate
  regression tests also passed.
- The built ARM JAR includes JavaFX and SQLite JDBC. Its
  `libprism_es2.dylib` is Mach-O ARM64.
- An isolated direct-classpath smoke test against that ARM JAR created the
  workspace, logged in as all three roles, took a request through creation,
  assignment, work log, completion, and closing, then reopened the database.
  SQLite integrity was `ok`.
- The built Apple silicon JAR passed a 15-second startup and SQLite integrity
  smoke test with macOS GUI access. A sandboxed attempt could not access the
  display. No human visual inspection is claimed. Pull-request CI and human
  clean-machine smoke tests remain required.
- The first pull-request CI run passed the release universal JAR job but failed
  the existing Ubuntu and Windows `build` jobs because Gradle 9 found an implicit
  dependency between unused ZIP/TAR distribution tasks and `shadowJar`. The
  project now skips unused launcher-script and archive distribution tasks and
  makes `build` depend on the actual release JAR. A serial local
  `clean build -PreleaseTarget=macos-aarch64` passed; Linux/Windows CI must
  still pass before merge. The x86-64 artifact assembled on this ARM Mac, but
  its JavaFX UI tests failed on x86-64 macOS natives, as expected for a
  cross-architecture test; this is not counted as an application test pass.
- The Intel macOS smoke job exited with SIGABRT (-6) before the GUI remained
  open. The archive's macOS native libraries were checked as x86-64; the cause
  is not yet established. The smoke script and workflow now capture additional
  JVM/native crash diagnostics on rerun. The PR is not ready to merge.

## Human decisions and pending work

The user requested the fix and push, and clarified the two-JAR approach. The
team must review the pull request, verify the CI matrix, merge it, create the
new tag from `master`, test the exact downloaded artifacts, and record those
results before claiming release completion.
