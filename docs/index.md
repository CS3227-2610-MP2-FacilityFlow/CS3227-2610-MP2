---
layout: default
title: FacilityFlow
---

# FacilityFlow

FacilityFlow is a Java desktop application for coordinating maintenance work in
schools, offices, condominiums, and other formal organisations. It keeps every
request, assignment, work update, and management decision in one persistent,
auditable local workspace.

## Built for three roles

- **Requesters** report problems, update their own requests, and follow visible
  progress without seeing internal work notes.
- **Technicians** manage assigned work, record time and internal notes, and submit
  completed work for review.
- **Facilities Managers** oversee all work, prioritise and assign requests,
  review completion, manage accounts, and inspect the audit trail.

## Key capabilities

- Explicit `OPEN → ASSIGNED → IN_PROGRESS → COMPLETED → CLOSED` workflow
- Safe cancellation, reassignment, return-for-rework, and reopening operations
- Role and object-level authorization enforced outside the interface
- SQLite persistence with versioned migrations and atomic audit events
- Searchable queues, accessible role-specific JavaFX screens, and demo data
- Automated Java 25 tests and a three-platform release workflow

## Install and try it

Download the package for your operating system from the
[latest GitHub release](https://github.com/CS3227-2610-MP2-FacilityFlow/CS3227-2610-MP2/releases/latest).
The package contains the application JAR, JavaFX libraries, and launch scripts.
A Java 25 runtime is required.

Follow the [User Guide](UserGuide.html) for installation, demo credentials, and
step-by-step role workflows.

Developers can review the [Developer Guide](DeveloperGuide.html) for architecture,
testing, CI/CD, persistence, monitoring, release, and acknowledgement details.

The source code is available in the
[public repository](https://github.com/CS3227-2610-MP2-FacilityFlow/CS3227-2610-MP2).
