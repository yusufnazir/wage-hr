# Payroll Wage Component Engine — Feature Design Document

## Overview

This document describes the design and architecture for a flexible payroll wage component engine intended for a modern payroll application.

The system should support:

- Generic payroll processing
- Country-specific payroll rules
- Earnings and deductions
- Taxable and non-taxable components
- Ledger integration
- Running balances
- Loan reimbursements
- Reserve/accrual components
- Employer and employee contributions
- Future multi-country support

The initial implementation will target Suriname payroll rules, while maintaining a generic architecture for future expansion.

**Implementation contract (DDL, services, formula editor):** [`../modules/payroll-wage-component-engine.md`](../modules/payroll-wage-component-engine.md) — especially **§11 Formula editor (planned)**.

---

## 1. Goals

The payroll engine must:

- Avoid hardcoded payroll logic
- Allow flexible wage component configuration
- Support reusable calculation logic
- Support accounting integration
- Support balance tracking
- Support accruals and settlements
- Support future localization
- Support auditability and historical recalculation

---

## 2. Core Concepts

The payroll engine revolves around:

1. Wage Component Definition
2. Wage Component Transaction
3. Payroll Run
4. Payroll Result
5. Ledger Posting
6. Balance Tracking

---

## 3. Wage Component Definition

A Wage Component Definition describes the behavior of a payroll component.

Examples:

- `1001`–`1044` — Suriname platform `template_code` values (seed; human-readable names live in `name` / `description`)
- BONUS, TAX, PENSION — generic categories (illustrative)
- `1003` — loan repayment template (balance-tracked deduction)
- VACATION_RESERVE

This is configuration/master data.

---

## 3.1 Wage Component Fields

### Identification

| Field | Description |
|---|---|
| code | Unique component code |
| name | Display name |
| description | Optional explanation |
| active | Active/inactive status |

---

### Component Classification

| Field | Description |
|---|---|
| componentType | EARNING, DEDUCTION, EMPLOYER_CONTRIBUTION, INFORMATIONAL |
| category | Salary, overtime, tax, loan, reserve, allowance, etc. |

---

### Net Salary Effect

| Field | Description |
|---|---|
| netEffect | ADD_TO_NET, SUBTRACT_FROM_NET, NO_EFFECT |

Examples:

- Salary → ADD_TO_NET
- Tax → SUBTRACT_FROM_NET
- Employer pension → NO_EFFECT

---

### Taxability Flags

The system must support multiple taxable bases.

| Field | Description |
|---|---|
| taxableWageTax | Included in wage tax calculation |
| taxableSocialSecurity | Included in social premiums |
| taxablePension | Included in pension base |
| taxableVacationReserve | Included in vacation reserve base |

The system must support adding additional taxability dimensions later.

Avoid using a single generic taxable boolean.

---

### Calculation Configuration

| Field | Description |
|---|---|
| calculationMethod | FIXED_AMOUNT, HOURLY, PERCENTAGE, FORMULA, MANUAL_INPUT |
| percentageBase | Base used for percentage calculations |
| formulaExpression | Optional **formula payload** (expression DSL or versioned JSON AST); semantics and validation rules are fixed in the module contract |
| defaultAmount | Default configured amount |
| roundingStrategy | Payroll rounding behavior |

#### Formula editor and expression contract

The product must support definitions such as **periodic base salary** (rate from employee compensation) and **hours × hourly rate** (quantity and rate from wage component transactions), without embedding a general-purpose scripting language.

**Authoritative spec:** [`../modules/payroll-wage-component-engine.md`](../modules/payroll-wage-component-engine.md) §11. The following is a **summary** aligned with that section.

| Topic | Requirement |
|--------|-------------|
| **Storage** | Reuse `formula_expression` (and `percentage_base` where relevant) on platform and tenant component rows. If canonical JSON exceeds current column length, migrate DDL (see module §11.6). |
| **Canonical form** | Prefer a **versioned JSON AST** parsed and evaluated only by payroll code; alternatively a **strict line-oriented DSL** (identifiers, literals, `+ - * / ( )`, fixed function set) — never `eval` of tenant strings. |
| **Identifiers** | Formulas reference an **allowlist** of bindings (e.g. periodic compensation rate, `transaction.quantity`, `transaction.rate`, `definition.default_amount`). Country-specific values enter via documented engine/context contracts. |
| **`calculationMethod`** | `FORMULA` requires a valid parsed `formula_expression`. `HOURLY` is the **quantity × rate** preset (implementation may store an equivalent expression or a dedicated branch — choose one behavior and keep it consistent). `PERCENTAGE` requires `percentageBase`. `MANUAL_INPUT` / `FIXED_AMOUNT` follow existing semantics. |
| **API** | Reject invalid formulas on create/update with stable error codes for UI feedback. |
| **UI** | **Guided presets** (e.g. “Periodic rate”, “Hours × rate”) plus **Monaco** completion (Ctrl+Space) for allowlisted fields and operators in the tenant web app. |
| **Engine** | Evaluate inside a payroll **`FormulaEvaluationContext`** (see `com.wagepayroll.payroll.formula.FormulaEvaluationContext`); **`DefaultPayrollEngine`** returns per-line previews in **`PayrollRunResult#evaluatedComponentAmounts`** when `PayrollContext.payPeriodId` and employees are set. Apply `roundingStrategy` after evaluation. **PERCENTAGE** tax math remains future work. |

