# Phase 3 — Component dependency graph

**Status:** Implemented  
**Module authority:** [`../modules/payroll-component-dependencies.md`](../modules/payroll-component-dependencies.md)  
**ADR:** [ADR-PE-003](../decisions/ADR-PE-003-processing-order-vs-graph.md)

---

## 1. Objective

- Persist **depends-on** edges (template + tenant copy).
- Evaluate tenant components in **topological order** with `processing_order` tie-break.
- Extend formula DSL with **`component("CODE").amount`**.
- Reject **cycles** on admin save and at engine run.

---

## 2. Prerequisites

- [ ] Phase 1 orchestrator shipped.
- [ ] Phase 2 statutory lines stable (optional but recommended for realistic graphs).
- [ ] Schema preflight on [`payroll-component-dependencies.md`](../modules/payroll-component-dependencies.md).
- [ ] ADR-PE-003 Accepted.

---

## 3. Liquibase

| ChangeSet | Content |
|-----------|---------|
| `schema-m3x-template-dependency-1` | `platform_wage_component_template_dependency` |
| `schema-m3x-tenant-dependency-1` | `tenant_wage_component_dependency` |

No seed dependencies required for regression; add test fixture changeset or IT setup only.

---

## 4. Backend tasks

| # | Task | Package |
|---|------|---------|
| 1 | JPA entities + repositories | `domain.wagecomponent` or `domain.payrolldependency` |
| 2 | `ComponentDependencyValidation` — cycle detection (DFS) | `wagecomponent` or `payroll.engine` |
| 3 | `PlatformWageComponentTemplateAdminService` — replace dependencies | extend existing |
| 4 | `WageComponentBaseEffectCopyService` pattern → `ComponentDependencyCopyService` on tenant create | |
| 5 | `ComponentExecutionOrderService.topologicalSort(components, edges)` | `payroll.engine` |
| 6 | `GrossAndBasesPhaseHandler` — use sort order | |
| 7 | `WageComponentFormulaValidator` + `FormulaEvaluationContext` — `component("X").amount` | `payroll.formula` |
| 8 | Map evaluated amounts by code on `PayrollRunState` during GROSS phase | |

### Cycle detection algorithm

- Build adjacency: `depends_on → dependent` for edges “A required before B” (edge from prerequisite to dependent for topo: prerequisite comes first).
- Kahn: in-degree count; queue nodes with in-degree 0; if processed count &lt; nodes, cycle exists.

### Save-time validation (template)

When replacing dependencies for template T:

1. Load all edges among templates in same country that are reachable from T’s subgraph (or validate only edges touching T plus transitive closure on submit batch).
2. Simpler v1: validate full country template graph on each save (max ~100 nodes).

---

## 5. Frontend (minimal)

| Surface | Change |
|---------|--------|
| Platform template edit — new tab or section **Dependencies** | Multi-select prerequisite templates (same country) |
| Save | Include `dependencies` in PUT body |
| Error display | Show `DEPENDENCY_CYCLE` message |

Full graph visualization → Phase 7.

---

## 6. Tests

| Test | Scenario |
|------|----------|
| `ComponentDependencyValidationTest` | A→B, B→A rejected |
| `ComponentExecutionOrderServiceTest` | Diamond graph unique topo with tie-break |
| `WageComponentFormulaEvaluatorTest` | `component("1001").amount * 0.1` after 1001 evaluated |
| `DefaultPayrollEngineIT` | Fixture: B depends on A; B amount uses A |

---

## 7. Acceptance criteria

| ID | Criterion |
|----|-----------|
| AC-PE3-1 | Engine fails run with clear code on cyclic tenant graph |
| AC-PE3-2 | Platform cannot save cyclic template dependencies |
| AC-PE3-3 | Topo order differs from `processing_order` in fixture IT |
| AC-PE3-4 | Missing dependency edge → formula ref fails validation on save |
| AC-PE3-5 | Empty dependency set → same order as today |

---

## 8. Document history

| Date | Change |
|------|--------|
| 2026-05-17 | Initial Phase 3 spec |
