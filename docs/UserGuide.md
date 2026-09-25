# FacilityFlow User Guide

## Overview

FacilityFlow helps people report maintenance problems and helps a Facilities
Manager assign the work to Technicians. Each person signs in to a screen for
their role.

A Requester can report a problem and follow their own requests. A Manager can
see all requests and assign new ones. A Technician can see their assigned work,
record progress, and submit finished work for review. The Manager cannot yet
complete the review in the app.

This version runs from the project folder and has no installer.

## Setup and launch

1. Install Java Development Kit (JDK) 25. You do not need to install other
   software separately.
2. Open PowerShell on Windows, or Terminal on macOS or Linux, in the FacilityFlow
   project folder. If the command cannot find Java, set `JAVA_HOME` to your JDK
   25 installation folder.
3. Run the command for your computer:

   **Windows (PowerShell)**

   ```powershell
   .\gradlew.bat run
   ```

   **macOS or Linux**

   ```sh
   sh ./gradlew run
   ```

   The first run may need an internet connection to download the tools used to
   start the app.
4. Sign in with a demo account. The starting password for every account is
   `Welcome123`.

   | Role | Usernames |
   |---|---|
   | Requester | `requester1`, `requester2` |
   | Technician | `technician1`, `technician2` |
   | Facilities Manager | `manager1`, `manager2` |

## Storage

FacilityFlow saves accounts, requests, and Technician work notes in
`facilityflow.db`. It keeps the list of request categories in
`categories.properties`. The app creates both files the first time you start
it. You can find them here:

| Computer | Folder |
|---|---|
| Windows | `%LOCALAPPDATA%\FacilityFlow` |
| macOS | `~/Library/Application Support/FacilityFlow` |
| Linux | `$XDG_DATA_HOME/FacilityFlow`, or `~/.local/share/FacilityFlow` if `XDG_DATA_HOME` is not set |

People who use FacilityFlow under the same computer account share these files.
Saved work is still there after you close and reopen the app. You will need to
sign in again. Text you have entered but have not saved is normally lost when
you close the app.

The starting categories are Electrical, Plumbing, HVAC, Structural, Cleaning,
Safety, and Other. To change the list, edit the `categories` line in
`categories.properties` while the app is closed. Separate names with commas,
keep each name unique, and keep `Other`. If the list is invalid, the app will
ask you to correct the file before it opens. Do not remove or rename a category
already used by a saved request; this version cannot move those requests to a
different category.

## Features

### Requester

#### Sign in and manage your account

Sign in with a Requester account. Use **Refresh account** if your account has
changed, **Change password** to set a new password, or **Log out** when finished.
To change your password, enter the current one and a new one of 8 to 24
characters. The new password is saved without signing you out. Use **Back** to
leave the password screen.

#### Report a problem

1. Choose **New request** and complete all five fields:

   | Field | What to enter |
   |---|---|
   | Title | A short summary, 5 to 100 characters |
   | Description | Details of the problem, 10 to 2,000 characters |
   | Location | Where the problem is, 2 to 120 characters |
   | Category | Choose one of the available categories |
   | Reported urgency | Choose Low, Normal, High, or Emergency |

   Spaces at the start and end of text are removed. Spaces and line breaks
   within your text are kept.
2. Choose **Submit request**. If a field needs correcting, the app shows a
   message beside it and keeps your other entries.
3. After a successful submission, the app opens the saved request and shows a
   confirmation. It gives the request a unique number, such as `FF-000010`.
   Use this number to identify the request when speaking to a Manager. A new
   request has the status `OPEN`, meaning it has not yet been assigned to a
   Technician.

If saving fails, your entries remain in the form so you can try again. If your
sign-in expires, signing in again as the same Requester restores the unfinished
form. Logging out or closing the app discards an unfinished form.

#### View your requests

**My requests** shows only requests you submitted, with the newest first.
Choose **Refresh requests** to check for changes. Select a request and choose
**View selected request** to read its details. On that screen, choose
**Refresh detail** to check its latest status or **Back to my requests** to
return to the list.

#### Practise filling out a request

You can open a separate practice form without saving anything. From the
project folder, run this command on Windows:

```powershell
.\gradlew.bat run "-PmainClass=sg.edu.nus.facilityflow.RequesterPreviewLauncher"
```

On macOS or Linux, run `sh ./gradlew run "-PmainClass=sg.edu.nus.facilityflow.RequesterPreviewLauncher"`.
Enter request details and choose **Check details** to see any corrections
needed. Closing this form discards your entries.

### Technician

#### Sign in and manage your account

Sign in with a Technician account. **Refresh account**, **Change password**,
and **Log out** work as described for Requesters.

#### Find and inspect your work

**Technician work queue** shows only requests currently assigned to you. The
counts above the list show how many are assigned, in progress, or awaiting
Manager review. The list puts higher-priority work first; within the same
priority, more urgent work comes first.

To find a request, type part of its request number, title, or location in
**Search**, or choose **Status**, **Category**, or **Priority**. Choose
**Apply filters**; you can use several filters together. Choose **Reset
filters** to see your full list again, or **Refresh requests** to check for
changes. Select a request to read its details and internal work history.

#### Start work

Select a request marked `ASSIGNED` and choose **Start work**. Its status becomes
`IN_PROGRESS`, meaning you are working on it. The list refreshes and shows a
confirmation. If someone changed the assignment or status while you were
viewing it, the app rejects the action and refreshes the list.

#### Record your work

For an `IN_PROGRESS` request, enter a note in **Describe the work performed**
and the time in **Whole minutes, 1 to 1440**, then choose **Add work log**. The
note must have 1 to 1,000 characters after spaces at its ends are removed. The
time must be a whole number from 1 to 1,440 minutes. A saved note appears in
**Internal work history** with its time and author. You cannot edit or delete
a saved note through the app. Requesters cannot see these internal notes.

If an entry is invalid or cannot be saved, correct it and try again. If the
request has been reassigned, the app refreshes your list instead of saving the
note.

#### Submit finished work

An `IN_PROGRESS` request needs at least one saved work note before you can
submit it. Enter a **Resolution summary for Manager review** of 10 to 2,000
characters, then choose **Submit for Manager review**. The request changes to
`COMPLETED`, remains assigned to you, and awaits Manager review. The app keeps
the summary if submission fails so you can correct it or try again.

### Facilities Manager

#### Sign in and manage your account

Sign in with a Facilities Manager account. **Refresh account**,
**Change password**, and **Log out** work as described for Requesters.

#### View requests

**All requests** shows requests from every Requester. New requests appear
first. Choose **Refresh requests** to see recent changes, and select a request
to read its description.

#### Assign a new request

Select a request marked `OPEN`. In **Assignment**, choose an active Technician
and a Manager priority of Low, Medium, High, or Critical. Choose **Assign
request**. The request changes to `ASSIGNED`, and the app confirms the
assignment. If it cannot save the assignment, it shows an error so you can
correct the selection or try again.

## Current limitations

- Requesters cannot yet edit, cancel, search, or filter their requests, add
  follow-up information, or view a history of status changes.
- Managers can assign new requests but cannot yet report a problem for someone
  else, correct request details, reassign or cancel work, review a Technician's
  completion, close or reopen requests, manage other accounts, or view the
  history of changes in the app.
- A new workspace has demo accounts but no sample requests. A Requester must
  submit a request before a Manager can assign it.

## Disclaimers

Use your organisation's usual reporting channel for real maintenance issues
until FacilityFlow can handle the full process.
