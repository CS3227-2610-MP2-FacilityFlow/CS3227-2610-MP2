# FacilityFlow specification index

Status: **Draft baseline v0.2 — requirements confirmed 12 September 2026**

These specifications are the source of truth for product behavior. They are
written before implementation so that the three role owners can work against a
shared contract without inventing incompatible behavior in code.

## How to use the specifications

Normative words have their usual meaning:

- **MUST** is required for the MVP and release.
- **SHOULD** is expected unless a documented reason justifies an alternative.
- **MAY** is optional and must not delay required work.

Every functional or non-functional requirement has a stable identifier. Tests,
pull requests, issues, and relevant documentation SHOULD cite these identifiers.
Do not reuse an identifier for a different requirement. If a requirement is
removed, mark it withdrawn rather than renumbering the remaining requirements.

## Specification set

| File | Purpose |
|---|---|
| [`product-requirements.md`](product-requirements.md) | Problem, goals, scope, glossary, and product-wide rules |
| [`roles/requester.md`](roles/requester.md) | Requester permissions, screens, and acceptance criteria |
| [`roles/technician.md`](roles/technician.md) | Technician permissions, screens, and acceptance criteria |
| [`roles/facilities-manager.md`](roles/facilities-manager.md) | Manager permissions, screens, and acceptance criteria |
| [`components/request-lifecycle.md`](components/request-lifecycle.md) | Request data, status machine, transitions, and validation |
| [`components/authentication-authorization.md`](components/authentication-authorization.md) | Accounts, login, sessions, and role enforcement |
| [`components/persistence-observability.md`](components/persistence-observability.md) | Storage, transactions, audit trail, and operational logs |
| [`components/user-interface.md`](components/user-interface.md) | Shared JavaFX interaction and accessibility contract |
| [`quality-delivery.md`](quality-delivery.md) | Testing, CI/CD, packaging, monitoring, and documentation |
| [`acceptance-test-matrix.md`](acceptance-test-matrix.md) | Release-level end-to-end scenarios and traceability |

## Requirement identifier prefixes

| Prefix | Area |
|---|---|
| `PRD` | Product-wide requirement |
| `REQ` | Requester role |
| `TEC` | Technician role |
| `MGR` | Facilities Manager role |
| `LIF` | Request lifecycle and data |
| `AUT` | Authentication and authorization |
| `DAT` | Persistence and audit data |
| `OBS` | Operational logging and monitoring |
| `UIX` | User interface and accessibility |
| `QLT` | Automated quality and testing |
| `REL` | Build, packaging, and release |
| `DOC` | Documentation and Agentic SE evidence |

## Change process

1. Open a specification change before or together with the implementation PR.
2. Explain the user problem, affected requirement identifiers, and alternatives.
3. Obtain review from every role owner affected by the change.
4. Update acceptance criteria and the test matrix when observable behavior changes.
5. Implement only after blocking TBDs and cross-role disagreements are resolved.
6. Record important architectural choices in the Developer Guide when created.

Minor wording fixes that do not alter behavior do not require a separate proposal.

## Baseline decisions still required

- Confirm the exact JavaFX release-packaging strategy after a cross-platform spike.
- Confirm with the teaching team what evidence they expect for desktop-app monitoring.

These decisions do not prevent repository setup, architecture scaffolding, or
test-harness work. Role feature implementation must wait when a listed decision
directly affects it.
