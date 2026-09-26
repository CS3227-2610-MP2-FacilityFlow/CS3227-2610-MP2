# Category catalogue migrations — 26 September 2026

## Goal and prompt

The user asked to implement the specified category rename/removal migrations.
The source behavior is LIF-021 and LIF-022 in
`specs/components/request-lifecycle.md` and the accepted catalogue contract in
`docs/adr/0001-category-catalogue-configuration.md`.

## Instructions and workflow used

- Read the category catalogue contract, lifecycle requirements, workspace startup,
  SQLite transaction boundary, and repository instructions.
- Applied the Ponytail coding skill for a minimal standard-library configuration parser.
- Invoked the repository unit-test agent and User Guide reviewer.
- No commit was made.

## Changes

- Added optional `renames=Old>New,...` and `removals=Old,...` properties to
  `categories.properties`. Removal mappings move affected requests to `Other`.
- Validate mapping syntax and targets before opening the database. Startup rejects
  unmapped stored categories and reports a corrective error.
- Apply every affected request update and `CATEGORY_MIGRATED` audit insert inside
  the existing startup transaction. Audit events use the first active Facilities
  Manager account by ID because the schema has no system actor.
- Updated the startup failure screen to show actionable configuration/database details.
- Documented the configuration syntax and behavior in CategoryCatalogue, User,
  and Developer Guides; updated the ADR and Requester preparation tracker.
- Added Workspace integration tests for migration, malformed and invalid mappings,
  unmapped categories, audit metadata, missing audit actor, and transaction rollback.

## Verification

- Focused `WorkspaceTest`: 10 tests passed.
- Clean Windows Gradle `check`: passed; 185 tests across 13 suites, no failures;
  Checkstyle passed.
- `git diff --check`: passed.
- No manual JavaFX smoke test or cross-platform CI run was performed.

## Errors and corrections

- The first test attempt reused a UNIQUE request display ID. The unit-test agent
  corrected the test helper to generate distinct IDs; the focused suite then passed.

## Outcome and review

LIF-021/LIF-022 category mapping behavior is implemented and automated checks pass.
A team member still needs to review the implementation and documentation.
