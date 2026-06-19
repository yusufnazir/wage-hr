# Web theming and design system

**Scope:** Browser web apps (e.g. Next.js). **Goal:** Ship **light + dark** from day one, look **modern**, and make **color tweaks or full theme swaps** a **data / token change**, not a hunt through hundreds of components.

Copy this file into **`docs/guides/`** in each product repo. Architecture summarizes *what* you chose; **this guide** is the *how* for implementation.

---

## 1. Layering (do this in order)

| Layer | Responsibility | Swapping colors | Swapping “entire theme” |
|-------|----------------|-----------------|-------------------------|
| **Primitives** | Raw palette (brand greens, neutrals, semantic danger) | Edit one palette file or CSS variables block | Replace primitive set (e.g. new brand kit) |
| **Semantic tokens** | `--color-bg`, `--color-text`, `--color-border`, `--color-primary`, `--radius-md`, `--font-sans` | Components **never** use primitives directly | Point semantic layer to a different primitive map |
| **Components** | Use **only** semantic tokens / Tailwind theme keys mapped to vars | Almost no component edits | Same — components stay stable |

**Rule:** UI code references **semantic** names only (`bg-background`, `text-foreground`, `border-border`, or `var(--color-surface)`). **Never** `#1a1a1a` or `green-600` scattered in JSX except in the **token definition** layer.

---

## 2. Light and dark mode

- Support **explicit user choice**: light, dark, **system** (follow `prefers-color-scheme`).
- Persist choice (cookie or localStorage — align with auth/cookie strategy in architecture).
- Apply mode with **one attribute** on the document root, e.g. `class="dark"` on `<html>` or `data-theme="dark"`, so CSS can scope all tokens.
- **Avoid flash of wrong theme:** inline script or SSR that sets class from cookie **before** paint (framework-specific; document the chosen approach in the repo).

---

## 3. Technology patterns (pick one stack; stay consistent)

**Recommended for Next.js + “modern fast” UI:**

- **CSS custom properties** for semantic tokens (single `globals` or `tokens.css` imported once).
- **Tailwind** `theme.extend` mapping colors/radii/fonts to `var(--…)` so utilities stay ergonomic.
- **`next-themes`** (or equivalent) for class on `<html>` + persistence.

**Alternative:** CSS-in-JS token provider — still keep the same **primitive → semantic → component** separation.

Document the **chosen stack** in architecture and in the repo `README` snippet for new devs.

**wage-payroll (current):** primitives are **cool slate** neutrals + **indigo** primary + **cyan** accent (`frontend/src/styles/tokens-primitives.css`); semantic layer maps to app background, surfaces, borders, and shadows (`tokens-semantic.css`). Auth marketing mesh uses CSS variables `--color-mesh-*` (indigo / cyan / violet washes). Rebrand by editing primitives + semantic mappings only — components stay on Tailwind semantic keys (`bg-primary`, `text-muted`, etc.).

---

## 4. Where files live (suggested layout)

Keep token and theme wiring **obvious and grep-friendly**:

```
styles/
  tokens-primitives.css    # optional: brand palette values
  tokens-semantic.css        # maps primitives → semantic vars for light
  tokens-semantic-dark.css   # overrides for .dark (or data-theme=dark)
  globals.css                # imports + base element styles
components/
  theme/
    ThemeProvider.tsx        # next-themes + attribute class
    ThemeToggle.tsx          # sun/moon control for auth shell + later app chrome
```

Feature screens **must not** define one-off colors; they consume shared utilities / vars.

---

## 5. Scaffold vs later features

| Phase | Theming expectation |
|-------|---------------------|
| **Scaffold** | Wire **ThemeProvider**, **semantic tokens**, **light/dark**, and a **polished minimal auth shell** (login + post-login redirect pages). Still **not** a full product UI — but it should **not** look like unstyled HTML. *wage-payroll:* auth pages also use **`AuthSplitLayout`** (marketing + form columns); tenant **`/app`** uses **`TenantAppShell`** (sidebar + header + user menu) — see **`docs/modules/tenant-web-vertical-slice.md`** §3.6. |
| **Features (Prompt 5+)** | Reuse tokens only; add **layout** and **components** that match the established system. |

