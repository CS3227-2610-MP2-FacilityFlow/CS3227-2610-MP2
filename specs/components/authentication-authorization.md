# Authentication and authorization specification

Status: **Draft v0.1**

## Account model

- **AUT-001:** A user account MUST contain an immutable ID, unique username,
  display name, role, password hash, active flag, created time, and last-updated time.
- **AUT-002:** Usernames MUST be 3–32 characters, use only letters, digits, `.`, `_`,
  or `-`, and be unique case-insensitively.
- **AUT-003:** Display names MUST contain 2–80 non-blank characters after trimming.
- **AUT-004:** Passwords MUST contain at least 10 characters and at least three of:
  lowercase letters, uppercase letters, digits, and symbols.
- **AUT-005:** Passwords MUST be salted and hashed using a maintained password
  hashing function. Plaintext or reversibly encrypted passwords MUST NOT be stored
  or written to logs.
- **AUT-006:** Each account MUST have exactly one role: `REQUESTER`, `TECHNICIAN`,
  or `FACILITIES_MANAGER`.

## First-run bootstrap

- **AUT-007:** If no account exists, the application MUST open a first-run setup
  flow that creates one Facilities Manager account.
- **AUT-008:** First-run setup MUST use the same username, display-name, and password
  validation as later account creation.
- **AUT-009:** After any account exists, the first-run setup flow MUST be unavailable.
- **AUT-010:** Creating the first Manager and recording its audit event MUST be atomic.

## Login and session

- **AUT-011:** Login MUST require username and password and return a generic invalid-
  credentials message that does not reveal whether the username exists.
- **AUT-012:** Inactive accounts MUST receive the same generic failure behavior as
  invalid credentials.
- **AUT-013:** Five consecutive failed attempts for one normalized username within
  ten minutes MUST delay further attempts for 30 seconds. This delay MAY be held
  in memory and reset when the application restarts.
- **AUT-014:** Successful login MUST reset that username's failed-attempt counter,
  create an authenticated session, and open only the dashboard for the account role.
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
- **AUT-028:** Role changes after account creation are outside MVP scope; create a
  new correctly scoped account and deactivate the obsolete account instead.

## Acceptance scenarios

- A valid active account reaches exactly its role dashboard.
- An incorrect password, unknown username, and inactive account produce the same
  external error wording.
- Directly invoking a Manager operation from a Requester or Technician session is
  rejected even when no JavaFX screen is involved.
- Directly requesting another Requester's record or another Technician's assignment
  is rejected without returning its title or description.
- Restarting the application never restores an authenticated session.
- Inspecting the database and application logs reveals no plaintext password.
