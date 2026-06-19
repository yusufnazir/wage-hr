# Phase 8 — Ledger posting & balance tracking

**Status:** Shipped (2026-05-18)  
**Module authority:** [`../modules/payroll-ledger-posting.md`](../modules/payroll-ledger-posting.md)

---

## 1. Objective

Extend the finalize pipeline to:

1. Write **ledger postings** from result lines + component ledger FKs.
2. Update **running balances** and append **balance transactions** for `maintains_balance` components.

Preview (`formula-preview`) must **not** trigger either path.

---

## 2. Prerequisites

- [x] Phase 4 persist (`tenant_payroll_result_line`) shipped.
- [x] Phase 5 NET computed.
- [x] Company has `tenant_ledger` rows linked on wage components (demo seed M37).
- [x] Schema `tenant_payroll_ledger_posting` (M37).

---

## 3. Implementation tasks

| # | Task | Notes |
|---|------|-------|
| 1 | Liquibase `tenant_payroll_ledger_posting` | Per module § Proposed Schema |
| 2 | JPA + repository | |
| 3 | `PayrollLedgerPostingService` | Idempotent by `pay_period_run_id` |
| 4 | `TenantWageComponentBalanceService` | getOrCreate + applyChange |
| 5 | `PayrollFinalizePostProcessor` | Wire into finalize / `NetAndAccumulatorsPhaseHandler` |
| 6 | Fail vs warn on missing ledgers | **Default: warn + skip line** (log + run summary); configurable strict mode later |
| 7 | GET APIs for balances | Optional v1 read surfaces |
| 8 | IT: template 1003 loan repayment reduces balance | |

---

## 4. Idempotency

Before posting for run `R`:

```sql
SELECT COUNT(*) FROM tenant_payroll_ledger_posting WHERE pay_period_run_id = R
```

If &gt; 0, skip posting phase (or throw `RUN_ALREADY_POSTED` if finalize retried — align with Phase 4 BR-P4-3).

Balances: use `transaction_kind = PAYROLL_DEDUCTION` + `pay_period_run_id` unique constraint (proposed) to prevent duplicate balance txs.

---

## 5. Posting algorithm (v1)

```text
FOR each result_line IN run ORDER BY processing_order_snapshot
  resolve component (tenant or platform)
  IF debit_ledger_id AND credit_ledger_id both set
    INSERT posting(debit, credit, rounded_amount, employee_id, result_line_id)
  ELSE
    LOG warn MISSING_LEDGER_LINK
```

Statutory platform components: use platform ledger template ids resolved to tenant ledger if provisioned; else platform-level posting report only (defer if no tenant ledger).

---

## 6. Balance algorithm (v1)

```text
FOR each result_line WHERE component.maintains_balance
  balance = getOrCreate(employee, component)
  delta = computeDelta(amount, balance_direction, component_type)
  IF delta != 0
    INSERT balance_transaction
    UPDATE balance.current_balance += delta
```

`computeDelta` documented in module §6; unit test with LOAN DEBIT direction.

---

## 7. Tests

| Test | Assert |
|------|--------|
| `PayrollLedgerPostingServiceIT` | Finalize creates ≥1 posting for demo 1001 with ledgers set |
| `TenantWageComponentBalanceServiceTest` | Repayment decreases LOAN balance |
| Idempotency IT | Second post attempt no new rows |
| Preview IT | No rows in posting/balance tables |

---

## 8. Acceptance criteria

| ID | Criterion |
|----|-----------|
| AC-PE8-1 | AC-PLP-1 … AC-PLP-5 from module doc |
| AC-PE8-2 | Finalize summary returns `{ postingsCreated, balancesUpdated }` |
| AC-PE8-3 | Orchestrator order: persist → balance → ledger |

All marked done in `PayrollLedgerPostingIT`, `TenantPayrollFinalizeIT`, `PayrollBalanceChangeCalculatorTest`.

---

## 9. Open decisions

| # | Question | Default proposal |
|---|----------|------------------|
| 1 | New `tenant_payroll_ledger_posting` table vs derived view | **New table** |
| 2 | Missing ledger on finalize | **Warn + skip** |
| 3 | Statutory line postings | Tenant ledger mapping when `platform_wage_component` ledger templates copied |

---

## 10. Document history

| Date | Change |
|------|--------|
| 2026-05-17 | Initial Phase 8 spec |
| 2026-05-18 | M37 schema, posting/balance services, demo loan seed, ITs |