The scaffold **non-goal** is still “no full business UI” — it is **not** “no CSS.”

---

## 6. Full rebrand or new color scheme

1. **Swap primitives** (or swap the file that defines them).
2. Adjust **semantic** mapping only if meaning changes (e.g. “primary” is now purple).
3. Run visual smoke on **auth + one dense screen** (tables, forms, errors).

Optional: support **`data-brand="acme"`** (or similar) if white-label is a requirement — second axis beside light/dark; document in architecture before implementing.

---

## 7. “Looks modern” baseline (non-designer bar)

Without mandating a specific art direction, scaffolds should include:

- Sensible **typography scale** and **spacing** rhythm (8px grid or Tailwind defaults mapped to tokens).
- **Radius**, **shadow**, and **focus rings** on interactive elements (a11y).
- **Empty / loading / error** states for the auth shell that are not raw browser defaults.

Product-specific **brand illustration / marketing polish** can still wait for dedicated design passes.

---

## 8. Page routing conventions for CRUD views

All resource management UIs follow a consistent routing shape:

| Route                          | Purpose                                     |
|-------------------------------|---------------------------------------------|
| `/app/{resource}`             | List page — table/grid + pagination controls |
| `/app/{resource}/new`         | **Separate create page** — full-page form   |
| `/app/{resource}/{id}/edit`   | **Separate edit page** — full-page form     |

**Rules:**
- Create and edit forms are **always separate routes/pages** — never modals, drawers, or inline-expanded rows on the list page.
- The list page is **read-only**: it shows data, pagination, and action buttons that navigate to the create/edit routes.
- After a successful create or edit, redirect back to the list page (`router.push("/app/{resource}")`).
- After a successful create, the list resets to page `0` so the new record is visible.
- After a successful edit, the list restores the same page the user was on.
- The `new` and `edit` pages share the same form component where possible; the edit page pre-populates via the resource id from the URL.

**Why separate pages (not modals):**
- Supports deep-linking and browser back/forward correctly.
- Avoids state conflicts between list data and form state.
- Keeps the list page simple and focused.
- Forms that grow (e.g. multi-section, file upload, rich text) have space without workarounds.

---

## 9. CRUD feedback: notifications and confirmation dialogs

All resource management UIs must provide consistent, predictable feedback for every mutating action.

### 9.1 Toast / inline notifications

Show a **success notification** after every successful mutation. Show an **error notification** (or inline error message) when the mutation fails.

| Action | Success message pattern | Error message pattern |
|--------|------------------------|-----------------------|
| **Create** | `"{Name}" created successfully.` | `Could not create {resource}. Please try again.` |
| **Update / edit** | `"{Name}" updated successfully.` | `Could not save changes. Please try again.` |
| **Toggle active/inactive** | `"{Name}" set to active.` / `"{Name}" set to inactive.` | `Could not update status. Please try again.` |
| **Delete (hard)** | `"{Name}" deleted.` | `Could not delete {resource}. Please try again.` |
| **Soft delete / deactivate** | covered by toggle active row above | same |

**Rules:**
- Notifications are **transient** (auto-dismiss after ~4 s for success; persistent or longer for errors).
- Success notifications are **non-blocking** — do not stop the user's workflow.
- Error notifications must remain visible until dismissed or a subsequent action clears them; never auto-dismiss an error.
- Use a **consistent toast component** across all modules — do not invent per-page inline banners for standard CRUD feedback.
- If a page already has an **inline error area** (e.g. a form), the form's own error display is acceptable for validation errors (`400`). Use the toast for post-submit success or unexpected server errors (`5xx`).

