# Where I left off

**Last updated:** 2026-06-18

| Field | Value |
|-------|-------|
| **Last completed** | Demo P2 seed for Andre (1049–1054, 1057); retro guide supervisor-approval step |
| **Working on next** | Manual UI test of Andre P2 on Feb 2026; optional `SurinamePayrollGoldenIT` Liquibase fix |
| **Branch** | `main` (uncommitted demo seed + doc updates) |
| **Do not redo** | P2 Art. 10 benefits-in-kind — Live on main; pay-period supervisor approval — Live on main |

## Demo tenant — Andre P2 (Art. 10)

After app start, `DemoP2BenefitStandingSeeder` sets Andre (`5fa00000-0000-4000-8000-000000000006`) standing inputs:

| Code | Input | Expected valuation (Feb 2026, wage SRD 6 000/mo) |
|------|-------|--------------------------------------------------|
| **1049** | List price SRD 180 000 | SRD **300.0000**/mo (AC-P2-1) |
| **1050** | Active (free housing) | SRD **450.0000**/mo (= 6 000 × 7.5%) |
| **1051** | 15 days | SRD **150.0000** |
| **1052** | 20 days | SRD **100.0000** |
| **1053** | 22 meals | SRD **110.0000** |
| **1054** | 20 meals | SRD **30.0000** |
| **1057** | SRD 275.50 chargeable | SRD **275.5000** |

**Try it:** Company calendar → Feb 2026 → materialize → formula preview / payroll run for Andre.

## Pay-period close workflow (on main)

1. Finalize a **FINAL** payroll run for the period
2. Supervisor: **Supervisor approve** (`PAY_PERIOD_SUPERVISOR_APPROVE`)
3. Operator: set status **CLOSED** (`PAY_PERIOD_MANAGE`)

Spec: [`docs/modules/pay-periods.md`](modules/pay-periods.md) §4.3  
Retro guide updated: [`docs/guides/gebruikershandleiding-retro-loonverwerking.md`](guides/gebruikershandleiding-retro-loonverwerking.md) §5.7

## Suriname tax roadmap

- **P1, P2:** Done
- **P3:** Product decision only — [`suriname-wage-tax-rules.md`](modules/suriname-wage-tax-rules.md) §5.2
- **Remaining §5 gaps:** transport, pension 2×AOV, training, evidence-required allowances

## Agent quick start

```
@docs/WHERE-I-LEFT-OFF.md

What should I work on next?
```
