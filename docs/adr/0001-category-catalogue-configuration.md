# ADR 0001: Use a documented developer-managed category catalogue

Date: 12 September 2026  
Status: Accepted

## Context

FacilityFlow must support a consistent set of maintenance categories while
allowing the team to correct a configured category name or remove a category.
The MVP does not include a Manager screen for configuring categories. Existing
requests must remain valid and traceable when the catalogue changes.

## Decision

FacilityFlow will use a documented developer-managed category catalogue. The
initial catalogue is Electrical, Plumbing, HVAC, Structural, Cleaning, Safety,
and Other. The configuration contract is maintained in
[`docs/CategoryCatalogue.md`](../CategoryCatalogue.md).

On startup, a valid explicit rename mapping migrates affected request categories
to the replacement. A removed category without a replacement migrates affected
requests to `Other`. The migration and audit records occur atomically. A malformed
catalogue, duplicate category, or invalid mapping stops startup with an
actionable error and leaves persistent data unchanged.

## Consequences

On 20 September 2026, yooplo confirmed the team's refinement: one Java
`.properties` file beside the shared database in the OS user's application-data
directory, owned by yu-sutong along with startup validation and atomic category
migrations. This records an accepted design, not completed implementation.

- Managers cannot add, rename, or delete categories through the MVP interface.
- The User Guide and Developer Guide must explain the catalogue contract.
- Configuration changes require tests for successful migrations and safe failure.
- Historical request data remains valid after a documented category change.
