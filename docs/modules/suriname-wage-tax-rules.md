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

Overtime is **excluded from label loon**; **1020** and **1013** apply whenever overtime gross (**1045**–**1047**) amount &gt; 0 (§5.2).

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

**1024** applies whenever gross **1009** is present with amount &gt; 0 (§5.2).

---

### 4.4 Jubilee (service anniversary)

| | |
|---|---|
| **When** | Service-anniversary / jubilee payments |
| **Law** | Art. 10 anniversary exclusion + Art. 17a payment-at-once table on taxable portion |
| **`rule_code`** | `SR_PAYMENTS_AT_ONCE_YEAR` (same marginal ladder as §4.3) |
| **Templates** | Gross: **1010** (`SUR_JUBILEE`); Tax: **1048** (`SUR_WAGE_TAX_JUBILEE`) |
| **Ledger** | Jubilee gross → 5310…006 debit; wage tax → 2400 |
| **Method** | (1) Apply Art. 10 anniversary exemption; (2) tax **taxable remainder** with payment-at-once marginal ladder (5/15/25/35% on benefit amount) |

**Service years source:** `tenant_employee.hire_date` → completed whole years as of pay-period end (`PayrollContext.countryRulesAsOf`). Required for Art. 10 exemption; if `hire_date` is missing, **1048** = 0 (no exemption computed).

**Tax formula (when taxable remainder > 0):** `tax = computePaymentAtOnceTax(taxableRemainder)` using `SR_PAYMENTS_AT_ONCE_YEAR` — same function as **1024** on lump sum, but base is **jubilee taxable remainder** after Art. 10 exemption (not full payout).

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

**Legacy note:** `SR_SERVICE_YEARS_17A_MONTH` (`LEGACY_SERVICE_YEAR_TABLE`) remains seeded for historical reference; **1048** no longer uses the service-year % table. Payslip line **1048** is retained for reporting.

**Difference from lump sum (§4.3):** jubilee applies Art. 10 tenure exemption first; lump sum taxes the full benefit with no anniversary exemption.

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
| Exchange rate compensation | Max SRD **800**/month | **Implemented** — **1055** gross, **1056** exclusion (`SR_EXCHANGE_RATE_COMPENSATION_MONTH`) |
| **Deductible acquisition costs** | 4%, max SRD **4 800**/year | **Implemented** — **1036** |
| **Free medical care (valuation)** | 3% of annual money wage, max SRD **200**/year | **Implemented** — **1042** |
| Free company car | ≥ 2% list price/year | **Implemented** — **1049** |
| Free housing | 7½% annual money wage | **Implemented** — **1050** |
| Free board and lodging | SRD 10/day | **Implemented** — **1051** (`SR_BOARD_LODGING_DAY`) |
| Free board | SRD 5/day | **Implemented** — **1052** (`SR_BOARD_DAY`) |
| Hot meal | SRD 5 | **Implemented** — **1053** (`SR_HOT_MEAL_UNIT`) |
| Bread meal | SRD 1.50 | **Implemented** — **1054** (`SR_BREAD_MEAL_UNIT`) |
| Free utilities | Actual chargeable amount | **Implemented** — **1057** (`SUR_FREE_UTILITIES_BENEFIT`) |

---

### 5.1 P2 — Art. 10 benefits-in-kind valuations (v1 planned)

**Status:** Spec complete — **1049** / **1050** / **1051**–**1054** / **1055** / **1056** / **1057** **Live** (P2 Phase A–D).

**Legal note:** FiscLe / Wet Loonbelasting assesses the rows below as **taxable benefit-in-kind valuations** added to wages. **Exchange rate compensation** is **not** a benefit valuation — it is an Art. 10 **exclusion** (cash paid, up to cap not counted as wage). Included here because it shares the P2 payroll-input pattern and §5 audit row.

**Out of scope v1:** home–work transport, pension 2×AOV, employer training, generic evidence-required cost allowances, pre-2022 exchange-rate history (2021 split month rules), upward company-car valuation above statutory minimum. **Inspector-approval workflows:** see **§5.2** (P3).

