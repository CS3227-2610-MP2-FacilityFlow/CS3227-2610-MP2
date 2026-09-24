# Technician read-model test pass — 24 September 2026

## Goal and prompt

Add requirement-based tests for the Technician dashboard, personal queue, safe
assigned-request read behavior, search, filters, deterministic ordering, and
dashboard counts. The supplied scope required test-owned files only and no
production changes.

## Instructions and skill used

- Repository `AGENTS.md` instructions.
- `cs3227-unit-test` skill.
- Relevant specifications: `specs/roles/technician.md` and
  `specs/components/request-lifecycle.md`.

## Test changes

- Extended `TechnicianRequestServiceTest` with dashboard count and dashboard
  authorization assertions, plus the new fake transaction method.
- Extended `SQLiteTechnicianRequestServiceTest` with isolated-database coverage
  for technician-scoped dashboard counts and persisted queue filtering/order.
- Updated `ManagerRequestServiceTest`'s transaction fake for the expanded
  `ManagerAssignmentStore.TransactionContext` interface; the manager tests do
  not use the Technician read method.
- No production files were modified.

The tests cover TEC-001, TEC-002, TEC-003, LIF-017, LIF-018, LIF-019, and
LIF-020 through observable service and SQLite behavior.

## Verification

The first targeted compile caught one test-fixture reference to an undefined
`CREATED_AT` constant. It was corrected to use the SQLite fixture's local
assignment timestamp. The next targeted run initially exposed two test setup
expectation mistakes: the second Technician already had one assigned fixture,
and the category filter is case-preserving. Both were corrected in test-owned
files without changing production behavior.

Commands and results:

```text
.\gradlew.bat test --tests 'sg.edu.nus.facilityflow.service.TechnicianRequestServiceTest' --tests 'sg.edu.nus.facilityflow.service.ManagerRequestServiceTest' --tests 'sg.edu.nus.facilityflow.storage.SQLiteTechnicianRequestServiceTest' --console=plain
BUILD SUCCESSFUL — 59 tests completed.

.\gradlew.bat check --console=plain
BUILD SUCCESSFUL in 48s.
```

The final test pass completed without failures. `git diff --check` was also
clean before the final verification.
