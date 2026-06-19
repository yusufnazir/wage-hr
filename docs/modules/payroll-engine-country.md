# Module: Payroll engine — country adapters

**Feature slug:** `payroll-engine-country`  
**Milestone:** M5 (execution; extends [`payroll-wage-component-engine.md`](./payroll-wage-component-engine.md))  
**Related:** [`payroll-calculation-bases.md`](./payroll-calculation-bases.md), [`platform-countries.md`](./platform-countries.md), [`../product/PAYROLL-ENGINE-ROADMAP.md`](../product/PAYROLL-ENGINE-ROADMAP.md), [`../decisions/ADR-PE-001-payroll-execution-model.md`](../decisions/ADR-PE-001-payroll-execution-model.md)

---

## 1. Objective

Keep **`DefaultPayrollEngine`** (and successors) **country-agnostic**. All jurisdiction-specific payroll law—progressive tax, social premiums, reporting hints—is implemented behind **`CountryRuleProvider`** adapters. **Suriname (`SR`)** is the first full adapter; additional ISO-2 codes register providers without changing orchestrator code.

---

## 2. Scope

### Included (by roadmap phase)

| Phase | Capability |
|-------|------------|
| **Today** | `CountryRuleProvider`, `CountryRuleProviderRegistry`, `CountryRuleContext`, `SurinameCountryRuleProvider`, `SurinameTaxRuleResolutionService`, versioned `platform_country_tax_rule` resolution |
| **Phase 2** | `SurinameWageTaxCalculator` (and stubs for AOV/SZF) consuming `employeeBaseTotals` |
| **Phase 3+** | Statutory platform component evaluation wired through provider |
| **Later** | Additional countries = new provider + seed data |

### Excluded

- Tenant-editable country rules (platform superadmin only via existing tax-rule APIs).
- Drools / external rule engines (see ADR-PE-002).
- Direct imports of SR types from non-country packages except through provider interfaces.

---

## 3. Product rules

| Rule | Detail |
|------|--------|
| **As-of date** | `PayrollContext.countryRulesAsOf()` selects tax rule rows (pay-period **end** on preview/finalize). |
| **Hints vs amounts** | Until Phase 2, providers may only add **hints** to `CountryRuleContext`. After Phase 2, phase 3 **must** emit evaluated statutory amounts. |
| **Base inputs** | SR wage tax primary input: **`LOONBELASTING`** base total per employee (see §5). Fallback policy if zero: document in Phase 2 IT. |
| **No orchestrator branches** | Forbidden: `if ("SR".equals(country))` in `PayrollRunOrchestrator` / `DefaultPayrollEngine`. |

---

## 4. Data model

### Existing tables (authority elsewhere)

| Table | Module |
|-------|--------|
| `platform_country_tax_rule` | [`payroll-wage-component-engine.md`](./payroll-wage-component-engine.md) §4.1 |
| `platform_payroll_base` | [`payroll-calculation-bases.md`](./payroll-calculation-bases.md) |
| `platform_wage_component` | Statutory slots (phase 3) |

**Phase 0:** No new tables.

**Phase 3 (dependency graph):** See ADR-PE-003 — `platform_wage_component_template_dependency`, `tenant_wage_component_dependency` (not owned by this module; referenced for ordering).

### Proposed Schema Extension (requires PII review)

None for Phase 0–2. Phase 5 may add `tenant_payroll_ytd_accumulator` under a separate module or § extension here.

---

## 5. Suriname (SR) — calculation contracts

### 5.1 Tax rule resolution

**Service:** `SurinameTaxRuleResolutionService`  
**Input:** `LocalDate asOf`, all active SR `platform_country_tax_rule` rows  
**Output:** `SurinameTaxRulesSnapshot` keyed by `rule_code`

**Primary wage tax rule:** `SR_WAGE_TAX_DEFAULT` (template FK `52000000-0000-0000-0000-000000000001`)

**Parameters contract:** `parameters_json` with `v: 2`, `kind`, `freq`, `rows` — see wage-component-engine §4.1.

### 5.2 Tenant template algorithms (`country_rule_key`)

SR templates with derived amounts (FiscLe wage tax summary, 2024–2025) are evaluated in the gross phase by `SurinameTenantDerivedComponentService` + `SurinameCountryRuleAlgorithms`:

