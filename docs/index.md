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
- Automated Java 25 tests and release checks for Windows, Linux, Intel Mac,
  and Apple silicon Mac

## Install and try it

Check the assets on the
[GitHub releases page](https://github.com/CS3227-2610-MP2-FacilityFlow/CS3227-2610-MP2/releases)
and choose a JAR matching your processor. Version 1.0.1 introduces a separate
Apple silicon Mac JAR; the older v1.0.0 JAR supports only x86-64 systems. A
Java 25 runtime is required for every release artifact.

Follow the [User Guide](UserGuide.html) for installation, demo credentials, and
step-by-step role workflows.

Developers can review the [Developer Guide](DeveloperGuide.html) for architecture,
testing, CI/CD, persistence, monitoring, release, and acknowledgement details.

The source code is available in the
[public repository](https://github.com/CS3227-2610-MP2-FacilityFlow/CS3227-2610-MP2).
