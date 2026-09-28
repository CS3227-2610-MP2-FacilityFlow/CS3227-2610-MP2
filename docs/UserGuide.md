# FacilityFlow User Guide

## Overview

FacilityFlow helps people report maintenance problems and coordinate them from
reporting through completion. Each person signs in to a screen for their role.

Each role has different tasks:

- Requesters report problems and follow their own requests.
- Facilities Managers triage all requests, review completed work, manage user
  accounts, and inspect the permanent record of important actions.
- Technicians see their assigned work, record progress, and submit finished
  work for review.

FacilityFlow saves its workspace under the signed-in computer user's data
folder. A newly created workspace includes demo accounts and six sample
requests, one in each request status, so every role can explore its main
workflow immediately. Existing workspaces are kept and are not reseeded.

FacilityFlow has no native installer. It requires Java 25. Choose the release
JAR for your computer before launching it.

## Setup and launch

1. Install Java 25. The download includes FacilityFlow and its required
   libraries, but it does not include Java. Open Terminal on macOS or Linux,
   or PowerShell on Windows, and check your Java version:

   ```sh
   java -version
   ```

   The first line must show version 25. If it shows an older version, select
   your Java 25 installation before launching FacilityFlow. An error saying
   `class file version 69.0` and `up to 61.0` means the command used Java 17.
