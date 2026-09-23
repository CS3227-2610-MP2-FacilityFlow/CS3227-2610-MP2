# FacilityFlow User Guide

## Overview

FacilityFlow is being built for people who report maintenance problems,
technicians who fix them, and managers who coordinate the work. The current
build offers a preview of the request form. It checks the details you enter
but does not send or save a request.

## Setup and launch

1. Install [Java Development Kit (JDK) 25](https://adoptium.net/temurin/releases/?version=25).
   You need a desktop display and internet access for the first run.
2. Open a terminal in the FacilityFlow project folder. Set `JAVA_HOME` to the
   folder where you installed JDK 25, replacing the example path below, then
   start the preview:

   **Windows (PowerShell)**

   ```powershell
   $env:JAVA_HOME = 'C:\path\to\jdk-25'
   ./gradlew.bat run
   ```

   **macOS or Linux**

   ```sh
   export JAVA_HOME=/path/to/jdk-25
   sh ./gradlew run
   ```

The first run downloads the tools needed to start the preview. There is no
installed release or application data file for this preview.

## Features

### Check request details

The **New maintenance request** window has five required fields:

| Field | What to enter |
|---|---|
| Title | A short summary, 5–100 characters. |
| Description | Details of the problem, 10–2,000 characters. |
| Location | Where the problem is, 2–120 characters. |
| Category | Choose Electrical, Plumbing, HVAC, Structural, Cleaning, Safety, or Other. |
| Reported urgency | Choose Low, Normal, High, or Emergency. |

The character limits are checked after spaces at the beginning and end are
removed. Select **Check details** to see guidance beside any invalid field.
Your entries stay in the form so you can correct them. If all fields are valid,
the preview confirms the check; it does not submit a request.

## Current limitations

The preview has no login, request history, or Requester, Technician, or
Facilities Manager workflows beyond this form check. It cannot submit requests
or save your entries; closing the window discards them. The category choices
are fixed in this preview and cannot be configured here.

## Disclaimers

Do not rely on this preview to report a maintenance issue. Use your usual
reporting channel until FacilityFlow request submission is available.
