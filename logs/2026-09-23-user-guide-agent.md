# User guide agent and guide format — 23 September 2026

Status: Draft for team-member verification.

## Goal and prompt

The user asked to "create an agent for this project whose role is to review and
update the user guide documentation" whenever a feature is added or changed.
They specified `docs/UserGuide.md`, plain and concise user-facing language,
and the Overview, Setup and launch, Features, optional Current limitations,
and optional Disclaimers sections.

## Instructions used and changes

Followed `AGENTS.md` and the skill-creator, g-writing-for-agents, and OpenAI Docs
guidance. Added the project-scoped `user_guide_reviewer` agent configuration and
an invocation rule in `AGENTS.md`. Reworked `docs/UserGuide.md` to the requested
structure using the current Requester preview, form validation, and Gradle
launch target as evidence. No application behavior changed.

## Verification and corrections

- Parsed the agent TOML with bundled Python; required keys are present.
- Ran `git diff --check`; it passed. Checked guide headings, field limits,
  button text, and default launch class against the source.
- Did not run Gradle tests or launch the UI because this session changed only
  agent instructions and documentation. The custom agent has not yet been
  exercised on a subsequent feature change.
- The first patch attempt to replace the guide failed and was retried as an
  update. The system `python` and `py` commands were unavailable; bundled
  Python was used for TOML validation.

## Outcome requiring human review

The guide now describes the form preview and its limits. A team member still
needs to review these generated edits and this summary. No human approval or
verification is claimed here.
