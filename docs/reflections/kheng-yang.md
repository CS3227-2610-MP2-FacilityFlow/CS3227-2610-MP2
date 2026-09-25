# Reflection on My FacilityFlow AI Agents

## 1. Tasks and skill selection

I created two project-scoped agents for recurring tasks in FacilityFlow:

1. I created the `unit test agent` to write requirement-based tests after
   implementation files change. Many of the test approaches given to this agent was learnt from one of the modules I am currently taking (CS4218 Software Testing) in an attempt to establish an agent to implement test cases based on proper unit testing approaches.
2. I created the `user_guide_reviewer` to check and update
   `docs/UserGuide.md` after user-visible behaviour changes.

I kept these agents separate instead of asking one general agent to write
production code, tests, and documentation. I selected the agent based on the
type of change and the evidence that I needed:

| Triggering change | Agent | Why this agent fits |
| --- | --- | --- |
| Java model, service, storage, or UI implementation files change. | `unit test agent` | The change requires requirement-based cases, authorization checks, state-transition checks, boundary tests, and SQLite integration tests where applicable. |
| User-visible behaviour is added or changed. | `user_guide_reviewer` | The guide must describe the controls, validation, persistence, launch path, and limitations of the feature. |

For the unit test agent, initially I considered having each unit test approach be invoked as
an individual skill. However, which unit test approach to use when creating test cases heavily 
depends on the function or component, hence I decided to compile the different approaches into 
a `design-unit-test` skill, providing the different appraoches and sample instances for each 
approach. I then used the [`writing-for-agents`](https://www.skills.sh/mattpocock/skills/writing-for-agents) 
skill I found line to help define the agents. 

## 2. How I defined and checked the agents

I used the repository instructions and the relevant specifications to define
the expected behaviour. This includes specific information/file changes to determine what the agent should inspect in order to avoid combining unrelated features in one test task. My main design decision was to set a clear boundary around each agent's responsibility rather than giving both agents broad instructions.

I checked the agent definitions by parsing the Codex TOML, checking the Claude
agent frontmatter and repository references. These checks only showed that the 
instructions were well-formed. They did not prove that the agents were useful, so I also ran them on the Technician completion work in order to test them.

### Unit-test agent

The unit-test agent has a clear input contract: changed implementation files,
applicable requirement IDs and acceptance criteria, and an optional test
directory. Its instructions require it to:

- derive expected results from specifications instead of copying the current
  implementation;
- use state-machine analysis, equivalence partitions, boundary values, and
  decision tables when they fit the feature;
- use fakes or stubs for unit tests and an isolated temporary SQLite database
  for persistence tests;
- edit test-owned files only;
- run targeted tests and relevant repository checks; and
- report every failure and identify whether it came from the implementation,
  specification, tests, or environment.

I used the unit-test agent to add coverage in
`TechnicianRequestServiceTest.java` and `AuthenticatedWorkflowTest.java`, where I 
reviewed and evaluated the unit tests created by the agent. The
tests covered successful completion, preserved assignment and audit data,
invalid-summary retention, missing evidence, duplicate clicks, and stale
reassignment. I reviewed the additions and reran the affected tests. The
targeted tests passed, followed by a successful full Gradle `check` including
Checkstyle, tests, and JaCoCo.

### User guide agent

The user-guide agent has a different input contract. It inspects the
implementation, reachable UI, tests, build setup, and relevant specifications.
Its initial instructions require it to organise the guide based on a format I provided, 
list granular user actions, and put only reachable behaviour under Features. 
Code or tests without a usable launch path are described under Current limitations 
instead of being presented as available features.

Here is the format of the user guide (along with the description of each part) which I defined:
- Overview - Everything written here should be high level, be concise and a user without technical background should be able to understand it.
  - High level description, purpose and target user (if any)
  - Rough high level information of the features in the application.
  - Any other information that should be known by the user (e.g. first launch, configuration)
- Setup and launch - Clear and step-by-step instructions to set up and run the application. This section should also talk about important file locations (if any, e.g. data file), different OS environments and pre-requisites.
- Features - All features of the application should be broken down and documented clearly and concisely in simple english.
  - GUI description should be provided to guide the user if needed
  - Field/Data inputs, if any, should be described clearly. This includes whether they are required/optional and validation rules.
  - If data persistence is involved, include a description of how data application data is handled.
- Current limitations - This section should describe the current limitations of the application. Limitations should be determined based on the existing requirements (e.g. gaps in application features). Note that this may or may not include potential features that are out of scope of the current version of the application. The agent must not come up with new potential features on their own, but focus on what is lacking in the application. This section may be omitted if none can be identified by the agent.
- Disclaimers - Any important disclaimers or actions the user should actively avoid. This section can be omitted if none can be identified by the agent.

To check the agent, I simply looked through the user guide after implementation of 
a few features, not limited to features for my role and analyzed the content and formatting provided by the agent. 

## 3. Tasks handled effectively with the agents

Together, I used the two agents as a completion loop. I implemented the
behaviour, used the test agent to challenge its rules, and used the guide agent
to check how users could access and understand the behaviour. I still reviewed
the generated changes, but the agents made that review more focused.

### Unit-test agent

I used the unit-test agent to turn the Technician completion requirements into
concrete test cases instead of creating only a checklist. The agent successfully 
created both positive and negative test cases while analyzing different possible 
inputs and external component results. It also applied various unit test approaches 
which I have specified, for instance:
- The agent applied boundary value analysis to work-log validation by testing the
minimum and maximum valid note lengths and minutes spent, followed by invalid
values just outside those limits. For example, it accepted 1 and 1,000 Unicode
code points and 1 and 1,440 minutes, while rejecting 1,001 code points, 0
minutes, and 1,441 minutes.
- The agent also applied finite-state-machine analysis to the Technician workflow.
It tested the valid `ASSIGNED -> IN_PROGRESS -> COMPLETED` path and rejected
starting work from `IN_PROGRESS` or `COMPLETED`, adding work logs outside
`IN_PROGRESS`, and completing a request without work-log evidence.

Production-code ownership was also kept separate. When a test exposed a stale-
selection problem, the implementation agent fixed the production behaviour. I
then invoked the test agent again. This prevented me from weakening the test
assertions just to make the build pass.

### User guide agent

The agent successfully stuck to the formatting I provided and managed to provide 
clear, step-by-step instructions. Most features are clearly defined and match the actual behavior of the application.

## 4. Where I had to guide or correct the agents

### Unit-test agent

I corrected the first unit-test-agent setup. I had not initially made the
production-fix owner and the required post-fix rerun explicit. I added those
handoff rules so that the test agent reports defects while the implementation
agent fixes production code. I also configured the equivalent Claude Code
workflow and clarified that I needed a repository agent rather than only a
generic reusable skill.

### User guide agent

I had to make the user-guide instructions more specific. The first version did
not clearly require role-based feature categories or distinguish a tested
service from a reachable feature. I added the Requester, Technician, and
Facilities Manager headings and the launch-path requirement.

Some parts of the user guide felt liek the agent simply documenting the requirements rather than describing features in a user-centric manner. It even adds the requirement IDs in paranthesis, for instance (in an older version of the user guide):
```
Restarting FacilityFlow reloads committed accounts, requests, work logs, and
audit records from these files, but never restores an authenticated session. Sign
in again after restarting. (AUT-016, E2E-016)
```

## 5. What I would change next time


## 6. What I learned about designing an effective AI agent

Throughout my definition of the agents, I tried to be as detailed as possible to avoid 
false assumptions made by agents and to adjust based on the project. This seems to have 
worked well and the agents seem to follow clearly defined instructions without much issues. 
Giving an agent a narrow task, specific inputs, clear ownership, and explicit completion criteria generated more reliable results. For example, “write good tests” is too broad. 
“Given these changed files and requirement IDs, edit only test-owned files, use isolated storage for persistence, run these checks, and report every failure” gives the agent a
workflow that I can review.

I also learned that I need to define completion in terms of evidence, not just
file changes. An agent can create a plausible test or polished guide while
missing a permission rule, an unreachable screen, or a failed environment
check. I therefore need to tell the agent what counts as evidence and what it
must report when evidence is unavailable.


