# Module: Suriname wage tax rules

**Feature slug:** `suriname-wage-tax-rules`  
**Milestone:** M5 (payroll engine — Suriname)  
**Authority:** This document is the **sole behavioral contract** for Suriname wage-tax withholding regimes, tax-free basic allowance, and wage exclusions.  
**Related:** [`payroll-engine-country.md`](./payroll-engine-country.md), [`suriname-loonbelasting-art17-bonus-vakantie.md`](./suriname-loonbelasting-art17-bonus-vakantie.md), [`payroll-wage-component-engine.md`](./payroll-wage-component-engine.md) §4.1, [`../datafiles/README-suriname-tax-data.md`](../datafiles/README-suriname-tax-data.md), [`../product/PAYROLL-GOLDEN-SCENARIO-SR.md`](../product/PAYROLL-GOLDEN-SCENARIO-SR.md)

---

## 1. Legal reference

Suriname wage tax (*loonbelasting*) is governed by the **Wet Loonbelasting**. Key articles for this module:

| Article | Topic |
|---------|--------|
| **Art. 10** | Amounts **not** counted as wages (exclusions) |
| **Art. 14** | Tax-free basic allowance (*belastingvrij*) |
| **Art. 17** | Special remuneration over multiple pay periods — **label method** (*Tabel Bijzondere Beloningen*) |
| **Art. 17a** | On-request regimes: payments at once, overtime, service-year tables |

