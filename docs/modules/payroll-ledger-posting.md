# Module: Payroll ledger posting & balance tracking

**Feature slug:** `payroll-ledger-posting`  
**Milestone:** M5 (shipped 2026-05-18)  
**Roadmap:** [`../product/PAYROLL-ENGINE-PHASE-8.md`](../product/PAYROLL-ENGINE-PHASE-8.md)  
**Design reference:** [`../features/payroll-wage-component-engine-design.md`](../features/payroll-wage-component-engine-design.md) §6–7

---

## 1. Objective

After payroll amounts and NET are finalized (Phases 4–5), generate **accounting postings** from wage component ledger links and update **running balances** (loans, reserves, savings) in an auditable, append-only way.

---

## 2. Scope

### Included (Phase 8 target)

- Post-finalize hook in payroll pipeline (`NET_AND_ACCUMULATORS` extension or sub-step after persist).
- **Ledger postings** from `tenant_wage_component` debit/credit ledger FKs + `EvaluatedComponentAmount` / result lines.
- **Balance mutations** for components with `maintains_balance = true`.
- Append-only `tenant_wage_component_balance_transaction` history.
- Idempotent posting per `pay_period_run_id` (no duplicate postings on re-entry).

### Excluded (v1)

- Full GL export / ERP connectors.
- Multi-currency revaluation beyond company currency.
- Tenant-editable posting rules (driven by component metadata + platform templates).
- Automatic creation of `tenant_ledger` rows (must exist from company setup).

---

## 3. Product rules

| Rule | Detail |
|------|--------|
| **Finalize-only** | Postings and balance updates run only when `PayrollContext.payPeriodRunId()` is set and finalize completed (not formula preview). |
| **Immutability** | Posted journal lines are not deleted; corrections use reversal run (future) or manual adjustment transaction kind. |
| **Balance sign** | `balance_direction` DEBIT vs CREDIT on component defines whether payroll deduction **increases** or **decreases** outstanding balance. |
| **Loan template 1003** | Repayment reduces LOAN balance; disbursement (future component) increases. |
| **Ledger required** | If debit or credit ledger missing, skip posting for that line and record warning in run metadata (or fail finalize — ADR in Phase 8 spec). |

---

## 4. Data model (existing tables)

### `tenant_wage_component` (posting inputs)

| Field | Use |
|-------|-----|
| `debit_tenant_ledger_id` | Debit account for posting entry |
| `credit_tenant_ledger_id` | Credit account |
| `posting_strategy` | Optional strategy code (v1: metadata only or `STANDARD_EARNING` / `STANDARD_DEDUCTION`) |
| `maintains_balance` | If true, participate in balance phase |
| `balance_type` | `LOAN`, `RESERVE`, `SAVINGS` |
| `balance_direction` | `DEBIT`, `CREDIT` |
| `counter_component_id` | Optional paired component for transfers |

### `tenant_wage_component_balance` (allowed columns — M13)

| Column | Type | Notes |
|--------|------|-------|
| `id` | VARCHAR(36) PK | |
| `tenant_id` | VARCHAR(36) NOT NULL | |
| `company_id` | VARCHAR(36) NOT NULL | |
| `employee_id` | VARCHAR(36) NOT NULL | |
| `tenant_wage_component_id` | VARCHAR(36) NOT NULL | |
| `currency_code` | CHAR(3) | |
| `current_balance` | DECIMAL(19,4) NOT NULL | |
| `updated_at` | TIMESTAMP NOT NULL | |

Unique: (`tenant_id`, `company_id`, `employee_id`, `tenant_wage_component_id`).

### `tenant_wage_component_balance_transaction` (allowed columns)

| Column | Type | Notes |
|--------|------|-------|
| `id` | VARCHAR(36) PK | |
| `tenant_id` | VARCHAR(36) NOT NULL | |
| `balance_id` | VARCHAR(36) FK NOT NULL | |
| `change_amount` | DECIMAL(19,4) NOT NULL | Signed delta |
| `balance_after` | DECIMAL(19,4) NOT NULL | Snapshot after change |
| `transaction_kind` | VARCHAR(30) NOT NULL | `BalanceTransactionKind` enum |
| `pay_period_run_id` | VARCHAR(36) FK | Link to payroll run |
| `remarks` | VARCHAR(500) | |
| `occurred_at` | TIMESTAMP NOT NULL | |
| `created_at` | TIMESTAMP NOT NULL | |

### Proposed Schema Extension (requires PII review)

**Table:** `tenant_payroll_ledger_posting` (new — Phase 8)

