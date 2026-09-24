# FacilityFlow User Guide

## Overview

FacilityFlow is a Java desktop application for reporting and coordinating
maintenance work. It is intended for Requesters, Technicians, and Facilities
Managers who share one local workspace under the same operating-system user.

This development build supports sign-in and account actions for all three roles.
Requesters can submit requests and view their own saved requests. Facilities
Managers can view all requests and assign an `OPEN` request to an active
Technician. Technicians can view their assigned work, inspect a selected request,
start work on an `ASSIGNED` request, record internal work logs, and submit
completed work for Manager review.

On first launch, FacilityFlow creates its local database, category configuration,
and two demo accounts for each role. This is not a completed product release and
there is no installer.

## Setup and launch

1. Install a Java Development Kit (JDK) 25. You do not need to install Gradle or
   JavaFX separately.
2. If `java` is not available in your terminal, set `JAVA_HOME` to the JDK 25
   installation directory.
3. Open a terminal in the FacilityFlow repository root. The first run may need an
   internet connection to download Gradle and application libraries.
4. Start the application with the command for your operating system:

   **Windows (PowerShell)**

   ```powershell
   .\gradlew.bat run
   ```

   **macOS or Linux**

   ```sh
   sh ./gradlew run
   ```

5. Sign in with one of the demo accounts. Every demo account initially uses the
   password `Welcome123`.

   | Role | Usernames |
   |---|---|
   | Requester | `requester1`, `requester2` |
   | Technician | `technician1`, `technician2` |
   | Facilities Manager | `manager1`, `manager2` |

FacilityFlow stores `facilityflow.db` and `categories.properties` in the
following directory:

- Windows: `%LOCALAPPDATA%\FacilityFlow`
- macOS: `~/Library/Application Support/FacilityFlow`
- Linux: `$XDG_DATA_HOME/FacilityFlow`, or `~/.local/share/FacilityFlow` when
  `XDG_DATA_HOME` is not set

All roles launched by the same operating-system user use these files. Existing
compatible databases are upgraded automatically to schema version 4 and are not
reseeded or reset. FacilityFlow stops safely if the database uses an unsupported
newer schema or does not have the expected structure.

Requests upgraded from schema version 3 may have an unknown assignment time. The
upgrade intentionally leaves that value unknown instead of deriving it from a
different timestamp. Technician queue ordering places unknown assignment times
after known times; the request's next assignment or reassignment records a known
assignment time.

The `categories.properties` file has one `categories` entry containing a
comma-separated list. A new workspace starts with Electrical, Plumbing, HVAC,
Structural, Cleaning, Safety, and Other. Names must be unique, and `Other` must
remain in the list. Invalid entries or additional configuration keys stop startup
before the workspace opens.

## Features

### Requester

#### Sign in and manage the session

1. Enter a demo Requester username and password, then choose **Sign in**. The
   password is masked and cleared after submission. Unknown usernames, incorrect
   passwords, and inactive accounts all show the same invalid-credentials message.
2. Use **Refresh account** to reload the account's active status and role. A role
   change reroutes the current session without another sign-in.
3. Use **Change password**, enter the current password and a new password of 8–24
   characters, then choose **Change password**. No mixture of character types is
   required. Both password fields are cleared on submission; success saves the new
   password and keeps the session signed in.
4. Use **Back** on the password screen to return to the current role route, or use
   **Log out** to clear the session and return to sign-in. Closing the application
   also discards the in-memory session.

#### Submit a maintenance request

1. Choose **New request**.
2. Complete every field:

   | Field | Required value |
   |---|---|
   | Title | 5–100 characters |
   | Description | 10–2,000 characters |
   | Location | 2–120 characters |
   | Category | One value from the configured category list |
   | Reported urgency | Low, Normal, High, or Emergency |

   Leading and trailing whitespace is removed before validation and storage.
   Meaningful spaces and line breaks inside the text are kept.
3. Choose **Submit request**. Field-specific messages beginning with **Error:**
   identify invalid or missing values without clearing the other fields. While the
   save is pending, the form and **Back to my requests** action are disabled to
   prevent duplicate submissions.
4. After a successful save, the detail view shows the generated `FF-` display ID,
   status `OPEN`, the stored field values, and a success message. The request and
   its audit event are saved in `facilityflow.db`. A storage failure leaves the
   entered form values available for another attempt.

If the session expires while a request is being submitted, signing in again as
the same account restores the draft held in memory. Explicit logout or closing
the application discards unsaved input.

#### View saved requests

1. The **My requests** list loads only requests owned by the signed-in Requester,
   newest first. Each entry shows its title, display ID, status, and location.
2. Choose **Refresh requests** to reload the list from the database.
3. Select one request and choose **View selected request**. The detail view shows
   title, description, display ID, status, reported urgency, Manager priority when
   set, category, location, and local creation and update times.
