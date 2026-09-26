# FacilityFlow User Guide

## Overview

FacilityFlow helps people report maintenance problems and helps a Facilities
Manager assign the work to Technicians. Each person signs in to a screen for
their role.

Each role has different tasks:

- Requesters report problems and follow their own requests.
- Facilities Managers see all requests and assign new ones.
- Technicians see their assigned work, record progress, and submit finished
  work for review.

The Manager cannot yet complete the review in the app.

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

## Features

### Requester

#### Sign in and manage your account

Sign in with a Requester account. You can then:

- Choose **Refresh account** if your account has changed.
- Choose **Log out** when you are finished.

To change your password:

1. Choose **Change password**.
2. Enter your current password and a new one of 8 to 24 characters.
3. Choose **Change password** again. The new password is saved without signing
   you out. Choose **Back** to leave the password screen.

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

An unfinished form behaves as follows:

- If saving fails, your entries remain so you can try again.
- If your sign-in expires, signing in again as the same Requester restores the
  form.
- Logging out or closing the app discards it.

#### Find and review your requests

**My requests** shows requests owned by your account, newest first. The summary
shows the number of requests in total and the counts that are open, assigned,
in progress, awaiting Manager review, closed, or cancelled.

1. Enter any part of a request number, title, or location in **Search**. Search
   ignores letter case and extra spaces at the beginning or end.
2. Optionally choose a status or category, and/or select **From date** and
   **Through date** to limit requests by when they were created. The chosen
   dates are included.
3. Choose **Apply filters**. You can combine search and filters. If nothing
   matches, the list is empty.
4. Choose **Reset filters** to clear the search and all selections.
5. Choose **Refresh requests** to reload the list, then select a request and
   choose **View selected request**.
6. On the detail screen, choose **Refresh detail** to reload its current
   information, or **Back to my requests** to return to the list.

The detail screen shows the current status, reported urgency, Manager priority
when one has been set, and the created and updated times. **Activity history**
shows status events and follow-up updates visible to you. Technician work notes
and private Manager notes are not shown.

#### Edit or cancel an open request

Only requests with status `OPEN` can be edited or cancelled.

1. Open the request from **My requests**.
2. Choose **Edit request** to change its details. The form starts with the
   saved values. The same required fields and length rules as a new request
   apply.
3. Choose **Submit request** to save. The updated detail appears with a success
   message. If a field is invalid or saving fails, the form keeps your entries
   and shows a message.

To cancel instead, choose **Cancel request**, enter a reason of 5 to 500
characters, and submit it in the dialog. The request becomes `CANCELLED`, and
the reason appears in its activity history. Cancelled requests cannot be edited
or receive follow-up updates.

#### Add a follow-up

1. Open a request that is not `CLOSED` or `CANCELLED`.
2. Enter an update of 1 to 1,000 characters in **Add a follow-up update**.
3. Choose **Add follow-up**. The detail reloads and the update appears in
   **Activity history**.

If the update cannot be saved, the app shows an error. Correct the text or
refresh the request and try again.

#### Practise filling out a request

You can open a separate practice form without saving anything:

1. From the project folder, run this command on Windows:

   ```powershell
   .\gradlew.bat run "-PmainClass=sg.edu.nus.facilityflow.RequesterPreviewLauncher"
   ```

   On macOS or Linux, run
   `sh ./gradlew run "-PmainClass=sg.edu.nus.facilityflow.RequesterPreviewLauncher"`.
2. Enter request details and choose **Check details** to see any corrections
   needed. Closing this form discards your entries.

### Technician

#### Sign in and manage your account

Sign in with a Technician account. **Refresh account**, **Change password**,
and **Log out** work as described for Requesters.

#### Find and inspect your work

**Technician work queue** shows only requests currently assigned to you:

- The counts above the list show how many are assigned, in progress, or awaiting
  Manager review.
- The list puts higher-priority work first. Within the same priority, more
  urgent work comes first.

1. Type part of a request number, title, or location in **Search**. You can also
   choose **Status**, **Category**, or **Priority** to narrow the list.
2. Choose **Apply filters**. You can use several filters together.
3. Select a request to read its details and internal work history.
4. Choose **Reset filters** to see your full list again, or **Refresh requests**
   to check for changes.

