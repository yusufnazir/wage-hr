# Prompt outputs (product repository)

Convention for **generated** text you reuse across prompts. Paths are relative to the product repo root.

| File | When |
|------|------|
| `ARCHITECTURE-DEFINITION.md` | Full output from Prompt 1. Commit it; later prompts ask the AI to read or attach this file instead of pasting the architecture inline. |
| `../product/BUILD-CHECKLIST.md` | Human-maintained milestones and checkboxes — **execution tracker** alongside Prompt 1 output |

**Consistency:** **Architecture contract** in **`docs/prompts/PROJECT-CONTEXT.md`** is authoritative for product scope and phases. Regenerate `ARCHITECTURE-DEFINITION.md` after contract changes so billing, subscriptions, and payroll modules are not contradicted by an older sample. Track progress in **`docs/product/BUILD-CHECKLIST.md`**.

**Note:** After contract edits, **re-run Phase 1** and refresh `ARCHITECTURE-DEFINITION.md` so it does not lag `PROJECT-CONTEXT.md` or module docs. The architecture file’s **§0 Governance** lists triggers and a **suggested next calendar review** date; update that section when you regenerate.

If you use the prompt-helper **Contract** page, pasting into **Architecture output** still inlines the text into assembled prompts when you prefer not to use this file.
