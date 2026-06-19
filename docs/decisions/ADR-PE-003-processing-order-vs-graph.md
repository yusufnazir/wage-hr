# ADR-PE-003: `processing_order` vs dependency graph order

**Status:** Accepted  
**Date:** 2026-05-17  
**Accepted:** 2026-05-17  
**Deciders:** Product + backend (review required)  
**Roadmap:** Phase 0 → unblocks Phases 3, 7

---

## Context

Wage components and templates expose **`processing_order`** / **`processing_order_hint`**. Documentation already states these control **UI list sort**, not engine calculation order—but the engine currently sorts by `processing_order` when evaluating, which contradicts the target DAG model.

Operators confuse “sort priority” with “calculation order.” The product spec requires a **visual dependency mapper** that updates a **graph** sent to the backend.

---

## Decision

### 1. Two independent concepts (accepted)

| Field / artifact | Purpose | Consumed by |
|----------------|---------|-------------|
| `processing_order` / `processing_order_hint` | **Display order** in admin lists, template pickers, payslip line sort hints | UI, reports, optional payslip renderer |
| `*_wage_component_dependency` (Phase 3) | **Execution order** among components with data dependencies | `PayrollEngine` phases 2–3 only |

### 2. Engine rule (accepted)

- **Must not** use `processing_order` as the primary execution sort when any dependency rows exist for the run set.
- **May** use `processing_order` as **tie-breaker** when topological sort yields multiple valid orderings.
- When **no** dependency rows exist for a component set, engine falls back to `processing_order` ascending (backward compatible with today’s preview).

### 3. UI copy (accepted)

- Rename labels in EN/NL where needed: e.g. “List sort priority” (already on template edit) — never “Calculation order”.
- Phase 7: separate **Dependencies** tab/section distinct from sort priority.

### 4. Templates and provisioning (accepted)

- Dependencies are defined on **`platform_wage_component_template`** (and copied to `tenant_wage_component` on create).
- Tenants **cannot** edit dependencies in v1 (same as base effects); platform operators own graph shape.

### 5. Payslip ordering (accepted)

Payslip line sequence may use `processing_order` **or** a dedicated `payslip_sort` later; it is **independent** of evaluation order. Default: `processing_order` for payslip until payslip module specifies otherwise.

---

## Consequences

**Positive**

- Resolves spec vs implementation mismatch without breaking list UX.
- Clear migration: existing seeds work via fallback until dependencies seeded.

**Negative**

- Operators must configure dependencies for formula chains; education required.

---

## Data model (Phase 3)

**Schema authority:** [`../modules/payroll-component-dependencies.md`](../modules/payroll-component-dependencies.md) — `platform_wage_component_template_dependency`, `tenant_wage_component_dependency`.

**Alternative rejected:** `depends_on_codes_json` on template row — harder to validate FK integrity and cycles in SQL; join table preferred.

---

## Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| Infer execution order from `PayrollPhase` enum only | Insufficient for “tax depends on gross component amount” |
| Single combined “order” field | Cannot satisfy both UI sort and DAG |
| Tenant-editable dependencies in v1 | Compliance risk; defer |

---

## Compliance

- [ ] UX review of label changes on template/component screens.
