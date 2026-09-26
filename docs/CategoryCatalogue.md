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

Team-confirmed on 20 September 2026: use one Java `.properties` file alongside
the shared SQLite database in the OS user's application-data directory. It holds
the category list and explicit rename/removal mappings and is validated at startup.
`yu-sutong` owns configuration and migrations together. Exact filenames/property
keys are documented below.

## Changing the catalogue

- A catalogue entry may be renamed only with an explicit mapping from its former
  name to its replacement.
- At application startup, a valid rename mapping changes every affected persisted
  request to the replacement category.
- Removing a category without a replacement changes every affected persisted
  request to `Other`.
- The category migration and its audit events are one atomic operation. A failure
  leaves neither partial request changes nor partial audit records.

The application uses UTF-8 `categories.properties` beside `facilityflow.db`:

```properties
categories=Plumbing,Climate Control,Other
renames=HVAC>Climate Control
removals=Electrical,Cleaning,Safety
```

`renames` is an optional comma-separated list of `old>new` pairs. Each target
must exactly match a category in the new `categories` list. `removals` is an
optional comma-separated list of old categories; affected requests move to
`Other`. Omit either key when it has no entries. A source can appear in only
one mapping and cannot also be in the new category list. Category names cannot
contain commas; rename entries also use `>` as a separator.

At startup, each affected request is updated and receives a
`CATEGORY_MIGRATED` audit event. The event records the old and new names and the
startup time. Since the application has no system audit identity, it attributes
the event to the first active Facilities Manager account by ID. If no such
account exists, startup fails and the database transaction rolls back.

## Invalid configuration

The application must stop startup with an actionable message, without changing
the database, if the catalogue is malformed, contains duplicate category names,
or includes an invalid rename/removal mapping.

Facilities Managers do not manage categories through the MVP interface.

## Implementation status (26 September 2026)

The application loads UTF-8 `categories.properties` beside `facilityflow.db` with
one key, `categories`, containing comma-separated names. A new file receives the
initial catalogue. Startup rejects empty/duplicate names, missing `Other`, unknown
keys, malformed mappings, and unmapped stored categories. Valid mappings migrate
all affected requests and append their audit events in one startup transaction.
