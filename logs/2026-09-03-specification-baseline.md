# AI session summary: FacilityFlow specification baseline

Date: 3 September 2026  
Verification status: **Requires human review**

## Goal

Understand the CS3227 MP2 brief, choose a feasible multi-role product, prepare
the required GitHub setup, and establish a specification-driven baseline before
implementation.

## Prompt and discussion summary

- The assignment brief was provided and the user asked for a walkthrough and a
  plan for the group discussion.
- FacilityFlow, a facilities-maintenance request system, was proposed and accepted.
- The group asked for repository-level AI context in `AGENTS.md`.
- The group then proposed a specification-driven approach in which role and shared
  component contracts are written before implementation.
- The user authorized creation of the public course repository and publication of
  the specification baseline after it was prepared.

This is a summary rather than a verbatim transcript. The team must compare it
with the actual conversation before changing the verification status.

## Decisions captured

- Product name: FacilityFlow
- Organization: `CS3227-2610-MP2-FacilityFlow`
- Repository: `CS3227-2610-MP2`
- Three roles: Requester, Technician, and Facilities Manager
- Core flow: report, triage/assign, start, record work, complete, review, close
- Java 25 desktop baseline using JavaFX and Gradle
- SQLite proposed as the local persistent store
- Specifications use stable requirement identifiers and precede implementation
- Individual role ownership remains undecided

## Artifacts produced

- Root `README.md`
- Repository agent guide in `AGENTS.md`
- Product-wide requirements and a specification index
- Separate detailed specifications for all three roles
- Shared specifications for lifecycle/data, authentication/authorization,
  persistence/audit/observability, UI behavior, quality, and delivery
- An end-to-end acceptance-test matrix

No application implementation was produced in this session.

## Corrections and human judgment

The initial planning mistakenly treated the two GitHub usernames first mentioned
as the complete team and drafted a two-role version. Later discussion and the
organization membership showed three team members: `ngkhengyang`, `yooplo`, and
`yu-sutong`. The agent guide and specification set were corrected to use three
roles. The team should verify this correction and assign one role to each person.

The detailed requirements contain proposed product decisions, not teaching-team
requirements. In particular, the group still needs to approve:

- role ownership;
- the category list;
- password and temporary lockout rules;
- whether local logs/audit evidence satisfy the monitoring expectation; and
- the cross-platform JavaFX packaging strategy.

## Agent skill usage

Browser-control instructions were used to inspect the signed-in GitHub organization
and prepare repository creation. No custom FacilityFlow project skill was created
or evaluated yet. Future logs must not count the three proposed project skills as
used until their instructions exist and they have been exercised against real work.

## Verification performed

- Confirmed the organization contains three members.
- Listed every generated specification file.
- Searched the specification set for stale two-person terminology and lifecycle
  status consistency.
- Reviewed requirement prefixes and the release acceptance matrix.
- No product tests were run because product code does not yet exist.

## Human verification checklist

- [ ] The team confirms this session summary is accurate.
- [ ] The team approves or edits the product scope and non-goals.
- [ ] Each team member reviews the specification for the role they may own.
- [ ] All three members review shared lifecycle and authorization rules.
- [ ] The team resolves the baseline decisions listed in `specs/README.md`.

After review, replace the verification status with the reviewer names and date.