| `country_rule_key` | Template | Algorithm |
|--------------------|----------|-----------|
| `SUR_TAXABLE_INCOME` | 1004 | `LOONBELASTING` base total (display) |
| `SUR_TAX_FREE_WAGE_TAX` | 1005 | Belastingvrij: `SR_TAX_FREE_WAGE_TAX_YEAR` ÷ periods when `apply_tax_exempt`, **capped at taxable income** for the period |
| `SUR_AQUISITION_COSTS` | 1036 | 4% of gross, annual cap SRD 4 800 ÷ periods |
| `SUR_FREE_MEDICAL_BENEFIT` | 1042 | 3% of annual wage in money, cap SRD 200/year ÷ periods |

Wage tax (statutory) uses `adjustTaxableBaseForWageTax` to subtract the same belastingvrij period allowance before `SR_WAGE_TAX_DEFAULT` marginal ladder.

### 5.4 SR wage-tax regimes (seven types)

**Authority:** [`suriname-wage-tax-rules.md`](./suriname-wage-tax-rules.md) — brackets, exclusions, audit matrix.

**Default:** earnings without a special `country_rule_key` → **1019** normal Table 1 (`SR_WAGE_TAX_DEFAULT`) on label loon.

| Regime | Templates | Engine status |
|--------|-----------|---------------|
| Normal + belastingvrij | **1019**, **1005** | **Live** — `SurinameStatutoryContributor` |
| Overtime | **1045**–**1047**, **1020**, **1013** | **Live** — `SR_OVERTIME_MONTH` from 2025-07-01 (pre-Jul history not in scope) |
| Payment at once | **1009**, **1024** | **Gap** — `SR_PAYMENTS_AT_ONCE_YEAR` seeded, not calculated |
| Jubilee | **1010** | **Gap** — `SR_SERVICE_YEARS_17A_MONTH` unsupported in calculator |
| Extra income (Art. 17) | **1011**, **1025**, **1018** | **Live** — label method on Table 1 |
| Vacation | **1006**, **1021**, **1014** | **Live** — see below |
| Bonus | **1007**, **1022**, **1015** | **Live** — see below |

#### Vacation allowance and bonus (art. 10 & 17)

See [`suriname-loonbelasting-art17-bonus-vakantie.md`](./suriname-loonbelasting-art17-bonus-vakantie.md).

| Template | Law | Engine |
|----------|-----|--------|
| 1006 / 1007 | Bruto uitkering | Gross phase — full amount to NET |
| Exempt slice | Art. 10 i/j + rules `SR_TAX_FREE_VACATION_YEAR` / `SR_TAX_FREE_BONUS_YEAR` | `SurinameSpecialRemunerationSupport.exemptPortion` — **implemented** |
| 1021 / 1022 | Art. 17 label method on taxable portion | `SurinameWageTaxCalculator.computeArt17BijzondereBeloningTax` |
| 1019 | Normal wage tax on **label** loon | `SR_WAGE_TAX_DEFAULT` on `LOONBELASTING` **without** treating full 1006/1007 as regular monthly taxable wage |

Art. **17a** is a separate on-request regime (lump-sum table); not the default path for 1006/1007.

### 5.5 Base → rule mapping (Phase 2)

| Calculator | Input base(s) | Rule code(s) | Output |
|------------|---------------|--------------|--------|
| Wage tax | `LOONBELASTING` (required), optional `GROSS` for diagnostics | `SR_WAGE_TAX_DEFAULT` (+ template `platformCountryTaxRuleId`) | Employee wage tax **deduction amount** for period |
| AOV employee premium | `AOV` | `SR_AOV_PREMIUM_MONTH` | Contribution amount (employee share) |
| AOV employer premium | `AOV` | TBD split JSON | Employer liability (informational component) |
| SZF / AWW / pension | Matching bases | Matching seeded rules | Phase 2+ stubs acceptable |

**Frequency handling (Policy A — approved 2026-05-17):**

| `freq` | Engine behavior |
|--------|-----------------|
| `YEAR` | Annualize period base (`periodBase × periodsPerYear`, default 12), apply marginal ladder, divide tax by `periodsPerYear` |
| `MONTH` | Apply ladder or flat rate directly to period base |

See [`../product/PAYROLL-GOLDEN-SCENARIO-SR.md`](../product/PAYROLL-GOLDEN-SCENARIO-SR.md).

### 5.6 Provider responsibilities

**`SurinameCountryRuleProvider` today**

- Resolves snapshot; puts hints: `sr.resolvedTaxRulesJson`, `sr.primaryTaxRuleId`, etc.

**Target (Phase 2+)**

