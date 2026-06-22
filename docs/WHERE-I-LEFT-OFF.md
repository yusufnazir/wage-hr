# Where I left off

**Last updated:** 2026-06-18

| Field | Value |
|-------|-------|
| **Last completed** | P4 spec (§5.3) for Art. 10 exclusions — plan only, not coded |
| **Working on next** | `/feature` P4 Phase E (pension 1064/1065 + AOV beneficiary rules) |
| **Branch** | `main` |
| **Do not redo** | P2 (1049–1057); supervisor approval; P4 spec drafting |

## P4 — Art. 10 exclusions (planned)

Spec: [`docs/modules/suriname-wage-tax-rules.md`](modules/suriname-wage-tax-rules.md) §5.3

| Phase | Templates | What |
|-------|-----------|------|
| **E** | **1064** / **1065** | Pension payout + 2× AOV cap exclusion |
| **F** | **1058** / **1059** | Cost allowance (full exclusion) |
| **G** | **1060** / **1061** | Home–work transport (full exclusion) |
| **H** | **1062** / **1063** | Training / study (full exclusion) |

Pattern: cash payout + wage-tax exclusion line (mirror **1055**/**1056**).

## Demo tenant — Andre P2 (live)

`DemoP2BenefitStandingSeeder` seeds **1049**–**1054**, **1057** for Andre. Try Feb 2026 formula preview after app restart.

## Agent quick start

```
@docs/WHERE-I-LEFT-OFF.md
@docs/modules/suriname-wage-tax-rules.md

Implement P4 Phase E (pension 2× AOV).
```
