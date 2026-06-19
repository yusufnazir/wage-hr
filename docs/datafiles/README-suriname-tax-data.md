# Suriname tax tariff types and brackets (legacy export)

**Authoritative spec (seven wage-tax regimes + audit):** [`docs/modules/suriname-wage-tax-rules.md`](../modules/suriname-wage-tax-rules.md)

**Source files (reference / re-import):**

| File | Legacy table | Rows |
|------|----------------|------|
| `Payroll data - tax_tariff_type.csv` | `tax_tariff_type` | 12 tariff types for country **740** (Suriname) |
| `Payroll data - tax_bracket.csv` | `tax_bracket` | Bracket rows keyed by `taxtarifftypeid` |

**Country id:** `740` in the legacy system = **ISO `SR`** in Wage Payroll (`platform_country`, company `payrollCountry`).

**Effective dating:** Bracket rows use `startdate` / `enddate`. Several types changed on **2025-07-01** (overtime, vacation/bonus thresholds).

---

## Seven wage-tax withholding regimes

Product-facing summary (full law + engine audit in module spec):

| # | Regime | `rule_code` | Tax template | Engine |
|---|--------|-------------|--------------|--------|
| 1 | Normal (Table 1) | `SR_WAGE_TAX_DEFAULT` | **1019** | **Live** |
| 2 | Overtime | `SR_OVERTIME_MONTH` | **1020** | **Live** (from 2025-07-01) |
| 3 | Payment at once | `SR_PAYMENTS_AT_ONCE_YEAR` | **1024** | **Live** |
| 4 | Jubilee | `SR_SERVICE_YEARS_17A_MONTH` | **1048** | **Live** — gross **1010** |
| 5 | Extra income (Art. 17) | `SR_WAGE_TAX_DEFAULT` (label) | **1025** | **Live** |
| 6 | Vacation allowance | Art. 10 + Art. 17 | **1021** | **Live** |
| 7 | Bonus / gratuities | Art. 10 + Art. 17 | **1022** | **Live** |

**Default:** unspecified earnings → regime 1 (normal Table 1 on label loon). Belastingvrij: SRD **108 000**/year (`SR_TAX_FREE_WAGE_TAX_YEAR`).

---

## Tariff types (`tax_tariff_type.csv`)

| id | Name | Frequency | Role in payroll |
|----|------|-----------|-----------------|
| 1 | Wage tax | YEAR | Progressive loonbelasting on taxable wage — **primary** (`SR_WAGE_TAX_DEFAULT`) |
| 2 | Payments at once | YEAR | Lump-sum / eenmalige uitkering ladder (`SR_PAYMENTS_AT_ONCE_YEAR`) |
| 3 | Overtime | MONTH | Overtime withholding brackets from 2025-07-01 (`SR_OVERTIME_MONTH`) |
| 4 | Tax free service years article 17a | MONTH | Service-year % table by years of service (`SR_SERVICE_YEARS_17A_MONTH`) |
| 5 | AOV premium percentage | MONTH | Flat **4%** employee AOV (`SR_AOV_PREMIUM_MONTH`) |
| 6 | Deductible expenses | YEAR | Deductible-cost rule (min 4800 @ 4%) (`SR_DEDUCTIBLE_EXPENSES_YEAR`) |
| 8 | Tax free wage tax | YEAR | Belastingvrij annual threshold **108 000** (`SR_TAX_FREE_WAGE_TAX_YEAR`) |
| 9 | Tax free for Vacation allowance | YEAR | Threshold **19 500** from 2025-07-01 (`SR_TAX_FREE_VACATION_YEAR`) |
| 10 | Tax free for Bonus | YEAR | Threshold **19 500** from 2025-07-01 (`SR_TAX_FREE_BONUS_YEAR`) |
| 11 | Child allowance (Art. 10(h)) | MONTH | Gross **1008**: per child 75 / 125; exclusion cap 300 / 500 (`SR_CHILD_ALLOWANCE_MONTH`, `PER_CHILD_MONTHLY`) |
| 12 | Free medical care | YEAR | 3% on 0–200 (`SR_FREE_MEDICAL_YEAR`) |
| 13 | AP contribution | MONTH | Placeholder — no brackets in export (`SR_AP_CONTRIBUTION_MONTH`) |

---

## Wage tax brackets (tariff type 1) — used in golden scenario

From `tax_bracket.csv` (effective 2024-01-01):

| Index | % | Min | Max |
|-------|---|-----|-----|
| 2 | 8 | 0 | 42 000 |
| 3 | 18 | 42 000 | 84 000 |
| 4 | 28 | 84 000 | 126 000 |
| 5 | 38 | 126 000 | (open) |

Annual base is annualized from the period `LOONBELASTING` base, tax computed on the ladder, then divided by periods per year (Policy A — see `docs/modules/payroll-engine-country.md`).

---

## How this repo stores the data

Legacy rows are **not** kept in separate `tax_tariff_type` / `tax_bracket` tables. They are normalized into **`platform_country_tax_rule`** as versioned `parameters_json` (contract **v2**).