#### Shared v1 conventions

| Topic | v1 rule |
|-------|---------|
| **Money wage base** | For %-of-wage benefits (**1050** housing, and cross-check with **1042**): `LOONBELASTING` subtotal at derived evaluation **before** benefit-in-kind valuation lines (**1042**, **1049**–**1054**, **1057**). Excludes those lines to avoid circularity. Cash money earnings only (templates with `SUR_NORMAL_WAGE` and equivalent cash gross). |
| **GROSS base** | Benefit-in-kind valuation lines: **no effect** on `GROSS` (non-cash; mirrors **1042**). Exchange-rate cash line **1055**: **INCREASE** `GROSS` and `LOONBELASTING` by full payout. |
| **LOONBELASTING base** | Each benefit valuation line: **INCREASE** by derived valuation amount. Exchange rate: **1055** increases by payout; wage-tax base reduced by **1056** exclusion (see child-allowance pattern **1008** / **1023**). |
| **NET pay** | Benefit valuations: **no cash effect** (`NO_EFFECT` or zero net — informational earning). **1055** exchange payout: **ADD_TO_NET**. |
| **AOV / SZF / pension bases** | Benefit valuations v1: **no effect** (same as **1042**). **1055** cash payout: follow default cash-earning base effects (v1: **INCREASE** `AOV` same as **1001**). |
| **Wage tax regime** | Valuations feed **label loon** → normal Table 1 (**1019** / `SR_WAGE_TAX_DEFAULT`). No separate bracket set. |
| **Activation** | Tenant enables template from SR catalog; recurring standing instruction and/or per-period `tenant_wage_component_transaction` (amount and/or quantity). |
| **Effective dating** | Platform rules `effective_from` as in per-row table; resolution `SurinameTaxRuleResolutionService` as-of pay-period end. |
| **Rounding** | HALF_UP, 4 dp (engine standard). |

#### 5.1.1 Free company car

| Field | Value |
|-------|-------|
| **Law** | Art. 10 benefit-in-kind — **at least** 2% of list price per year |
| **Template** | **1049** (new) — `SUR_COMPANY_CAR_BENEFIT` |
| **`rule_code`** | `SR_COMPANY_CAR_YEAR` |
| **Formula** | `periodValuation = listPrice × 2% ÷ periodsPerYear` (v1 uses statutory **minimum** only; no upward override) |
| **List price input** | Employer: `default_amount` on standing instruction **or** period transaction `amount` (SRD catalog/list price). Quantity ignored v1. |
| **Employee input** | None (employer-only). |
| **`country_rule_key`** | `SUR_COMPANY_CAR_BENEFIT` |
| **LOONBELASTING** | INCREASE by derived valuation |
| **GROSS** | IGNORE |
| **Effective from** | 2024-01-01 (stable rate 2024–2026 per FiscLe) |
| **Acceptance (AC-P2-1)** | List price SRD **180 000**, monthly payroll (`periodsPerYear = 12`) → **1049** = SRD **300.0000** (= 180 000 × 0.02 ÷ 12) |

#### 5.1.2 Free housing

| Field | Value |
|-------|-------|
| **Law** | Art. 10 — 7½% of yearly wage in money |
| **Template** | **1050** (new) — `SUR_FREE_HOUSING_BENEFIT` |
| **`rule_code`** | `SR_FREE_HOUSING_YEAR` |
| **Formula** | `annualValuation = moneyWageAnnual × 7.5%`; `periodValuation = annualValuation ÷ periodsPerYear` where `moneyWageAnnual = moneyWageBase × periodsPerYear` |
| **Money wage base** | Shared convention above (pre–benefit-in-kind `LOONBELASTING`). |
| **Employee input** | None v1 (binary component on/off via standing instruction). |
| **Employer input** | Enable component when free housing provided. |
| **`country_rule_key`** | `SUR_FREE_HOUSING_BENEFIT` |
| **LOONBELASTING** | INCREASE by derived valuation |
| **GROSS** | IGNORE |
| **Effective from** | 2024-01-01 |
| **Acceptance (AC-P2-2)** | Money wage base SRD **8 000**/month → **1050** = SRD **600.0000** (= 8 000 × 12 × 0.075 ÷ 12) |

