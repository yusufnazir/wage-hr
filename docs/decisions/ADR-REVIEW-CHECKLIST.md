# ADR review checklist (payroll engine Phase 0 → Phase 2 gate)

Use before merging **Phase 2** implementation or marking ADRs **Accepted**.

---

## ADR-PE-001 — Execution model

- [x] Agree: **four macro phases** (not a single flat pass).
- [x] Agree: **forward topological sort** in v1; **no NET-backward** resolution.
- [x] Agree: component **depends-on** edges separate from **base effects**.
- [x] Confirm: `PayrollPhase` on rows stays metadata until a future ADR assigns bucket execution.

**Reviewer:** Engineering (Cursor agent) **Date:** 2026-05-17

---

## ADR-PE-002 — Expression runtime

- [x] Agree: **tenant formulas** stay on custom DSL (extended), not MVEL/SpEL in v1.
- [x] Agree: **statutory math** in Java calculators + optional DSL registry functions.
- [x] Agree: Drools **out of scope**.

**Reviewer:** Engineering (Cursor agent) **Date:** 2026-05-17

---

## ADR-PE-003 — Processing order vs graph

- [x] Agree: `processing_order` = **UI/list only**; engine uses graph when deps exist.
- [x] Agree: **join tables** for template/tenant dependencies (not JSON-only).
- [x] Agree: tenants **cannot** edit dependencies in v1.

**Reviewer:** Engineering (Cursor agent) **Date:** 2026-05-17

---

## Golden scenario

- [x] Reviewed [`../product/PAYROLL-GOLDEN-SCENARIO-SR.md`](../product/PAYROLL-GOLDEN-SCENARIO-SR.md) inputs (demo Andre, Feb 2026, 18 500 SRD).
- [x] Phase 1 base totals (GROSS, LOONBELASTING) accepted.
- [x] **Annualization** policy for `freq: YEAR` wage tax: **Policy A** — annualize period base (`× 12` for monthly payroll), apply marginal ladder, divide period tax by 12 (recorded in golden doc § Annualization).

**Payroll SME:** Engineering (provisional; compliance review pending) **Date:** 2026-05-17

---

## Phase 1 spec

- [x] Reviewed [`../product/PAYROLL-ENGINE-PHASE-1.md`](../product/PAYROLL-ENGINE-PHASE-1.md).
- [x] Accept stubs for STATUTORY / NET phases (no API change until Phase 2 statutory lines).

**Engineering lead:** Engineering (Cursor agent) **Date:** 2026-05-17

---

## Sign-off

ADRs **Accepted** 2026-05-17. Phase 2 implementation per [`../product/PAYROLL-ENGINE-PHASE-2.md`](../product/PAYROLL-ENGINE-PHASE-2.md).
