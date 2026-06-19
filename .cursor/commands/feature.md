# Feature implementation (end-to-end)

Read these files **in full** before writing or changing any code. Do not skip.

**Master + context:**
- @docs/prompts/MASTER-FEATURE-END-TO-END.md
- @docs/prompts/PROJECT-CONTEXT.md
- @docs/guides/README.md
- @docs/output/ARCHITECTURE-DEFINITION.md

**Schema / persistence (include even if you think the feature has none — confirm from module doc):**
- @docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md
- @docs/guides/DATA-MODEL-STANDARDS.md
- @docs/guides/LIQUIBASE-RULES.md

**Templates (match depth when implementing):**
- @docs/templates/4. BACKEND-FEATURE-PROMPT.md
- @docs/templates/5. WEB-FRONTEND-PROMPT.md
- @docs/templates/6. MOBILE-PROMPT.md

**Broader docs tree:**
- @docs

**Module contract (REQUIRED):** The user must attach exactly one `@docs/modules/{feature-slug}.md` in this message. If missing, **STOP** and ask for the slug — do not pick a module doc yourself.

**Application code:** Also use `@backend`, `@frontend`, and any other top-level dirs the feature touches (repo root is not in `@docs` alone).

---

## Execute

Foundation (scaffold + security) is already in place. Run **MASTER-FEATURE-END-TO-END** Phases 0–4 for the attached module doc:

- Phase 0: Ingest — summarize goal, scope, out-of-scope, privileges, API surface.
- Phase 1: Backend per template 4.
- Phase 2: Web per template 5 + `@docs/guides/E2E-TESTING-STANDARDS.md` where applicable.
- Phase 3: Mobile per template 6 (only if in module scope).
- Phase 4: Tests + `@docs/output/FEATURE-{feature-slug}-VERIFICATION.md` if the team uses it.

**Precedence:** If architecture output conflicts with `PROJECT-CONTEXT.md` or the module doc, follow **PROJECT-CONTEXT + module doc**.

**Do not** merge schema or behavior from other `docs/modules/*.md` files unless the user explicitly scoped them.

**Do not** run greenfield scaffold (`MASTER-FOUNDATION-TO-FEATURES` Phases 2–3) unless the module explicitly requires it.

If the module doc is incomplete, expand it with acceptance criteria **before** implementing.

When done: update the module doc, refresh verification output if used, confirm builds/tests for all touched areas.

---

**User task** (everything after `/feature` in the chat message):