#### 5.1.3 Free board and lodging / board / hot meal / bread meal

Daily-cap benefits use **transaction quantity** = count of days or meals in the pay period (same mechanism as **1008** children count).

| Benefit | Template | `country_rule_key` | `rule_code` | Cap (SRD) | Quantity unit |
|---------|----------|-------------------|-------------|-----------|---------------|
| Board + lodging | **1051** | `SUR_BOARD_LODGING_BENEFIT` | `SR_BOARD_LODGING_DAY` | **10** / day | days |
| Board only | **1052** | `SUR_BOARD_BENEFIT` | `SR_BOARD_DAY` | **5** / day | days |
| Hot meal | **1053** | `SUR_HOT_MEAL_BENEFIT` | `SR_HOT_MEAL_UNIT` | **5** / meal | meals |
| Bread meal | **1054** | `SUR_BREAD_MEAL_BENEFIT` | `SR_BREAD_MEAL_UNIT` | **1.50** / meal | meals |

| Field | Value |
|-------|-------|
| **Formula** | `periodValuation = quantity × cap` (no pro-rata partial days v1; quantity is whole days/meals entered by payroll) |
| **Employee input** | None |
| **Employer input** | Period quantity (days or meals). Optional zero = component omitted from run. |
| **LOONBELASTING** | INCREASE each line by its valuation |
| **GROSS** | IGNORE |
| **NET** | NO cash effect |
| **Effective from** | 2024-01-01 |
| **Multiple types same period** | Allowed — caps are independent (e.g. **1051** + **1053** same day permitted v1). |
| **Acceptance (AC-P2-3)** | **1051** quantity **15** → **1051** = SRD **150.0000** |
| **Acceptance (AC-P2-3b)** | **1052** quantity **20** → **1052** = SRD **100.0000** |
| **Acceptance (AC-P2-4)** | **1053** quantity **22** → **1053** = SRD **110.0000** |
| **Acceptance (AC-P2-5)** | **1054** quantity **20** → **1054** = SRD **30.0000** |

#### 5.1.4 Exchange rate compensation (Art. 10 exclusion)

| Field | Value |
|-------|-------|
| **Law** | Art. 10 exclusion — up to SRD **800**/month (2022–2026 per FiscLe) |
| **Templates** | **1055** (new) gross cash — `SUR_EXCHANGE_RATE_COMPENSATION`; **1056** (new) exclusion display — `SUR_WAGE_TAX_EXCHANGE_RATE` |
| **`rule_code`** | `SR_EXCHANGE_RATE_COMPENSATION_MONTH` |
| **Gross formula** | `payout = period transaction amount` (employer-entered cash paid) |
| **Exclusion formula** | `excluded = min(payout, maxAmount)` where `maxAmount = 800` from rule |
| **Taxable delta** | `max(payout − excluded, 0)` remains in label loon after exclusion subtracted in `adjustTaxableBaseForWageTax` (mirror **1008** / **1023**) |
| **Employer input** | Period amount (SRD paid as exchange-rate compensation). |
| **Employee input** | None |
| **LOONBELASTING** | **1055**: INCREASE full payout; wage-tax computation subtracts **1056** exclusion |
| **GROSS** | **1055**: INCREASE full payout |
| **NET** | **1055**: ADD_TO_NET full payout |
| **Effective from** | 2022-01-01 (`maxAmount` 800); pre-2022 tiers **out of scope** |
| **Acceptance (AC-P2-6)** | Payout SRD **950** → **1055** = **950.0000**, **1056** = **800.0000**, net taxable increment **150.0000** on label loon |