For how recurring inputs **materialize** into transactions, see [`../prompts/AGENT-GUIDE-EMPLOYEE-PERIODIC-PAYROLL-TRANSACTIONS.md`](../prompts/AGENT-GUIDE-EMPLOYEE-PERIODIC-PAYROLL-TRANSACTIONS.md) and the module [`employee-periodic-payroll-transactions.md`](../modules/employee-periodic-payroll-transactions.md).

---

### Processing Configuration

| Field | Description |
|---|---|
| processingOrder | **List / UI sort** order for templates and tenant wage components in pickers and catalogs (lower = earlier). Everyday components first, system or predefined slots later. **Not** the sole driver of payroll engine sequencing (use **phase** and dependencies). |
| phase | GROSS, PRE_TAX, TAX, POST_TAX, NET — primary payroll pipeline stage |

---

### Country Configuration

| Field | Description |
|---|---|
| countryCode | Country ownership |
| countrySpecificSettings | JSON or extension settings |

The engine must support:

- global components
- country-specific components
- company-specific overrides

---

## 4. Wage Component Transaction

A Wage Component Transaction represents actual payroll input for an employee.

Examples:

- 10 overtime hours
- bonus of 500
- tax deduction
- loan installment deduction

This is transactional/runtime data.

---

## 4.1 Transaction Fields

| Field | Description |
|---|---|
| employeeId | Employee |
| wageComponentId | Linked component |
| payrollPeriod | Payroll period |
| quantity | Hours/days/units |
| rate | Applied rate |
| amount | Calculated amount |
| manualOverride | Manual adjustment |
| remarks | Optional notes |

---

## 5. Balance-Based Wage Components

The engine must support wage components that maintain balances over time.

Examples:

- Employee loans
- Salary advances
- Vacation accruals
- Bonus reserves
- Savings plans
- Deferred payments

---

## 5.1 Balance Tracking

A balance-enabled component maintains:

| Field | Description |
|---|---|
| openingBalance | Starting balance |
| increaseAmount | Amount added |
| decreaseAmount | Amount deducted |
| closingBalance | Remaining balance |

---

## 5.2 Balance Behaviors

### Loan Reimbursement Example

Employee receives:

- Loan = 5,000

Monthly deduction:

- 500 per month

Balance evolves:

| Month | Balance |
|---|---|
| Start | 5,000 |
| After month 1 | 4,500 |
| After month 2 | 4,000 |

The system must:

- automatically track outstanding balances
- stop deductions at zero
- support manual adjustments

---

### Accrual/Reserve Example

Vacation reserve:

- Employee builds reserve monthly
- Paid out at year-end

Example:

| Month | Reserve Balance |
|---|---|
| January | 200 |
| February | 400 |
| March | 600 |

At payout:

- reserve resets
- payout transaction generated

---

## 6. Balance Ledger Design

Balance-enabled components should support double-sided accounting behavior.

Recommended fields:

| Field | Description |
|---|---|
| maintainsBalance | Indicates balance tracking |
| balanceType | LOAN, RESERVE, SAVINGS |
| balanceDirection | DEBIT or CREDIT |
| counterWageComponentId | Opposite balancing component |

---

## 6.1 Counter Wage Component

Example:

### Loan Granted

- Component: EMPLOYEE_LOAN_DISBURSEMENT
- Creates positive employee debt balance

### Loan Repayment

- Component: `1003` (loan repayment template)
- Reduces outstanding balance

These should be linked together.

---

## 7. Ledger Integration

Every wage component may optionally integrate with accounting/GL.

---

## 7.1 Ledger Fields

| Field | Description |
|---|---|
| debitLedgerAccount | Debit account |
| creditLedgerAccount | Credit account |
| costCenter | Optional |
| projectCode | Optional |
| postingStrategy | Payroll posting behavior |

