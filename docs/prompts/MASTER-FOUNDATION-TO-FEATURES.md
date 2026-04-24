# Master prompt — foundation through security (before features)

You are a **principal engineer** shipping a **production-grade multi-tenant** product. You work **inside this repository** and use the **on-disk `docs/` layout** as the source of truth.

---

## How to use this prompt (human)

1. Ensure the product repo has **`docs/guides/`**, **`docs/templates/`**, **`docs/modules/`**, **`docs/output/`** (copy from your methodology when bootstrapping).
2. Maintain **`docs/prompts/PROJECT-CONTEXT.md`** (copy from **`docs/prompts/PROJECT-CONTEXT.template.md`**) with **filled** product name, stack, domains, and the **full architecture contract** pasted in plain markdown — this replaces what numbered templates call `[PASTE FULL CONTRACT HERE]` and `{PROJECT_NAME}` / stack tokens for `@docs` runs.
3. Put or generate **architecture output** in **`docs/output/ARCHITECTURE-DEFINITION.md`** when Phase 1 completes (or note its path in `PROJECT-CONTEXT.md`).
4. In Cursor, attach **`@docs/prompts/PROJECT-CONTEXT.md`**, **`@docs/prompts/MASTER-FOUNDATION-TO-FEATURES.md`**, and **`@docs`** (see `docs/prompts/README.md`).
5. For **Phase 2 (scaffold)** and **Phase 3 (security)**, also attach the **repository root** (the folder that **contains** `docs/`) via **`@`** — not **`@docs` alone**. Otherwise the model only sees documentation and cannot create app files next to `docs/`.

---

## Greenfield layout (read this before refusing Phase 2)

It is **normal** for the repo root to show **only `docs/`** (plus dotfiles) **before** Phase 2 — there is often **no** `backend/` or `frontend/` yet. **That is not a docs-only workspace mistake** and **not** a reason to stop. **Phase 2’s job is to create** the scaffold directories and files (e.g. `backend/`, `frontend/`, Maven wrapper, Next app) per **`ARCHITECTURE-DEFINITION.md`** and **`docs/templates/2. SCAFFOLD-GENERATOR-PROMPT.md`**.

**Do not** tell the human to “open the full repo” or “switch workspace” solely because those folders are missing. **Do** stop only if the opened folder is **not** the product root (e.g. only `docs/` was opened as the Cursor workspace so `docs` sits at the **top** of the tree with no parent in the workspace — then the agent cannot create sibling `backend/`). If **`docs/` is a child** of the root (as in `WAGE-PAYROLL/docs/`), proceed with Phase 2 and create siblings under that root.

---

## Non‑negotiables

- Read and follow **`docs/guides/README.md`** and every guide it lists that applies (at minimum: **data model**, **`SCHEMA-PERSISTENCE-PREFLIGHT`** before DDL/entities, **privileges**, **routing/tenancy**, **cross-cutting security**, **web theming** (`WEB-THEMING-AND-DESIGN-SYSTEM.md` for browser apps), **Liquibase**, **API conventions**, **E2E standards**). For a **Spring Boot / Maven** backend, also **`docs/guides/JAVA-BACKEND-TOOLING.md`** (wrapper in git, `.jdk/` gitignored) and **`docs/guides/LOCAL-DEV-PORTS.md`** (defaults **8080** / **3000**; other ports are fine if documented consistently — see that guide).
- Treat **`docs/templates/1` … `3`** (`ARCHITECTURE`, `SCAFFOLD`, `SECURITY-INFRASTRUCTURE`) as the **canonical checklist** for depth and headings. Open them and **do not skip** sections that still apply to this codebase.
- **Do not invent** stack choices if they are already fixed in contract or repo — align with what is written.

---

## Phase 0 — Ingest

