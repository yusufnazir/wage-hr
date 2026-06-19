/**
 * Canonical edit/detail form field classes.
 * Reference: `frontend/src/app/app/platform-tenants/[tenantId]/page.tsx`
 * Documented in `docs/guides/WEB-THEMING-AND-DESIGN-SYSTEM.md` §11.
 */

/** Card wrapping fields on a create/edit page (max width matches dense forms). */
export const formCardClass =
  "flex max-w-lg w-full min-w-0 flex-col gap-4 rounded-lg border border-border bg-surface p-6 shadow-sm";

/** One label + control (+ optional helper). */
export const formFieldClass = "flex w-full min-w-0 flex-col gap-1.5";

/** Field label — sentence case, not uppercase. */
export const formLabelClass = "text-xs font-medium text-muted";

/** Editable text input, select, and textarea. */
export const formInputClass =
  "w-full min-w-0 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground shadow-sm";

export const formSelectClass = formInputClass;

export const formTextareaClass = formInputClass;

/** Read-only values shown in an input-shaped control (e.g. handle, country code). */
export const formInputReadOnlyClass =
  "w-full min-w-0 rounded-md border border-border bg-muted/20 px-3 py-2 font-mono text-sm text-foreground";

/** Caption under a field or below the page title. */
export const formHelperClass = "text-sm text-muted";

export const formFieldHelperClass = "text-xs text-muted";

/** Primary submit on create/edit cards — full width of the card. */
export const formPrimaryButtonClass =
  "inline-flex w-full items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50";

/** Checkbox with label on one row. */
export const formCheckboxRowClass = "flex items-center gap-2 text-sm text-foreground";

/** Page shell for simple single-card editors. */
export const detailPageClass = "mx-auto max-w-2xl space-y-6";
