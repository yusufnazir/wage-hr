# Golden payroll scenario — Suriname (demo tenant)

**Status:** Approved for engine regression (Phase 1–2); compliance bracket sign-off still pending  
**Version:** 0.3  
**Roadmap:** Phase 0 deliverable; regression anchor for Phases 2–5  
**Related:** [`PAYROLL-ENGINE-ROADMAP.md`](./PAYROLL-ENGINE-ROADMAP.md), [`../modules/payroll-engine-country.md`](../modules/payroll-engine-country.md)

---

## Purpose

Single **end-to-end** reference case: known tenant data in → expected calculation bases, statutory tax, and NET out. Implementation phases assert against this document; update version when rules or annualization policy changes.

---

## Scenario identity

| Field | Value |
|-------|-------|
| Tenant | Demo (`tenant_id` `10000000-0000-0000-0000-000000000001`) |
| Company | Demo payroll company (`5fa00000-0000-4000-8000-000000000001`) |
| Payroll country | `SR` |
| Currency | `SRD` (company default) |
| Employee | Andre Ling (`5fa00000-0000-4000-8000-000000000006`) |
| Pay period | Feb 2026 (`5fa00000-0000-4000-8000-00000000000c`, `2026-02-01` … `2026-02-28`, status `OPEN`) |
| `countryRulesAsOf` | `2026-02-28` (period end) |

**Seed reference:** `data-m20-demo-tenant-payroll-seed-1.xml`

---

## Inputs (materialized payroll)

### Standing instruction / component

| Item | Value |
|------|-------|
| Tenant wage component | `5fa00000-0000-4000-8000-00000000000f` |
| Template / code | `1001` — Basissalaris |
| `calculation_method` | `FIXED_AMOUNT` |
| `default_amount` | **18 500.00** SRD |
| `platform_country_tax_rule_id` | `52000000-0000-0000-0000-000000000001` (`SR_WAGE_TAX_DEFAULT`) |
| Standing instruction | Active from `2026-01-01` for Andre → component above |

**Assumption for golden run:** Period transactions materialized so component `1001` evaluates to **18 500.00** for February 2026 (no overtime, no manual overrides). If materialize uses compensation rate instead of default, document actual engine input in Phase 1 IT setup.

### P2 Art. 10 benefits (Andre demo seed)

`DemoP2BenefitStandingSeeder` (app startup) adds standing inputs for Andre only:

| Code | Standing input | Expected derived amount (with wage SRD 6 000/mo) |
|------|----------------|---------------------------------------------------|
| `1049` | List price 180 000 | 300.0000 |
| `1050` | Active | 450.0000 |
| `1051` | 15 days | 150.0000 |
| `1052` | 20 days | 100.0000 |
| `1053` | 22 meals | 110.0000 |
| `1054` | 20 meals | 30.0000 |
| `1057` | 275.50 | 275.5000 |

See [`suriname-wage-tax-rules.md`](../modules/suriname-wage-tax-rules.md) §5.1 AC-P2-*.

### P4 Art. 10 exclusions (Andre demo seed)

`DemoP4ExclusionStandingSeeder` (app startup) adds payout standing inputs for Andre only:

| Code | Standing input | Expected derived amount |
|------|----------------|-------------------------|
| `1058` | 425.00 | **1058** = **425.0000**, **1059** = **425.0000** |
| `1060` | 1 200.00 | **1060** = **1200.0000**, **1061** = **1200.0000** |
| `1062` | 3 500.00 | **1062** = **3500.0000**, **1063** = **3500.0000** |
| `1064` | 3 000.00 | **1064** = **3000.0000**, **1065** = **3000.0000** (full exclusion under cap) |

P4 pairs are **label-loon neutral** per AC-P4-* (unit-tested in `SurinameTenantDerivedComponentServiceTest`). `SurinamePayrollGoldenIT.feb2026AndrePreviewIncludesP4ExclusionPairs` asserts payout/exclusion line amounts for the demo seed inputs.

### Tax rule in force (`SR_WAGE_TAX_DEFAULT`)

From `data-m25-platform-country-tax-rules-sr-1.xml` (effective `2024-01-01`):

```json
{
  "v": 2,
  "legacyTariffTypeId": 1,
  "freq": "YEAR",
  "kind": "MARGINAL_RATES",
  "source": "legacy-740-2024",
  "rows": [
    { "i": 2, "pct": 8,  "min": 0,      "max": 42000  },
    { "i": 3, "pct": 18, "min": 42000,  "max": 84000  },
    { "i": 4, "pct": 28, "min": 84000,  "max": 126000 },
    { "i": 5, "pct": 38, "min": 126000 }
  ]
}
```

### Base effects (after migration M34)

