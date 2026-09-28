# PR #20 release CI debugging - 29 September 2026

Status: Pending team review and GitHub CI verification.

## Goal and prompts

The user provided two debugging PDFs and asked what issues remained, then narrowed
the work to PR #20 and asked the agent to continue the debugging and "do that".
The session focused only on the release workflow, packaged-JAR smoke gate, and
PR #20 verification.

## Instructions and skills used

- Followed the repository `AGENTS.md`, especially REL-005, REL-006, QLT-006,
  QLT-008, and the requirement to preserve verifiable AI-session evidence.
- Used the `pdf` skill to read and visually inspect the two supplied debugging
  summaries before distinguishing historical findings from current PR state.
- Used the `g-diagnosing-bugs` skill to establish failing feedback loops before
  changing the workflow and smoke checker.

## Changes made

- Changed the Apple silicon GitHub Actions runner label from the unroutable
  `macos-15-arm64` label to the supported `macos-15` label.
- Added release-test sources to the release workflow's pull-request path filter.
- Made release reruns remove assets that are not present in the current `dist`
  directory after uploading the authoritative artifacts.
- Added workflow regression tests for the runner label, rerun asset cleanup, and
  pull-request path coverage.
- Closed SQLite connections explicitly in the packaged-JAR smoke checker and its
  tests so Windows can clean isolated temporary databases.
- Made the smoke checker retry transient database/schema initialization for up to
  30 seconds while still requiring the application to remain alive for at least
  15 seconds.
- Added a regression test for delayed workspace initialization.

## Verification performed

- Confirmed the workflow regression tests failed before the fixes for the ARM
  runner label, stale assets, and missing test-source path trigger.
- Ran all nine Python release/smoke-gate tests successfully after the fixes, with
  a second consecutive pass before the database-readiness change.
- Ran `gradlew.bat check shadowJar --no-daemon` successfully: Java compilation,
  Java tests, Checkstyle, JaCoCo, and x86-64 release-JAR packaging passed.
- Ran the built `facilityflow-1.0.1.jar` smoke gate twice successfully on Windows;
  each run stayed open, seeded six accounts and six requests, and passed SQLite
  integrity checking.
- Ran `git diff --check`; it reported no whitespace errors.

## Errors and corrections

- `gradlew.bat clean` could not delete two pre-existing `facilityflow-1.0.0` JARs
  held open by an older Java process. The verification was rerun without `clean`,
  which still rebuilt the current sources and `facilityflow-1.0.1.jar`. The older
  process was not terminated because its ownership and purpose were not proven.
- The first Windows smoke run caught a cold-start race: the database file existed
  before its schema was ready, causing `no such table: user_accounts`. A second
  run passed, confirming nondeterministic initialization timing. The smoke gate
  now polls for the expected seeded state and has a regression test for this case.
- Explicitly closing fixture connections initially removed the transaction commit
  previously supplied by the connection context manager. Explicit commits were
  added, after which the suite passed.

## Verified outcome and remaining work

The local PR #20 fixes and regression checks pass on Windows. This does not prove
the GitHub-hosted Apple silicon job or the final tagged release. A team member must
review the diff, CI must run on the pushed commit, and the exact artifacts from the
reviewed `v1.0.1` tag must still be downloaded and tested on the required clean
machines before REL-007 can be marked complete.
