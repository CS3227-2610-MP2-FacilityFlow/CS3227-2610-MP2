# Reflections on Basic Agentic Software Engineering

This document answers the current MP2 reflection prompt: how we selected and
defined skills for a customised AI agent, how we checked that those skills
worked, where they improved the engineering process, where they required human
correction, and what we learned about designing an effective single agent.

The detailed personal notes remain in [`docs/reflections/`](reflections/). The
examples below are grounded in repository instructions, committed agent/skill
definitions, tests, pull requests, and verified session summaries rather than
retrospective claims without evidence.

## How we selected skills

We did not give the agent one broad instruction such as “finish the app”. We
identified recurring tasks where the expected evidence and ownership boundary
could be stated precisely:

| Engineering need | Focused skill or agent behaviour | Evidence expected |
|---|---|---|
| Challenge role, permission, validation, and lifecycle code | Requirement-based unit-test design skill and unit-test agent | Test cases derived from specifications, targeted execution, failure classification |
| Keep user-facing documentation aligned with reachable behavior | User-guide reviewer agent | Implementation-to-guide comparison, corrected `docs/UserGuide.md`, limitations kept explicit |
| Build a dense but accessible JavaFX operations interface | UI/UX design-intelligence skill plus repository UI rules | Keyboard labels, visible focus, grouped forms, responsive layout, actionable feedback |

These were appropriate because each task had a different failure mode. Tests
can accidentally mirror implementation defects; guides can document planned
features as if they are usable; and visual guidance can optimise appearance
while missing desktop accessibility or operational density. A narrow contract
made each result easier to review.

## Skill example 1: requirement-based unit-test design

### Definition and use

The team created the project skill in
`.codex/skills/design-unit-test/SKILL.md` and the corresponding Codex and Claude
agent definitions. Its input contract includes changed implementation files,
requirement IDs, acceptance criteria, and an optional test directory. Its output
contract restricts the agent to test-owned files and requires it to run the
relevant tests and classify failures as implementation, specification, test, or
environment problems.

The skill tells the agent to select an appropriate testing technique rather
than mechanically generating examples:

- boundary-value analysis for title, reason, password, work-note, and time limits;
- equivalence partitions for roles, active state, assignment ownership, and
  valid/invalid catalogue values;
- finite-state-machine analysis for request lifecycle transitions; and
- decision tables for account administration and role/permission combinations.

It was exercised on the Technician workflow and later on the complete Manager
workflow. Technician tests covered the valid
`ASSIGNED → IN_PROGRESS → COMPLETED` path, forbidden states, work-log limits,
missing completion evidence, stale reassignment, and authorization. The Manager
work used the same principle for assignment, reassignment, close, return,
reopen, cancellation, account safety, filtering, history, and transaction
rollback.

### Benefits

This skill improved coverage quality because it asked what the specification
allows and forbids before reading the implementation branches. The SQLite tests
were especially valuable: an injected audit-insert failure demonstrated that a
state transition and audit event rolled back together. That evidence is stronger
than a mock that merely verifies two methods were called.

The skill also protected code ownership. When a generated test exposed a stale
selection or invalid-dialog recovery defect, the implementation agent corrected
production code and the test agent reran the test. The test was not weakened to
match defective behaviour.

### Corrections and limitations

The initial definition did not clearly say who fixes production defects or that
the test agent must run again after a fix. Those handoff rules were added. Some
generated tests also used unsuitable fixtures, including an immutable list that
could not contain a null filter value and assertions against an outdated request
snapshot. Human review corrected the fixtures without reducing the required
behaviour.

Passing unit and integration tests do not prove packaging or usability. We kept
automated test evidence separate from manual smoke tests and cross-platform CI.

### Improvement

If repeated, each generated test group should state which technique it selected
and why. A compact coverage matrix mapping requirement, partition/state, test,
and result would make omissions easier to identify during review.

## Skill example 2: user-guide implementation reviewer

### Definition and use

The project-scoped user-guide reviewer is defined in
`.codex/agents/user_guide_reviewer.toml` and its Claude equivalent. It receives
changed user-visible behaviour and requirement IDs, then inspects the reachable
UI, tests, launch path, and build setup. It may edit documentation but not
production code.

The important custom rule is that a tested service is not automatically a user
feature. Behaviour appears under **Features** only when a user can reach it from
the released application. Otherwise it belongs under **Current limitations**.
This rule prevented the earlier Manager password-reset service from being
presented as an account-management screen before such a screen existed.

### Benefits