| Column | Type | Notes |
|--------|------|-------|
| `id` | VARCHAR(36) PK | |
| `tenant_id` | VARCHAR(36) NOT NULL | |
| `pay_period_run_id` | VARCHAR(36) NOT NULL | |
| `employee_id` | VARCHAR(36) NOT NULL | |
| `tenant_payroll_result_line_id` | VARCHAR(36) | Optional FK to result line |
| `debit_tenant_ledger_id` | VARCHAR(36) NOT NULL | |
| `credit_tenant_ledger_id` | VARCHAR(36) NOT NULL | |
| `amount` | DECIMAL(19,4) NOT NULL | |
| `currency_code` | CHAR(3) NOT NULL | |
| `posting_sequence` | INT NOT NULL | Order within run |
| `created_at` | TIMESTAMP NOT NULL | |

Unique: (`pay_period_run_id`, `tenant_payroll_result_line_id`) when line id present.

**Alternative v1:** derive postings on read from result lines + component ledger FKs without new table — acceptable for MVP if performance OK; module prefers explicit posting table for audit.

---

## 5. Pipeline placement

```text
Phase NET_AND_ACCUMULATORS (extended)
  5a. NetPayCalculator (Phase 5)
  5b. PayrollResultPersistenceService (Phase 4)
  5c. BalanceUpdateService          (Phase 8)
  5d. LedgerPostingService          (Phase 8)
```

Runs **after** result lines persisted so postings reference stable line ids.

---

## 6. Balance update semantics

| `balance_direction` | Component amount (deduction) | `change_amount` on balance |
|---------------------|------------------------------|----------------------------|
| DEBIT (loan owed) | Positive repayment | Decrease outstanding (negative change to DEBIT balance) |
| CREDIT | TBD per product | Invert per SME |

**`BalanceTransactionKind`:** use `PAYROLL_DEDUCTION`, `PAYROLL_ACCRUAL`, `OPENING`, `ADJUSTMENT` (verify enum in `payroll.model`).

**Loan 1003 example:**

- Employee owes 10 000; repayment component evaluates 500.
- `current_balance` decreases by 500; transaction row `change_amount = -500`, `balance_after = 9500`.

---

## 7. Ledger posting semantics

For each **persisted** `tenant_payroll_result_line`:

1. Resolve `tenant_wage_component` (or platform component for statutory).
2. If both ledger ids present → create balanced entry (debit/credit) for `rounded_amount`.
3. Earning vs deduction may flip debit/credit interpretation via `component_type` or `posting_strategy`.

**Standard pattern (illustrative):**

| `component_type` | Debit | Credit |
|------------------|-------|--------|
| EARNING | Expense (debit ledger on component) | Payable (credit ledger) |
| DEDUCTION | Payable | Receivable / deduction ledger |

Exact mapping defined in Phase 8 implementation from seeded template ledger links (e.g. 1001 → 3300 / 3100).

---

## 8. Services (target)

| Service | Responsibility |
|---------|----------------|
| `TenantWageComponentBalanceService` | getOrCreate balance header; apply change with optimistic locking on `current_balance` |
| `PayrollLedgerPostingService` | build postings from result lines; insert `tenant_payroll_ledger_posting` |
| `PayrollFinalizePostProcessor` | orchestrates 5c+5d; called from finalize endpoint |

---

## 9. API

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/v1/employees/{id}/wage-component-balances` | List balances (privilege `WAGE_COMPONENT_VIEW`) |
| GET | `/api/v1/wage-component-balances/{id}/transactions` | History |

Finalize endpoint (Phase 4) triggers posting automatically — no separate POST.

---

## 10. Security

- `WAGE_COMPONENT_VIEW` for balance read.
- `PAY_PERIOD_RUN_MANAGE` for finalize (triggers mutations).
- Tenant isolation on all queries.

---

## 11. Audit

| Action (proposed) | When |
|-------------------|------|
| `TENANT_PAYROLL_BALANCE_UPDATED` | Balance change applied |
| `TENANT_PAYROLL_LEDGER_POSTED` | Postings created for run |

---

## 12. Acceptance criteria

| ID | Criterion |
|----|-----------|
| AC-PLP-1 | Finalize on demo employee with loan component creates balance transaction |
| AC-PLP-2 | Second finalize same run does not duplicate postings (idempotent) |
| AC-PLP-3 | Preview does not mutate balances or create postings |
| AC-PLP-4 | Posting amounts tie to sum of result lines per ledger pair |
| AC-PLP-5 | Balance `current_balance` equals last `balance_after` in transaction history |

---

## 13. Document history

| Date | Change |
|------|--------|
| 2026-05-17 | Initial module spec (Phase 8) |
