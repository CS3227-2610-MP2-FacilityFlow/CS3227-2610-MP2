# Account menu refinement

- User prompt: after discussing whether account actions belong under Settings,
  the user authorized grouping them under an account menu.
- Instructions: AGENTS.md and the ongoing ponytail skill; required unit test
  agent and user guide reviewer delegated again.
- Scope: on `ui/clear-navigation-tabs`, replaced the three header actions with
  native menu items under a right-aligned role/username account menu. Kept Main
  workspace visible and highlighted only while active; added a Change password
  heading and retained Back. Log out follows a separator. Requester action-row
  work from the previous session remains intact.
- Updated UIX-005 and developer guidance; user-guide and test updates delegated.
- Verification: `gradlew.bat test check` passed, 263 tests with zero failures,
  errors, or skips, including Checkstyle. Updated tests cover menu order and
  separator, keyboard opening and header fit at 760×600, 1024×700, and 1440×900
  for all roles, workspace highlighting, password heading/Back, About preserving
  the page, logout, and pending-operation disabling. Requester action-row tests
  remain passing. Screenshots regenerated under `build/ui-navigation-review`.
  Native cross-platform window checks were not run.
- All generated work remains for team-member review.
  No commit, merge, push, or release performed.
