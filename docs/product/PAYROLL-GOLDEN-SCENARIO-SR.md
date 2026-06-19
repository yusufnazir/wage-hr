# Golden payroll scenario — Suriname (demo tenant)

**Status:** Approved for engine regression (Phase 1–2); compliance bracket sign-off still pending  
**Version:** 0.2  
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
