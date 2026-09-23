# FacilityFlow User Guide

<<<<<<< HEAD
## Overview

FacilityFlow is a desktop application being developed to help people report
maintenance issues, technicians work on assigned issues, and Facilities Managers
coordinate the work.

The current runnable build is a Requester form preview. It lets you check whether
the details of a maintenance report meet the required rules. It does not send or
save a report.
=======
This development build supports login, Requester submission/list/detail, and
Manager assignment. It is not a completed product release.

Follow the [developer setup](DeveloperGuide.md#setup-and-commands) and run
`./gradlew.bat run` on Windows, or `sh ./gradlew run` on macOS/Linux.

## Sign in

New workspaces create the following demo accounts, all with password **Welcome123**:

| Role | Usernames |
|---|---|
| Requester | `requester1`, `requester2` |
| Technician | `technician1`, `technician2` |
| Facilities Manager | `manager1`, `manager2` |

Enter your username and password, then choose **Sign in**. An incorrect password,
unknown username, or inactive account produces the same invalid-credentials message.
The password field is cleared when submitted. Each role has separate navigation;
the header shows your account, role, and **Log out** action.

**Change password** requires your current password and a new password of 8–24
characters, with no composition requirement. A successful change keeps you signed
in. Logout and closing the app discard the session. If your account is deactivated
or a Manager resets your password, the next protected action requires login again.
**Refresh account** picks up your current role; role changes do not require login.

## Submit and follow a request

1. Sign in as a Requester and choose **New request**.
2. Enter title, description, and location, and choose category and reported urgency.
3. Choose **Submit request**. Field errors begin with **Error:** and preserve input.
   Pending saves disable submission and navigation to prevent duplicate clicks.
4. Success opens the stored request detail with its generated `FF-` ID and `OPEN`
   status. **Back to my requests** reloads your own list, newest first.
5. Select a row and choose **View selected request**. **Refresh detail** picks up
   the latest assignment/status. Storage failures retain the form for retry.

Only your requests appear. Internal audit details/notes are not displayed. Visible
activity history, editing, cancellation, follow-ups, and filters are not implemented.
If your session expires during submission, signing back in as the same account
restores the draft in memory. Explicit logout or closing the app discards unsaved input.

## Manager handoff

The refreshed interface uses teal primary actions, labelled inline errors and
scrollable forms. Resize the window to suit your workspace: account actions wrap
on narrower windows, and the Manager assignment panel moves below the queue.
Use the panel's scrollbar to reach assignment controls on a shorter window;
the queue scrolls horizontally to show all columns. Resizing keeps form input
and selections. The default content area is 1,024 × 700, with layouts also
checked at 760 × 600 and 1,440 × 900.

Log out, then sign in as `manager1`. Select the Requester's `OPEN` request, choose
an active Technician and Manager priority, then choose **Assign request**. The
confirmed result becomes `ASSIGNED`. Sign back in as the owning Requester to
inspect the updated detail. Technician work management is not yet available.

## Local workspace

Committed requests and accounts persist between launches in `facilityflow.db`:

- Windows: `%LOCALAPPDATA%\FacilityFlow`
- macOS: `~/Library/Application Support/FacilityFlow`
- Linux: `$XDG_DATA_HOME/FacilityFlow`, or `~/.local/share/FacilityFlow`

All roles share this workspace. Existing databases are upgraded but never reseeded
or reset. Old test databases with placeholder password hashes do not gain valid
demo credentials automatically. Fresh-workspace lists start empty; representative
requests across all lifecycle states are still pending.
>>>>>>> master

## Setup and launch

<<<<<<< HEAD
1. Install a Java Development Kit (JDK) 25. No separate Gradle or JavaFX
   installation is needed.
2. Open a terminal in the FacilityFlow project folder.
3. Run the command for your operating system:

   **Windows (PowerShell)**

   ```powershell
   .\gradlew.bat run
   ```

   **macOS or Linux**

   ```sh
   sh ./gradlew run
   ```

If Java is not already available in your terminal, set `JAVA_HOME` to your JDK
25 installation folder before running the command. The first launch may download
the build tools and application libraries, so it needs an internet connection.

There is currently no installed release package, sign-in account, configuration
file, or application data file for this preview. Closing its window discards all
entered details.

## Features

### Requester

#### Check maintenance report details

The **New maintenance request** window shows five required fields. Complete them
and select **Check details**.

| Field | What to enter |
|---|---|
| Title | A short summary with 5 to 100 characters. |
| Description | A description of the problem with 10 to 2,000 characters. |
| Location | Where the problem is, with 2 to 120 characters. |
| Category | Choose Electrical, Plumbing, HVAC, Structural, Cleaning, Safety, or Other. |
| Reported urgency | Choose Low, Normal, High, or Emergency. |

Spaces at the beginning and end of text do not count towards the character
limits. Spaces and line breaks within the text are kept. If any field needs
attention, guidance appears beside that field and your other entries remain in
the form. When every field is valid, the preview confirms the check.

No maintenance request, history, or other application data is created by this
preview.

### Facilities Manager

The current preview has no Facilities Manager sign-in or dashboard, so there
are no Manager actions to take.

### Technician

The current preview has no Technician sign-in or work queue, so there are no
Technician actions to take.

## Current limitations

The preview cannot submit, edit, cancel, search, or show maintenance requests.
It has no sign-in, saved data, Requester request history, or Technician work
queue. Facilities Managers cannot yet view a request queue, choose a priority,
or assign an open request to an active Technician. The categories are fixed for
this preview and cannot be changed through the window.

## Disclaimers

Do not use this preview to report a real maintenance issue. Use your usual
reporting channel until FacilityFlow can submit and save requests.
=======
`categories.properties` beside the database uses a `categories` comma-separated
list, initially the seven categories in the [catalogue contract](CategoryCatalogue.md).
Names must be unique and include `Other`. Invalid configuration stops startup.
Rename/removal mappings and migration of existing categories are not implemented;
unknown keys or removing a category used by a stored request stop startup safely.

The validation-only preview remains available with
`./gradlew.bat run "-PmainClass=sg.edu.nus.facilityflow.RequesterPreviewLauncher"`.
Its **Check details** action never saves. Release installers and the remaining
role workflows will be documented when implemented and verified.
>>>>>>> master
