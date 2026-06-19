# Module: Payroll component dependencies

**Feature slug:** `payroll-component-dependencies`  
**Milestone:** M5 (extends [`payroll-wage-component-engine.md`](./payroll-wage-component-engine.md))  
**Roadmap:** Phase 3 — [`../product/PAYROLL-ENGINE-PHASE-3.md`](../product/PAYROLL-ENGINE-PHASE-3.md)  
**ADR:** [`../decisions/ADR-PE-003-processing-order-vs-graph.md`](../decisions/ADR-PE-003-processing-order-vs-graph.md)

---

## 1. Objective

Model an explicit **directed acyclic graph** of wage component **depends-on** relationships so the payroll engine can evaluate components in **topological order** and formulas can reference **other components’ evaluated amounts**. **`processing_order`** remains UI/list sort only.

---

## 2. Scope

### Included (Phase 3)

- DDL: `platform_wage_component_template_dependency`, `tenant_wage_component_dependency`
- Copy template dependencies to tenant row on wage component provision
- Platform API: replace-all dependencies on template save
- Cycle detection on save (template + tenant graph at run time)
- Engine: topological sort in `GrossAndBasesPhaseHandler` (fallback to `processing_order`)
- DSL extension: `component("CODE").amount` reference (exact syntax in Phase 3 spec)

### Excluded

- Tenant-editable dependencies (platform operators only via template)
- Visual graph editor (Phase 7)
- Dependencies on **statutory** platform components from tenant templates (statutory ordering via country module)
- Automatic inference of edges from base effects

---

## 3. Product rules

| Rule | Detail |
|------|--------|
| **Country scope** | Both ends of an edge must share the same `country_code` (template) or tenant+company (tenant row). |
| **No self-deps** | `dependent_id` ≠ `depends_on_id`. |
| **Acyclic** | Save and run reject cycles with `DEPENDENCY_CYCLE`. |
| **Provision copy** | On `TenantWageComponentService` create from template, copy all template dependency rows to tenant table mapped by new tenant component ids. |
| **Template code resolution** | Dependencies stored as FK to template rows; engine resolves tenant components by `code` / id at run time. |

---

## 4. Data model (allowed columns)

### `platform_wage_component_template_dependency`

| Column | Type | PII | Notes |
|--------|------|-----|--------|
| `id` | VARCHAR(36) PK | none | UUID |
| `platform_wage_component_template_id` | VARCHAR(36) NOT NULL | none | FK → `platform_wage_component_template` (dependent node) |
| `depends_on_template_id` | VARCHAR(36) NOT NULL | none | FK → prerequisite template, same country |
| `created_at` | TIMESTAMP NOT NULL | none | |
| `updated_at` | TIMESTAMP NOT NULL | none | |

**Unique:** (`platform_wage_component_template_id`, `depends_on_template_id`).

**Indexes:** FK indexes on both template ids.

### `tenant_wage_component_dependency`

| Column | Type | PII | Notes |
|--------|------|-----|--------|
| `id` | VARCHAR(36) PK | none | UUID |
| `tenant_id` | VARCHAR(36) NOT NULL | low | Tenant scope |
| `tenant_wage_component_id` | VARCHAR(36) NOT NULL | none | FK dependent → `tenant_wage_component` |
| `depends_on_tenant_wage_component_id` | VARCHAR(36) NOT NULL | none | FK prerequisite, same tenant+company |
| `created_at` | TIMESTAMP NOT NULL | none | |
| `updated_at` | TIMESTAMP NOT NULL | none | |

**Unique:** (`tenant_id`, `tenant_wage_component_id`, `depends_on_tenant_wage_component_id`).

---

## Proposed Schema Extension (requires PII review)

None in initial Phase 3. Future: `edge_type` (`AMOUNT` | `BASE`) — not v1.

---

## 5. API (platform)

Extend `PUT /api/v1/platform/wage-component-templates/{id}` body (or sub-resource):

```json
{
  "dependencies": [
    { "dependsOnTemplateId": "uuid-of-prerequisite-template" }
  ]
}
```

**Semantics:** replace-all for dependent template `{id}` (same pattern as `baseEffects`).

**Errors:**

| Code | HTTP | When |
|------|------|------|
| `DEPENDENCY_CYCLE` | 400 | Cycle in submitted graph |
| `DEPENDENCY_SELF_LOOP` | 400 | depends on self |
| `DEPENDENCY_COUNTRY_MISMATCH` | 400 | templates different countries |
| `DEPENDENCY_UNKNOWN_TEMPLATE` | 400 | invalid FK |

---

## 6. Engine integration

| Step | Component |
|------|-----------|
| Load edges | `tenant_wage_component_dependency` for active tenant components in run |
| Build graph | Nodes = components; directed edge prerequisite → dependent |
| Sort | Kahn topological sort; tie-break `processing_order` asc |
| Evaluate | For each node in order, build `FormulaEvaluationContext` including prior `component("X").amount` values |
| Re-accumulate bases | After full tenant pass (or incremental per node if effects depend on order — v1: single pass after all tenant amounts) |

**Run-time cycle:** if cycle detected (should not happen if save validation works), fail run with `PAYROLL_DEPENDENCY_CYCLE` Problem+JSON.

---

## 7. Formula DSL extension

**Syntax (v2):** `component("1001").amount` — string literal is **tenant/component code** (template code for provisioned row).

| Ref | Resolves to |
|-----|-------------|
| `component("1001").amount` | Evaluated amount of code `1001` for same employee in current run (0 if not yet evaluated → validation error at eval time) |

Validator must ensure referenced code exists and appears **before** dependent in topological order (or reject at save if dependency edge missing).

---

## 8. Security

- Platform superadmin only for template dependency CRUD.
- Tenants read-only (no API).

---

## 9. Audit (proposed)

| Action | When |
|--------|------|
| `PLATFORM_WAGE_COMPONENT_TEMPLATE_DEPENDENCIES_UPDATED` | Template dependencies replace |

Register in `AuditActionCodes` when implementing.

---

## 10. Acceptance criteria

| ID | Criterion |
|----|-----------|
| AC-PCD-1 | Liquibase creates both tables on clean DB |
| AC-PCD-2 | Provision copies template deps to tenant component |
| AC-PCD-3 | Save rejects A→B→A cycle |
| AC-PCD-4 | Engine evaluates B after A when B depends on A and formula references A |
| AC-PCD-5 | Without deps, engine order matches `processing_order` (regression) |

---

## 11. Document history

| Date | Change |
|------|--------|
| 2026-05-17 | Initial module spec (Phase 3) |
