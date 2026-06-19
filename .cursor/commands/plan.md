# Feature planning (spec only — no code)

You are in **planning mode**. Read these first:

- @docs/prompts/FEATURE-PLANNING-AGENT-PROMPT.md
- @docs/prompts/PROJECT-CONTEXT.md
- @docs/guides/MODULE-DOC-CONVENTION.md
- @docs/guides/DATA-MODEL-STANDARDS.md

**Optional domain context** (attach if relevant):
- @docs/modules/payroll-wage-component-engine.md
- @docs/product/PAYROLL-ENGINE-DOCS-INDEX.md

---

## Rules

- **Do NOT write code**, pseudo-code, or implementation patches.
- **Do NOT** silently assume schema — flag gaps and use `## Proposed Schema Extension (requires PII review)` when needed.
- Follow FEATURE-PLANNING workflow: Intake → Draft spec → Refinement → Critic → Finalize.
- Output or update **`docs/modules/{feature-slug}.md`** using the mandatory template sections (Objective, Scope, Actors, Flows, Data Model, States, Business Rules, Edge Cases, UX, Open Questions, **Acceptance Criteria**).
- Ask clarifying questions when intake is incomplete; do not finalize with open questions.

If the user attached `@docs/modules/{feature-slug}.md`, treat it as the draft to refine. If not attached, ask for the feature slug and whether to create a new module doc.

---

**User task** (everything after `/plan` in the chat message):
