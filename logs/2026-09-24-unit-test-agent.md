# AI session summary: Unit test agent

Date: 24 September 2026
Human review: Pending.

## Goal and important prompts

The user requested a reusable unit test agent for FacilityFlow. The agent must
accept the newest feature's changed implementation files as its primary input
and the applicable requirements and acceptance criteria as its secondary input.
It may accept an explicit test directory and must ask for one only when the
repository and supplied context do not provide a reliable location.

The user required the agent to:

- write the tests rather than only propose them;
- test behavior from requirements and component purpose instead of copying the
  implementation's current behavior;
- keep cases granular, descriptive, isolated, and readable as Arrange, Act,
  Assert without section-label comments;
- apply finite-state-machine analysis, equivalence partitioning and boundary
  value analysis, and decision tables when each technique fits the behavior;
- use fakes or stubs for external dependencies and isolated temporary SQLite
  databases for persistence behavior;
- modify test-owned files only, remove temporary files, and report any new test
  dependency instead of adding it;
- run the completed tests, report failures, and preserve valid failing tests
  when they expose implementation defects;
- report tested components and their cases when requested.

The user later clarified that this must be a repository agent named `unit test
agent`, not a skill. It must be invoked after AI-assisted implementation in
both Codex and Claude Code. The implementation agent owns production fixes;
after a fix, the unit test agent reruns the affected tests and reports every
remaining failure to the user.

## Instructions and dependencies

The agent follows `AGENTS.md` and treats the specifications under `specs/` as
the expected behavior. The repository provides JUnit Jupiter 5.13.4, the JUnit
Platform launcher, SQLite JDBC 3.50.3.0, the Gradle wrapper, JaCoCo, and
Checkstyle. Mockito is not present, so the agent uses small fakes or stubs and
reports a missing dependency when a case cannot be implemented with the current
test stack.

The `g-writing-for-agents` guidance was used to make the trigger, ordered
workflow, ownership boundaries, and completion criteria explicit.

## Files and behavior changed

- Added `.codex/agents/unit_test_agent.toml` as the Codex agent definition.
- Added `.claude/agents/unit-test-agent.md` as the equivalent Claude Code
  subagent definition.
- Added `CLAUDE.md` to load the shared repository instructions and invoke the
  Claude subagent after implementation changes.
- Updated `AGENTS.md` with the implementation, test, fix, rerun, and report
  workflow for both supported agent environments.

The unit test agent creates and runs tests but cannot edit production code or
build configuration. The AI agent responsible for the implementation receives
implementation failures and fixes the production code. The unit test agent is
then invoked again to verify the fix.

## Corrections and human decisions

- An earlier skill-based direction was rejected. The final repository setup
  contains agent definitions and no repository unit-test skill.
- Verification of the first agent-only setup found that it covered Codex test
  creation but did not explicitly assign production fixes, require a rerun, or
  configure Claude Code. The workflow and Claude files were added to close
  those gaps.
- Expected results remain requirement-based. Implementation code is inspected
  to identify interfaces, collaborators, and test seams.

## Verification and outcome

- Parsed the Codex TOML definition successfully.
- Checked the Claude agent frontmatter and required test workflow content.
- Checked that Claude loads `AGENTS.md` and that the shared instructions point
  to both agent definitions.
- Checked the changed files for formatting errors.
- Application tests were not rerun for the final correction because it changed
  agent instructions only.

The repository now contains an instruction-driven unit test workflow for Codex
and Claude Code. Generated tests, production fixes, and reported findings still
require team-member review.