#### Start work

1. Select a request marked `ASSIGNED`.
2. Choose **Start work**. Its status becomes `IN_PROGRESS`, meaning you are
   working on it. The list refreshes and shows a confirmation.

If someone changed the assignment or status while you were viewing it, the app
rejects the action and refreshes the list.

#### Record your work

1. Select a request marked `IN_PROGRESS`.
2. Complete both fields:

   | Field | What to enter |
   |---|---|
   | **Describe the work performed** | A note of 1 to 1,000 characters after spaces at the ends are removed |
   | **Whole minutes, 1 to 1440** | The time spent, as a whole number from 1 to 1,440 minutes |

3. Choose **Add work log**. The saved note appears in **Internal work history**
   with its time and author.

After you submit a note:

- Saved notes cannot be edited or deleted through the app. Requesters cannot
  see them.
- If an entry is invalid or cannot be saved, correct it and try again.
- If the request has been reassigned, the app refreshes your list instead of
  saving the note.

#### Submit finished work

1. Select an `IN_PROGRESS` request with at least one saved work note.
2. Enter a **Resolution summary for Manager review** of 10 to 2,000 characters.
3. Choose **Submit for Manager review**. The request changes to `COMPLETED`,
   remains assigned to you, and awaits Manager review.

If submission fails, the app keeps the summary so you can correct it or try
again.

### Facilities Manager

#### Sign in and manage your account

Sign in with a Facilities Manager account. **Refresh account**,
**Change password**, and **Log out** work as described for Requesters.

#### View requests

**All requests** shows requests from every Requester. New requests appear
first.

1. Choose **Refresh requests** to see recent changes.
2. Select a request to read its description.

#### Assign a new request

1. Select a request marked `OPEN`.
2. In **Assignment**, choose an active Technician and a Manager priority of Low,
   Medium, High, or Critical.
3. Choose **Assign request**. The request changes to `ASSIGNED`, and the app
   confirms the assignment.

If the app cannot save the assignment, it shows an error so you can correct the
selection or try again.

## Storage

FacilityFlow creates two files the first time you start it:

- `facilityflow.db` holds accounts, requests, and Technician work notes.
- `categories.properties` holds the list of request categories.

You can find the files here:

| Computer | Folder |
|---|---|
| Windows | `%LOCALAPPDATA%\FacilityFlow` |
| macOS | `~/Library/Application Support/FacilityFlow` |
| Linux | `$XDG_DATA_HOME/FacilityFlow`, or `~/.local/share/FacilityFlow` if `XDG_DATA_HOME` is not set |

People who use FacilityFlow under the same computer account share these files.
When you close and reopen the app:

- Saved work is still there.
- You need to sign in again.
- Text you entered but did not save is normally lost.

The starting categories are Electrical, Plumbing, HVAC, Structural, Cleaning,
Safety, and Other. To change the list:

1. Close the app and open `categories.properties`.
2. Edit the `categories` line. Separate names with commas, keep each name
   unique, and keep `Other`.
3. If saved requests use a category that you are renaming or removing, add the
   matching mapping. `renames` and `removals` are optional; omit a line when it
   has no entries. For example:

   ```properties
   categories=Plumbing,Climate Control,Other
   renames=HVAC>Climate Control
   removals=Electrical,Cleaning,Safety
   ```

   In `renames`, write each old category followed by `>` and its replacement.
   Separate multiple mappings with commas. Each replacement must be in the new
   `categories` list. In `removals`, list old category names separated by
   commas; requests in those categories move to `Other`.
4. Save the file and start the app again. Startup moves affected requests and
   records each change for auditing as one operation. If the file or a mapping
   is invalid, or a change cannot be recorded, the app will not start and its
   database changes are rolled back.

## Current limitations

- Managers can assign new requests but cannot yet report a problem for someone
  else, correct request details, reassign or cancel work, review a Technician's
  completion, close or reopen requests, manage other accounts, or view the
  history of changes in the app.
- A new workspace has demo accounts but no sample requests. A Requester must
  submit a request before a Manager can assign it.

## Disclaimers

Use your organisation's usual reporting channel for real maintenance issues
until FacilityFlow can handle the full process.