#### 5.1.5 Free utilities (gas, electricity, water)

| Field | Value |
|-------|-------|
| **Law** | Art. 10 — amount that would otherwise have been charged to the employee |
| **Template** | **1057** (new) — `SUR_FREE_UTILITIES_BENEFIT` |
| **`rule_code`** | None required v1 (actual amount; no statutory cap). Optional seed `SR_FREE_UTILITIES_ACTUAL` placeholder for platform UI consistency. |
| **Formula** | `periodValuation = period transaction amount` (employer enters chargeable utility cost) |
| **Employer input** | Period amount (SRD). |
| **Employee input** | None |
| **`country_rule_key`** | `SUR_FREE_UTILITIES_BENEFIT` |
| **LOONBELASTING** | INCREASE by entered amount |
| **GROSS** | IGNORE |
| **Effective from** | 2024-01-01 (rate is “actual”; date anchors rule row only) |
| **Acceptance (AC-P2-7)** | Entered amount SRD **275.50** → **1057** = **275.5000** |

#### 5.1.6 Template summary (new codes)

| Code | Name | Type | `country_rule_key` | `rule_code` |
|------|------|------|-------------------|-------------|
| **1049** | Free company car (valuation) | Derived earning | `SUR_COMPANY_CAR_BENEFIT` | `SR_COMPANY_CAR_YEAR` |
| **1050** | Free housing (valuation) | Derived earning | `SUR_FREE_HOUSING_BENEFIT` | `SR_FREE_HOUSING_YEAR` |
| **1051** | Free board and lodging | Derived earning | `SUR_BOARD_LODGING_BENEFIT` | `SR_BOARD_LODGING_DAY` |
| **1052** | Free board | Derived earning | `SUR_BOARD_BENEFIT` | `SR_BOARD_DAY` |
| **1053** | Hot meal | Derived earning | `SUR_HOT_MEAL_BENEFIT` | `SR_HOT_MEAL_UNIT` |
| **1054** | Bread meal | Derived earning | `SUR_BREAD_MEAL_BENEFIT` | `SR_BREAD_MEAL_UNIT` |
| **1055** | Exchange rate compensation | Cash earning | `SUR_EXCHANGE_RATE_COMPENSATION` | — |
| **1056** | Wage tax exchange rate exclusion | Derived informational | `SUR_WAGE_TAX_EXCHANGE_RATE` | `SR_EXCHANGE_RATE_COMPENSATION_MONTH` |
| **1057** | Free utilities (valuation) | Derived earning | `SUR_FREE_UTILITIES_BENEFIT` | — (optional placeholder) |

**Reference pattern:** **1042** (`SUR_FREE_MEDICAL_BENEFIT` / `SR_FREE_MEDICAL_YEAR`) — already **Live**; P2 rows extend the same engine path (`SurinameTenantDerivedComponentService` + `SurinameCountryRuleAlgorithms`).

#### 5.1.7 Edge cases (v1)

| Case | Behavior |
|------|----------|
| List price zero / missing (**1049**) | Valuation **0**; trace warning optional |
| Money wage base zero (**1050**) | Valuation **0** |
| Quantity zero / missing (meal/board lines) | Valuation **0** |
| Exchange payout ≤ 800 | **1056** = payout; no taxable remainder |
| Exchange payout > 800 | Taxable remainder = payout − 800 |
| Belastingvrij employee | Benefit valuations still increase label loon before belastingvrij cap applied |
| Component not on employee | No derived lines; no base effect |

#### 5.1.8 UX (v1)

| Actor | Action |
|-------|--------|
| Tenant payroll admin | Add templates **1049**–**1057** from SR catalog to employee standing instructions or period transactions. |
| Payroll clerk | Enter period **quantity** (days/meals) or **amount** (utilities, exchange rate, car list price override). |
| Payslip | Show derived valuation lines; **1056** visible when **1055** present (exclusion transparency, like **1023**). |

#### 5.1.9 Proposed schema extension (requires PII review)