4. Choose **Refresh detail** to reload its latest visible state, including a Manager
   assignment or priority change. Choose **Back to my requests** to return to the
   refreshed list.

Other Requesters' records, internal audit details, and Technician work logs are
not returned to this view.

#### Check fields without saving

The separate validation preview can be launched with:

```powershell
.\gradlew.bat run "-PmainClass=sg.edu.nus.facilityflow.RequesterPreviewLauncher"
```

On macOS or Linux, replace `.\gradlew.bat` with `sh ./gradlew`. Complete the same
five fields and choose **Check details**. The preview reports field errors and
keeps the entries, but it never creates a request or writes application data.

### Technician

#### Sign in and manage the session

Sign in with a Technician account to open the separate Technician route. The
header provides the same **Refresh account**, **Change password**, and **Log out**
actions described for Requesters. The password screen provides **Back**. Password
changes are saved, while logout and application shutdown discard only the
in-memory session.

#### View assigned work

1. When the Technician route opens, the **Technician work queue** loads requests
   currently assigned to the signed-in Technician. It does not show requests
   assigned to other Technicians. The table shows Request ID, title, location,
   Manager priority, reported urgency, and status. If there are no matching
   requests, it shows **No requests are currently assigned to you.** (TEC-001)
2. Choose **Refresh requests** to reload the queue from the database. While a
   load is in progress, the queue and refresh control are disabled. If the
   refresh fails, the page shows a safe error message; an expired or invalid
   session is handled by the normal sign-in flow.

#### Inspect a request

1. Select one row in the queue. The **Request detail** panel shows the request's
   display ID and title, location, current status, Manager priority (or **Not
   set**), and description. Selecting a row does not change the saved request.
2. If a refresh finds that the selected request is no longer assigned to you,
   the selection is cleared.

#### Record internal work

1. Select a request and wait for its **Internal work history** to load. The
   history is available only for requests currently assigned to you. Existing
   entries show their timestamp, Technician author, minutes spent, and note in
   chronological order. **No work logs recorded yet.** means that the selected
   request has no entries. Work logs are internal: they are not returned to
   Requester views. (TEC-005, TEC-007, TEC-011, LIF-007–LIF-010)
2. Add a log only after the request is `IN_PROGRESS`. The **Add accountable
   progress** area contains:

   | Control | Required value |
   |---|---|
   | **Describe the work performed** | Required; 1–1,000 characters after leading and trailing whitespace is removed. The note may contain meaningful spaces and line breaks. |
   | **Whole minutes, 1 to 1440** | Required; a whole number from 1 through 1,440 inclusive. |

   The **Add work log** button is disabled until a request is selected and its
   current status is `IN_PROGRESS`. It is also disabled while the history is
   loading or another action is in progress. A blank note, a note over 1,000
   characters, or minutes outside the stated range is rejected without saving a
   work log. (TEC-005, TEC-006, LIF-007)
3. Choose **Add work log**. A successful save appends one entry, updates the
   request's saved updated time, reloads the queue and history, clears both
   input controls, and shows a confirmation such as **FF-000010 work log saved
   successfully.** The saved entry retains the Technician author, timestamp,
   note, and minutes. There are no edit or delete controls; work-log history is
   append-only through the application. (TEC-007, TEC-013, LIF-007, LIF-010)
4. If the minutes field is not a whole number, the page immediately shows
   **Minutes spent must be a whole number from 1 to 1,440.** Other validation,
   authorization, or storage failures show a safe error message and leave the
   entered values available for another attempt. If the assignment or status
   changed while the request was open, the write is rejected, no log is stored,
   and the queue is refreshed with **The request assignment or status changed.
   The queue was refreshed.** (TEC-012)

#### Start assigned work

1. Select a request whose status is `ASSIGNED`. The **Start work** control is
   enabled only when a request is selected and its current displayed status is
   `ASSIGNED`; it is disabled for other statuses and while another queue action
   is in progress.
2. Choose **Start work**. The application rechecks your Technician role, the
   current assignment, and the persisted status before saving. A successful
   action changes the request to `IN_PROGRESS`, records the transition and one
   audit event, reloads the queue, and shows a confirmation such as **FF-000010
   is now IN_PROGRESS. Work started successfully.** (TEC-004, TEC-A01, LIF-011,
   LIF-012, LIF-016)
3. If the assignment or status changed after the request was displayed, the
   start is rejected and the queue is refreshed with **The request assignment or
   status changed. The queue was refreshed.** If the start operation itself
   fails, a safe error message is shown and no successful confirmation is
   reported. If the follow-up refresh fails after a successful start, the page
   shows the refresh error while the saved transition remains in the database.
   A failed transaction does not leave a partial status or audit-event write.

