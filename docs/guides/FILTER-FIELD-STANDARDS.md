# Filter field standards (employee-style chip bar)

This guide defines the **canonical list-filter UX** for tenant and platform web apps: the same **chip row**, **popover editor**, and **clear** behavior as the **Employees** list (`/app/employees`).

A second, **URL-first** variant exists for some screens (`/app/users`) using `@/components/filters/FilterBar.tsx` and a different chip affordance. **New list pages should follow the Employees pattern** unless the product explicitly requires shareable filter URLs on day one—in that case, use the UI component below **and** wire `onApply` / clear actions to `useRouter` + `@/lib/filter-url` (see [URL-backed filters](#url-backed-filters-optional)).

## Reference implementation

- **Primary example:** `frontend/src/app/app/employees/page.tsx` — filter row, `Clear filters`, pagination placement.
- **Component:** `frontend/src/components/ui/FilterChip.tsx` — `FilterChip<T>` with suggested vs active states and built-in **Apply** in the popover.
- **URL + chips:** `frontend/src/app/app/platform-wage-component-templates/page.tsx` — same chip UX with shareable URL keys `country`, `active`, and `page` via `useSearchParams` + `@/lib/filter-url`.

## Goals

1. Consistent UX across modules (same chip shape, popover, Apply, clear-all).
2. Deterministic server-side pagination: filters apply on the server **before** row counts and paging (no client-side filtering that desyncs totals).
3. Shareable links **when required**: encode filter + `page` in the URL (see below); otherwise in-memory state is acceptable if documented for that screen.
4. Backend remains the authority for authorization; the UI only hides or disables actions.

## Concepts

### Filter field

A single filterable attribute (e.g. company, first name, country, “active only”).

### Filter chip (`FilterChip`)

One chip per field, from **`@/components/ui/FilterChip`**:

| State | Appearance | Interaction |
|-------|------------|-------------|
| **Suggested** (no value) | Rounded pill, **dashed** border, small **+** icon, label | Opens popover; does not change the list until **Apply**. |
| **Active** | Solid border, light primary-tint background, **×** (clear), label, then a short separator and truncated **value** | **×** clears that field only (calls `onApply(null)`). Clicking the pill toggles the popover for edits. |

Popover content:

- Title: `Filter by {label}` (from the component).
- Body: your `renderInput(draft, setDraft, apply)` (inputs, multi-select list, etc.).
- Footer: primary **Apply** (commits draft via `onApply`).

Use **`formatValue`** for readable active labels (e.g. country name, `"3 selected"`, `Active` / `Inactive`).

### Filter row layout (Employees)

Place the chip row **above** the list or table:

- Container: `flex flex-wrap items-center gap-2`.
- Chips: one `FilterChip` per field, in a stable order.
- **Clear filters:** show only when at least one field is active. Plain text button next to the chips, same style as Employees:

  `text-xs font-medium text-muted underline-offset-4 hover:text-foreground hover:underline`

  Action: clear every filter and reload from page **0** (and reset any URL filter keys if the screen is URL-backed).

- **Pagination** (if present on the same row): `ml-auto` on a wrapper so chips stay left and paging stays right (Employees pattern).

Do **not** wrap the chip row in a separate heavy fieldset unless the product design calls for it; Employees uses the flat flex row only.

## URL-backed filters (optional)

When filters must be **shareable** and survive reload/back/forward:

1. Read **`page`** and filter keys from `useSearchParams()`.
2. On **Apply** / per-chip clear / **Clear filters**, update the URL with `router.push` + helpers from **`@/lib/filter-url`** (`nextSearchParams`, `toQueryString`).
3. Treat “unset” as **missing** query keys (not empty strings), per [URL query parameter contract](#url-query-parameter-contract).
4. Refetch when search params change (e.g. `useEffect` depending on parsed params).

## URL query parameter contract

When using URL state, every filterable list should follow:

1. **Canonical keys**
   - Pagination: `page`, `size` (size may be fixed in code but should be documented).
   - Sorting: `sort` (single stable token), if the list supports sort.
   - Filters: one or more keys per field (documented per module).
2. **Empty vs unset** — omit keys when the filter is not applied; use real values when applied.
3. **Determinism** — server sort includes a stable tie-break (e.g. id ASC).
4. **Pagination correctness** — server applies filters before totals and page slices.

## Filter field types (UI + server / URL)

| Type | Chip `T` / editor | URL example (when URL-backed) | Server semantics |
|------|-------------------|-------------------------------|------------------|
| `TEXT_CONTAINS` | `string \| null`, text input in popover | `firstName=ann` | Case-insensitive contains / `LIKE` (document per API) |
| `ENUM_EXACT` | `string \| null`, select or radio in popover | `country=SR` | Exact match on code |
| `MULTI_ENUM_EXACT` | `string[]` / empty = inactive chip, multi checkbox list | `companyId=id1&companyId=id2` or comma form (document per API) | Set membership |
| `BOOLEAN_FLAG` | e.g. `"active" \| null` for “active only” | `active=true` | Document mapping (e.g. only send `active=true` when set) |

## Chip behavior rules

1. **Apply** — multi-step or ambiguous edits use **Apply** in the popover (default from `FilterChip`). Single immediate apply is allowed only if it does not fight popover close/navigation (prefer Apply for consistency).
2. **Per-chip clear** — the **×** on an active chip clears that field only (`onApply(null)`).
3. **Clear filters** — visible only when any filter is active; clears all filters and resets **`page`** to `0`; keeps `size` / `sort` unless the module documents otherwise.

## Sorting token standards (server)

Encode sorting as one `sort` token, e.g. `FIELD_ASC` / `FIELD_DESC`, with a deterministic tie-break on the server.

## Acceptance checklist (per list page)

- [ ] Filter chips from **`@/components/ui/FilterChip`** sit in a **`flex flex-wrap items-center gap-2`** row above the list/table.
- [ ] Suggested chips show **+**; active chips show **×** and a readable value (`formatValue` where needed).
- [ ] Popover edits commit with **Apply** (component default).
- [ ] **Clear filters** appears only when at least one filter is active, and matches Employees’ text-button styling.
- [ ] Server applies filters before row totals; pagination matches filtered totals.
- [ ] If the screen is URL-backed: filter + `page` round-trip in the URL; clearing removes filter keys and sets `page=0`.

## Implementation notes

- **Imports:** `import { FilterChip } from "@/components/ui/FilterChip";`
- **Do not** confuse with `FilterChip` / `FilterBar` under `@/components/filters/FilterBar.tsx` (different API; reserved for legacy or URL-heavy screens until migrated).
- Reuse list patterns inside `renderInput` (e.g. checkbox list like Employees’ `MultiSelectBody`, text inputs with `placeholder="contains…"`, **Enter** to `apply()` where appropriate).

## Related docs

- Theming / tokens: [`WEB-THEMING-AND-DESIGN-SYSTEM.md`](./WEB-THEMING-AND-DESIGN-SYSTEM.md) — use design tokens for borders, `text-muted`, `primary`, etc., consistent with `FilterChip` and the Employees page.
