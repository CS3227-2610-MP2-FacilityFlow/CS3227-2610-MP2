# Reflection on My FacilityFlow Work

## 1. Tasks and decisions

I worked on the Requester slice, from preparation and integration planning through the authenticated workflow and the remaining Requester requirements. The work grew from the initial create/list/detail path into a full workflow for managing a request.

The Requester implementation includes owner-only reads, validated request creation and editing, cancellation with a reason, follow-up updates, search and filters, and requester-visible history. The history view excludes internal audit actions and Technician work logs. The shared application work also connected login, sessions, role routing, and the Requester interface to the existing Manager assignment workflow.

I treated the specifications and the confirmed Requester integration decision as the source of truth. A key design choice was to extend the existing shared transaction boundary so request creation and its audit entry succeed or fail together. Authorization is checked in the service and storage paths, rather than relying only on which controls the UI displays.

## 2. How I used AI and project instructions

I used the repository instructions and the relevant Requester, authentication, lifecycle, and persistence specifications to guide implementation. The Ponytail skill was used for focused coding changes, and Context7 was used when checking library and API details during backend and authentication integration.

I followed the project's test-agent and user-guide-reviewer workflows. The test agent added requirement-based tests for service behavior, SQLite persistence and migrations, and JavaFX workflows. The guide reviewer updated user-facing documentation to reflect implemented behavior. I reviewed generated changes and corrections rather than treating agent output as automatically correct.

## 3. What worked well

The staged implementation helped contain the shared-slice risk. The backend integration established owner-only reads and transaction behavior before authentication and UI routing were connected. Later work could then exercise the full flow, including role isolation and the Manager handoff.

Automated tests provided useful evidence beyond happy paths. They covered ownership and role checks, migration preservation, transaction rollback, filters and date boundaries, invalid cancellation reasons, and UI recovery after failed saves. In the latest Requester workflow session, the focused suites and full Gradle `check` passed; the recorded run included 179 tests, followed by a clean check after the final Requester assertions were added.

The testing workflow also surfaced real issues. For example, UI automation found that an invalid cancellation reason closed the dialog and lost the opportunity to correct it. The implementation was changed to keep the dialog open with the entered text, and the invalid-then-valid retry test passed. This reinforced the value of testing recovery paths as well as successful saves.

## 4. Corrections and limitations

Some initial test failures came from incorrect fixtures, such as using `List.of` with nullable values or asserting against an outdated request snapshot. Those tests were corrected to represent the intended scenario. Checkstyle also caught formatting and unused-import issues in production changes, which were fixed before the successful checks.

Automated JavaFX tests are not the same as a complete manual acceptance pass. The latest workflow log explicitly records that the application was not manually smoke-tested for that session, and no cross-platform CI run was performed. Earlier manual testing was reported for the authenticated Requester UI, but detailed results for every scenario were not supplied. Team review of generated code, tests, and documentation is still needed.

The work also exposed how much coordination a shared vertical slice needs. Schema and authentication changes affected other roles, so preserving existing Manager behavior and recording the shared contract were important. The migration and shared-schema work still requires review by the teammate responsible for those components.

## 5. What I would change next time

I would make each implementation handoff include the exact requirement IDs, changed files, expected verification commands, and explicit non-goals. This would help keep each stage focused while still making cross-role effects visible.

I would also record manual acceptance results as a short scenario checklist, separately from automated test results. That would make it clear which behaviors were exercised through the running application and which were only verified through tests.

For shared database and session changes, I would arrange teammate review earlier, while the interface and migration are still being shaped. That would give the other role owners a chance to catch integration concerns before the work accumulates around the contract.

## 6. What I learned

I learned that requirement-based tests are most useful when they test the rules independently of the implementation. Ownership, transaction boundaries, state transitions, and visibility rules are easy to miss if tests only mirror the code's current branches.

I also learned to distinguish evidence types. A passing service test, an automated UI test, a manual smoke test, and a cross-platform run establish different things. Recording those distinctions makes the result more trustworthy and makes remaining work easier for teammates to review.

Finally, AI assistance was most effective when its task and ownership boundaries were specific. Agents helped generate tests and documentation, but they still needed review, correction, and clear evidence requirements—especially when a change touched shared authentication or persistence behavior.
