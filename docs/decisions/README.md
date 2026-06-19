# Architecture decisions (payroll engine)

Lightweight ADRs for the generic payroll engine. **Status** in each file: `Proposed` until reviewed, then `Accepted`.

| ID | Title | Status |
|----|-------|--------|
| [ADR-PE-001](./ADR-PE-001-payroll-execution-model.md) | Payroll execution model (phases + DAG) | Proposed |
| [ADR-PE-002](./ADR-PE-002-formula-expression-runtime.md) | Formula expression runtime | Proposed |
| [ADR-PE-003](./ADR-PE-003-processing-order-vs-graph.md) | `processing_order` vs dependency graph | Proposed |

**Roadmap:** [`../product/PAYROLL-ENGINE-ROADMAP.md`](../product/PAYROLL-ENGINE-ROADMAP.md) Phase 0.  
**Phase 1 spec:** [`../product/PAYROLL-ENGINE-PHASE-1.md`](../product/PAYROLL-ENGINE-PHASE-1.md)  
**Review gate:** [`ADR-REVIEW-CHECKLIST.md`](./ADR-REVIEW-CHECKLIST.md)  
**Convention:** New ADRs use prefix `ADR-PE-` and link back to the roadmap phase they unblock.
