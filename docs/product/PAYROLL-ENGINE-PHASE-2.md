# Phase 2 — Statutory calculation from bases (Suriname wage tax)

**Status:** Implemented (SR wage tax + AOV employee premium; API `componentSource` on preview items)  
**Depends on:** Phase 1 orchestrator, [`payroll-engine-country.md`](../modules/payroll-engine-country.md) §5  
**Regression:** [`PAYROLL-GOLDEN-SCENARIO-SR.md`](./PAYROLL-GOLDEN-SCENARIO-SR.md) § Phase 2+ (provisional tax amounts)

---

## 1. Objective

Implement **`StatutoryPhaseHandler`** so phase 3 computes **SR wage tax** (and optional AOV stub) from **`employeeBaseTotals`**, using versioned **`platform_country_tax_rule`** JSON—not hints only.

Preview API: extend `items` with **statutory** `EvaluatedComponentAmount` rows OR add parallel `statutoryItems` — **decision required** (§6).

---

## 2. Prerequisites

- [ ] Phase 1 complete (AC-PE1-*).
- [ ] Annualization policy signed off on golden scenario (monthly payroll vs `freq: YEAR`).
- [ ] ADR-PE-002 Accepted (Java calculators).

---

## 3. Core types

### 3.1 `SurinameWageTaxCalculator`

| Method | Input | Output |
|--------|-------|--------|
| `computePeriodTax(ResolvedSurinameTaxRule rule, BigDecimal taxableBase, int periodCountPerYear)` | Rule + **LOONBELASTING** period base + periods/year (12) | Period tax amount `BigDecimal` |

**Algorithm (MARGINAL_RATES, freq YEAR):**

1. `annualBase = taxableBase × periodCountPerYear` (policy A — default in golden doc).
2. Walk `rows` by bracket; sum marginal tax on slices.
3. `periodTax = annualTax / periodCountPerYear`, scale HALF_UP 4 dp.

**Algorithm (MARGINAL_RATES, freq MONTH):** apply ladder directly to `taxableBase`.

Unit tests: fixture JSON from `data-m25-platform-country-tax-rules-sr-1.xml` rule `SR_WAGE_TAX_DEFAULT`.

### 3.2 `StatutoryPhaseHandler`

| Step | Action |
|------|--------|
| 1 | Read `SurinameTaxRulesSnapshot` from state (load in CONTEXT if not already) |
| 2 | For each employee in `context.employeeIds()` |
| 3 | `base = employeeBaseTotals[emp].get("LOONBELASTING")` (zero if missing) |
| 4 | `tax = calculator.computePeriodTax(primaryRule, base, 12)` |
| 5 | Append `EvaluatedComponentAmount` for platform statutory wage-tax component code |

**Platform component code:** resolve from seed (statutory slot linked to wage tax)—document exact UUID/code in module doc when implementing.

### 3.3 AOV (optional in Phase 2)

| Rule | `SR_AOV_PREMIUM_MONTH` |
| Base | `AOV` |
| Kind | `FLAT_RATE` `pct: 4` |

Employee deduction line if `impact_side` = EMPLOYEE; employer share informational only (Phase 2 may ship wage tax only).

---

## 4. Data model

**No new tables** if statutory amounts only appear in preview/future result lines.

Optional: store rule version id on result line in Phase 4.

---

## 5. Tests

| Test | Assert |
|------|--------|
| `SurinameWageTaxCalculatorTest` | Known bracket fixture → expected annual/period tax |
| `DefaultPayrollEngineIT` golden | LOONBELASTING 18500 → period tax ≈ 4930 (±1 SRD) when policy A |
| Rule versioning | Different `effective_from` changes tax |

---

## 6. API decision (choose before coding)

| Option | Pros | Cons |
|--------|------|------|
| **A** — Merge statutory into `items` | Single list for UI | Mixes tenant + platform component ids |
| **B** — Add `statutoryItems` array | Clear separation | Breaking change to preview JSON |
| **C** — `items` + `componentSource` field on DTO | Explicit | DTO change, still one array |

**Chosen:** **C** — `componentSource` (`TENANT` \| `PLATFORM`) on `EvaluatedComponentAmountDto`; tenant rows omit `platformWageComponentId`, platform rows omit `tenantWageComponentId`.

---

## 7. Acceptance criteria

| ID | Criterion |
|----|-----------|
| AC-PE2-1 | Wage tax > 0 for golden employee when LOONBELASTING > 0 |
| AC-PE2-2 | `countryRulesAsOf` selects correct rule version |
| AC-PE2-3 | No SR tax constants in orchestrator (calculator + JSON only) |
| AC-PE2-4 | STATUTORY phase no longer empty when SR company + bases present |

---

## 8. Document history

| Date | Change |
|------|--------|
| 2026-05-17 | Initial Phase 2 spec |