#### Submit work for Manager review

1. Select an `IN_PROGRESS` request and wait for **Internal work history** to
   finish loading. At least one saved work log is required. The **Submit for
   Manager review** button stays disabled until the history has loaded and
   contains at least one work log.
2. Enter the outcome in **Resolution summary for Manager review**. This is a
   required summary of the work completed. After leading and trailing
   whitespace is removed, it must contain 10–2,000 Unicode code points;
   meaningful spaces and line breaks inside the summary are kept.
3. Choose **Submit for Manager review**. The application rechecks your role,
   current assignment, and `IN_PROGRESS` status before saving. A successful
   submission changes the request to `COMPLETED`, stores the trimmed summary
   and completion time, preserves the current Technician assignment, records
   an audit event, reloads the queue and work-log history, and shows a message
   such as **FF-000010 was submitted for Manager review successfully.** The
   summary field is cleared only after the completion write commits. (TEC-008,
   TEC-009, TEC-013, TEC-A05, LIF-011, LIF-012, LIF-016)
4. A missing or invalid summary is rejected without changing the request or
   its work logs. The summary remains in the text area after a validation,
   authorization, or storage failure so it can be corrected or retried. A
   request with no work log cannot be completed; the service rejects that
   attempt and keeps the request `IN_PROGRESS` with guidance to add a work log
   first. (TEC-008, TEC-A04)
5. If the assignment or status changes while the request is open, the
   completion is rejected, no completion data is stored, and the queue is
   refreshed with **The request assignment or status changed. The queue was
   refreshed.** (TEC-012, TEC-013, LIF-016)

After a successful submission, the request remains in the Technician's queue
with status `COMPLETED`, but Technician work actions are disabled. The current
Manager route does not yet expose review of the work logs or resolution summary,
or the actions to close, return, or reopen the request. (TEC-009, MGR-007–009)

### Facilities Manager

#### Sign in and manage the session

Sign in with a Facilities Manager account to open the Manager dashboard. The
header provides the same **Refresh account**, **Change password**, and **Log out**
actions described for Requesters. The password screen provides **Back**.

#### View the request queue and details

1. The **All requests** table loads every saved request. It shows Request ID,
   title, location, reported urgency, Manager priority, and status.
2. `OPEN` requests appear first by reported urgency and age. Assigned and
   in-progress work follows by Manager priority and oldest update time. Completed
   and terminal work follows by newest update time; display ID breaks ties.
3. Choose **Refresh requests** to reload the queue and active-Technician list.
4. Select one row to show its display ID, title, and full description in the
   **Request detail** panel. Selecting a row does not change stored data.

#### Assign an open request

1. Select a request whose status is `OPEN`.
2. In **Assignment**, choose an active Technician and a required Manager priority:
   Low, Medium, High, or Critical.
3. Choose **Assign request**. The action remains unavailable until all three
   selections are valid and is disabled while the save is pending.
4. A successful assignment saves the Technician and priority, changes the status
   to `ASSIGNED`, records an audit event, refreshes the queue, and shows a success
   message. If validation, authorization, or storage fails, a safe error is shown
   and no successful assignment is reported.

The Manager layout adapts to the window width. The queue and assignment panel
stack in narrower windows and appear side by side at wider sizes. The detail panel
and forms can be scrolled, and the queue can be scrolled horizontally.

## Current limitations

- The Technician route exposes the personal queue, request selection and detail,
  Start work, internal work-log history and entry, and submission of completed
  work for Manager review. Queue search and filtering and dashboard counts are
  not yet reachable through the application (TEC-002).
- Manager reassignment of `ASSIGNED` or `IN_PROGRESS` work is implemented behind
  the interface, including reason validation and audit storage, but the Manager
  dashboard has no reassignment controls (MGR-006). Managers can currently assign
  only `OPEN` requests.
- Requesters cannot edit, cancel, search, or filter requests; add follow-up
  information; or view requester-visible activity history.
- Managers cannot yet record requests on behalf of Requesters, correct request
  details, review work logs or resolution summaries, cancel or close/return/reopen
  requests, manage accounts, or view audit history through the dashboard. Manager
  password reset exists only behind the interface.
- Fresh workspaces contain the six demo accounts but no representative requests,
  so lifecycle examples must first be created and assigned manually.
- Category rename and removal mappings are not implemented. Removing a category
  used by a saved request, adding unknown keys, or using invalid category values
  stops startup safely instead of migrating existing data.
- FacilityFlow has no release installer. The development build must be launched
  from the repository with Gradle.

## Disclaimers

Do not use this development build as the only channel for a real maintenance
issue. Use your organisation's established reporting process until FacilityFlow
has a reviewed release and the complete role workflow.
