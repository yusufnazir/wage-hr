# Filter field standards (chip-based, URL-state backed)

This guide defines an application-wide contract for building **table/list filter UI** (filter chips, menu/select controls, clear filters behavior) and the corresponding **URL query parameter** mapping for server-side pagination correctness.

## Inspiration (Stripe pattern)
- Stripe’s “filter controls” pattern uses a **chip filter bar** above a DataTable, where each chip represents one filterable attribute.
- Each chip has two states:
  - **Suggested** (no value selected): shows a `+` and opens a menu.
  - **Active** (value selected): shows the selected value with an `×` and allows clearing.
- Active chips are rendered separately from the menu-trigger path to avoid simultaneous “close + navigation” event issues.
- A **Clear filters** affordance is shown only when at least one filter is active.

(See: [Stripe Filter controls](https://docs.stripe.com/stripe-apps/patterns/filter-controls) and the associated dashboard search/filter docs.)

## Goals
1. Consistent UX across modules (same “shape” of filters, same clear/reset behavior).
2. Deterministic server-side pagination: the UI must filter **before** applying row counts/paging (no client-side filtering that desyncs totals).
3. Shareable links: filter state is encoded in URL query params and survives reload/back/forward.
4. Backend remains final authority: frontend only hides/disables actions based on permissions, never enforces authorization itself.

## Concepts
### Filter field
A single filterable attribute in a list view (e.g. `email`, `status`, `role`).

### Filter chip
The chip UI representing a single filter field.

### Filter menu / editor
The UI shown when the user activates a chip in suggested state (e.g. select options, enter value, date range).

## URL query parameter contract
Every filterable list endpoint must follow these rules:
1. **Canonical keys**:
   - Pagination: `page`, `size`
   - Sorting: `sort` (single stable token)
   - Filters: each filter field gets one or more query keys (see below)
2. **Empty vs unset**:
   - When a filter field is “not set”, omit its key from the URL (prefer `undefined` on the client, not empty strings).
   - When set, include the key with the filter’s encoded value.
3. **Determinism**:
   - Always include a deterministic tie-break in server sort order (commonly `user_id ASC`) so pages don’t reshuffle.
4. **Pagination correctness**:
   - Server must apply filters in SQL (or equivalent) before computing row counts and returning the paged result.

## Filter field types (UI + server mapping)
Define a filter field in your module’s code using this conceptual model:

| Type | UI control | URL encoding (example) | Server semantics |
|------|-------------|--------------------------|------------------|
| `TEXT_CONTAINS` | text input (freeform) | `email=jane` | case-insensitive substring match (typically `LIKE` on normalized column) |
| `ENUM_EXACT` | dropdown/select (single) | `status=ACTIVE` | exact match on a short enum code or canonical display name |
| `ENUM_EXACT_MAPPED` | dropdown/select (single) | `role=Viewer` (display name) | server maps display -> stored rows (see module doc for exact mapping) |
| `DATE_RANGE` | date inputs (from/to) | `created_from=...&created_to=...` | inclusive/exclusive rules must be documented per endpoint |
| `NUMBER_RANGE` | numeric inputs (min/max) | `amount_min=...&amount_max=...` | documented numeric comparison |
| `MULTI_ENUM_EXACT` | multi-select | `status=ACTIVE,PAUSED` OR repeated keys | documented set membership semantics |

Notes:
- For `TEXT_CONTAINS`, treat values as potentially PII; still store only what the module allows (do not add speculative DB columns).
- For `ENUM_EXACT_MAPPED`, the module must document what the URL value represents (stored enum code vs display label).

## Chip behavior rules
1. **Suggested vs active rendering is separate**:
   - Suggested chip: opens a menu; does not navigate immediately.
   - Active chip: shows a clear affordance; clearing updates the URL by removing keys.
2. **Clear filters**:
   - The “Clear filters” control is shown only if at least one filter field is active.
   - Clearing removes filter keys from the URL, resets `page` to `0`, and keeps `size`/`sort` as module-defined (or resets them if documented).
3. **Apply / cancel**:
   - For menu-based filters, prefer an explicit Apply action if selecting multiple values or entering ranges.
   - For simple ENUM_EXACT, selecting an option can immediately apply.

## Sorting token standards (server required)
Modules must encode sorting as a single `sort` token string.
Suggested naming:
- `FIELD_ASC` / `FIELD_DESC`
- Include deterministic tie-break behavior on the server for stable pagination.

Example fields used in this codebase:
- `EMAIL_ASC|DESC`
- `LAST_ACTIVE_ASC|DESC`
- `STATUS_ASC|DESC`
- `ROLES_ASC|DESC` (server defines role sort key semantics)

## Acceptance checklist (per list page)
- [ ] Filter chips appear above the table.
- [ ] Each chip opens a control only for suggested state.
- [ ] Active chips can clear and update the URL without full page reload.
- [ ] “Clear filters” is present only when something is active.
- [ ] Server applies filters before computing row totals.
- [ ] Filter state is encoded in URL params and round-trips correctly.

## Implementation plan (recommended order)
1. Implement filter chip bar component + URL encoding helpers.
2. Migrate one module (e.g. `/app/users`) to the standard.
3. Reuse for other list modules once the pattern proves stable.