---

## 7.2 Example

### Salary

| Action | Ledger |
|---|---|
| Salary expense | Debit |
| Payroll payable | Credit |

---

### Loan Repayment

| Action | Ledger |
|---|---|
| Employee receivable | Credit |
| Cash/payroll payable | Debit |

---

## 8. Payroll Processing Engine

The payroll engine must process components generically.

Avoid:

```java
if (component == TAX)
```

Instead:

- use metadata
- use phases
- use formulas
- use **phase** and explicit dependencies for run order (not the list-sort `processingOrder` alone)

---

## 8.1 Suggested Processing Flow

### Phase 1 — Gross Earnings

- salary
- overtime
- bonuses

### Phase 2 — Pre-Tax Deductions

- pension
- insurance

### Phase 3 — Tax Calculation

- wage tax
- social premiums

### Phase 4 — Post-Tax Deductions

- loans
- garnishments

### Phase 5 — Net Adjustments

- reimbursements
- corrections

### Phase 6 — Ledger Posting

- generate accounting entries

### Phase 7 — Balance Updates

- update loan balances
- update reserves

---

## 9. Country Rule Architecture

The core payroll engine must remain generic.

Country-specific logic should be isolated.

---

## 9.1 Country Provider Pattern

Example:

```text
PayrollEngine
    -> CountryRuleProvider
        -> SurinameRules
        -> GuyanaRules
        -> TrinidadRules
```

Country modules may define:

- tax tables
- social security
- reporting rules
- statutory calculations
- legal validations

---

## 10. Extensibility Requirements

The architecture must support:

- New countries
- New taxes
- New contribution schemes
- New balance types
- New accounting integrations
- Formula engine extensions
- Multi-company payroll
- Historical payroll recalculation

---

## 11. Audit & History

Payroll is highly sensitive.

The system must:

- never overwrite finalized payroll data
- maintain historical balances
- maintain payroll snapshots
- log manual changes
- support audit trails

---

## 12. Recommended Database Structure

Suggested tables:

| Table | Purpose |
|---|---|
| wage_component | Component definitions |
| wage_component_transaction | Payroll input |
| payroll_run | Payroll execution |
| payroll_result | Final payroll values |
| wage_component_balance | Running balances |
| wage_component_balance_transaction | Balance history |
| ledger_posting | Accounting entries |
| tax_rule | Country tax rules |

**Repository mapping (M13):** platform statutory definitions → `platform_wage_component`; tenant definitions → `tenant_wage_component`; templates → `platform_wage_component_template`; tax parameters → `platform_country_tax_rule`; payroll run anchor → existing `tenant_pay_period_run`; results → `tenant_payroll_result_line`; balances → `tenant_wage_component_balance` (+ `_transaction`).

---

## 13. Example Java Domain Model

```java
class WageComponent {

    String code;
    String name;
    String description;

    boolean active;

    ComponentType componentType;
    ComponentCategory category;

    NetEffect netEffect;

    boolean taxableWageTax;
    boolean taxableSocialSecurity;
    boolean taxablePension;
    boolean taxableVacationReserve;

    CalculationMethod calculationMethod;

    PercentageBase percentageBase;

    String formulaExpression;

    BigDecimal defaultAmount;

    Integer processingOrder; // list/UI sort key (pickers, catalogs); engine uses phase + deps

    PayrollPhase phase;

    boolean maintainsBalance;

    BalanceType balanceType;

    BalanceDirection balanceDirection;

    WageComponent counterWageComponent;

    LedgerAccount debitLedgerAccount;
    LedgerAccount creditLedgerAccount;

    String countryCode;

    String countrySpecificSettingsJson;

}
```

---

## 14. Important Design Principles

### DO

- Use configuration over hardcoding
- Separate definitions from transactions
- Separate payroll from accounting
- Support extensibility
- Support historical recalculation

### DO NOT

- Hardcode taxes
- Hardcode overtime logic
- Use single taxable boolean
- Mix balance logic with payroll logic
- Store only net salary results

---

## 15. Initial Scope Recommendation

### Priority 1

- Earnings
- Deductions
- Taxability
- Net calculations
- Ledger linkage

### Priority 2

- Balance components
- Loan reimbursement
- Accrual/reserve payouts

### Priority 3

- Formula engine
- Multi-country support
- Dynamic rules engine

---

## 16. Expected Outcome

The result should be:

- Flexible
- Maintainable
- Auditable
- Country-extensible
- Accounting-ready
- Enterprise-capable

without becoming overly complex during the first implementation phase.