**None required v1** if list price, utility cost, and exchange payout use existing `tenant_wage_component.default_amount` and `tenant_wage_component_transaction.amount` / `quantity`. Revisit if a dedicated employee asset register (multiple cars) is requested later.

---

### 5.2 P3 — Product control model (no tax-office approval)

**Status:** **Live** (product decision locked 2026-06-19). Belastingdienst / inspector approval is **not** modeled in the product.

**Objective:** The employer is responsible for legal compliance off-system. The product records **what was paid** and computes wage tax from **active** payroll inputs only.

#### 5.2.1 How gross lines enter payroll

Via existing [`employee-periodic-payroll-transactions.md`](./employee-periodic-payroll-transactions.md):

| Path | When component runs |
|------|---------------------|
| Standing instruction | `active = true` and pay-period end ∈ [`effective_from`, `effective_to`] → materialized `tenant_wage_component_transaction` |
| Manual period transaction | Row exists with `amount` or `quantity` &gt; 0 for the pay period |
| Inactive / absent | No row or zero amount → component **omitted** from engine |

Payroll operators **activate or deactivate** recurring inputs (standing instruction `active`) and enter or remove period amounts. No separate “tax approval” entity.

#### 5.2.2 Engine rules (Art. 17a gross templates)

When gross amount &gt; 0 and `tenant_employee_compensation.apply_taxes = true`:

| Gross | Tax line | Rule |
|-------|----------|------|
| **1009** lump sum | **1024** | `SR_PAYMENTS_AT_ONCE_YEAR` — unconditional |
| **1045**–**1047** overtime | **1020** + **1013** AOV | `SR_OVERTIME_MONTH` + overtime AOV — unconditional |
| **1010** jubilee | **1048** | Art. 10 exempt → `SR_PAYMENTS_AT_ONCE_YEAR` on taxable remainder (§4.4) |

No fallback to **1019** when these gross lines are present. No blocking when a gross line is active.

#### 5.2.3 Internal supervisor sign-off (pay period close)

Quality review before closing a pay period is specified in [`pay-periods.md`](./pay-periods.md) §4.3 — **not** in this module. That workflow is separate from wage-tax calculation.

#### 5.2.4 Out of scope

- Belastingdienst reference numbers, approval expiry, FALLBACK/BLOCK tax policies
- `tenant_employee_art17a_regime_approval` or similar tables
- Platform or tenant APIs for external tax approvals

#### 5.2.5 Proposed schema extension (requires PII review)

**None** for this slice.

---

## 6. Engine mapping