The reviewer repeatedly caught drift created by incremental development. It
updated the guide after authentication, Requester recovery behaviour,
Technician work management, category migration, and Manager completion. This
made documentation review part of the definition of done instead of a final-day
memory exercise.

It also encouraged user-oriented evidence: exact launch commands, demo
credentials, persistent-data locations, field limits, safe retry behaviour, and
the distinction between Requester-visible history and internal work logs.

### Corrections and limitations

The first instructions were too vague. The agent copied requirement IDs into
user-facing prose, listed obvious table columns without explaining the task,
and placed several validation rules inside long paragraphs. Those outputs were
technically accurate but hard to scan.

The team refined the agent with role-based headings, a launch-path check, short
numbered procedures, tables for structured field rules, and an explicit
readability pass. Human review remained necessary because “easy to understand”
is more subjective than a lifecycle assertion.

### Improvement

Next time, the agent should render the Markdown and run a short task-based
usability check: can a new user find installation, credentials, one primary
workflow per role, recovery advice, and data locations in under a minute? It
should also produce a claim-to-evidence list so reviewers can verify every
statement efficiently.

## Skill example 3: JavaFX UI/UX design intelligence

### Definition and use

For the Manager foundation and completion work, the implementation agent used a
UI/UX search skill with the detected `javafx` stack. The task was an internal
facilities operations dashboard used at a desktop, so the selected design dials
favoured low visual variance, subtle motion, and high information density. We
combined the search result with the repository's UIX-001–023 requirements.

Useful JavaFX-specific recommendations included:

- associate visible labels with every control;
- use keyboard mnemonics for frequent form and lifecycle actions;
- preserve a strong focus indicator;
- group long administration forms into clear sections;
- keep actions and feedback textual rather than relying on colour; and
- reflow the request/detail split when the window becomes narrow.

These rules influenced the Manager overview, request filters, full history,
lifecycle controls, account administration, and audit tabs. The interface keeps
the existing identifiers used by automated cross-role UI tests and moves
storage/service work off the JavaFX thread.

### Benefits

The skill supplied a repeatable accessibility checklist at a time when it would
have been easy to concentrate only on completing many Manager actions. It also
helped keep the interface consistent with the Requester and Technician screens:
the same feedback colours, control sizing, table treatment, safe confirmations,
and 1,024 × 700 target are used across roles.

### Corrections and limitations

The generic design-system search recommended an operations landing-page pattern,
oversized typography, animated effects, and a web-font pairing. Those suggestions
did not fit an offline JavaFX administration tool. We rejected them and retained
only the accessible green palette, dense dashboard spacing, explicit focus,
and form guidance.

This example showed that a skill's output is advice, not authority. Stack and
usage context must override superficial keyword matches. Automated layout tests
also cannot replace a human checking readability, tab order, and dialog wording
on each target operating system.

### Improvement

A future version should route desktop enterprise queries away from landing-page
patterns before generating a design system. It should include JavaFX-specific
checks for tab traversal, screen-reader accessible text, dialog default buttons,
table virtualization, and native font/layout differences across Windows,
macOS, and Linux.

## Where the AI created extra work

AI assistance was not always faster. The first Gradle 9 test run failed because
the generated build omitted the explicit JUnit Platform launcher. The dependency
was added and the checks rerun. Generic UI recommendations had to be filtered.
Generated documentation needed restructuring. Schema and demo-data changes made
old tests' assumptions about numeric IDs invalid, so fixtures had to identify
their own records instead of assuming an empty database.

These corrections were useful because they revealed where our instructions were
underspecified. They also reinforced the rule that generated code, tests, and
claims remain drafts until a team member reviews the diff and actual command
output.

## What we learned about an effective single AI agent

An effective engineering agent needs less persona and more contract:

1. **A bounded responsibility.** The test agent owns tests, the documentation
   reviewer owns user-facing claims, and the implementation agent owns production
   fixes.
2. **Authoritative inputs.** Requirement IDs, accepted decisions, and changed
   files are supplied explicitly; the agent must not infer product rules from
   current code alone.
3. **Observable completion.** “Done” means named checks ran, results were
   recorded, limitations were stated, and another person can inspect the evidence.
4. **Failure behaviour.** The agent must report ambiguity, failing tests, and
   unavailable environments rather than silently weakening requirements.
5. **Human judgement.** Accessibility, readability, architectural trade-offs,
   cross-role impact, and release readiness still require team review.

The largest productivity gain came from using one agent in a disciplined loop:
inspect the specification, implement a bounded change, invoke a focused testing
skill, fix production defects, invoke the documentation skill, and record the
verified result. The skills were valuable not because they removed human work,
but because they made the work and its evidence more systematic.
