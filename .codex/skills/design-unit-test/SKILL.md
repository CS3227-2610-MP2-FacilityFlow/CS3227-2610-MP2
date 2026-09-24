---
name: design-unit-test
description: Design and create requirement-based JUnit tests for changed implementation files in the CS3227 FacilityFlow project. Use when a newly implemented feature needs tests, including isolated SQLite integration tests for storage behavior.
---

# Design Unit Test

Use this repository workflow for FacilityFlow test creation. The input is a list of changed implementation files; the secondary input is the relevant feature requirements. An explicit test directory is optional.

## Invocation

If coordinating another agent's implementation task, delegate test creation to the project's configured unit-test agent when it is available. Pass the changed implementation file paths and any supplied requirements or test directory. Wait for its result and keep other agents from editing the same test files concurrently. If acting as that test agent, or if no project unit-test agent is configured, execute the workflow below without delegating again.

## Establish expected behavior

1. Require the changed implementation file list. If it is missing or combines unrelated features without a clear target, ask the user or invoking agent to identify the files.
2. Read the applicable repository `AGENTS.md`, specifications under `specs/`, acceptance criteria, and existing tests. Use requirement IDs in test names or display names where useful.
3. Derive expected results from the requirements and component purpose. Read implementation to identify public behavior and test seams, not to copy its current output into assertions. Report essential TBDs or conflicts rather than inventing expectations.
4. Follow the production package when placing tests under `src/test/java`. Honor a supplied test directory. Ask if placement cannot be inferred.

## Design cases

- Map each relevant requirement to an observable behavior before writing tests.
- Keep cases granular: one input, rule, transition, or failure property per case. Give each case a descriptive name.
- Apply equivalence partitions and values immediately below, at, and above meaningful boundaries.
- For stateful behavior, derive cases from the specified state machine. Cover every allowed transition and representative forbidden transitions, including role and object-level authorization.
- Use decision tables when several conditions jointly determine an outcome. Include realistic empty and failure responses from dependencies.
- Avoid redundant cases and impossible scenarios. Preserve valid test inputs and expectations even when they reveal implementation defects.

## Implement and verify

- Make Arrange, Act, and Assert clear through code structure; omit section-label comments.
- Isolate units with small fakes or stubs for storage, time, sessions, and other external dependencies. Keep unit tests independent of JavaFX and a production database.
- When the changed files include repository or transactional behavior, add integration tests using an isolated temporary SQLite database. Verify persistence and rollback where applicable.
- Keep tests deterministic and independent of network access, execution order, local time zone, and a developer's existing files.
- Modify test files and test-owned fixtures only. Do not modify production implementation. If a new test dependency or build change is necessary, report the missing dependency and affected cases; do not edit build configuration.
- After all intended cases are implemented, run targeted tests and relevant repository checks. Correct defects in test code, but do not change implementation or weaken valid expectations to obtain a pass.
- Remove temporary files you created, including temporary databases, without disturbing pre-existing files. If explicitly told to retain any, list each file and its purpose.
- Report tested components, requirement IDs, test cases, commands and results, and each failing case with its likely cause. Distinguish implementation, specification, test, and environment problems. Do not fix implementation defects.
- For a substantial AI-assisted session, prepare the repository-required `logs/` summary using only verified prompts and results for team review.