| # | Regime | `rule_code` | `country_rule_key` (tax line) | Java handler | Status |
|---|--------|-------------|-------------------------------|--------------|--------|
| 1 | Normal | `SR_WAGE_TAX_DEFAULT` | `SUR_WAGE_TAX` (1019) | `SurinameStatutoryContributor` | **Live** |
| 1b | Belastingvrij | `SR_TAX_FREE_WAGE_TAX_YEAR` | `SUR_TAX_FREE_WAGE_TAX` (1005) | `SurinameCountryRuleAlgorithms` | **Live** |
| 2 | Overtime | `SR_OVERTIME_MONTH` | `SUR_WAGE_TAX_OVERTIME` (1020) | `SurinameTenantDerivedComponentService` | **Live** (from 2025-07-01) |
| 3 | Payment at once | `SR_PAYMENTS_AT_ONCE_YEAR` | `SUR_WAGE_TAX_LUMP_SUM` (1024) | `SurinameTenantDerivedComponentService` | **Live** |
| 4 | Jubilee | `SR_PAYMENTS_AT_ONCE_YEAR` | `SUR_WAGE_TAX_JUBILEE` (1048) | `SurinameTenantDerivedComponentService` | **Live** (payment-at-once ladder on taxable remainder) |
| 5 | Extra income (Art. 17) | `SR_WAGE_TAX_DEFAULT` (inside label) | `SUR_WAGE_TAX_EXTRA_EARNINGS` (1025) | `SurinameTenantDerivedComponentService` | **Live** |
| 6 | Vacation | `SR_TAX_FREE_VACATION_YEAR` + Table 1 | `SUR_WAGE_TAX_VACATION_ALLOWANCE` (1021) | `SurinameTenantDerivedComponentService` | **Live** |
| 7 | Bonus | `SR_TAX_FREE_BONUS_YEAR` + Table 1 | `SUR_WAGE_TAX_BONUS` (1022) | `SurinameTenantDerivedComponentService` | **Live** |
| 8 | Free medical (Art. 10) | `SR_FREE_MEDICAL_YEAR` | `SUR_FREE_MEDICAL_BENEFIT` (1042) | `SurinameTenantDerivedComponentService` | **Live** |
| 9 | Company car valuation | `SR_COMPANY_CAR_YEAR` | `SUR_COMPANY_CAR_BENEFIT` (1049) | `SurinameTenantDerivedComponentService` | **Live** |
| 10 | Free housing valuation | `SR_FREE_HOUSING_YEAR` | `SUR_FREE_HOUSING_BENEFIT` (1050) | `SurinameTenantDerivedComponentService` | **Live** |
| 11 | Board / lodging / meals | `SR_BOARD_LODGING_DAY`, `SR_BOARD_DAY`, `SR_HOT_MEAL_UNIT`, `SR_BREAD_MEAL_UNIT` | **1051**–**1054** | `SurinameTenantDerivedComponentService` | **Live** |
| 12 | Exchange rate exclusion | `SR_EXCHANGE_RATE_COMPENSATION_MONTH` | **1055** / **1056** | `SurinameTenantDerivedComponentService` + wage-tax base adjust | **Live** |
| 13 | Free utilities | — (actual amount) | `SUR_FREE_UTILITIES_BENEFIT` (1057) | `SurinameTenantDerivedComponentService` | **Live** |

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
| 4b | Jubilee 20-year, payout SRD 12 000, ref. month SRD 6 000 | Art. 10 exempt SRD 4 500; taxable **7 500**; **1048** = SRD **375** (5% ladder on remainder) |
| 5 | Extra earnings SRD 360, N=12, label loon SRD 6 000 | **1025** > 0; uses Art. 17 label on Table 1 |
| 6 | Vacation SRD 500 ≤ exempt cap | **1021** = 0 |
| 7 | Bonus above exempt cap | **1022** > 0; **1019** unchanged (no double count) |
| 8 | 2 children, `apply_tax_exempt` | **1008** = SRD 250; **1023** exclusion = SRD 250 |
| 9 | Company car list price SRD 180 000 (AC-P2-1) | **1049** = SRD **300.0000**; `LOONBELASTING` +300; `GROSS` unchanged |
| 10 | Money wage SRD 8 000/mo, free housing (AC-P2-2) | **1050** = SRD **600.0000** |
| 11 | Board + lodging 15 days (AC-P2-3) | **1051** = SRD **150.0000** |
| 12 | Hot meals 22 (AC-P2-4) | **1053** = SRD **110.0000** |
| 13 | Exchange rate payout SRD 950 (AC-P2-6) | **1055** = **950.0000**; **1056** = **800.0000**; label loon +**150.0000** |
| 14 | Free utilities SRD 275.50 (AC-P2-7) | **1057** = **275.5000** |
| 15 | Bread meals 20 (AC-P2-5) | **1054** = SRD **30.0000** |
| 16 | P2 slice: car + housing on golden employee | **1019** increases vs baseline; no double-count with **1042** medical |

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
| `SR_SERVICE_YEARS_17A_MONTH` | **Match (data)** | Legacy seed; **1048** retargeted to `SR_PAYMENTS_AT_ONCE_YEAR` at runtime |
| `SR_TAX_FREE_VACATION_YEAR` | **Match** | 19 500 from 2025-07-01 |
| `SR_TAX_FREE_BONUS_YEAR` | **Match** | 19 500 from 2025-07-01 |
| `SR_CHILD_ALLOWANCE_MONTH` | **Match** | m42: 125/child, max 500, max 4 children (from 2021-07-01) |
| `SR_DEDUCTIBLE_EXPENSES_YEAR` | **Match** | 4% from 4 800 min |
| `SR_FREE_MEDICAL_YEAR` | **Match** | 3% on 0–200 |
| `SR_COMPANY_CAR_YEAR` | **Match** | 2% FLAT_RATE; effective 2024-01-01; **wired** via `periodCompanyCarBenefit` |
| `SR_FREE_HOUSING_YEAR` | **Match** | 7.5% FLAT_RATE; effective 2024-01-01; **wired** via `periodFreeHousingBenefit` |
| `SR_BOARD_LODGING_DAY` | **Match** | UNIT_CAP SRD 10/day; effective 2024-01-01; **wired** via `periodBoardLodgingBenefit` |
| `SR_BOARD_DAY` | **Match** | UNIT_CAP SRD 5/day; effective 2024-01-01; **wired** via `periodBoardBenefit` |
| `SR_HOT_MEAL_UNIT` | **Match** | UNIT_CAP SRD 5/meal; effective 2024-01-01; **wired** via `periodHotMealBenefit` |
| `SR_BREAD_MEAL_UNIT` | **Match** | UNIT_CAP SRD 1.50/meal; effective 2024-01-01; **wired** via `periodBreadMealBenefit` |
| `SR_EXCHANGE_RATE_COMPENSATION_MONTH` | **Match** | max SRD 800/month; effective 2022-01-01; **wired** via `periodExchangeRateCompensationExcludedFromLoon` |

