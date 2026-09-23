# FacilityFlow Agent Guide

## Project identity

- Product name: FacilityFlow
- GitHub organization: `CS3227-2610-MP2-FacilityFlow`
- Repository name: `CS3227-2610-MP2`
- Required default/final-submission branch: `master`
- Team GitHub usernames: `ngkhengyang`, `yooplo`, `yu-sutong`
- Course: CS3227, AY2026/27 Semester 1, MP2
- Final deadline: 29 September 2026, 2:00 PM SGT

FacilityFlow is a production-oriented Java desktop application for managing
maintenance requests in a school, office, condominium, or similar formal
setting. It connects Requesters, Technicians, and a Facilities Manager through
a traceable request-to-resolution workflow.

This is a new MP2 codebase. Never copy or reuse source code, documentation, or
assets from either team member's MP1.

## Users and MVP scope

This is a three-person project, so the product has three user roles. Each role
must have a simple, clearly separate interface. Role ownership:

- Requester: `yooplo`.
- Technician: `ngkhengyang`.
- Facilities Manager: `yu-sutong`.

These assignments were agreed by the team, as confirmed by yooplo on
14 September 2026.

Each owner covers UI, service logic, persistence integration, authorization,
tests, and documentation. Requester preparation is tracked in
`docs/RequesterPreparation.md`.

Shared responsibilities:

- `yooplo`: login, session management, and role routing, in addition to Requester.
- `yu-sutong`: versioned database migrations, atomic demo-data seeding, and
  category configuration including startup validation and rename/removal migrations.
- Shared package: `sg.edu.nus.facilityflow`; reuse the Manager foundation's shared
  models and extend its transaction boundary for Requester creation/owner-only reads.

See `docs/decisions/yooplo/0001-requester-integration.md` for the confirmed contract.
Agreement does not mean these components have been implemented or verified.

### Requester

- Submit a maintenance request with validated details.
- View, search, and filter only their own requests.
- Edit or cancel an eligible request.
- Add follow-up information and see non-internal status history.

### Facilities Manager

- Record a maintenance request received from a caller or other reporting channel.
- Validate and edit request details such as title, description, location,
  category, and reported urgency.
- Prioritize requests and assign them to a technician.
- Monitor work across statuses and search/filter requests.
- Review completed work, close or reopen requests, and view summary reports.

### Technician

- View work assigned to the logged-in technician.
- Accept an assignment and start work.
- Update progress and add timestamped work notes.
- Record time spent and a resolution summary.
- Mark work as completed for coordinator review.

### Core workflow

Use an explicit state model rather than free-form status text:

`OPEN -> ASSIGNED -> IN_PROGRESS -> COMPLETED -> CLOSED`

`CANCELLED` is a terminal alternative state. The complete transition table and
exception rules are defined in `specs/components/request-lifecycle.md`.

Define and test exceptional transitions, including reassignment, cancellation,
and reopening. Enforce permissions and transitions in the service layer, not
only by hiding UI controls.

Features outside this complete workflow are secondary. Finish and test the
end-to-end workflow before adding dashboards, attachments, notifications, or
decorative UI.

## Specification-driven workflow

The Markdown files under `specs/` are the product source of truth. Read the
relevant specifications before changing behavior. Every functional requirement
has a stable identifier, and implementation, tests, pull requests, and user
documentation should cite the identifiers they satisfy.

For a new feature or behavior change:

1. Add or amend the requirement and acceptance criteria in `specs/`.
2. Obtain team review for decisions that affect another role or shared component.
3. Implement the smallest change that satisfies the approved specification.
4. Add automated tests referencing the relevant requirement identifiers.
5. Update user/developer documentation and the verified AI-session log.

After implementing a new feature or changing user-visible behavior, immediately
delegate the documentation pass before treating the feature as done:

- In Codex, invoke the project's `user_guide_reviewer` agent in
  `.codex/agents/`.
- In Claude Code, invoke the project's `user-guide-reviewer` subagent in
  `.claude/agents/` (or mention it with `@user-guide-reviewer`).

Give the documentation agent the changed behavior and relevant requirement IDs.
It must review and update `docs/UserGuide.md`, even when the implementation
agent believes no documentation change is needed; it should record that finding
in its response. A team member must review the resulting edits.

Do not silently change implementation behavior when it conflicts with a spec.
Resolve the inconsistency by updating the spec deliberately and recording the
decision. Do not begin proper feature implementation while an essential
requirement for that feature remains marked TBD.

