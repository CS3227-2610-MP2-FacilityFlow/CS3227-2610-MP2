# FacilityFlow domain glossary

## Actors

### User account

An authenticated local identity with exactly one active role: Requester,
Technician, or Facilities Manager.

### Requester

The account that owns a maintenance request. A Requester can see only requests
they own, including requests a Manager recorded on their behalf.

### Technician

The account currently assigned to perform maintenance work on a request.

### Facilities Manager

The operational administrator who manages accounts, records requests on behalf
of Requesters, triages work, and reviews completed work.

## Work management

### Request

A maintenance issue that belongs to one Requester and progresses through the
defined lifecycle.

### Recording actor

The authenticated account that records a request. This is normally the Request
owner, but can be a Facilities Manager acting on the owner's behalf.

### Category catalogue

The documented set of valid categories used to classify requests. It includes
explicit mappings for category renames or removals.

### Reported urgency

The Requester's assessment of the issue's urgency: Low, Normal, High, or
Emergency.

### Manager priority

The Facilities Manager's operational ranking for assigned work: Low, Medium,
High, or Critical.

### Assignment

The current responsibility of a Technician for a request.

### Work log

An append-only, timestamped Technician note recording work completed and time
spent.

### Requester update

A follow-up note provided by a Requester and visible to the Requester, assigned
Technician, and Facilities Manager.

### Resolution summary

The Technician's account of the completed work submitted for Manager review.

## Records

### Audit event

An immutable record of a significant account, request, or workflow action.

### Operational log

A diagnostic record for maintainers that excludes passwords and full request
descriptions.
