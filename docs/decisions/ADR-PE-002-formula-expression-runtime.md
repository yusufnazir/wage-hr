# ADR-PE-002: Formula expression runtime

**Status:** Accepted  
**Date:** 2026-05-17  
**Accepted:** 2026-05-17  
**Deciders:** Backend + security (review required)  
**Roadmap:** Phase 0 → unblocks Phases 2, 6

---

## Context

The product spec recommends **MVEL or SpEL** for evaluating database-stored formula strings (e.g. `base_salary * 0.04`). The repository ships a **custom DSL** (`WageComponentFormulaDsl`) and JSON `expr` trees with a **fixed allowlist** of input references (`compensation.periodic_rate`, `transaction.quantity`, …).

Country-specific statutory logic (progressive wage tax, AOV %) must be callable from tenant formulas without embedding Suriname law in Java for every line.

---

## Decision

### 1. v1 tenant component formulas: extend the existing DSL (accepted)

- Keep **`WageComponentFormulaValidator`** + **`WageComponentFormulaEvaluator`** as the only path for **tenant** `formula_expression` on save and on engine run.
- Extend DSL v2 with:
  - **Cross-component reads** (after ADR-PE-003 graph exists): `component("1001").amount` or `@component.1001.amount` (exact syntax in Phase 3 module doc).
  - **No** arbitrary method calls, no reflection, no string `eval`.

### 2. Country/statutory functions: registered Java functions, not MVEL (accepted)

Statutory math (marginal tax, flat AOV rate, etc.) is implemented as **typed Java calculators** in `com.wagepayroll.payroll.country.*`, invoked from:

- **Phase 3** statutory component handlers, and
- Optionally **DSL built-ins** exposed as a small registry, e.g. `surinameWageTax(loonbelastingBase)` that delegates to `SurinameWageTaxCalculator`.

**We do not expose** a generic `CALCULATE_SURINAME_TAX(x)` interpreted inside MVEL in v1. The external contract is the same (named function + base input); implementation is **whitelist registry → Java**, not embedded EL.

### 3. SpEL/MVEL: deferred, platform-only candidate (accepted)

- **Not** introduced for tenant-facing formulas in v1 (attack surface, auditability).
- **Optional Phase 6+:** sandboxed SpEL for **platform superadmin** statutory expression experiments, with timeout, length limits, and no bean resolver—only if DSL built-ins prove insufficient.

### 4. Drools: rejected for v1

Rule matrices remain **data** (`platform_country_tax_rule.parameters_json`) + Java parsers until complexity warrants Drools (spec: optional).

### 5. Security boundary (accepted)

| Rule | Enforcement |
|------|-------------|
| Allowlisted identifiers only | Validator parse pass |
| Max expression length | 500 chars (column limit); stricter app limit TBD in Phase 6 |
| No I/O, no threads | DSL evaluator pure functions |
| Country functions | Fixed registry; no user-defined function names |

---

## Consequences

**Positive**

- Consistent with shipped validator/tests and Monaco editor.
- Statutory logic unit-testable in Java against `parameters_json` fixtures.
- Clear story for spec’s “decoupled formulas”: tenant DSL for amounts, country tables for law.

**Negative**

- Not literal MVEL/SpEL strings in DB for tenant lines; spec readers must map “formula string” → “DSL string or JSON expr”.
- Each new country function needs code + registry entry (acceptable for SR-first).

---

## Alternatives considered

| Alternative | Why rejected for v1 |
|-------------|---------------------|
| MVEL for all formulas | Security review burden; harder audit trail |
| SpEL (Spring) everywhere | Same; tenants must not access application context |
| Pure JSON expr trees only | Already supported; DSL line format retained for editor UX |
| Store Java bytecode / scripts | Non-starter for multi-tenant SaaS |

---

## References

- [`../modules/payroll-wage-component-engine.md`](../modules/payroll-wage-component-engine.md) §11 Formula editor  
- `WageComponentFormulaDsl.java`, `FormulaEvaluationContext.java`
