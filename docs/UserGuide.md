# FacilityFlow User Guide

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

Title, description, and location limits count Unicode code points after trimming.
A supplementary emoji counts once; combining marks and joined emoji sequences
can count as multiple characters even when displayed as one symbol.

`categories.properties` beside the database uses a `categories` comma-separated
list, initially the seven categories in the [catalogue contract](CategoryCatalogue.md).
Names must be unique and include `Other`. Invalid configuration stops startup.
Rename/removal mappings and migration of existing categories are not implemented;
unknown keys or removing a category used by a stored request stop startup safely.

The validation-only preview remains available with
`./gradlew.bat run "-PmainClass=sg.edu.nus.facilityflow.RequesterPreviewLauncher"`.
Its **Check details** action never saves. Release installers and the remaining
role workflows will be documented when implemented and verified.
