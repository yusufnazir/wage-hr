# ADR-PE-001: Payroll execution model (phases + component DAG)

**Status:** Accepted  
**Date:** 2026-05-17  
**Accepted:** 2026-05-17  
**Deciders:** Backend + product (review required)  
**Roadmap:** Phase 0 → unblocks Phases 1, 3, 5

---

## Context

The product spec describes payroll as a **directed acyclic graph (DAG)** with **NET wage** as the ultimate outcome, resolved via **topological sort** and a **four-phase** lifecycle. The repository today runs a **linear preview**: evaluate tenant components in list order, then accumulate calculation bases—no graph, no phased statutory step, no NET closure.

We need one execution model so Phase 1+ implementations do not fork (backward NET resolution vs forward topo, phase buckets vs graph-only, etc.).

---

## Decision

### 1. Macro lifecycle: four explicit phases (accepted)

Every `PayrollEngine.calculate()` run executes **exactly these phases in order**:

| Phase | Code | Responsibility |
|-------|------|----------------|
| 1 | `CONTEXT` | Load tenant, company, period, employees, transactions, compensation, FX (future), resolve `countryRulesAsOf` |
| 2 | `GROSS_AND_BASES` | Evaluate **tenant** (and optionally configured) components → amounts → `PayrollBaseAccumulator` → `employeeBaseTotals` |
| 3 | `STATUTORY` | Evaluate **platform statutory** components and country calculators using bases + `CountryRuleProvider` |
| 4 | `NET_AND_ACCUMULATORS` | Compute final NET, persist result lines (Phase 4+), update YTD (Phase 5+) |

Phases are **orchestration boundaries**, not optional hooks. Country-specific logic enters only in phases 3–4 via providers/calculators, not `if (country)` in the orchestrator.

### 2. Micro ordering within phases: forward topological sort (accepted)

Inside phases 2 and 3, component nodes run in **forward topological order** derived from explicit **depends-on** edges (Phase 3 roadmap). Tie-break: ascending `processing_order` on the definition row.

**We do not implement NET-backward graph resolution in v1.** For any acyclic graph where NET depends (directly or transitively) on gross, tax, and deductions, forward topo from sources to NET is equivalent and simpler to test. Backward resolution is reserved unless a future ADR proves a case that forward topo cannot express.

### 3. Graph edges (accepted)

Two edge types, both validated on save:

| Edge type | Meaning | Storage (Phase 3) |
|-----------|---------|-------------------|
| **Component depends-on** | Node B’s formula or amount may read node A’s evaluated amount | `platform_wage_component_dependency` (+ template mirror) |
| **Base effect** | Node amount contributes to calculation base `GROSS`, `LOONBELASTING`, etc. | Existing `*_base_effect` tables |

The engine does **not** infer component dependencies from base effects alone; both must be declared when cross-component reads are required.

### 4. DAG invariants (accepted)

- Graph must be **acyclic** at save and at run (defensive check).
- **Statutory** platform components are nodes in phase 3; they may depend on base totals (virtual inputs) or other statutory nodes.
- **Drools** is **out of scope** for v1; use Java calculators + data-driven `platform_country_tax_rule` JSON.

---

## Consequences

**Positive**

- Matches product spec’s 4-phase story and graph ordering without NET-backward complexity.
- Clear extension point: new countries = new `CountryRuleProvider` + calculators, not orchestrator edits.
- Phase 1 can land with stubs in 3–4 while preserving preview regression tests.

**Negative / trade-offs**

- Operators must maintain **depends-on** explicitly when formulas reference other components (UI in Phase 7).
- Base effects without component deps do not auto-order cross-component reads—documentation and validators must be clear.

**Neutral**

- `PayrollPhase` enum on component rows (`GROSS`, `TAX`, …) remains **metadata** for reporting/UI until a later ADR assigns phase-bucket execution; v1 engine ordering is **graph + macro phase**, not `PayrollPhase` alone.

---

## Alternatives considered

| Alternative | Why rejected for v1 |
|-------------|---------------------|
| NET-backward resolution from a single root | Harder debugging, same results for acyclic DAGs; spec allows forward topo |
| Graph-only, no macro phases | Loses clear audit/stub boundaries for context vs statutory vs persist |
| `processing_order` only (status quo) | Cannot express cross-component formulas safely |
| Single flat pass (no phase 3 split) | Mixes tenant earnings with statutory tax in one bucket; blocks country isolation |

---

## Compliance

- [ ] Product/compliance review: phase 3 statutory split acceptable for SR inkomstenbelasting reporting.
- [ ] Engineering review: `PayrollRunState` design in Phase 1 module doc.