Template `1001` expects effects (typical migration from legacy flags):

| Base code | Direction | Type | Value |
|-----------|-----------|------|-------|
| `GROSS` | INCREASE | FULL | 100 |
| `LOONBELASTING` | INCREASE | FULL | 100 |
| `AOV`, `AWW`, `SZF`, `PENSION` | INCREASE | FULL | 100 |
| `NET` | INCREASE | FULL | 100 |
| `VACATION` | IGNORE | FULL | 0 |

---

## Expected outputs by roadmap phase

### Phase 1 (preview today + orchestrator)

**Component evaluation**

| Component code | Expected amount (SRD) |
|----------------|----------------------|
| `1001` | 18 500.00 |

**`employeeBaseTotals` (accumulator, after Phase 1 behavior)**

| Base code | Expected total (SRD) | Notes |
|-----------|------------------------|-------|
| `GROSS` | 18 500.00 | FULL effect on gross |
| `LOONBELASTING` | 18 500.00 | Taxable wage |
| `AOV` | 18 500.00 | Subject base |
| `NET` | 18 500.00 | ADD_TO_NET migration |
| `VACATION` | 0 or absent | IGNORE |

*Statutory tax lines: not computed.*

### Phase 2 (statutory — approved annualization Policy A)

**Annualization (Policy A, ADR gate 2026-05-17):** For `freq: YEAR` rules on **monthly** payroll, annualize the period base (`periodBase × 12`), apply the marginal ladder on the annual amount, then **de-annualize** withholding as `annualTax ÷ 12` (scale HALF_UP, 4 dp).

Example: `18 500 × 12 = 222 000` SRD taxable annual wage.

**Annual tax (Policy A on `SR_WAGE_TAX_DEFAULT` / legacy-740-2024):**

| Bracket | Tax (SRD) |
|---------|-----------|
| 8% × 42 000 | 3 360 |
| 18% × 42 000 | 7 560 |
| 28% × 42 000 | 11 760 |
| 38% × 96 000 | 36 480 |
| **Annual total** | **59 160** |
| **Monthly (÷12)** | **≈ 4 930.00** |

| Output | Expected (SRD) |
|--------|----------------|
| Wage tax deduction (period) | **4 930.0000** |
| Platform component code | `WAGE_TAX` (`50000000-0000-0000-0000-000000000001`) |
| `LOONBELASTING` base | 18 500.00 (unchanged) |

**AOV (`SR_AOV_PREMIUM_MONTH`, 4% flat on `AOV` base, `freq: MONTH`):** employee share **740.0000** SRD when full `AOV` base = 18 500 (Phase 2 ships with wage tax).

### Phase 5 (NET closure — provisional)

| Item | Formula (conceptual) | Provisional (SRD) |
|------|----------------------|-------------------|
| Gross earnings | Component `1001` | 18 500.00 |
| − Wage tax | Phase 2 | ≈ 4 930.00 |
| − AOV employee | Phase 2 | ≈ 740.00 |
| **NET pay** | | **≈ 12 830.00** |

*Replace with signed-off numbers after annualization policy is confirmed.*

---

## API exercise (manual / IT)

```http
POST /api/v1/pay-periods/5fa00000-0000-4000-8000-00000000000c/formula-preview
Content-Type: application/json

{
  "employeeIds": ["5fa00000-0000-4000-8000-000000000006"]
}
```

**Privilege:** `PAY_PERIOD_VIEW`  
**Precondition:** User in demo tenant; Feb 2026 period open; transactions materialized.

**Assert today (Phase 1):**

- `items[].evaluatedAmount` for component `1001` = 18500.0000 (scale 4)
- `employeeBaseTotals[employeeId].GROSS` = 18500.0000
- `employeeBaseTotals[employeeId].LOONBELASTING` = 18500.0000

**Assert Phase 2+:** add statutory lines and tax amount per signed-off table.

---

## Sign-off checklist

- [x] Annualization policy for `freq: YEAR` on monthly payroll: **Policy A** (see § Phase 2).
- [ ] Compliance confirms bracket data matches official 2024 tariefgroep export (`legacy-740-2024`).
- [x] Engineering links `SurinameWageTaxCalculatorTest` and `SurinamePayrollGoldenIT` to this document version.
- [x] NET pay **12 830.00** SRD shipped (`NetPayCalculator` + `SurinamePayrollGoldenIT`).

---

## Document history

| Version | Date | Change |
|---------|------|--------|
| 0.1 | 2026-05-17 | Initial provisional scenario from demo seed |
| 0.2 | 2026-05-17 | Policy A annualization approved; Phase 2 expected amounts |
| 0.3 | 2026-06-18 | P4 demo seed + `SurinamePayrollGoldenIT` P2/P4 derived-line regression |
