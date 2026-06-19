# Phase 5 — NET closure & year-to-date accumulators

**Status:** Implemented (2026-05-18)  
**Regression:** [`PAYROLL-GOLDEN-SCENARIO-SR.md`](./PAYROLL-GOLDEN-SCENARIO-SR.md) § Phase 5 provisional NET

---

## 1. Objective

- **Phase 4 handler (`NET_AND_ACCUMULATORS`)** computes explicit **NET pay** per employee from accumulated bases and statutory deductions.
- Persist **YTD** totals per employee per base/tax year for reporting and SR annual rules.

---

## 2. Prerequisites

- [x] Phase 2 statutory amounts available in `PayrollRunState`.
- [x] Phase 4 persist path (lines exist for finalized runs).
- [x] Golden scenario NET approved (12 830 SRD per Policy A).

---

## 3. NET equation (conceptual)

Per employee for period:

```text
NET = Σ(earnings affecting NET) − Σ(deductions affecting NET) − Σ(statutory employee deductions)
```

**Implementation approach (v1):**

1. Use **`employeeBaseTotals["NET"]`** after all base effects applied post-evaluation, **or**
2. Recompute from signed component lines by `net_effect` / base effects on NET.

**Decision:** Prefer **base-effect-driven NET base** as canonical when migration complete; cross-check against sum of `EvaluatedComponentAmount` with NET effects for AC-PE5-1.

Expose on preview response (non-breaking):

```json
"employeeNetPay": { "employee-uuid": 12830.00 }
```

New top-level map on formula-preview / finalize response — document in Phase 5 module update to `pay-periods.md`.

---

## 4. Data model — `tenant_payroll_ytd_accumulator` (shipped, M36)

| Column | Type | PII | Notes |
|--------|------|-----|--------|
| `id` | VARCHAR(36) PK | none | UUID |
| `tenant_id` | VARCHAR(36) NOT NULL | low | |
| `company_id` | VARCHAR(36) NOT NULL | none | |
| `employee_id` | VARCHAR(36) NOT NULL | none | |
| `tax_year` | INT NOT NULL | none | e.g. 2026 from pay period |
| `accumulator_code` | VARCHAR(50) NOT NULL | none | e.g. `LOONBELASTING`, `WAGE_TAX_PAID` |
| `amount` | DECIMAL(19,4) NOT NULL | none | Running total |
| `currency_iso3` | CHAR(3) NOT NULL | none | |
| `updated_at` | TIMESTAMP NOT NULL | none | |

**Unique:** (`tenant_id`, `employee_id`, `tax_year`, `accumulator_code`).

**Update rule:** On finalize, `amount += period_delta` (upsert).

Requires schema preflight + module doc `payroll-ytd-accumulators.md` or section in this file promoted to module when approved.

---

## 5. Backend tasks

| Task | Detail |
|------|--------|
| `NetPayCalculator` | From state: bases + statutory lines → `Map<UUID, BigDecimal> netPay` |
| `NetAndAccumulatorsPhaseHandler` | Invoke calculator; update YTD; optional attach to result DTO |
| `TenantPayrollYtdAccumulatorRepository` | Upsert increments |
| GET API | `/api/v1/employees/{id}/payroll-ytd?taxYear=2026` (privilege `PAY_PERIOD_VIEW` or new `PAYROLL_YTD_VIEW`) |

---

## 6. Tests

| Test | Assert |
|------|--------|
| Golden NET | ≈ 12 830 SRD (when Phase 2 tax/AOV provisional approved) |
| YTD | Two periods in same year increment LOONBELASTING YTD |

---

## 7. Acceptance criteria

| ID | Criterion |
|----|-----------|
| AC-PE5-1 | `employeeNetPay` matches golden within 0.01 SRD |
| AC-PE5-2 | YTD LOONBELASTING after two periods = sum of period bases |
| AC-PE5-3 | NET phase runs after STATUTORY in orchestrator |

---

## 8. Document history

| Date | Change |
|------|--------|
| 2026-05-17 | Initial Phase 5 spec |
| 2026-05-18 | NET calculator, YTD table, APIs, golden tests |