## Technical baseline

- Use Java SE 25 and JavaFX.
- Use Gradle and commit the Gradle wrapper.
- Use a local persistent store; SQLite through JDBC is the preferred default.
- Use JUnit 5 for automated tests.
- Add code-quality and coverage tooling such as Checkstyle/SpotBugs and JaCoCo.
- Use GitHub Actions to build and test on Linux, Windows, and macOS.
- Produce a formal GitHub release with the runnable application artifact.
- Treat cross-platform JavaFX packaging as an early technical risk and test a
  release artifact on all three operating-system families well before submission.

Do not commit secrets, personal credentials, generated build directories,
local database files, or IDE-specific state.

## Architecture rules

Keep responsibilities separated. Prefer packages along these boundaries:

- `ui`: JavaFX views and controllers; presentation logic only
- `model`: domain entities, value objects, and enums
- `service`: use cases, permissions, validation, and workflow transitions
- `storage`: repositories, SQLite/JDBC code, and migrations
- `auth`: authentication and the logged-in session
- `util`: narrowly scoped shared helpers

Controllers must not issue SQL or directly manipulate storage. Storage classes
must not depend on JavaFX. Shared logic belongs in services rather than being
duplicated between role-specific controllers. Apply SRP and DRY without adding
abstractions that have no current use.

Model role permissions explicitly. Every protected service operation must
verify the authenticated role even when the UI also disables the operation.
Persist an audit record for important actions such as creation, assignment,
status changes, completion, closing, and reopening.

## Reliability and testing

For every feature, cover the normal path, invalid input, authorization failure,
invalid state transition, and storage failure where relevant. Keep domain and
service tests independent of JavaFX. Add repository integration tests using an
isolated temporary database. Use UI tests selectively for critical workflows.

Once the Gradle wrapper exists, the standard verification commands are:

```shell
./gradlew test
./gradlew check
```

Never claim a task is complete without running the relevant verification or
stating clearly why it could not be run.

Use structured operational logging and graceful user-facing error messages.
Logs must be useful for diagnosing failures without exposing passwords or other
sensitive information.

## Required repository deliverables

Keep these requirements visible when planning work:

- Application source under `src/`
- Accurate user guide at `docs/UserGuide.md`
- Current design and process documentation at `docs/DeveloperGuide.md`
- Agentic SE reflection at `docs/Reflections.md`
- Verified AI-interaction summaries under `logs/`
- GitHub Pages product website
- CI/CD workflows and automated tests
- Latest formal GitHub release with a cross-platform-compatible JavaFX artifact
- Acknowledgements for all reused ideas, code, and documentation

Documentation must describe the released product exactly. Do not document
planned or incomplete features as available.

## Agentic SE workflow

The assignment requires customization and evaluation of a single AI agent.
Develop and evaluate at least three focused skills:

1. JavaFX architecture reviewer: checks UI/service/storage boundaries and
   identifies concrete SRP or DRY problems.
2. Role-permission test generator: derives tests for allowed and forbidden
   operations and request-state transitions.
3. Release/documentation verifier: compares the implementation, guides,
   repository structure, and release requirements.

Treat generated work as a draft. A team member must review every generated
change, test, log summary, and documentation update.

After every substantial AI-assisted session, add a short Markdown summary under
`logs/` using a descriptive date-and-topic filename. Record:

- the goal and important prompts;
- which skill/instructions were used;
- files or behavior changed;
- tests and checks performed;
- errors, corrections, and human decisions;
- the verified outcome.

Never fabricate prompts, results, test runs, or reflections. Preserve evidence
that can later support `docs/Reflections.md`.

## Collaboration rules

- Assign one complete vertical role slice to each member: UI, service logic,
  persistence integration, authorization, tests, and documentation.
- Both members must also make substantial team-level contributions.
- Use small branches and pull requests; do not commit substantial feature work
  directly to `master`.
- Require review by the other member before merging non-trivial changes.
- Keep commits focused and use messages that explain the change.
- Do not overwrite or discard a teammate's uncommitted or unrelated work.
- Resolve design decisions in writing when they affect both role slices.

## Definition of done

A feature is done only when its behavior and permissions are implemented, its
important paths are tested, failures are handled, documentation is accurate,
and the relevant AI-session summary has been verified and recorded.

Before the final deadline, ensure `master` contains the intended release,
the public repository is accessible while logged out, the release artifact has
been tested across operating systems, and no repository changes are made after
submission.
