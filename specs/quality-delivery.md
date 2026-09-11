# Quality, delivery, and documentation specification

Status: **Draft v0.2 — requirements confirmed 12 September 2026**

## Automated testing

- **QLT-001:** Domain, validation, permission, and lifecycle behavior MUST have fast
  unit tests independent of JavaFX and a production database.
- **QLT-002:** Every allowed and representative forbidden lifecycle transition MUST
  have an automated service test.
- **QLT-003:** Repository behavior and transactional rollback MUST have integration
  tests using an isolated temporary SQLite database.
- **QLT-004:** Authentication tests MUST cover valid login, invalid credentials,
  inactive users, authorization by role, object-level access, and safe password storage.
- **QLT-005:** Critical role workflows MUST have system-level or focused JavaFX UI tests.
- **QLT-006:** Tests MUST be deterministic and MUST NOT depend on network access,
  execution order, local time zone, or a developer's existing files.
- **QLT-007:** Model, service, auth, and storage code SHOULD maintain at least 80%
  line coverage, but coverage MUST NOT replace behavior-focused assertions.
- **QLT-008:** Every fixed defect MUST gain a regression test when practical.

## Code quality

- **QLT-009:** The codebase MUST use consistent automated formatting and static analysis.
- **QLT-010:** JavaFX controllers MUST NOT issue SQL or contain lifecycle/authorization rules.
- **QLT-011:** Storage classes MUST NOT depend on JavaFX.
- **QLT-012:** Duplicated validation or permission rules across role controllers are defects;
  shared rules belong in domain/services.
- **QLT-013:** Unexpected exceptions MUST not be silently swallowed.
- **QLT-014:** Public classes and non-obvious decisions SHOULD have useful documentation,
  while comments MUST NOT merely repeat the code.

## CI/CD

- **REL-001:** GitHub Actions MUST run tests and static checks for every pull request
  and push to `master`.
- **REL-002:** CI MUST use Java 25 and a committed Gradle wrapper.
- **REL-003:** CI MUST build/test on Linux, Windows, and macOS, or document and verify
  an equivalent platform matrix where a job is technically unavailable.
- **REL-004:** Failed required checks MUST block merging to `master` once branch
  protection is enabled.
- **REL-005:** The release workflow MUST produce checksummed runnable artifacts from
  a version tag and attach them to a formal GitHub release.
- **REL-006:** Release artifacts MUST include required JavaFX runtime/native libraries
  or provide supported platform-specific artifacts if one universal JAR is not viable.
- **REL-007:** The team MUST perform clean-machine smoke tests on Windows, macOS, and
  Linux before the final release and record tester, artifact, OS, and result.
- **REL-008:** The application MUST expose its version in an About screen or equivalent.
- **REL-009:** `master` MUST contain exactly the submitted release state by
  29 September 2026, 2:00 PM SGT, and receive no later changes.

The team must clarify the precise acceptable JavaFX artifact format with the
teaching staff early rather than assuming a single native-dependent fat JAR works
unchanged on every operating system.

## Performance and resilience

- **QLT-015:** With 10,000 seeded requests, login and opening a role dashboard SHOULD
  finish within two seconds on a typical student laptop after application startup.
- **QLT-016:** Search/filter interactions SHOULD return within one second for 10,000
  requests under the deployment assumption.
- **QLT-017:** Recoverable validation or storage failures MUST NOT crash the JavaFX process.
- **QLT-018:** A failed multi-write operation MUST not leave partial business data.

## Documentation and Agentic SE evidence

- **DOC-001:** `docs/UserGuide.md` MUST describe only released behavior and include
  installation, seeded-demo-workspace credentials and guidance, role-based usage,
  troubleshooting, and category-catalogue configuration guidance.
- **DOC-002:** `docs/DeveloperGuide.md` MUST describe architecture, data model,
  authorization, request lifecycle, testing, CI/CD, release process, monitoring,
  and acknowledgements.
- **DOC-003:** The GitHub Pages product website MUST identify the product, users,
  key features, installation route, and documentation links.
- **DOC-004:** `docs/Reflections.md` MUST analyze at least three meaningful agent
  skills with concrete examples, benefits, corrections, and proposed improvements.
- **DOC-005:** Every substantial AI-assisted work session MUST have a human-verified
  summary under `logs/` covering prompts/goals, skill used, changes, verification,
  corrections, and outcome.
- **DOC-006:** Logs and reflections MUST NOT claim a prompt, test, result, or human
  decision that did not occur.
- **DOC-007:** Reused ideas, code, assets, and documentation MUST be acknowledged in
  the Developer Guide, including source and extent of reuse.

## Feature definition of done

A requirement is done only when:

1. its specification and acceptance criteria are approved;
2. implementation respects the documented architecture and permissions;
3. automated tests cover the normal, invalid, and unauthorized paths as applicable;
4. CI passes;
5. the relevant user/developer documentation is accurate; and
6. AI-assisted work has a verified log summary.
