# Preserve assignment time and internal work history in the shared request store

Status: accepted 24 September 2026. For the Technician workflow, the team chose an
additive schema version 4 in the existing shared SQLite transaction seam: current
assignment time is stored separately from mutable update time, work logs retain
their original Technician author, completion metadata remains on the request, and
work-log audits target the request without copying the note. Legacy assignment
times remain unknown rather than being inferred from `updated_at`; completion-cycle
history is deferred until return/reopen can create a second completion. Yu-sutong
and yooplo approved the shared-storage and Requester-visibility effects, as reported
during the implementation session.