2. Open the [GitHub releases page](https://github.com/CS3227-2610-MP2-FacilityFlow/CS3227-2610-MP2/releases),
   choose version **v1.0.1**, and download the file for your computer from
   **Assets**:

   | Computer | File to download |
   |---|---|
   | 64-bit Windows, 64-bit Linux, or Intel Mac | `facilityflow-1.0.1.jar` |
   | Apple silicon Mac (M-series chip) | `facilityflow-1.0.1-macos-aarch64.jar` |

   These files become available when v1.0.1 is published. If v1.0.1 is not
   listed yet, do not use the older v1.0.0 JAR on an Apple silicon Mac.
3. Open Terminal or PowerShell in the folder where you downloaded the JAR.
   On 64-bit Windows, 64-bit Linux, or an Intel Mac, run:

   ```sh
   java -jar facilityflow-1.0.1.jar
   ```

   On an Apple silicon Mac, run:

   ```sh
   java -jar facilityflow-1.0.1-macos-aarch64.jar
   ```

   If `java -version` showed an older version even after installing Java 25,
   replace `java` in the launch command with the full path to your Java 25
   `java` program.
4. Sign in with a demo account. The starting password for every account is
   `Welcome123`.

   | Role | Usernames |
   |---|---|
   | Requester | `requester1`, `requester2` |
   | Technician | `technician1`, `technician2` |
   | Facilities Manager | `manager1`, `manager2` |

5. Choose **About** after signing in to see the application version and a short
   description of FacilityFlow. Choose **Log out** when finished.

If the app cannot open its workspace, read the message on the opening screen.
Correct an invalid `categories.properties` file, check that the workspace folder
is writable, and then restart the app.

## Features

### Requester

#### Sign in and manage your account

Sign in with a Requester account. The header shows your role and username, such
as **Requester - requester1**. Choose **Main workspace** to return to your
role's main screen and load any account changes. Choose **Log out** when you
are finished.

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
   dates are included. Choose **-** in a status or category list to remove that
   filter.
3. Choose **Apply filters**. You can combine search and filters. If nothing
   matches, the list is empty.
4. Choose **Reset filters** to clear the search and all selections.
5. Choose **Refresh requests** to reload the list, then select a request and
   choose **View selected request**.
6. On the detail screen, choose **Refresh detail** to reload its current
   information, or **Back to my requests** to return to the list.

The detail screen labels your **Description** and shows the current status,
reported urgency, Manager priority when one has been set, and the created and
updated times. **Activity history** describes status changes, reasons, and your
follow-up updates in plain language. Technician work notes and private Manager
notes are not shown.

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

If the update cannot be saved, an error appears beside the follow-up box.
Correct the text or refresh the request and try again.

### Technician

#### Sign in and manage your account

Sign in with a Technician account. The header shows **Technician -** followed
by your username. **Main workspace**, **Change password**, and **Log out** work
as described for Requesters.

#### Find and inspect your work

**Technician work queue** shows only requests currently assigned to you:

- The counts above the list show how many are assigned, in progress, or awaiting
  Manager review.
- The list puts higher-priority work first. Within the same priority, more
  urgent work comes first.

1. Type part of a request number, title, or location in **Search**. You can also
   choose **Status**, **Category**, or **Priority** to narrow the list. Choose
   **-** in any of these lists to remove that filter.
2. Choose **Apply filters**. You can use several filters together.
3. Select a request to read its details, work notes, and any follow-up updates
   from its Requester in **Work and Requester history**. Each entry names its
   author by username.
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

3. Choose **Add work log**. The saved note appears in **Work and Requester
   history** with its time and the author's username.

After you submit a note:

- Saved notes cannot be edited or deleted through the app. Requesters cannot
  see them.
- If an entry is invalid or cannot be saved, read the error beside the affected
  field or **Add work log**, correct it, and try again.
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

Sign in with a Facilities Manager account. The header shows **Manager -**
followed by your username. **Main workspace**, **Change password**, and
**Log out** work as described for Requesters.

The Manager workspace has **Overview**, **Requests**, **Accounts**, and **Audit**
tabs. It opens on **Requests**.

#### Review the operational overview

1. Open **Overview** to see the number of requests in each status and at each
   Manager priority.
2. Review **Active assignments per Technician** to compare the number of
   assigned and in-progress requests held by each active Technician.
3. Choose **Refresh overview** to reload all Manager information.

#### Find and inspect any request

The **Requests** tab shows requests from every Requester. Its normal order puts
unassigned `OPEN` requests first, from Emergency to Low reported urgency and
then oldest first. Assigned and in-progress work follows, ordered by Manager
priority. Completed, closed, and cancelled requests follow with the most
recently updated first.

1. Enter any part of a request number, title, location, Requester name or
   username, or Technician name or username in **Search**. Search ignores letter
   case and spaces at the beginning or end.
2. Optionally choose a status, category, Manager priority, Technician, **From**
   date, and/or **Through** date. Dates limit requests by creation date and
   include both chosen dates. Choose **-** in a list to remove that filter.
3. Choose **Apply filters**. You can combine search and filters, and an empty
   result is allowed.
4. Choose **Reset filters** to clear every search and filter value, or **Refresh
   requests** to reload the current result without changing the filters.
5. Select a request. **Request detail** shows its description, the Requester and
   Technician usernames, creation and update times on separate lines, and a
   resolution summary when available.
6. Read **Full history** for its important actions, Requester updates, and
   Technician work logs, including time spent. Actions and reasons appear in
   plain language. Selecting another request loads that request's history.

#### Record a request for a Requester

Use this when a problem was reported by telephone or another channel. The
selected Requester becomes the owner and can use the normal Requester actions.

1. On **Requests**, choose **Record request on behalf**.
2. Select an existing Requester and complete the request fields:

   | Field | What to enter |
   |---|---|
   | Requester | The Requester account that will own the request |
   | Title | A short summary, 5 to 100 characters |
   | Description | Details of the problem, 10 to 2,000 characters |
   | Location | Where the problem is, 2 to 120 characters |
   | Category | One of the configured categories |
   | Reported urgency | Low, Normal, High, or Emergency |

3. Choose **Record request**. The new request receives a number and status
   `OPEN`, and the history records that the Manager entered it for the
   Requester.

#### Correct request details

A correction updates factual details without changing the request's owner,
status, assignee history, work logs, resolution, or request number. It is
separate from the lifecycle actions below and is available in every status.

1. On **Requests**, select a request and choose **Correct selected details**.
2. Correct the title, description, location, category, reported urgency, and/or
   Manager priority. The text fields follow the same length rules as a new
   request. Existing Manager priority cannot be cleared. A request cancelled
   before triage may remain without a Manager priority.
3. Choose **Save correction**. The request stays in the same status, and the
   correction is added to its history.

If the app reports that the request changed, choose **Refresh requests**,
reselect it, review the latest details, and enter the correction again.

#### Assign or reassign work

To assign a new request:

1. Select a request marked `OPEN`.
2. Under **Lifecycle actions**, choose an active Technician and a Manager
   priority of Low, Medium, High, or Critical.
3. Choose **Assign request**. The request changes to `ASSIGNED`.

To transfer active work:

1. Select a request marked `ASSIGNED` or `IN_PROGRESS`.
2. Choose a different active Technician and enter a reason of 5 to 500
   characters.
3. Choose **Reassign**. The request returns to `ASSIGNED` under the chosen
   Technician. Existing work logs and the Manager priority remain in its
   history.

Only active Technician accounts appear in the selection. If another user has
changed the request since it was loaded, choose **Refresh requests** and try
again.

**Manager priority** appears under **Lifecycle actions** only while an `OPEN`
request is being assigned. To change it later, use **Correct selected details**.
If a lifecycle action fails, read the error beside the affected field or the
action buttons.

#### Review and finish completed work

1. Select a request marked `COMPLETED`.
2. Read its resolution in **Request detail** and its work logs in **Full
   history**.
3. To accept the result, choose **Close completed work**, then confirm. The
   request changes to `CLOSED`.
4. To send it back instead, select an active Technician, enter a reason of 5 to
   500 characters, and choose **Return for rework**. The request changes to
   `ASSIGNED`; its earlier completion and work records remain in the history.

#### Reopen or cancel a request

To reopen accepted work:

1. Select a request marked `CLOSED`.
2. Select an active Technician and enter a reason of 5 to 500 characters.
3. Choose **Reopen closed request**. The request changes to `ASSIGNED`, while
   its earlier completion remains recorded.

To stop work that should no longer proceed:

1. Select a request marked `OPEN`, `ASSIGNED`, or `IN_PROGRESS`.
2. Enter a reason of 5 to 500 characters.
3. Choose **Cancel request**, then confirm. The request changes to `CANCELLED`.

#### Manage accounts

Open **Accounts** to see each account's username, display name, role, and active
or inactive status. Accounts are retained for their history and are not deleted.

To create an account:

1. Complete the account fields:

   | Field | Essential rules |
   |---|---|
   | Username | 3 to 32 letters, digits, dots, underscores, or hyphens; unique regardless of letter case |
   | Display name | 2 to 80 characters after spaces at the ends are removed |
   | Role | Requester, Technician, or Facilities Manager |
   | Initial password | 8 to 24 characters |

2. Choose **Create account**. The new account is active and can sign in with
   the chosen password. If the username is already taken, the error appears
   beside **Username**.

To update an existing account:

1. Select the account in the list.
2. To change its active status, choose **Deactivate / reactivate**, then confirm.
   A deactivated user is rejected when they next use a protected action.
3. To change its role, choose the new role and **Change role**. A user who is
   signed in can choose **Main workspace** to move to the new role's screen.
4. To set a new password for another account, enter 8 to 24 characters and
   choose **Reset password**. The affected user's current session ends when they
   next use a protected action; the Manager performing the reset stays signed
   in.

After a successful account change, the Manager workspace reloads automatically.
Newly eligible Requesters and Technicians become available immediately in
request and assignment controls, while accounts that are no longer eligible are
removed from those choices. A Technician selection is kept only when that
account remains eligible.

The app prevents a Manager from deactivating or changing their own account,
prevents removal of the last active Manager, and prevents a Technician's role
from changing while they still have assigned or in-progress work. Reassign or
cancel that work first.

#### Inspect the audit trail

An audit trail is the permanent record of important account and request actions.
It can be read but not changed or deleted in the app.

1. Open **Audit**.
2. Optionally enter part of a request number, actor name, or action type, and/or
   choose **From** and **Through** dates. The chosen dates are included.
3. Choose **Apply audit filters** to show matching events.
4. Choose **Reset audit filters** to return to the full audit trail.

Actions and reasons in the audit trail appear in plain language.

## Storage

FacilityFlow creates its workspace the first time you start it:

- `facilityflow.db` holds accounts, requests, and Technician work notes.
- `categories.properties` holds the list of request categories.
- The `logs` folder holds rotating operational logs used to diagnose startup and
  unexpected application errors. Up to five log files of about 1 MB each are
  retained.

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

Demo accounts and requests are added only when the database is first created.
Starting a newer version with an existing database does not replace its data or
add the samples again.

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

- FacilityFlow has no native installer and does not include Java. Install Java
  25 before launching the release JAR, or install JDK 25 to run from the source
  project.
- The v1.0.1 JAR for 64-bit Windows, 64-bit Linux, and Intel Macs does not run
  natively on Apple silicon Macs. Use the separate Apple silicon JAR there.
- Categories cannot be changed while the app is open or through a Manager
  screen. Close FacilityFlow and use `categories.properties` as described above.

## Disclaimers

- Every demo account starts with the publicly documented password
  `Welcome123`. Before using a workspace for real information, sign in to each
  retained demo account and change its password, or have a Manager deactivate
  accounts that are not needed.
- Do not edit, replace, or delete `facilityflow.db` while FacilityFlow is open.
- Editing `facilityflow.db` or other workspace files incorrectly may cause
  errors or change saved information. Keep a backup before editing them.