1. Read **`docs/prompts/PROJECT-CONTEXT.md`** first (if it exists). Treat its tables and pasted markdown as **authoritative** for project name, stack, domains, and the **architecture contract** body.
2. List or summarize what you read under **`docs/guides/`** and **`docs/templates/`** (files present).
3. If **`PROJECT-CONTEXT.md`** is missing or empty, fall back to any committed contract under **`docs/output/`**, or stop and ask the human to create **`PROJECT-CONTEXT.md`** from the template — **do not** silently invent a contract.
4. **`docs/output/ARCHITECTURE-DEFINITION.md`**: if it already exists before Phase 1, treat it as **draft** architecture unless `PROJECT-CONTEXT.md` directs otherwise.
5. **Reconcile before big work:** Compare **`PROJECT-CONTEXT.md`** (contract + local ports table) with **`ARCHITECTURE-DEFINITION.md`**. The **contract** in `PROJECT-CONTEXT.md` wins on **v1 product scope** (e.g. billing in vs out of v1, OIDC, regions). If architecture contradicts the contract (or ports in context differ from what architecture examples assume), **edit `ARCHITECTURE-DEFINITION.md` or re-run Phase 1** — do not proceed with scaffold or features until they match.

---

## Phase 1 — Architecture (equivalent to Prompt 1)

**Objective:** Produce the **full architecture document** this product will follow.

**Rules:**

- **No application code** in this phase — design, interfaces, decisions, risks.
- **v1 scope** (billing, bank money movement, OIDC, etc.) MUST match the **Architecture contract** in **`PROJECT-CONTEXT.md`** — no PayPal/subscription checkout in architecture if the contract says billing is **out** of v1 (and vice versa).
- Output MUST follow the **structure and mandatory sections** required in **`docs/templates/1. ARCHITECTURE-PROMPT.md`** (same headings where applicable).
- Include **cross-cutting** items from **`docs/guides/CROSS-CUTTING-SECURITY.md`** and **`docs/guides/MULTI-TENANCY-AND-ROUTING.md`** in the architecture text.

**Deliverable:**

- Write **`docs/output/ARCHITECTURE-DEFINITION.md`** (create or replace) with the complete architecture.
- If the team keeps a separate contract snapshot, optionally also write **`docs/output/CONTRACT-ASSEMBLED.md`** only if that file already exists as a convention in this repo; otherwise do not invent extra files.

---

## Phase 2 — Scaffold (equivalent to Prompt 2)

**Objective:** Create the **enforceable project skeleton** (structure, minimal infra, proof of auth/tenant/privilege path).

**Rules:**

- Use **`docs/output/ARCHITECTURE-DEFINITION.md`** as the architecture input.
- Follow **`docs/templates/2. SCAFFOLD-GENERATOR-PROMPT.md`** for scope (**non-goals** matter — no big business CRUD).
- Apply methodology guides for **folders, env naming, and docs layout**.
- **Create** `backend/`, `frontend/`, and any other scaffold paths the architecture names **if they do not exist** — missing app folders at the start of this phase are **expected**, not a blocker.

**Deliverable:**

- Implement or adjust **real files** in the repo (not markdown-only hand-waving).
- Short **`docs/output/SCAFFOLD-SUMMARY.md`** listing what was created/changed (paths), if the repo does not already forbid extra output files.

---

## Phase 3 — Security infrastructure (equivalent to Prompt 3)

**Objective:** **Privilege-based security** and foundations every feature will use.

**Rules:**

- Follow **`docs/templates/3. SECURITY-INFRASTRUCTURE-PROMPT.md`**.
- All **schema / privilege / seed / DML** changes follow **`docs/guides/LIQUIBASE-RULES.md`** (or the project’s agreed migration standard documented there).

**Deliverable:**

- Working enforcement (middleware/filters, privilege resolution, SuperAdmin rules per guide).
- Migrations/changelog entries as required.
- **`docs/output/SECURITY-INFRA-SUMMARY.md`** — what was implemented and where (optional but recommended).

---

## Stop condition

Stop when **Phases 1–3** are done and the repo **builds**. Do **not** implement product **business features** here — that is **`MASTER-FEATURE-END-TO-END.md`**.

If anything in `docs/` is missing or contradictory, **state the gap** and propose the smallest fix (file names, paths) before writing large amounts of code.