External reference: [FiscLe wage tax summary (2025)](https://fiscleconsultancy.com/2025/07/23/wage-tax/).

Bonus and vacation allowance detail (Art. 10 i/j + Art. 17 steps) lives in [`suriname-loonbelasting-art17-bonus-vakantie.md`](./suriname-loonbelasting-art17-bonus-vakantie.md) — referenced here, not duplicated.

---

## 2. Default rule

**When no special wage-tax type applies**, earnings are taxed with **Table 1 (normal rate)** via platform statutory component **1019** (`SUR_WAGE_TAX` → `SR_WAGE_TAX_DEFAULT`).

- Primary rule resolution: `SurinameCountryRuleProvider` → `SR_WAGE_TAX_DEFAULT`
- Input base: **label loon** = `LOONBELASTING` minus vacation (1006), bonus (1007), extra earnings (1011), and overtime (1045–1047) payouts
- Belastingvrij (Art. 14): SRD **108 000/year** applied when `tenant_employee_compensation.apply_tax_exempt` is true
- Policy A frequency: annualize period base × 12, apply annual ladder, divide tax by 12

---

## 3. Tax-free basic allowance (belastingvrij)

| Field | Value |
|-------|-------|
| Annual threshold | SRD **108 000** |
| Monthly equivalent | SRD **9 000** (108 000 ÷ 12) |
| `rule_code` | `SR_TAX_FREE_WAGE_TAX_YEAR` |
| Template | **1005** (`SUR_TAX_FREE_WAGE_TAX`) |
| Toggle | `apply_tax_exempt` on employee compensation |

Tax on income **above** SRD 108 000/year uses Table 1 marginal rates (§4.1).

---

## 4. Seven wage-tax withholding regimes

Each regime maps to a payslip withholding line (ledger 2400–3000 series). Gross earning templates drive which regime applies.

### 4.1 Normal tax (Table 1)

| | |
|---|---|
| **When** | Default — all regular periodic wages without a special regime |
| **Law** | Periodic wage tax table |
| **`rule_code`** | `SR_WAGE_TAX_DEFAULT` |
| **Templates** | Gross: **1001** (base salary); Tax: **1019** |
| **Ledger** | 2400 Wage tax |
| **Method** | Progressive marginal ladder on label loon (Policy A annualization) |

**Annual brackets** (amounts per year, effective 2024-01-01):

| Portion of income | Rate |
|-------------------|------|
| Up to and including SRD 42 000 | 8% |
| Above SRD 42 000 up to SRD 84 000 | 18% |
| Above SRD 84 000 up to and including SRD 126 000 | 28% |
| Above SRD 126 000 | 38% |

**Monthly equivalents** (amounts per month — Table 1 ÷ 12):

| Portion of income | Rate |
|-------------------|------|
| Up to and including SRD 3 500 | 8% |
| Above SRD 3 500 up to and including SRD 7 000 | 18% |
| Above SRD 7 000 up to and including SRD 10 500 | 28% |
| Above SRD 10 500 | 38% |

Engine applies the **annual** ladder via Policy A; monthly figures are informational equivalents.

---

### 4.2 Overtime tax

| | |
|---|---|
| **When** | On request — overtime performed in a **one-month** wage period |
| **Law** | Art. 17a overtime table |
| **`rule_code`** | `SR_OVERTIME_MONTH` |
| **Templates** | Gross: **1045**–**1047**; Tax: **1020**; AOV: **1013** |
| **Ledger** | 2500 Wage tax overtime |
| **Method** | Direct monthly marginal ladder on overtime payout (no annualization) |
| **Effective from** | 2025-07-01 |

**Brackets** (amounts per month):

| Portion | Rate |
|---------|------|
| Up to and including SRD 2 500 | 5% |
| Above SRD 2 500 up to SRD 7 500 | 15% |
| Above SRD 7 500 | 25% |

Pre–Jul 2025 historical overtime brackets are **out of product scope** — not seeded and not required.

Overtime is **excluded from label loon**; normal **1019** does not tax overtime amounts.

---

### 4.3 Payment at once (lump sum)

| | |
|---|---|
| **When** | On request — lump-sum payments **not** relating to a specific multi-period wage span |
| **Law** | Art. 17a — payments at once table |
| **`rule_code`** | `SR_PAYMENTS_AT_ONCE_YEAR` |
| **Templates** | Gross: **1009** (`SUR_LUMP_SUM`); Tax: **1024** (`SUR_WAGE_TAX_LUMP_SUM`) |
| **Ledger** | 2900 Wage tax lump sum |
| **Method** | Progressive ladder on **benefit amount** (per payment, not annualized via label) |

**Brackets** (amounts per benefit, effective 2024-01-01):

| Portion | Rate |
|---------|------|
| Up to and including SRD 42 000 | 5% |
| Above SRD 42 000 up to and including SRD 84 000 | 15% |
| Above SRD 84 000 up to and including SRD 126 000 | 25% |
| Above SRD 126 000 | 35% |

Requires taxpayer request to Tax Authorities (*Belastingdienst*) in production practice; engine does not model approval workflow yet.

---

### 4.4 Jubilee (service anniversary)

| | |
|---|---|
| **When** | Service-anniversary / jubilee payments |
| **Law** | Art. 10 anniversary exclusion + Art. 17a service-year table for taxable portion |
| **`rule_code`** | `SR_SERVICE_YEARS_17A_MONTH` (`LEGACY_SERVICE_YEAR_TABLE`) |
| **Templates** | Gross: **1010** (`SUR_JUBILEE`); Tax: **1048** (`SUR_WAGE_TAX_JUBILEE`) |
| **Ledger** | Jubilee gross → 5310…006 debit; wage tax → 2400 |
| **Method** | (1) Apply Art. 10 anniversary exemption; (2) tax remainder per service-year % table |

**Service years source:** `tenant_employee.hire_date` → completed whole years as of pay-period end (`PayrollContext.countryRulesAsOf`). If `hire_date` is missing, jubilee wage tax **1048** = 0.

**Tax formula (when taxable remainder > 0):** `tax = referenceMonthWage × (serviceYearPct ÷ 100)` where `serviceYearPct` comes from `SR_SERVICE_YEARS_17A_MONTH` for the employee's tenure band.

**Art. 10 anniversary exemption** (exempt portion of jubilee, as fraction of one month’s wage):

| Service years | Exemption |
|---------------|-----------|
| 10 | ¼ month wage |
| 15 | ½ month wage |
| 20 | ¾ month wage |
| 25 | 1× month wage |
| 30 | 1½× month wage |
| 35 | 2× month wage |
| 40 | 3× month wage |

**Service-year tax table** (`SR_SERVICE_YEARS_17A_MONTH` — % of monthly wage used in legacy export):

| Years of service | % |
|------------------|---|
| 0–9 | 0% |
| 10–14 | 25% |
| 15–19 | 50% |
| 20–24 | 75% |
| 25–29 | 100% |
| 30–34 | 150% |
| 35–39 | 200% |
| 40+ | 300% |

**Not the same as payment at once (§4.3):** jubilee uses tenure-based exemption + service-year table; lump sum uses the 5/15/25/35% benefit ladder.

---

### 4.5 Extra income tax (Art. 17 — Tabel Bijzondere Beloningen)

| | |
|---|---|
| **When** | Special payment covering **several normal pay periods** (default path for irregular earnings) |
| **Law** | Art. 17 lid 2 (label method) |
| **`rule_code`** | Uses **`SR_WAGE_TAX_DEFAULT`** (Table 1) inside label calculation — no separate bracket set |
| **Templates** | Gross: **1011**; Tax: **1025**; AOV: **1018** |
| **Ledger** | 3000 Wage tax extra earnings |
| **Method** | Art. 17 label: divide payout by N periods, add slice to label loon, tax combined minus tax on label only, multiply difference by N |

**No Art. 10 cap** on extra earnings — full payout is taxable via Art. 17.

Default **N = 12** attribution periods; configurable on employee payroll inputs (`employeeArt17AttributionPeriods`).

Implementation: `SurinameWageTaxCalculator.computeArt17BijzondereBeloningTax`.

---

### 4.6 Vacation / holiday allowance

| | |
|---|---|
| **When** | Holiday allowance payments |
| **Law** | Art. 10(i) exemption + Art. 17 on taxable portion |
| **Exempt cap** | One month’s wage per year (pro-rata for shorter periods); configurable year threshold SRD **19 500** from 2025-07-01 (`SR_TAX_FREE_VACATION_YEAR`) |
| **Templates** | Gross: **1006**; Tax: **1021**; AOV: **1014** |
| **Ledger** | 2600 Wage tax vacation allowance |
| **Method** | `exempt = min(payout, referenceMonthWage, annualThreshold)` → Art. 17 on `(payout − exempt)` |

See [`suriname-loonbelasting-art17-bonus-vakantie.md`](./suriname-loonbelasting-art17-bonus-vakantie.md).

---

### 4.7 Bonus and gratuities allowance

| | |
|---|---|
| **When** | Bonuses and gratuities (without another special regime) |
| **Law** | Art. 10(j) exemption + Art. 17 on taxable portion |
| **Exempt cap** | Same pattern as vacation; year threshold SRD **19 500** from 2025-07-01 (`SR_TAX_FREE_BONUS_YEAR`) |
| **Templates** | Gross: **1007**; Tax: **1022**; AOV: **1015** |
| **Ledger** | 2700 Wage tax bonus |
| **Method** | Same as §4.6 |

---

## 5. Wage exclusions (Art. 10)

Amounts **not** counted as wages. Status in Wage Payroll as of this audit:

| Exclusion | Spec / cap | Engine status |
|-----------|------------|---------------|
| Relevant/necessary cost allowances (evidence required) | Actual costs | **Not implemented** |
| Pension scheme claims | — | **Not implemented** |
| Surinamese Accident Regulation claims | — | **Not implemented** |
| Cash/benefits in kind under wage claim | — | **Not implemented** |
| Obligatory pension contributions withheld | — | Partial (APF **1043/1044** as deduction, not Art. 10 exclusion) |
| Employer-arranged home–work transport | Full value | **Not implemented** |
| **Child allowance** | SRD **125**/child/month, max **500** (4 children) | **Implemented** — **1008** gross, **1023** exclusion (`SR_CHILD_ALLOWANCE_MONTH`) |
| **Holiday allowance** | ≤ 1 month wage, max **19 500**/year | **Implemented** — **1006** + `SR_TAX_FREE_VACATION_YEAR` |
| **Gratuities/bonus** | ≤ 1 month wage, max **19 500**/year | **Implemented** — **1007** + `SR_TAX_FREE_BONUS_YEAR` |
| Demise/disability accident benefits | — | **Not implemented** |
| Periodic wage-replacement benefits | — | **Not implemented** |
| Childbirth/illness/disability cost benefits | — | **Not implemented** |
| Demise-related benefits | — | **Not implemented** |
| Employer training (benefits in kind) | — | **Not implemented** |
| Employee goods damage compensation | — | **Not implemented** |
| Anniversary benefits (jubilee exemption table §4.4) | Tenure-based fractions | **Implemented** — `SurinameJubileeSupport` |
| Pension payment 2× AOV/year (SRD 4 500/month cap) | SRD 2 250 × 2 | **Not implemented** |
| Exchange rate compensation | Max SRD **800**/month | **Not implemented** |
| **Deductible acquisition costs** | 4%, max SRD **4 800**/year | **Implemented** — **1036** |
| **Free medical care (valuation)** | 3% of annual money wage, max SRD **200**/year | **Implemented** — **1042** |
| Free company car | ≥ 2% list price/year | **Not implemented** |
| Free housing | 7½% annual money wage | **Not implemented** |
| Free board and lodging | SRD 10/day | **Not implemented** |
| Free board | SRD 5/day | **Not implemented** |
| Hot meal | SRD 5 | **Not implemented** |
| Bread meal | SRD 1.50 | **Not implemented** |
| Free utilities | Actual chargeable amount | **Not implemented** |

---

## 6. Engine mapping

| # | Regime | `rule_code` | `country_rule_key` (tax line) | Java handler | Status |
|---|--------|-------------|-------------------------------|--------------|--------|
| 1 | Normal | `SR_WAGE_TAX_DEFAULT` | `SUR_WAGE_TAX` (1019) | `SurinameStatutoryContributor` | **Live** |
| 1b | Belastingvrij | `SR_TAX_FREE_WAGE_TAX_YEAR` | `SUR_TAX_FREE_WAGE_TAX` (1005) | `SurinameCountryRuleAlgorithms` | **Live** |
| 2 | Overtime | `SR_OVERTIME_MONTH` | `SUR_WAGE_TAX_OVERTIME` (1020) | `SurinameTenantDerivedComponentService` | **Live** (from 2025-07-01) |
| 3 | Payment at once | `SR_PAYMENTS_AT_ONCE_YEAR` | `SUR_WAGE_TAX_LUMP_SUM` (1024) | `SurinameTenantDerivedComponentService` | **Live** |
| 4 | Jubilee | `SR_SERVICE_YEARS_17A_MONTH` | `SUR_WAGE_TAX_JUBILEE` (1048) | `SurinameTenantDerivedComponentService` | **Live** |
| 5 | Extra income (Art. 17) | `SR_WAGE_TAX_DEFAULT` (inside label) | `SUR_WAGE_TAX_EXTRA_EARNINGS` (1025) | `SurinameTenantDerivedComponentService` | **Live** |
| 6 | Vacation | `SR_TAX_FREE_VACATION_YEAR` + Table 1 | `SUR_WAGE_TAX_VACATION_ALLOWANCE` (1021) | `SurinameTenantDerivedComponentService` | **Live** |
| 7 | Bonus | `SR_TAX_FREE_BONUS_YEAR` + Table 1 | `SUR_WAGE_TAX_BONUS` (1022) | `SurinameTenantDerivedComponentService` | **Live** |

**Data storage:** versioned `platform_country_tax_rule.parameters_json` (v2). Seeds: `data-m25-platform-country-tax-rules-sr-1.xml`, `data-m42-sr-child-allowance-tax-rules-1.xml`.  
**Resolution:** `SurinameTaxRuleResolutionService` as-of pay-period end.  
**Platform UI:** `/app/platform-country-tax-rules` (superadmin bracket editor).

---

## 7. Acceptance criteria

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Monthly label loon SRD 18 500, `apply_tax_exempt` true, belastingvrij fully applied | Wage tax **≈ SRD 4 930**/period — see golden scenario |
| 2 | Overtime SRD 259.62 in Feb 2026 | **1020** = SRD 33.75; **1013** AOV on overtime |
| 3 | Lump sum SRD 50 000 on **1009** | **1024** = SRD **3 300** (5/15/25/35% marginal ladder on benefit) |
| 4 | Jubilee 25-year employee, 1× month wage payout (ref. month SRD 6 000) | Art. 10 exempt SRD 6 000; **1048** = **0** |
| 5 | Extra earnings SRD 360, N=12, label loon SRD 6 000 | **1025** > 0; uses Art. 17 label on Table 1 |
| 6 | Vacation SRD 500 ≤ exempt cap | **1021** = 0 |
| 7 | Bonus above exempt cap | **1022** > 0; **1019** unchanged (no double count) |
| 8 | 2 children, `apply_tax_exempt` | **1008** = SRD 250; **1023** exclusion = SRD 250 |

---

## 8. Audit results (2026-06-18)

Audit performed against seeds, templates, engine paths, and tests. **No code changes** in this pass.

### 8.1 Seed verification (`platform_country_tax_rule`)

| `rule_code` | Spec match | Notes |
|-------------|------------|-------|
| `SR_WAGE_TAX_DEFAULT` | **Match** | 8/18/28/38% at 0–42k / 42–84k / 84–126k / 126k+; effective 2024-01-01 |
| `SR_TAX_FREE_WAGE_TAX_YEAR` | **Match** | amount 108 000 |
| `SR_OVERTIME_MONTH` | **Match** | 5/15/25% at 2 500 / 7 500; effective 2025-07-01 (pre-Jul history out of scope) |
| `SR_PAYMENTS_AT_ONCE_YEAR` | **Match** | 5/15/25/35% ladder seeded; **wired** via `computePaymentAtOnceTax` |
| `SR_SERVICE_YEARS_17A_MONTH` | **Match (data)** | Service-year % table seeded; **wired** via `computeJubileeWageTax` |
| `SR_TAX_FREE_VACATION_YEAR` | **Match** | 19 500 from 2025-07-01 |
| `SR_TAX_FREE_BONUS_YEAR` | **Match** | 19 500 from 2025-07-01 |
| `SR_CHILD_ALLOWANCE_MONTH` | **Match** | m42: 125/child, max 500, max 4 children (from 2021-07-01) |
| `SR_DEDUCTIBLE_EXPENSES_YEAR` | **Match** | 4% from 4 800 min |
| `SR_FREE_MEDICAL_YEAR` | **Match** | 3% on 0–200 |

### 8.2 Template verification (`data-m23-platform-wage-component-templates-sr-law-1.xml`)

| Code | `country_rule_key` | `platform_country_tax_rule_id` FK | Audit |
|------|-------------------|-----------------------------------|-------|
| 1009 | `SUR_LUMP_SUM` | — | Gross only; no tax rule FK |
| 1010 | `SUR_JUBILEE` | — | Gross only; no tax rule FK |
| 1048 | `SUR_WAGE_TAX_JUBILEE` | `...000004` (`SR_SERVICE_YEARS_17A_MONTH`) | **OK** |
| 1019 | `SUR_WAGE_TAX` | `...000001` (`SR_WAGE_TAX_DEFAULT`) | **OK** |
| 1020 | `SUR_WAGE_TAX_OVERTIME` | `...000001` | **Note:** FK points to default rule; runtime uses `SR_OVERTIME_MONTH` by code |
| 1021 | `SUR_WAGE_TAX_VACATION_ALLOWANCE` | `...000001` | **OK** (Art. 17 uses default ladder) |
| 1022 | `SUR_WAGE_TAX_BONUS` | `...000001` | **OK** |
| 1023 | `SUR_WAGE_TAX_CHILD_ALLOWANCE` | `...000001` | **OK** (exclusion via `SR_CHILD_ALLOWANCE_MONTH`) |
| 1024 | `SUR_WAGE_TAX_LUMP_SUM` | `...000002` (`SR_PAYMENTS_AT_ONCE_YEAR`) | **OK** |
| 1025 | `SUR_WAGE_TAX_EXTRA_EARNINGS` | `...000001` | **OK** |

### 8.3 Engine path verification

| Type | In `DERIVED_COMPONENT_KEYS`? | Handler | Verdict |
|------|------------------------------|---------|---------|
| Normal 1019 | Platform statutory | `SurinameStatutoryContributor` → `computePeriodTax(SR_WAGE_TAX_DEFAULT)` | **Live** |
| Overtime 1020 | Yes (`SUR_WAGE_TAX_OVERTIME`) | `overtimeWageTax()` → `SR_OVERTIME_MONTH` | **Live** (from 2025-07-01) |
| Lump sum 1024 | Yes (`SUR_WAGE_TAX_LUMP_SUM`) | `lumpSumWageTax()` → `SR_PAYMENTS_AT_ONCE_YEAR` | **Live** |
| Jubilee 1010 / 1048 | Yes (`SUR_WAGE_TAX_JUBILEE`) | `jubileeWageTax()` → Art. 10 + `SR_SERVICE_YEARS_17A_MONTH` | **Live** |
| Extra 1025 | Yes | `art17WageTax()` | **Live** |
| Vacation 1021 | Yes | `art17WageTax()` + `exemptPortion()` | **Live** |
| Bonus 1022 | Yes | `art17WageTax()` + `exemptPortion()` | **Live** |
| Child 1023 | Yes (`SUR_WAGE_TAX_CHILD_ALLOWANCE`) | `periodChildAllowanceExcludedFromLoon()` | **Live** |

`SurinameWageTaxCalculator` supports `MARGINAL_RATES`, `FLAT_RATE`, and `LEGACY_SERVICE_YEAR_TABLE` kinds.

### 8.4 Test coverage map

| Area | Test file | Covered |
|------|-----------|---------|
| Table 1 marginal tax | `SurinameWageTaxCalculatorTest` | Golden base 18 500 → 4 930; belastingvrij example |
| Art. 17 label method | `SurinameWageTaxCalculatorTest` | `computeArt17BijzondereBeloningTax` |
| Overtime rule effective dating | `SurinameCountryRuleProviderIT` | Rule present from 2025-07-01; absent before (by design) |
| Special remuneration splits | `SurinameSpecialRemunerationSupportTest` | Exempt caps, overtime sum |
| End-to-end payroll | `SurinamePayrollGoldenIT` | 1020, 1021, 1023, 1025, 1005; **not** 1024, 1010 |
| Lump sum / jubilee | `SurinameWageTaxCalculatorTest`, `SurinameTenantDerivedComponentServiceTest`, `SurinameJubileeSupportTest` | **1024** ladder; **1048** jubilee (25-year scenario) |
| Payment-at-once ladder | `SurinameWageTaxCalculatorTest` | SRD 50 000 → SRD 3 300 |

### 8.5 Platform UI

Route `/app/platform-country-tax-rules` lists SR rules with structured bracket editor (`CountryTaxRuleParametersEditor.tsx`, `country-tax-rule-parameters.ts`). All seeded `rule_code` rows are editable by superadmin; v2 kinds include `MARGINAL_RATES`, `FLAT_RATE`, `THRESHOLD_AMOUNT`, `PER_CHILD_MONTHLY`, `LEGACY_SERVICE_YEAR_TABLE`.

### 8.6 Gap summary (prioritized for future implementation)

| Priority | Gap | Impact |
|----------|-----|--------|
| **P2** | Benefits-in-kind valuations (car, housing, meals) | Compliance gap |
| **P3** | Inspector-approval flag for Art. 17a regimes | Process gap |

---

## 9. Related docs

- [`../datafiles/README-suriname-tax-data.md`](../datafiles/README-suriname-tax-data.md) — legacy CSV ↔ `rule_code` index
- [`payroll-engine-country.md`](./payroll-engine-country.md) — SR adapter contracts
- [`suriname-loonbelasting-art17-bonus-vakantie.md`](./suriname-loonbelasting-art17-bonus-vakantie.md) — Art. 17 vacation/bonus detail
- [`../product/PAYROLL-GOLDEN-SCENARIO-SR.md`](../product/PAYROLL-GOLDEN-SCENARIO-SR.md) — regression anchor (normal wage tax)
