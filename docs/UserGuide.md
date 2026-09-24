# FacilityFlow User Guide

## Overview

FacilityFlow is a Java desktop application for reporting and coordinating
maintenance work. It is intended for Requesters, Technicians, and Facilities
Managers who share one local workspace under the same operating-system user.

This development build supports sign-in and account actions for all three roles.
Requesters can submit requests, view their own saved requests, and edit or cancel
their own `OPEN` requests. Facilities
Managers can view all requests and assign an `OPEN` request to an active
Technician. The Technician route is currently a placeholder and has no work
management controls.

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

#### Edit an open request

1. Open your request using **View selected request**. If its status is `OPEN`,
   choose **Edit request**.
2. The form starts with the saved title, description, location, category, and
   reported urgency. Change the required fields using the same values and length
   rules described under **Submit a maintenance request**.
3. Choose **Save changes**. While saving, the form and **Back to my requests**
   action are disabled. A successful save returns to the detail view with the
   stored changes and a success message. The request keeps its display ID and
   `OPEN` status; its update time and an audit event are saved.
4. Validation or storage errors preserve your entries for correction or retry.
   If a Manager has assigned the request since you opened the form, saving is
   rejected and the assignment remains unchanged. Use **Back to my requests**
   and reopen the request to see its current state; leaving the form discards
   unsaved changes.

Only your own `OPEN` requests can be edited (REQ-007, REQ-011–012).

#### Cancel an open request

1. Open your `OPEN` request and choose **Cancel request**.
2. Enter a required **Cancellation reason** of 5–500 characters. Leading and
   trailing whitespace is removed before validation and storage.
3. Choose **Confirm cancellation**. The confirmation dialog identifies the
   request and explains that cancellation is permanent. Choose **Cancel request**
   in the dialog to proceed. **Keep request** is the default; choosing it or
   dismissing the dialog leaves the request unchanged and retains your reason.
4. While cancellation is saving, the reason and screen actions are disabled.
   Success returns to the detail view with status `CANCELLED`, a success message,
   and the saved cancellation reason. The reason remains available when you
   reopen the detail view after restarting the application. Cancellation and its
   audit event are saved together.
5. Invalid input or a failed save leaves the reason available for correction or
   retry. If a Manager has already assigned the request, cancellation is rejected
   and the assignment remains unchanged. To leave without cancelling, choose
   **Keep request** on the reason screen; this discards the unsaved reason.

Only your own `OPEN` requests can be cancelled. Cancelled requests remain in your
list but cannot be edited, cancelled again, or reopened (REQ-008, REQ-011–012,
REQ-A11).

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

The route displays **Technician work management is not available in this
development build.** There are currently no reachable controls to view assigned
work, open request details, start work, add work logs, or complete work.

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

- The complete Technician backend exists for the personal queue and request detail,
  queue ordering, case-insensitive search by display ID, title, and location,
  filtering by status, category, and priority, starting work, appending internal
  work logs, completing work, and rejecting stale writes after reassignment
  (TEC-001–TEC-013, LIF-016). None of it is connected to the JavaFX Technician
  route, so no Technician queue, search, filter, detail, start, log, or completion
  controls are reachable in the application.
- Manager reassignment of `ASSIGNED` or `IN_PROGRESS` work is implemented behind
  the interface, including reason validation and audit storage, but the Manager
  dashboard has no reassignment controls (MGR-006). Managers can currently assign
  only `OPEN` requests.
- Requesters cannot search or filter requests, add follow-up information, or view
  a full activity history. The saved cancellation reason is available in the
  detail view, but a timeline of status changes and updates is not yet available
  (REQ-004–006, REQ-009, REQ-017).
- Managers cannot yet record requests on behalf of Requesters, correct request
  details, cancel or review work, close/return/reopen requests, manage accounts,
  or view audit history through the dashboard. Manager password reset exists only
  behind the interface.
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
