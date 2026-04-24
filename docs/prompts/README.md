# Master prompts (product repo)

Use these when you want the AI to **drive from your `docs/` tree** instead of stepping through each numbered template by hand.

**Templates vs filled values:** `docs/templates/*.md` keep **placeholders** (`{PROJECT_NAME}`, `[PASTE FULL CONTRACT HERE]`, …) so the **prompt-helper** can substitute from the Contract page. For **`@docs`** + **master prompts**, maintain **`docs/prompts/PROJECT-CONTEXT.md`** (copy from [`PROJECT-CONTEXT.template.md`](./PROJECT-CONTEXT.template.md)) with the **same information in plain text** — that is the file the model should read first so it never depends on empty brackets inside templates.

**Progress and module planning (wage-payroll):** [`docs/product/BUILD-CHECKLIST.md`](../product/BUILD-CHECKLIST.md) and [`docs/product/MODULE-INDEX.md`](../product/MODULE-INDEX.md).

---

## How to run this in Cursor (important)

**Do not `@` only the master file.** That pulls in the instructions, but the model may not see `guides/`, `templates/`, or `output/` unless you add them.

**Recommended attachments (three) — docs + masters:**

1. **`@docs/prompts/MASTER-FOUNDATION-TO-FEATURES.md`** *or* **`@docs/prompts/MASTER-FEATURE-END-TO-END.md`**
2. **`@docs/prompts/PROJECT-CONTEXT.md`** *(after you create it from the template — filled values + pasted contract)*
3. **`@docs`** *(whole folder), or at minimum **`@docs/guides`** and **`@docs/templates`***

**Schema / entity / Liquibase work:** also attach **`@docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md`**, **`@docs/guides/DATA-MODEL-STANDARDS.md`**, and **exactly one** **`@docs/modules/{feature-slug}.md`** (sole schema authority — see preflight §0). Default: *if not explicitly allowed, do not implement.*

**Foundation only — fourth attachment (repo root):**  
**`MASTER-FOUNDATION-TO-FEATURES` Phase 2 (scaffold) and Phase 3 (security)** require creating or editing **real code** under e.g. **`backend/`**, **`frontend/`**, **`pom.xml`**, etc. **`@docs` alone does not include those paths** in context. You must also attach the **product repository root** (the folder you opened in **File → Open Folder** — the one that **contains** `docs/`):

- Type **`@`** and pick the **workspace / repo folder** (e.g. `wage-payroll`), **or**
- Attach **`@backend`** and **`@frontend`** (and any other top-level dirs the scaffold will touch), **or**
- Use **`@Codebase`** (if you rely on it) **in addition to** `@docs`.

**Workspace check:** Open the **repo root**, not only `docs/` as the Cursor folder. If you only open `.../wage-payroll/docs`, the agent never sees `backend/` at the sibling path.

**Only `docs/` under the root?** If Explorer shows **`YOUR-REPO/docs/`** and nothing else yet, that is **normal greenfield** — Phase 2 **creates** `backend/`, `frontend/`, etc. Attach the **repo root** (`@` that folder) anyway. The model must **not** refuse scaffold because those folders are missing; it should **add** them (unless the workspace root is wrong — see above).

**Example messages you can paste:**

- Foundation (docs **+** repo root — use your real folder name from `@`):  
  `Run this: @docs/prompts/MASTER-FOUNDATION-TO-FEATURES.md — filled values: @docs/prompts/PROJECT-CONTEXT.md — docs: @docs — repo root: @<your-repo-folder>`

- One feature (docs **+** one module + preflight when persistence changes **+** repo root — this master edits **backend / web / mobile** code, not only `docs/`). **Copy the full block** from **`docs/prompts/MASTER-FEATURE-END-TO-END.md`** → section **“Example Cursor message (copy and adjust)”** (includes `SCHEMA-PERSISTENCE-PREFLIGHT`, `DATA-MODEL-STANDARDS`, precedence over stale architecture, and `FEATURE-{slug}-VERIFICATION.md`). Short form (omit the two `docs/guides/…` lines if the feature has **no** DB/schema work):  
  `Run this: @docs/prompts/MASTER-FEATURE-END-TO-END.md — @docs/prompts/PROJECT-CONTEXT.md — @docs/modules/{feature-slug}.md — docs: @docs — repo root: @<your-repo-folder>`  
  (Same as foundation: **`@docs` alone is not enough**; attach the folder that contains `docs/` plus `backend/`, `frontend/`, mobile app root, etc.)

You can shorten the sentence; what matters is **master + PROJECT-CONTEXT + module file + `@docs`**, and **always** the **application tree** (repo root or **`@backend`** / **`@frontend`** / mobile) for both **foundation Phases 2–3** and **feature end-to-end**.

---

| File | Use when |
|------|----------|
| [`PROJECT-CONTEXT.template.md`](./PROJECT-CONTEXT.template.md) | Copy to **`docs/prompts/PROJECT-CONTEXT.md`** in the product repo and fill — **replaces template placeholders** for `@docs` workflows. |
| [`MASTER-FOUNDATION-TO-FEATURES.md`](./MASTER-FOUNDATION-TO-FEATURES.md) | Greenfield or major reset: architecture → scaffold → security, **before** recurring feature work. |
| [`MASTER-FEATURE-END-TO-END.md`](./MASTER-FEATURE-END-TO-END.md) | A **single feature** is specified in `docs/modules/` — backend, web, mobile, and checks. |

Numbered templates under `docs/templates/` remain the **detailed spec**; these masters tell the model to **read those files** and the guides, then **execute in order** and **write into the repo**.
