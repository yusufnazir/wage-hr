# Retro payroll (Suriname) — doc-first review or implementation

Read the user guide and related engine docs **before** answering or changing code:

- @docs/guides/gebruikershandleiding-retro-loonverwerking.md
- @docs/modules/payroll-wage-component-engine.md
- @docs/modules/pay-periods.md
- @docs/product/PAYROLL-GOLDEN-SCENARIO-SR.md
- @docs/prompts/PROJECT-CONTEXT.md

For **implementation** (not review-only), also attach the sole module authority and run `/feature` rules:
- @docs/prompts/MASTER-FEATURE-END-TO-END.md
- @docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md

**Backend touchpoints** (read when comparing doc vs code):
- @backend/src/main/java/com/wagepayroll/payroll/

---

## Mode

1. Summarize what the retro guide says the product must do (3–5 bullets).
2. If the user asked for a **gap analysis**, compare guide requirements to current code/API — cite file paths; do not invent missing features as if they exist.
3. If the user asked for **implementation**, confirm module slug and switch to MASTER-FEATURE-END-TO-END with acceptance criteria from the guide.

Do not paste full tax tables into code comments; reference effective-dated `platform_country_tax_rule` and official sources per module conventions.

---

**User task** (everything after `/payroll-retro` in the chat message):