### 8.2 Template verification (`data-m23-platform-wage-component-templates-sr-law-1.xml`)

| Code | `country_rule_key` | `platform_country_tax_rule_id` FK | Audit |
|------|-------------------|-----------------------------------|-------|
| 1009 | `SUR_LUMP_SUM` | — | Gross only; no tax rule FK |
| 1010 | `SUR_JUBILEE` | — | Gross only; no tax rule FK |
| 1048 | `SUR_WAGE_TAX_JUBILEE` | `...000004` (`SR_SERVICE_YEARS_17A_MONTH`) | **Retarget:** runtime uses `SR_PAYMENTS_AT_ONCE_YEAR`; FK update optional |
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
| Jubilee 1010 / 1048 | Yes (`SUR_WAGE_TAX_JUBILEE`) | `jubileeWageTax()` → Art. 10 + `computePaymentAtOnceTax` on taxable remainder | **Live** (retarget from service-year table) |
| Extra 1025 | Yes | `art17WageTax()` | **Live** |
| Vacation 1021 | Yes | `art17WageTax()` + `exemptPortion()` | **Live** |
| Bonus 1022 | Yes | `art17WageTax()` + `exemptPortion()` | **Live** |
| Child 1023 | Yes (`SUR_WAGE_TAX_CHILD_ALLOWANCE`) | `periodChildAllowanceExcludedFromLoon()` | **Live** |
| Exchange 1056 | Yes (`SUR_WAGE_TAX_EXCHANGE_RATE`) | `periodExchangeRateCompensationExcludedFromLoon()` + `adjustTaxableBaseForWageTax` | **Live** |

`SurinameWageTaxCalculator` supports `MARGINAL_RATES`, `FLAT_RATE`, and `LEGACY_SERVICE_YEAR_TABLE` kinds.

### 8.4 Test coverage map