**wage-payroll implementation note:** pages currently display inline `<p className="text-sm font-medium text-destructive">` messages for errors and rely on redirect-plus-reload for implicit success. A shared `useToast` / `ToastProvider` should be wired to replace this pattern; until that component is shipped, inline feedback is acceptable as a temporary measure — but the UX contract above applies to the final UI.

### 9.2 Confirmation dialogs for destructive actions

Any action that **permanently removes data** or **deactivates a record** (soft delete / toggle inactive) **must** be preceded by a confirmation dialog before the API call is made.

**Trigger rule:**
- Hard delete → always confirm.
- Soft delete / deactivate (toggle `active = false`) → always confirm.
- Reactivate (toggle `active = true`) → no confirmation required.
- Create / update → no confirmation required.

**Dialog content:**
- **Title:** short, action-oriented — e.g. `"Deactivate department?"` or `"Delete job?"`
- **Body:** one sentence describing the consequence — e.g. `"This will deactivate \"HR\" and hide it from all payroll operations."` or `"This will permanently delete \"HR-A\". This cannot be undone."`
- **Confirm button:** destructive styling (`bg-destructive text-destructive-foreground`) — e.g. `"Deactivate"` / `"Delete"`
- **Cancel button:** neutral — e.g. `"Cancel"`

**Rules:**
- The dialog must be **modal** — it must block interaction with the page behind it.
- Clicking outside the dialog or pressing `Escape` cancels the action (same as clicking Cancel).
- The confirm button must be **disabled and show a loading state** while the API call is in flight to prevent double-submission.
- After a confirmed destructive action, show the appropriate toast (§9.1) and refresh the list.
- Never make a destructive API call on first click (no "click once to delete" with only a hover tooltip as protection).

**wage-payroll implementation note:** current list pages use an inline `onClick` that calls the API directly for active-toggle without confirmation. This must be updated to route through a confirmation dialog. A shared `ConfirmDialog` component (or `useConfirm` hook) should be added to `frontend/src/components/ui/` and reused across all modules.

---

## 10. Date display: always use the platform date format

All **user-facing date values** (date-only fields — no time component) **must** be rendered through the platform date format, not hardcoded or passed through raw.

**Rule:** Never render an ISO string (`yyyy-MM-dd`) directly in JSX. Always call `formatUserFacingDate(iso, me.dateFormat)` (from `@/lib/user-date-format`) using the date format from the current session.

```tsx
// ✅ correct — honours the operator-configured date format
import { formatUserFacingDate } from "@/lib/user-date-format";
const { me } = useTenantAppSession();
<td>{formatUserFacingDate(row.effectiveDate, me.dateFormat)}</td>

// ❌ wrong — hardcodes ISO output regardless of platform setting
<td>{row.effectiveDate}</td>
```

**Where the format comes from:**

- Platform setting: `platform.date_format` (configurable by the platform admin in Platform Settings).
- Exposed to the frontend via `MePayload.dateFormat` on the session context.
- Supported tokens: `yyyy-MM-dd`, `dd/MM/yyyy`, `MM/dd/yyyy`, `ISO-8601`, or a custom pattern using `yyyy`, `MM`, `dd` separated by `-`, `/`, `.`, or space.

**Scope:**

| Context | Rule |
|---------|------|
| **Table cells, list views** | Always `formatUserFacingDate` |
| **Detail/read-only field in a form** | Always `formatUserFacingDate` |
| **`<input type="date">` value** | Always keep `yyyy-MM-dd` (required by the HTML spec); the display is handled by the browser/OS. The formatted date is for read-only display only. |
| **API requests / responses** | Always ISO `yyyy-MM-dd`; do not send the formatted string to the backend. |

**Why:**

Operators configure their preferred date format (e.g. Dutch tenants prefer `dd/MM/yyyy`, US tenants `MM/dd/yyyy`). Bypassing `formatUserFacingDate` causes confusing locale mismatches in read-only cells and audit trails.

---

## 11. Form fields and detail-page edit layouts

