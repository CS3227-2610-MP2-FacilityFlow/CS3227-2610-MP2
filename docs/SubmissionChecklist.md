# FacilityFlow submission checklist

Updated: 27 September 2026

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
| Platform-specific release packaging | Implemented locally | `releaseZip` and tag workflow; tagged GitHub release still required |

## Final automated verification

Record the release-candidate commit SHA and exact outcomes here after the final
branch checks. Do not replace local results with assumptions about CI.

| Check | State | Evidence |
|---|---|---|
| Compilation and Checkstyle | Passed locally | `./gradlew clean check releaseZip`, 27 September 2026 |
| Full unit/integration/JavaFX suite | Passed locally | 241 tests; 0 failures, errors, or skips |
| Coverage report | Passed project target locally | Model/service/auth/storage: 1,493 of 1,710 lines, 87.3%; JaCoCo report in `build/reports/jacoco/test/html/index.html` |
| Release ZIP build/content inspection | Passed locally on Apple silicon macOS | 21 MB `FacilityFlow-1.0.0-macos.zip`; JAR manifest version/main class, scripts, SQLite, and macOS JavaFX libraries present; shell launcher syntax valid; final local SHA-256 `428970edf312af6dd9146569b4ed78aadde7f19a4f6c234a363faac47ddd1d87` |
| Development launch/UI smoke test | Pending human | Run the app and complete one primary workflow per role |

## GitHub and clean-machine release actions

These actions must be completed after this branch is reviewed and merged. They
cannot be truthfully completed only by editing repository files.

Public read-only check on 27 September 2026:

- The organization/repository is public and correctly named, and `master` is the
  default branch.
- The latest `master` build before this completion branch, commit `c622aba`, passed
  its Windows, macOS, and Ubuntu jobs in
  [Actions run 36234055977](https://github.com/CS3227-2610-MP2-FacilityFlow/CS3227-2610-MP2/actions/runs/36234055977).
- GitHub reports that Pages is not enabled (`has_pages: false`).
- The latest-release endpoint returns 404, so no formal GitHub release exists yet.
- The saved local GitHub CLI login is expired; authenticated settings such as
  branch protection could not be inspected or changed during this pass.

- [ ] Obtain teammate review and merge the completion pull request into `master`.
- [ ] Confirm `master` CI passes on Windows, macOS, and Linux for the merge commit.
- [ ] Confirm GitHub Pages is configured to deploy through GitHub Actions and opens
      the FacilityFlow product site.
- [ ] Create and push annotated tag `v1.0.0` from the reviewed `master` commit.
- [ ] Confirm the Release workflow publishes Windows, macOS, and Linux ZIPs plus
      `SHA256SUMS.txt` in a formal GitHub release.
- [ ] Smoke-test the matching release ZIP on clean Windows, macOS, and Linux systems.
      Record tester, OS/version, artifact name/checksum, workflow performed, and result.
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
| Pending | macOS | Pending | Login and one Requester → Manager → Technician → Manager cycle | Pending | — |
| Pending | Linux | Pending | Login and one Requester → Manager → Technician → Manager cycle | Pending | — |
