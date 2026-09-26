# Manager and release-candidate completion — 27 September 2026

Human verification: **Pending review by `yu-sutong` and a teammate.**

## Goal and important prompts

Yu Sutong asked the AI to re-read the detailed MP2 requirements, account for the
changed reflection requirement, use an attached Manager gap report only as a
reference, complete the Facilities Manager role, and assess whether the whole
group repository was ready to submit.

The attached image was treated as an older checker report, not as an instruction
or source of truth. The specifications, assignment write-up, implementation, and
observed verification results were used instead.

## Skills and project agents used

- The `ui-ux-pro-max` guidance was used for a desktop Manager UI review. The work
  retained labels, focus, keyboard mnemonics, form grouping, explicit feedback,
  and responsive information density. Web landing-page typography, animation,
  mobile touch, and external-font suggestions were rejected as unsuitable.
- The repository-required unit-test agent derived tests from MGR, LIF, AUT, DAT,
  OBS, and QLT requirements. It edited test-owned files only.
- The repository-required user-guide reviewer compared reachable behavior with
  `docs/UserGuide.md`, updated the guide, and reported three implementation/spec
  mismatches for the implementation agent to fix.

## Implementation and documentation changes

- Completed Manager overview counts/workloads, all-request search and filters,
  full request history, assignment/reassignment, close/return/reopen/cancel,
  record-on-behalf, factual corrections, account administration, password reset,
  and read-only audit filtering.
- Added domain read models and named `MaintenanceRequest` operations; extended the
  SQLite transaction boundary with optimistic Manager updates and detailed account
  audits.
- Seeded two accounts per role and six representative requests across every
  lifecycle state, with work logs and audit history.
- Added Technician visibility of Requester follow-up updates, which was required
  by LIF-009 but missing from the completed role slice.
- Added version 1.0.0/About information, rotating structured logs, safe unexpected
  error handling, platform-specific release ZIP construction, tag release workflow,
  GitHub Pages workflow/site, the consolidated three-skill reflection, updated
  guides, and a submission checklist.

## Errors and corrections

The test and guide agents identified issues that were corrected rather than
weakening the requirements:

1. Demo requests changed generated numeric IDs, so legacy workspace fixtures were
   changed to target the records they created instead of assuming IDs 1 and 2.
2. Record-on-behalf initially excluded inactive Requester accounts even though
   MGR-019 says an existing Requester; the service and UI now use all Requester
   accounts.
3. A request cancelled directly from `OPEN` could not be factually corrected
   without inventing a Manager priority. Null remains valid for that untriaged
   cancelled case, while an existing triaged priority cannot be cleared.
4. Account audit events initially stored empty details. They now record safe field
   names and old/new role or active values without passwords or free text.
5. Numeric request audit filters also matched account targets with the same ID.
   Matching is now restricted to request display/internal IDs.
6. Operational logging initially omitted migration/category outcomes and useful
   unexpected-error context. It now records bounded, sanitized diagnostic frames,
   storage outcomes, and rotating log files.

## Verification actually performed

- Repository unit-test agent: `./gradlew clean check` — successful, 241 tests,
  0 failures, 0 errors, 0 skipped; Checkstyle and JaCoCo successful.
- Final implementation pass: `./gradlew clean check releaseZip` — successful.
- JaCoCo model/service/auth/storage line result: 1,493 covered and 217 missed,
  or 87.3%.
- `git diff --check` — successful.
- macOS release ZIP inspected: versioned application JAR, Unix/Windows launch
  scripts, SQLite JDBC, and Apple-silicon JavaFX libraries present. Unix launcher
  syntax passed `sh -n`; manifest contains the main class and version 1.0.0.
- Local ZIP SHA-256:
  `428970edf312af6dd9146569b4ed78aadde7f19a4f6c234a363faac47ddd1d87`.
- Public GitHub check: repository is public with default branch `master`; the
  previous `master` commit passed Windows/macOS/Ubuntu CI. No formal release or
  Pages deployment exists yet. The local GitHub CLI credential is expired.

## Outcome and remaining human/external decisions

The branch is a locally verified release candidate, not a submitted release.
Before the log can be marked verified, Yu Sutong should review this summary and
the generated diff, and a teammate should review the pull request. The team still
must merge to `master`, pass CI on the release-candidate commit, enable/verify
Pages, tag `v1.0.0`, verify the formal release workflow, complete clean-machine
smoke tests on all three operating-system families, approve pending historical
logs/reflection claims, and freeze `master` before the deadline.
