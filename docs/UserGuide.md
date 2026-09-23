# FacilityFlow User Guide

## Overview

FacilityFlow is a desktop application being developed to help people report
maintenance issues, technicians work on assigned issues, and Facilities Managers
coordinate the work.

The current runnable build is a Requester form preview. It lets you check whether
the details of a maintenance report meet the required rules. It does not send or
save a report.

## Setup and launch

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
