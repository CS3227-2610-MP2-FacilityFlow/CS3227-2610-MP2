# FacilityFlow submission checklist

Updated: 28 September 2026

Deadline: 29 September 2026, 2:00 PM SGT

This checklist deliberately separates implementation, local automated evidence,
GitHub-hosted evidence, and human verification. A configured workflow is not marked
as a passed run, and generated documentation is not marked as human-approved.

## Repository deliverables

| Deliverable | Release-candidate state | Evidence or remaining action |
|---|---|---|
| Java 25 desktop source under `src/` | Implemented | Three authenticated role UIs and shared SQLite workflow |
| Requester role | Implemented and locally tested | Creation, own reads, filters, edits, cancellation, follow-ups, and visible history |
| Technician role | Implemented and locally tested | Scoped queue, filters, start, combined history, work logs, and completion |
| Facilities Manager role | Implemented and locally tested | Overview, all-request operations, lifecycle, accounts, and audit viewer |
| Versioned SQLite and demo workspace | Implemented and locally tested | Schema v5, atomic seed, two accounts per role, six lifecycle-state requests |
| User Guide | Updated by the required reviewer agent | Team member must compare it with a manual run and approve |
| Developer Guide and acknowledgements | Updated | Team member must review architecture and reuse statements |
| `docs/Reflections.md` | Present | Three detailed skills; team must verify that examples match its experience |
| AI-session summaries under `logs/` | Present | New completion log and older summaries with “Pending” labels require human verification |
| Product website source | Present | `docs/index.md` and Pages workflow; deployment still needs GitHub evidence |
| Operational monitoring | Implemented | Rotating structured logs plus global unexpected-error boundary |
| Two release JARs for x86-64 platforms and Apple silicon | Implemented on the release-fix branch; CI pending | `shadowJar` selects matching JavaFX natives; both assets must come from the reviewed `v1.0.1` tag |

## Final automated verification

Record the release-candidate commit SHA and exact outcomes here after the final
branch checks. Do not replace local results with assumptions about CI.

| Check | State | Evidence |
|---|---|---|
| Compilation, tests, Checkstyle, and Apple silicon JAR packaging | Passed locally on Apple silicon macOS | `sh ./gradlew clean check shadowJar -PreleaseTarget=macos-aarch64`, 28 September 2026; final branch verification passed |
| Standard CI `build` task | Passed locally after ZIP/TAR task correction | `sh ./gradlew clean build -PreleaseTarget=macos-aarch64`; JAR included, unused distributions skipped. GitHub rerun pending. |
| Full unit/integration/JavaFX suite | Passed locally | 257 tests; 0 failures, errors, or skips on the ARM target |
| Release smoke-gate regression tests | Passed locally | Four Python tests; 0 failures |
| Coverage report | Passed project target locally | Model/service/auth/storage: 1,525 of 1,714 lines, 89.0%; JaCoCo report in `build/reports/jacoco/test/html/index.html` |
| Apple silicon JAR content and startup | Passed locally | JAR contains JavaFX, SQLite JDBC, and ARM64 `libprism_es2.dylib`; isolated 15-second process/database smoke test passed with macOS GUI access. This is not a human visual test or downloaded-release test. |
| Development launch/UI smoke test | Pending human | Run the app and complete one primary workflow per role |

## GitHub and clean-machine release actions

These actions must be completed after this branch is reviewed and merged. They
cannot be truthfully completed only by editing repository files.

Public read-only check on 28 September 2026:

- The organization/repository is public and correctly named, and `master` is the
  default branch.
- `master` commit `6637171` passed its Windows, macOS, and Ubuntu build jobs in
  [Actions run 36323533357](https://github.com/CS3227-2610-MP2-FacilityFlow/CS3227-2610-MP2/actions/runs/36323533357).
- The `v1.0.0` tag points to `6637171`, whose build does not contain the later
  runnable-JAR packaging changes. Its formal release instead contains a JAR
  built from the unmerged `release-prep` work.
- The [v1.0.0 release run](https://github.com/CS3227-2610-MP2-FacilityFlow/CS3227-2610-MP2/actions/runs/36412463839)
  packaged all three ZIPs but failed publishing because the release already
  existed. The published JAR's macOS native libraries are x86-64, so it cannot
  launch on Apple silicon.

- [ ] Obtain teammate review and merge the release-fix pull request into `master`.
- [ ] Confirm the release workflow's pull-request and `master` checks pass on
      Windows, Linux, Intel macOS, and Apple silicon macOS.
- [ ] Create and push annotated tag `v1.0.1` from the reviewed `master` commit.
- [ ] Confirm the workflow publishes both JARs and `SHA256SUMS.txt` from that tag.
- [ ] Smoke-test the exact downloaded JAR on clean Windows, Intel macOS, Apple
      silicon macOS, and Linux systems. Record tester, OS/version, artifact,
      checksum, workflow performed, and result.
- [ ] Confirm the organization/repository and release are publicly accessible to a
      signed-out browser.
- [ ] Confirm required checks/branch protection prevent an unverified merge, or
      record the team's equivalent manual control.
- [ ] Verify all reflection and AI-session claims; replace each remaining “Pending”
      human-verification label with the reviewer and date only after actual review.
- [ ] Freeze `master` at the submitted release state before 29 September 2026,
      2:00 PM SGT, and make no later changes.

## Manual smoke record template

| Tester | OS/version | Artifact/checksum | Workflow exercised | Result/date | Issue link |
|---|---|---|---|---|---|
| Pending | Windows | Pending | Login and one Requester → Manager → Technician → Manager cycle | Pending | — |
| Pending | Intel macOS | Pending | Login and one Requester → Manager → Technician → Manager cycle | Pending | — |
| Pending | Apple silicon macOS | Pending | Login and one Requester → Manager → Technician → Manager cycle | Pending | — |
| Pending | Linux | Pending | Login and one Requester → Manager → Technician → Manager cycle | Pending | — |
