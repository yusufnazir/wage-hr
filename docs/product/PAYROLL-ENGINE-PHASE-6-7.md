# Phases 6–7 — Formula platform & frontend administrator

**Status:** Phase 6–7 shipped (2026-05-18)  
**Roadmap:** [`PAYROLL-ENGINE-ROADMAP.md`](./PAYROLL-ENGINE-ROADMAP.md)

---

## Phase 6 — Formula validate API & DSL registry

### Objective

Stateless **validate / preview single component** without full pay period; optional **registered country functions** per ADR-PE-002.

### API (shipped)

| Method | Path | Privilege |
|--------|------|-----------|
| POST | `/api/v1/wage-components/validate-formula` | `WAGE_COMPONENT_VIEW` |
| POST | `/api/v1/platform/wage-component-templates/validate-formula` | Platform superadmin |

Body: `calculationMethod`, `formulaExpression`, optional `percentageBase`, optional `roundingStrategy`, `mockContext`.

**Response:** `{ "ok": true, "amount": "1234.5600" }` or Problem+JSON `INVALID_FORMULA`.

### Mock context shape

```json
{
  "compensationPeriodicRate": "18500",
  "transactionQuantity": "0",
  "transactionRate": "0",
  "transactionAmount": "0",
  "definitionDefaultAmount": "18500",
  "componentAmounts": { "1001": "18500" }
}
```

### Acceptance (summary)

- AC-PE6-1: Invalid syntax → 400 with stable code.
- AC-PE6-2: Same expression as engine evaluation for identical context.

---

## Phase 7 — Frontend graph administrator

### Objective

Match product spec §5: token formula builder, dependency mapper, live validate.

### Surfaces

| Page | Features |
|------|----------|
| Platform template edit | Dependencies tab (Phase 3 minimal → graph read-only view) |
| Platform template edit | Base effects (exists) |
| Tenant wage component | Formula editor + validate panel (Phase 6 API) |
| Pay period | Preview: bases, statutory lines, NET (Phase 5 fields) |

### Components (shipped)

| Component | Path |
|-----------|------|
| `WageComponentDependencyGraph` | `frontend/src/components/payroll/WageComponentDependencyGraph.tsx` |
| `WageComponentFormulaTokenBar` | `frontend/src/components/payroll/WageComponentFormulaTokenBar.tsx` |
| `FormulaValidatePanel` | `frontend/src/components/payroll/FormulaValidatePanel.tsx` |
| `WageComponentFormulaEditor` | `frontend/src/components/wage-components/WageComponentFormulaEditor.tsx` |
| `dependency-graph` utils | `frontend/src/lib/dependency-graph.ts` |

`WageComponentTemplateDependencies` — cycle warning + graph on platform template **Dependencies** tab.

### i18n

Prefix: `platformWageComponentTemplates.dependencies.*`, `wageComponents.formula.validate.*`

### Acceptance (summary)

- [x] AC-PE7-1: Cycle error before save (platform template edit).
- [x] AC-PE7-2: Validate panel without pay period (tenant + platform; Phase 6 API).
- [x] AC-PE7-3: Read-only dependency graph on template Dependencies tab.
- [x] Pay period formula preview shows `employeeBaseTotals` and `employeeNetPay`.

---

## Document history

| Date | Change |
|------|--------|
| 2026-05-17 | Initial combined outline |
| 2026-05-18 | Phase 6 validate API + `WageComponentFormulaValidateService` |
| 2026-05-18 | Phase 7 frontend: graph, token bar, validate panel, pay-period preview |