Create and edit pages use a **single card** of fields (not modals). The canonical reference in **wage-payroll** is the platform tenant editor:

`frontend/src/app/app/platform-tenants/[tenantId]/page.tsx`

Shared class constants live in `frontend/src/components/ui/formStyles.ts` — import these instead of inventing per-page label/input styles.

### 11.1 Page shell

| Element | Classes / pattern |
|---------|-------------------|
| Page container | `mx-auto max-w-2xl space-y-6` (`detailPageClass`) |
| Title | `text-lg font-semibold text-foreground` |
| Intro / helper under title | `text-sm text-muted` (`formHelperClass`) — one short sentence before the card |
| Back link | `text-sm font-medium text-primary underline-offset-4 hover:underline` |

Tabbed editors (e.g. component group templates) may use a wider page (`max-w-5xl`) for tables on other tabs; **each form tab** still uses the same **card + field** rules below.

### 11.2 Form card

| Element | Classes |
|---------|---------|
| Card | `flex max-w-lg flex-col gap-4 rounded-lg border border-border bg-surface p-6 shadow-sm` (`formCardClass`) |

- **`gap-4`** between every block inside the card (fields, section title, submit).
- Do **not** use `p-5`, `space-y-6`, or a separate footer row with `border-t` and right-aligned buttons unless a second neutral action (e.g. Cancel) requires it — primary **Save** is **full width** at the bottom of the card.

### 11.3 Field row

Each control is wrapped in a column with **`gap-1.5`** between label and input (`formFieldClass`):

```tsx
<div className={formFieldClass}>
  <label htmlFor="…" className={formLabelClass}>Display name</label>
  <input id="…" className={formInputClass} … />
</div>
```

| Element | Rule |
|---------|------|
| **Label** | `text-xs font-medium text-muted` — **sentence case** (e.g. “Display name”, “Sort order”). **Do not** use `uppercase` on form labels. |
| **Text input / select / textarea** | `w-full min-w-0 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground shadow-sm` |
| **Read-only value** | Same border/radius as inputs; `bg-muted/20`; use `font-mono` for codes/handles (`formInputReadOnlyClass`), or a read-only `<input>` — not bare text jammed against the next label. |
| **Field helper** | Optional `text-xs text-muted` directly under the control (`formFieldHelperClass`) — keep one line; avoid stacking duplicate page-level copy. |
| **Checkbox** | Own `formFieldClass` row; `flex items-center gap-2 text-sm` (`formCheckboxRowClass`). **Do not** align checkboxes with `mt-6` beside number inputs in a cramped row. |

### 11.4 Primary action

```tsx
<button type="submit" className={formPrimaryButtonClass} disabled={busy}>
  Save
</button>
```

- Full width of the card (`w-full` on the button).
- `rounded-md`, `font-medium` (not `font-semibold`), `shadow-sm`, `hover:opacity-90`.

### 11.5 Anti-patterns (do not ship)

- Uppercase grey labels on CRUD forms (`uppercase text-muted`).
- `max-w-md` on inputs inside a `max-w-lg` card — inputs should be **`w-full`** within the card.
- Sort order + Active on one row with the checkbox vertically offset (`mt-6`).
- Right-aligned save-only footers separated by `border-t` when there is no secondary action in that row.
- Long i18n policy copy under every field — put catalog/i18n rules once in the page intro or on the Translations tab.

---

## 12. Where this is decided in your methodology

| Artifact | What to capture |
|----------|-----------------|
| **Architecture contract / architecture output** | Stack (Tailwind? shadcn?), **must** have light+dark, and whether white-label / multi-brand exists. |
| **This guide** | Implementation rules everyone follows. |
| **Scaffold** | First working **token pipeline + auth UI** that proves the system. |
| **Feature module docs** | Per-screen layout and behavior — **not** per-screen hex colors unless exceptional. |

If architecture and this guide **disagree**, update **architecture** first, then implementation.
