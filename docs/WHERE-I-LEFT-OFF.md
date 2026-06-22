# Where I left off

**Last updated:** 2026-06-21

| Field | Value |
|-------|-------|
| **Last completed** | Suriname P2 (1049–1057) merged to `main`; pay-period supervisor approval committed |
| **Working on next** | Try P2 templates on demo tenant (option B), or employee periodic payroll transactions |
| **Branch** | `main` |
| **Do not redo** | P2 Art. 10 benefits-in-kind — Live on main |

## Pay-period close workflow (now on main)

1. Finalize a **FINAL** payroll run for the period
2. Supervisor: **Supervisor approve** (`PAY_PERIOD_SUPERVISOR_APPROVE`)
3. Operator: set status **CLOSED** (`PAY_PERIOD_MANAGE`)

Spec: [`docs/modules/pay-periods.md`](modules/pay-periods.md) §4.3

## Suriname tax roadmap

- **P1, P2:** Done
- **P3:** Product decision only (no Belastingdienst approval workflow) — [`suriname-wage-tax-rules.md`](modules/suriname-wage-tax-rules.md) §5.2
- **Remaining §5 gaps:** transport, pension 2×AOV, training, evidence-required allowances

## Agent quick start

```
@docs/WHERE-I-LEFT-OFF.md

What should I work on next?
```
