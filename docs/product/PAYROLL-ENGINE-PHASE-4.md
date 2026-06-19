# Phase 4 — Persist payroll run & audit trail

**Status:** Implemented (2026-05-18)  
**Related:** [`../modules/pay-periods.md`](../modules/pay-periods.md), [`../modules/payroll-wage-component-engine.md`](../modules/payroll-wage-component-engine.md)

---

## 1. Objective

On **pay period run finalize**, persist immutable **`tenant_payroll_result_line`** rows for every evaluated tenant + statutory amount so historical payslips and audits survive future formula/tax rule changes.

---

## 2. Prerequisites

- [x] Phases 1–2 producing complete in-memory evaluation lists.
- [x] Pay period run exists (`tenant_pay_period_run`).
- [x] ADR: **one finalize per run** vs allow re-run — default **new run only** (see §5).

---

## 3. Data model

### Existing: `tenant_payroll_result_line` (no DDL change required for MVP)

| Column | Use on persist |
|--------|----------------|
| `pay_period_run_id` | Required FK |
| `employee_id` | From evaluation |
| `component_source` | `TENANT` \| `PLATFORM` |
| `component_ref_id` | `tenant_wage_component.id` or `platform_wage_component.id` |
| `phase` | Snapshot from component definition at run time |
| `processing_order_snapshot` | From `processing_order` at run time |
| `quantity`, `rate`, `amount`, `rounded_amount` | From evaluation / transaction |

### Proposed Schema Extension (requires PII review)

Optional Phase 4b columns on `tenant_payroll_result_line`:

| Column | Type | Notes |
|--------|------|-------|
| `formula_expression_snapshot` | VARCHAR(500) | Copy at run time |
| `calculation_method_snapshot` | VARCHAR(30) | |
| `country_tax_rule_id_snapshot` | VARCHAR(36) | For statutory lines |
| `base_totals_json` | VARCHAR(4000) | Per-employee bases JSON at run time |

Defer unless compliance requires formula audit in v1.

---

## 4. Backend

### 4.1 `PayrollResultPersistenceService`

```text
persistRun(PayrollRunState state, UUID payPeriodRunId)
  → delete existing lines for run? NO (immutable: never delete)
  → insert batch tenant_payroll_result_line
  → return count
```

Called from **`NetAndAccumulatorsPhaseHandler`** (Phase 4) only when `context.payPeriodRunId()` non-null and finalize flag true.

### 4.2 Finalize flow

| Step | Owner |
|------|-------|
| 1 | User creates `FINAL` run via existing pay-period run API |
| 2 | New endpoint or extend run create: `POST /pay-periods/{id}/runs/{runId}/finalize` |
| 3 | Materialize inputs (optional pre-step) |
| 4 | Build `PayrollContext` with `payPeriodRunId` |
| 5 | `PayrollEngine.calculate` |
| 6 | `PayrollResultPersistenceService.persistRun` |
| 7 | Audit `TENANT_PAYROLL_RUN_FINALIZED` |

**Preview** (`formula-preview`) does **not** persist (unless superadmin `?persist=false` default).

### 4.3 `PayrollRunState.toResult()` vs persist

- Preview API continues to use in-memory `PayrollRunResult` only.
- Finalize uses same engine path with `payPeriodRunId` set.

---

## 5. Business rules

| ID | Rule |
|----|------|
| BR-P4-1 | Lines are **append-only** per run; no UPDATE amount after insert. |
| BR-P4-2 | Unique slot per (`pay_period_run_id`, `employee_id`, `component_source`, `component_ref_id`) — existing constraint. |
| BR-P4-3 | Second finalize on same run **rejected** (`RUN_ALREADY_FINALIZED`) unless ADR chooses replace (rejected: use new run). |
| BR-P4-4 | Pay period status may move to `CLOSED` after successful FINAL finalize (optional product rule). |

---

## 6. API (shipped)

| Method | Path | Privilege |
|--------|------|-----------|
| POST | `/api/v1/pay-periods/{periodId}/runs/{runId}/finalize` | `PAY_PERIOD_RUN_MANAGE` |
| GET | `/api/v1/pay-period-runs/{runId}/result-lines` | `PAY_PERIOD_RUN_VIEW` |

**Request body (optional):**

```json
{ "employeeIds": ["uuid", "..."], "materializeInputs": true }
```

Default: all active employees in company for period.

**Response:**

```json
{
  "linesCreated": 42,
  "employeeCount": 3,
  "runId": "..."
}
```

Update [`pay-periods.md`](../modules/pay-periods.md) §9 when implemented.

---

## 7. Tests

| Test | Assert |
|------|--------|
| `PayrollResultPersistenceServiceIT` | Finalize creates lines; count = employees × components evaluated |
| Immutability | Update tax rule + re-preview ≠ stored line amount |
| Golden SR | At least one line for employee `…000006`, component 1001, amount 18500 |

---

## 8. Acceptance criteria

| ID | Criterion |
|----|-----------|
| AC-PE4-1 | Query lines by `pay_period_run_id` + `employee_id` |
| AC-PE4-2 | Stored `rounded_amount` matches engine at finalize time |
| AC-PE4-3 | Historical lines unchanged after platform tax rule update |
| AC-PE4-4 | Preview does not insert lines |
| AC-PE4-5 | Duplicate finalize on same run returns 409 |

---

## 9. Document history

| Date | Change |
|------|--------|
| 2026-05-17 | Initial Phase 4 spec |
| 2026-05-18 | Implemented persistence, finalize API, tests |