| Phase | Hook | Action |
|-------|------|--------|
| 1 | `CountryRuleProvider.contribute` via **CONTEXT** phase | Hints on `PayrollRunState`; optional attach `SurinameTaxRulesSnapshot` in `variables` (internal) — see [`../product/PAYROLL-ENGINE-PHASE-1.md`](../product/PAYROLL-ENGINE-PHASE-1.md) |
| 1 | `StatutoryPhaseHandler` stub | No calculation |
| 2 | `StatutoryPhaseHandler` + `SurinameWageTaxCalculator` | Produce statutory `EvaluatedComponentAmount` rows |

**Interface evolution (Phase 2 proposal):** extend `CountryRuleProvider` with `void computeStatutory(PayrollRunState state)` default no-op, or inject `SurinameStatutoryCalculator` only into `StatutoryPhaseHandler` to avoid SPI churn in Phase 1. Decision recorded when Phase 2 module doc is updated.

This module owns **semantic** requirements; Phase 1 does not change `CountryRuleProvider` signature.

### 5.7 Registered formula functions (ADR-PE-002)

Optional DSL built-ins (Phase 6) delegate here:

| Function | Delegates to |
|----------|----------------|
| `surinameWageTax(base)` | `SurinameWageTaxCalculator` |
| `surinameAovEmployee(base)` | AOV calculator |

Not required for Phase 2 if statutory components invoke calculators directly.

---

## 6. Engine integration

```text
PayrollEngine.calculate(context)
  Phase CONTEXT
    → CountryRuleProviderRegistry.forCountry(iso2)
    → provider.contributeContext(state)
  Phase GROSS_AND_BASES
    → (tenant components, base accumulation)
  Phase STATUTORY
    → provider.computeStatutory(state)   // Phase 2+
  Phase NET_AND_ACCUMULATORS
    → NET closure, persist, YTD
```

**Java packages**

| Package / type | Role |
|----------------|------|
| `com.wagepayroll.payroll.country.CountryRuleProvider` | SPI |
| `com.wagepayroll.payroll.country.CountryRuleProviderRegistry` | Lookup by ISO-2 |
| `com.wagepayroll.payroll.country.SurinameCountryRuleProvider` | SR adapter |
| `com.wagepayroll.payroll.country.SurinameTaxRuleResolutionService` | Rule versioning |
| `com.wagepayroll.payroll.country.SurinameWageTaxCalculator` | **Phase 2** (new) |

---

## 7. Security

- Country rule JSON may contain economically sensitive parameters; not secret but integrity-protected via platform superadmin APIs + audit.
- Hints exposed on preview DTOs must not leak other tenants’ data (tenant-scoped runs only).

---

## 8. Audit (future)

| Action (proposed) | When |
|-------------------|------|
| `PLATFORM_COUNTRY_TAX_RULE_*` | Already on tax rule admin |
| `TENANT_PAYROLL_STATUTORY_COMPUTED` | Phase 4 finalize — metadata: rule version ids, base snapshots hash |

---

## 9. Acceptance criteria

| ID | Criterion | Phase |
|----|-----------|-------|
| AC-PEC-1 | `CountryRuleProviderRegistry` returns SR provider for `SR` | Today |
| AC-PEC-2 | Preview with SR company attaches `sr.resolvedTaxRulesJson` hint | Today |
| AC-PEC-3 | Wage tax amount > 0 for demo golden employee when `LOONBELASTING` > 0 | 2 |
| AC-PEC-4 | Changing `effective_from` on tax rule changes computed tax | 2 |
| AC-PEC-5 | No `import com.wagepayroll.payroll.country.suriname.*` from `DefaultPayrollEngine` | 1+ |

---

## 10. Testing

| Test | Content |
|------|---------|
| `SurinameCountryRuleProviderIT` | Hint contribution (exists) |
| `SurinameWageTaxCalculatorTest` | Marginal ladder fixtures from `parameters_json` |
| `DefaultPayrollEngineIT` | Golden scenario employee (Phase 2+) |

Fixture data: [`../product/PAYROLL-GOLDEN-SCENARIO-SR.md`](../product/PAYROLL-GOLDEN-SCENARIO-SR.md)

---

## 11. Web / platform UI

| Surface | Status |
|---------|--------|
| `/app/platform-country-tax-rules` | Exists — rule maintenance |
| Payslip / preview statutory lines | Phase 7 — show provider outputs |

---

## 12. Open questions

1. **Annualization** for `freq: YEAR` rules on monthly payroll (§5.2).
2. **Which statutory `platform_wage_component.code`** receives wage tax amount vs tenant-visible deduction line.
3. **Employer AOV** posting: informational component vs ledger-only (Phase 8).

---

## 13. Document history

| Date | Change |
|------|--------|
| 2026-05-17 | Initial module spec (Phase 0) |
