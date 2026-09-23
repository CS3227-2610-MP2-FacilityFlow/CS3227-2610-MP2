@AGENTS.md

<<<<<<< HEAD
When implementing or changing any user-visible FacilityFlow behavior, use the
project `user-guide-reviewer` subagent proactively before declaring the feature
complete. The subagent must review `docs/UserGuide.md` and update it when the
verified behavior requires a change.
=======
After implementing or changing FacilityFlow production code, invoke the project
`unit-test-agent` subagent before declaring the implementation complete. Follow
the test, fix, rerun, and report workflow in `AGENTS.md`.
>>>>>>> master
