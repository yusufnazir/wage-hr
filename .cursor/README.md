# Cursor workflow (wage-payroll)

Slash commands and rules so agents read project specs **before** improvising.

## Slash commands

Type `/` in Agent chat, then pick a command. Add your task **after** the command name (and `@`-attach a module doc when required).

| Command | When to use |
|---------|-------------|
| `/feature` | Implement one feature end-to-end. **Also attach** `@docs/modules/{feature-slug}.md` |
| `/plan` | Write or refine a module spec only — no code |
| `/payroll-retro` | Retro loonverwerking guide + payroll engine context |
| `/foundation` | Greenfield architecture / scaffold / security reset |

### Examples

```text
/plan @docs/modules/employee-periodic-payroll-transactions.md

Add standing instructions for monthly loan deductions with end date
```

```text
/feature @docs/modules/pay-periods.md

Add CSV export for formula preview results
```

```text
/payroll-retro

Gap analysis: does materialize pay period support retroactive component changes per the user guide?
```

## Always-on rule

`.cursor/rules/doc-first-workflow.mdc` — agents must ask for a module doc if missing and must not invent schema/API/privileges.

## More detail

Full attachment checklist: `docs/prompts/README.md`