**Liquibase seed:** `backend/src/main/resources/db/changelog/dml/data-m25-platform-country-tax-rules-sr-1.xml`

Each rule has:

- `country_code` = `SR`
- `rule_code` (e.g. `SR_WAGE_TAX_DEFAULT`, `SR_AOV_PREMIUM_MONTH`)
- `legacyTariffTypeId` in JSON (matches CSV `tax_tariff_type.id`)
- `kind`: `MARGINAL_RATES`, `FLAT_RATE`, `THRESHOLD_AMOUNT`, `AMOUNT_BAND`, `LEGACY_SERVICE_YEAR_TABLE`, `PLACEHOLDER`

**Runtime:** `SurinameTaxRuleResolutionService` picks active rules as-of pay-period end date → `SurinameWageTaxCalculator` / `SurinameStatutoryContributor`.

**Platform UI:** `/app/platform-country-tax-rules` (superadmin) — structured bracket editor for v2 `parameters_json` (progressive rows, flat %, thresholds, amount bands, service-year table); JSON fallback for edge cases.

---

## Bonus and vacation allowance (Wet Loonbelasting art. 10 & 17)

**Law (application, not exact SRD caps):** [`docs/modules/suriname-loonbelasting-art17-bonus-vakantie.md`](../modules/suriname-loonbelasting-art17-bonus-vakantie.md)

| Step | Rule |
|------|------|
| Exempt portion | Art. 10 i/j (≈ one month’s wage per year, pro-rata); configurable via `SR_TAX_FREE_VACATION_YEAR` / `SR_TAX_FREE_BONUS_YEAR` |
| Tax on taxable portion | **Art. 17** label method — `SurinameWageTaxCalculator.computeArt17BijzondereBeloningTax` → payslip lines **1021** / **1022** |
| Normal wage tax | **1019** on regular `LOONBELASTING` only — must not double-count full 1006/1007 |

**Not** art. 17a (that is a separate on-request regime for lump-sum tables).

---

## Engine wiring status (SR)

| Tariff / rule | Seeded | Used in payroll |
|---------------|--------|-----------------|
| 1 Wage tax | Yes | Statutory **1019** — progressive ladder on label loon (after belastingvrij when `apply_tax_exempt`) |
| 2 Payments at once | Yes | **1024** on gross **1009** — `SR_PAYMENTS_AT_ONCE_YEAR` via `SurinameTenantDerivedComponentService` |
| 3 Overtime | Yes | **1020** + **1013** AOV — `SR_OVERTIME_MONTH` from 2025-07-01 (pre-Jul history not in scope) |
| 4 Service years (17a) | Yes | **1048** on gross **1010** — Art. 10 exemption + `SR_SERVICE_YEARS_17A_MONTH` |
| 5 AOV | Yes | Statutory **1012** + derived lines on special payouts |
| 6 Deductible 4% / max 4 800/year | Yes | Template **1036** (`SUR_AQUISITION_COSTS`) |
| 8 Belastingvrij 108 000/year | Yes | Template **1005** + reduces wage-tax base when `apply_tax_exempt` |
| 9 Vacation tax-free year | Yes | Exempt split + **1021** Art. 17 — **implemented** |
| 10 Bonus tax-free year | Yes | Exempt split + **1022** Art. 17 — **implemented** |
| 11 Child allowance | Yes | **1008** gross + **1023** Art. 10(h) exclusion |
| 12 Free medical 3% / max 200/year | Yes | Template **1042** benefit valuation |
| — Taxable income display | — | Template **1004** = label loon |
| 13 AP contribution | Yes | APF schedule via `SurinameApfCalculator` |

Reference: [FiscLe wage tax summary (2025)](https://fiscleconsultancy.com/2025/07/23/wage-tax/) — algorithms in `SurinameCountryRuleAlgorithms`, templates via `country_rule_key`.

Employee compensation toggles (`apply_taxes`, `apply_tax_exempt`, `apply_aov`) live on `tenant_employee_compensation`; only taxes and AOV affect payroll today.

---

## Updating rates from CSV

1. Edit or replace rows in the CSVs (or official tariefgroep export).
2. Regenerate / update `parameters_json` in `data-m25-platform-country-tax-rules-sr-1.xml` (or a new changeset with later `effective_from`).
3. Run golden tests: `SurinameWageTaxCalculatorTest`, `SurinamePayrollGoldenIT`, `docs/product/PAYROLL-GOLDEN-SCENARIO-SR.md`.

Do not hard-code bracket tables in Java; extend `SurinameWageTaxCalculator` **kinds** only when a tariff type needs behaviour not expressible in v2 JSON.

---

## Related docs

- `docs/modules/suriname-wage-tax-rules.md` — **authority** for seven regimes, exclusions, audit
- `docs/modules/payroll-engine-country.md` — SR engine contracts
- `docs/modules/suriname-loonbelasting-art17-bonus-vakantie.md` — Art. 17 vacation/bonus detail
- `docs/modules/payroll-wage-component-engine.md` — §4.1 `parameters_json` v2
- `docs/product/PAYROLL-GOLDEN-SCENARIO-SR.md` — acceptance amounts
