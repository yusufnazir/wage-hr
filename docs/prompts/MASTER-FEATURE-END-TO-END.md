# Master prompt — one feature, end‑to‑end (backend + web + mobile)

You are a **principal engineer** implementing **one** feature in an **existing** multi-tenant codebase. The product has already passed **foundation** (architecture, scaffold, security). You work **in this repository** and read **`docs/`** as truth.

---

## How to use this prompt (human)

1. Author or update **`docs/modules/{feature-slug}.md`** for this feature (see **`docs/guides/MODULE-DOC-CONVENTION.md`**). That file is the **contract for behavior** and, for persistence, the **sole schema authority** (see **`docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md`** §0 — **no module spec = no implementation**).
2. Fill **`docs/prompts/PROJECT-CONTEXT.md`** (from the template) with **feature name**, **description**, and optional **API contract** paths or paste — so template tokens like `{FEATURE_NAME}` are not empty when the model reads `docs/templates/4`–`6`.
3. Attach **`@docs/prompts/PROJECT-CONTEXT.md`**, **`@docs/prompts/MASTER-FEATURE-END-TO-END.md`**, **exactly one** **`@docs/modules/{feature-slug}.md`** (concrete path — not a placeholder), **`@docs`**, and the **repository root** (folder that contains `docs/` and **`backend/`**, **`frontend/`**, mobile project, etc.) via **`@`** — **not `@docs` alone**. **Recommended:** also attach **`@docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md`** and **`@docs/guides/DATA-MODEL-STANDARDS.md`** whenever the feature adds or changes **Liquibase, entities, or persistence** (explicit attach improves compliance even though these files live under `docs/`).
4. **Do not** treat multiple `docs/modules/*.md` files as merged schema authority unless the human explicitly scopes which tables belong to which doc; default is **one feature slug = one module spec** for the change.

---

## Non‑negotiables

- Read **`docs/guides/README.md`** and every relevant guide (API, privileges, Liquibase, security, routing, E2E, module doc convention). For **any** schema, entity, or persistence work: **`docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md`** + **`docs/guides/DATA-MODEL-STANDARDS.md`** + the feature **`docs/modules/{feature-slug}.md`** — **only** explicitly allowed fields; **`## Proposed Schema Extension (requires PII review)`** for anything else; validate before returning.
- Read **`docs/output/ARCHITECTURE-DEFINITION.md`** (and cross-cutting excerpts if stored separately) so you do **not** contradict global auth, tenancy, or API rules. **Precedence:** If architecture output **disagrees** with **`docs/prompts/PROJECT-CONTEXT.md`** (contract) or the **sole feature `docs/modules/{feature-slug}.md`** on product scope, persistence shape, or feature behavior, follow **`PROJECT-CONTEXT.md`** + the **module doc**; treat conflicting architecture as **stale** until Phase 1 regenerates it (see status banner on `ARCHITECTURE-DEFINITION.md` if present).
- **Do not** merge schema or behavior assumptions from **other** `docs/modules/*.md` files unless the human explicitly scoped which doc owns which tables for this run (default: **one slug = one authority**).
- **Do not** run **`MASTER-FOUNDATION-TO-FEATURES`** or greenfield scaffold Phases 2–3 **unless** the module explicitly requires net-new stack work; foundation is assumed present.
- Follow the **depth and structure** of:
  - **`docs/templates/4. BACKEND-FEATURE-PROMPT.md`**
  - **`docs/templates/5. WEB-FRONTEND-PROMPT.md`**
  - **`docs/templates/6. MOBILE-PROMPT.md`**
  Open them and treat missing sections in your implementation as **bugs**, not optional.

---

## Phase 0 — Ingest

1. Read **`docs/prompts/PROJECT-CONTEXT.md`** for **feature name / description** and pointers to **API contract** if stored outside the module doc.
2. Confirm **exactly one** **`docs/modules/{feature-slug}.md`** path (**sole** behavioral + schema authority for this run per **`docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md`** §0) and summarize: goal, scope, **out of scope**, privileges, API surface, web flows, mobile screens. If unclear or missing → **stop** and clarify or complete the module doc first.
3. Scan the repo for **existing patterns** (packages, naming, tests) and **match** them.

---

## Phase 1 — Backend (equivalent to Prompt 4)

**Deliverable:**

- Domain module, APIs, persistence, **privilege checks**, migrations per **`docs/guides/LIQUIBASE-RULES.md`**.
- Tests appropriate to the stack.
- Update **`docs/modules/{feature-slug}.md`** with **API notes** and file pointers if that is the team convention.

---

## Phase 2 — Web (equivalent to Prompt 5)

**Deliverable:**

- UI and client integration per module doc and **`docs/guides/E2E-TESTING-STANDARDS.md`** where applicable (subdomain/auth/CSRF-sensitive flows).
- Extend **`docs/modules/{feature-slug}.md`** with **web flows** section.

---

## Phase 3 — Mobile (equivalent to Prompt 6)

**Deliverable:**

- Screens and API usage per module doc; respect session/token and error-shape conventions from architecture/guides.

---

## Phase 4 — Verification

- Run or add **tests** (unit/integration/E2E as appropriate) tied to this feature.
- **Confirm builds** (and tests) pass for **every** touched area (backend, web, mobile) per stack norms.
- List **manual smoke steps** in a short appendix inside the module doc or in **`docs/output/`** as **`FEATURE-{feature-slug}-VERIFICATION.md`** if the team uses that pattern.

---

## Stop condition

Feature is **done** when backend + web + mobile (per scope in the module doc) are **implemented**, **guarded by privileges**, **documented**, and **tests you added pass**.

If the module doc is incomplete, **expand it first** with explicit acceptance criteria, then implement — do not silently shrink scope.

---

## Example Cursor message (copy and adjust)

Replace `{feature-slug}` with the real slug (e.g. `notifications-inbox`). Replace `@wage-payroll` with your **`@`** repo root folder name. **Omit** the two `docs/guides/…` lines only for features with **no** Liquibase, entity, or persistence changes.

```text
Run feature (Phases 0–4): @docs/prompts/MASTER-FEATURE-END-TO-END.md — filled values: @docs/prompts/PROJECT-CONTEXT.md — module (sole behavioral + schema authority): @docs/modules/{feature-slug}.md — @docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md — @docs/guides/DATA-MODEL-STANDARDS.md — docs: @docs — repo root: @wage-payroll

Existing codebase: foundation (scaffold + security) is already in place. Execute MASTER-FEATURE-END-TO-END for this feature using the single attached module doc as the contract — backend (depth of docs/templates/4. BACKEND-FEATURE-PROMPT.md), web (5), mobile (6), then Phase 4 verification. Read docs/guides/README.md and docs/output/ARCHITECTURE-DEFINITION.md; if architecture conflicts with PROJECT-CONTEXT or the module doc, follow PROJECT-CONTEXT + module. Do not merge schema assumptions from other module docs. Do not use MASTER-FOUNDATION-TO-FEATURES or greenfield scaffold Phases 2–3 unless the module explicitly requires net-new stack work. If the module doc is incomplete, expand it with acceptance criteria before implementing — do not shrink scope silently. When done, update the module doc, add or refresh docs/output/FEATURE-{feature-slug}-VERIFICATION.md if used, and confirm builds/tests for all touched areas.
```
