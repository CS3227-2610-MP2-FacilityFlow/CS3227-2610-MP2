# User interface specification

Status: **Draft v0.2 — requirements confirmed 12 September 2026**

## Role separation and navigation

- **UIX-001:** After authentication, the application MUST open exactly the dashboard
  associated with the account's role.
- **UIX-002:** Role dashboards MUST use separate view/controller classes. Shared
  visual components MAY be reused without merging role-specific navigation.
- **UIX-003:** Every authenticated screen MUST show the display name, role, and a
  logout action.
- **UIX-004:** Protected screens and actions outside the active role MUST not appear
  in navigation and MUST still be blocked by service authorization.
- **UIX-005:** Users MUST be able to return from a detail/form view to their role's
  list or dashboard without losing committed work.

## Forms and feedback

- **UIX-006:** Required fields MUST be visibly identified before submission.
- **UIX-007:** Validation MUST identify the affected field and a concrete correction;
  a generic “invalid input” message alone is insufficient.
- **UIX-008:** Validation errors MUST preserve other entered values.
- **UIX-009:** Saving MUST prevent accidental duplicate submissions while the
  operation is pending.
- **UIX-010:** A successful create/update/transition MUST produce visible confirmation
  and refresh the displayed persisted state.
- **UIX-011:** Cancellation, account deactivation, request cancellation, and closing
  MUST require a confirmation dialog describing the affected record and result.
- **UIX-012:** Unexpected or persistence errors MUST use user-safe language, preserve
  recoverable input, and offer an appropriate retry or exit path.

## Lists and empty states

- **UIX-013:** Each table/list MUST label columns clearly, provide deterministic
  ordering, and show progress for operations that can visibly take time.
- **UIX-014:** Empty collections and zero-result filters MUST show distinct messages.
- **UIX-015:** Search/filter state MUST be visible, and a one-action reset MUST exist.
- **UIX-016:** Status, priority, category, and timestamps MUST use consistent wording
  and formatting across all three role interfaces.

## Accessibility and layout

- **UIX-017:** Essential actions MUST be reachable using the keyboard with a logical
  focus order.
- **UIX-018:** Every text input and non-decorative control MUST have an accessible label.
- **UIX-019:** Meaning MUST NOT rely on color alone; badges/icons must include text.
- **UIX-020:** Normal text and controls SHOULD meet WCAG AA contrast guidance.
- **UIX-021:** The UI MUST remain usable at a 1,024 × 700 content area and SHOULD
  adapt cleanly when enlarged.
- **UIX-022:** Dialogs MUST identify a default safe action; destructive actions MUST
  never be the implicit default.
- **UIX-023:** Dates MUST display local date, time, and time-zone abbreviation where
  ambiguity matters.

## Role screen inventory

| Capability | Requester | Technician | Manager |
|---|---:|---:|---:|
| Own dashboard | Yes | Yes | Yes |
| Create request | Yes | No | Record on behalf of an existing Requester |
| View all requests | No | No | Yes |
| View assigned requests | No | Yes | Yes |
| Add requester update | Yes | Read | Read |
| Add internal work log | No | Yes | Read |
| Assign/reprioritize | No | No | Yes |
| Complete work | No | Yes | Review |
| Close/reopen/cancel operationally | Own open cancellation only | No | Yes |
| Manage accounts and audit | No | No | Yes |

At least one automated UI or system-level test MUST verify that each role reaches
the correct dashboard and cannot navigate to another role's screen.
