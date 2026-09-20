# Authentication and authorization specification

Status: **Baseline with team-confirmed integration amendments, 20 September 2026**

## Account model

- **AUT-001:** A user account MUST contain an immutable ID, unique username,
  display name, role, password hash, active flag, created time, and last-updated time.
- **AUT-002:** Usernames MUST be 3–32 characters, use only letters, digits, `.`, `_`,
  or `-`, and be unique case-insensitively.
- **AUT-003:** Display names MUST contain 2–80 non-blank characters after trimming.
- **AUT-004:** Passwords MUST contain 8–24 characters inclusive. No character-class
  composition rule applies. Salted hashing under AUT-005 remains required.
- **AUT-005:** Passwords MUST be salted and hashed using a maintained password
  hashing function. Plaintext or reversibly encrypted passwords MUST NOT be stored
  or written to logs.
- **AUT-006:** Each account MUST have exactly one role: `REQUESTER`, `TECHNICIAN`,
  or `FACILITIES_MANAGER`.

## Initial demo workspace

- **AUT-007:** When creating a new local database, the application MUST atomically
  seed two active accounts for each role: Requester, Technician, and Facilities
  Manager. The User Guide MUST document the demo credentials.
- **AUT-008:** The initial workspace MUST include representative requests across
  every request lifecycle state so each role can demonstrate its primary workflow.
- **AUT-009:** The seed operation MUST run only for a newly created database. The
  application MUST NOT reseed or provide an in-app reset after persistent data
  exists.
- **AUT-010:** Seeding accounts, requests, and their required audit events MUST be
  atomic; a failed seed MUST leave no partial demo workspace.

## Login and session

- **AUT-011:** Login MUST require username and password and return a generic invalid-
  credentials message that does not reveal whether the username exists.
- **AUT-012:** Inactive accounts MUST receive the same generic failure behavior as
  invalid credentials.
- **AUT-013:** Withdrawn. Login-attempt throttling is outside the MVP scope.
- **AUT-014:** Successful login MUST create an authenticated session and open only
  the dashboard for the account role.
- **AUT-015:** Logout MUST clear the in-memory session and return to the login screen.
- **AUT-016:** Closing the application MUST discard the session; automatic login is
  outside MVP scope.
- **AUT-017:** Password text MUST be masked by default and MUST NOT remain in UI
  objects longer than required to authenticate or create the hash.

## Authorization

- **AUT-018:** Every protected service operation MUST require an authenticated
  session and check the role before reading or mutating data.
- **AUT-019:** Object-level checks MUST enforce Requester ownership and Technician
  assignment in addition to checking the broad role.
- **AUT-020:** UI visibility or disabled controls MUST NOT be the sole authorization
  mechanism.
- **AUT-021:** Authorization failure MUST return a safe domain error, write no
  persistent business changes, and avoid revealing inaccessible record details.
- **AUT-022:** A deactivated user's existing session MUST be rejected by the next
  protected service call and returned to login.

## Account administration

- **AUT-023:** A Manager MAY create accounts for any of the three roles, provided
  all validation rules pass.
- **AUT-024:** A Manager MAY deactivate or reactivate an account but MUST NOT
  permanently delete it through the UI.
- **AUT-025:** The system MUST reject any action that would leave zero active
  Facilities Manager accounts.
- **AUT-026:** A Manager MUST NOT deactivate the account associated with their own
  current session.
- **AUT-027:** A deactivated Technician MUST NOT receive a new assignment; existing
  non-terminal assignments MUST remain visible to the Manager for reassignment.
- **AUT-028:** A Manager MAY change another account's role. The Manager MUST NOT
  change their own role, and the operation MUST reject any result that leaves zero
  active Facilities Manager accounts.
- **AUT-029:** The system MUST reject changing a Technician's role while that
  Technician has an `ASSIGNED` or `IN_PROGRESS` request. The Manager must first
  reassign or cancel that work; historical authorship remains intact.
- **AUT-030:** An active user MAY change their own password. A Manager MAY reset
  another account's password. A Manager reset MUST invalidate the target's session
  on its next protected service operation.
- **AUT-031:** Account role changes and password changes or resets MUST satisfy the
  relevant validation rules and produce audit events without storing password data.
- **AUT-032:** A role change MUST retain the authenticated session. Every protected
  action MUST recheck the persisted active flag and current role. An action no
  longer permitted by the new role MUST be rejected without business changes;
  the UI MUST route to the current role's screen without requiring another login.
- **AUT-033:** A successful change of the user's own password MUST retain their
  current authenticated session. This does not override deactivation rejection
  under AUT-022 or Manager-reset invalidation under AUT-030.

## Acceptance scenarios

- Passwords of 8 and 24 characters pass the length rule without requiring mixed
  character classes; lengths 7 and 25 fail. Hashing remains mandatory.
- Changing an eligible account's role retains its session, rejects an old-role
  action that is no longer permitted, and routes to the new role without login.
- Changing one's own password retains the current session; a Manager reset
  instead rejects the affected session on its next protected operation.

- A valid active account reaches exactly its role dashboard.
- An incorrect password, unknown username, and inactive account produce the same
  external error wording.
- Directly invoking a Manager operation from a Requester or Technician session is
  rejected even when no JavaFX screen is involved.
- Directly requesting another Requester's record or another Technician's assignment
  is rejected without returning its title or description.
- Restarting the application never restores an authenticated session.
- Inspecting the database and application logs reveals no plaintext password.
- A Manager cannot change a Technician's role while the Technician has assigned or
  in-progress work, and cannot leave zero active Manager accounts.
