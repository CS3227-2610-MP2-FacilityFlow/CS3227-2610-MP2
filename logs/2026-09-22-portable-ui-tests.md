# 22 September 2026 — Portable responsive layout tests

## Goal and evidence

The user reported a failed GitHub build and provided its macOS job screenshot.
Run 35704149373 passed Windows and Ubuntu. On macOS, 98 of 99 tests passed;
the 1440 × 900 Manager layout case failed its orientation assertion at line 257.
The screenshot does not show the actual native window dimensions.

## Diagnosis and change

The test requested native Stage dimensions, then checked orientation using the
requested width without verifying the content width. Native resizing is subject
to platform timing and display constraints. The precise macOS constraint is not
confirmed, but the test's dependence on native sizing is unnecessary.

Using AGENTS.md, the previously applied Ponytail skill and JavaFX documentation
through Context7, changed the shared UI test fixture to an explicitly sized,
unmanaged application root hosted inside a scene. Each resize asserts the actual
root dimensions before testing layout. Bounds and optional screenshots use that
root. All three sizes and the existing orientation assertions remain covered;
no tests are skipped and no production UI behavior or CI matrix is changed.
Updated the Developer Guide to distinguish content-layout tests from native
window verification. Requirement: UIX-021.

## Verification

Local `gradlew.bat test --tests sg.edu.nus.facilityflow.ui.AuthenticatedWorkflowTest
check` passed: all 15 UI tests, zero failures/errors/skips, Checkstyle and JaCoCo
report generation. `git diff --check` passed. The macOS fix must
still be confirmed by rerunning GitHub Actions; this Windows machine cannot
verify native macOS behavior. Used the existing temporary JDK/cache/build path
to avoid the known OneDrive build lock.

## Review

The user requested committing this fix. Changes and this log still need teammate
review before merge. No push performed.