| Area | Test file | Covered |
|------|-----------|---------|
| Table 1 marginal tax | `SurinameWageTaxCalculatorTest` | Golden base 18 500 → 4 930; belastingvrij example |
| Art. 17 label method | `SurinameWageTaxCalculatorTest` | `computeArt17BijzondereBeloningTax` |
| Overtime rule effective dating | `SurinameCountryRuleProviderIT` | Rule present from 2025-07-01; absent before (by design) |
| Special remuneration splits | `SurinameSpecialRemunerationSupportTest` | Exempt caps, overtime sum |
| Free medical / P2 benefits | `SurinameCountryRuleAlgorithmsTest`, `SurinameTenantDerivedComponentServiceTest` | **1042**; **1049**/**1050** (AC-P2-1/2); **1051**–**1054** (AC-P2-3/3b/4/5); **1055**/**1056** (AC-P2-6); **1057** (AC-P2-7) |
| End-to-end payroll | `SurinamePayrollGoldenIT` | 1020, 1021, 1023, 1025, 1005; **not** 1024, 1010 |
| Lump sum / jubilee | `SurinameWageTaxCalculatorTest`, `SurinameTenantDerivedComponentServiceTest`, `SurinameJubileeSupportTest` | **1024** ladder; **1048** jubilee (25-year + 20-year payment-at-once remainder) |
| Payment-at-once ladder | `SurinameWageTaxCalculatorTest` | SRD 50 000 → SRD 3 300 |

### 8.5 Platform UI

Route `/app/platform-country-tax-rules` lists SR rules with structured bracket editor (`CountryTaxRuleParametersEditor.tsx`, `country-tax-rule-parameters.ts`). All seeded `rule_code` rows are editable by superadmin; v2 kinds include `MARGINAL_RATES`, `FLAT_RATE`, `THRESHOLD_AMOUNT`, `PER_CHILD_MONTHLY`, `LEGACY_SERVICE_YEAR_TABLE`.

### 8.6 Gap summary (prioritized for future implementation)

| Priority | Gap | Impact | Status |
|----------|-----|--------|--------|
| ~~**P1**~~ | Lump sum **1009** → **1024** | Compliance | **Done** |
| ~~**P1**~~ | Jubilee **1010** → **1048** | Compliance | **Done** |
| **P2** | Benefits-in-kind + exchange rate (§5.1) | Compliance | **Live** (**1049**–**1057**) |
| ~~**P3**~~ | Product control model — no tax-office approval (§5.2) | Process | **Done** (spec) |
| **P3b** | Jubilee **1048** → payment-at-once ladder on taxable remainder | Compliance | **Done** (engine retarget) |

**P2 v1 scope (moves from gap → planned):** company car, housing, board/lodging/meals, exchange-rate exclusion, free utilities. **Still out of scope:** transport, pension 2×AOV, training, evidence-required allowances, Belastingdienst approval workflows.

#### 8.6.1 P2 implementation order (recommended)

| Phase | Deliver | Rationale |
|-------|---------|-----------|
| **A** | **1049** company car + **1050** housing + seeds `SR_COMPANY_CAR_YEAR`, `SR_FREE_HOUSING_YEAR` | **Done** |
| **B** | **1055** / **1056** exchange rate + `SR_EXCHANGE_RATE_COMPENSATION_MONTH` + wage-tax base adjustment | **Done** |
| **C** | **1051**–**1054** daily-cap meals + four `SR_*_DAY` / `SR_*_UNIT` rules | **Done** |
| **D** | **1057** free utilities | Simplest — passthrough amount; no new rule kind required | **Done** |

Each phase: Liquibase templates + rules → `SurinameCountryRuleKeys` → algorithms → derived service → unit test per AC-P2-* row → update §6/§8 status to **Live**.

---

## 9. Related docs

- [`../datafiles/README-suriname-tax-data.md`](../datafiles/README-suriname-tax-data.md) — legacy CSV ↔ `rule_code` index
- [`payroll-engine-country.md`](./payroll-engine-country.md) — SR adapter contracts
- [`suriname-loonbelasting-art17-bonus-vakantie.md`](./suriname-loonbelasting-art17-bonus-vakantie.md) — Art. 17 vacation/bonus detail
- [`../product/PAYROLL-GOLDEN-SCENARIO-SR.md`](../product/PAYROLL-GOLDEN-SCENARIO-SR.md) — regression anchor (normal wage tax)
