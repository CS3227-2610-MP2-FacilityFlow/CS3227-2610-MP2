# Category catalogue configuration contract

FacilityFlow validates every request category against a developer-managed
catalogue. The initial catalogue contains:

- Electrical
- Plumbing
- HVAC
- Structural
- Cleaning
- Safety
- Other

The concrete configuration-file path and syntax will be selected during
implementation. Its behaviour must satisfy the contract below.

## Changing the catalogue

- A catalogue entry may be renamed only with an explicit mapping from its former
  name to its replacement.
- At application startup, a valid rename mapping changes every affected persisted
  request to the replacement category.
- Removing a category without a replacement changes every affected persisted
  request to `Other`.
- The category migration and its audit events are one atomic operation. A failure
  leaves neither partial request changes nor partial audit records.

## Invalid configuration

The application must stop startup with an actionable message, without changing
the database, if the catalogue is malformed, contains duplicate category names,
or includes an invalid rename/removal mapping.

Facilities Managers do not manage categories through the MVP interface.
