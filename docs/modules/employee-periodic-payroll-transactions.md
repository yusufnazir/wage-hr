# Feature: Employee periodic payroll transactions

**Feature slug:** `employee-periodic-payroll-transactions`  
**Related:** [`payroll-wage-component-engine.md`](./payroll-wage-component-engine.md) (period input + engine), [`payroll-org-structure.md`](./payroll-org-structure.md) (employee, company), [`work-times.md`](./work-times.md) (potential overtime source), agent guide [`../prompts/AGENT-GUIDE-EMPLOYEE-PERIODIC-PAYROLL-TRANSACTIONS.md`](../prompts/AGENT-GUIDE-EMPLOYEE-PERIODIC-PAYROLL-TRANSACTIONS.md)

This module is the **sole behavioral and schema authority** for this feature until superseded by an explicit human decision. Follow [`../guides/SCHEMA-PERSISTENCE-PREFLIGHT.md`](../guides/SCHEMA-PERSISTENCE-PREFLIGHT.md) before any Liquibase or entity work.

---

## 1. Objective

Payroll operators need to **define recurring payroll amounts per employee** (e.g. fixed monthly allowance, recurring deduction) so they are **not re-entered every pay period**, while still allowing **per-period overrides** when something changes once.

The feature bridges **employee master data**, **tenant wage component definitions**, and **period-scoped inputs** (`tenant_wage_component_transaction`) that the payroll engine consumes.

**Suriname context:** Many items are capped, excluded, or taxed on different tables depending on nature and effective dates (see agent guide §3). This feature focuses on **capturing and scheduling inputs**; full wage-tax logic remains in `platform_country_tax_rule` and `CountryRuleProvider` / engine phases.

---

## 2. Scope

**Included**

- **Standing instructions** (recurring employee-level payroll inputs) stored per `tenant_id` + `company_id` + `employee_id`, linked to a **`tenant_wage_component`**.
- **Materialization**: generating or updating **`tenant_wage_component_transaction`** rows for a given **`pay_period`** from active standing instructions, with defined **idempotency** and conflict rules.
- **APIs and privileges** (tenant-scoped) to create, read, update, and end standing instructions; to list period transactions; to adjust period rows subject to business rules.
- **Audit** events for mutating operations on standing instructions and on generated period inputs (reuse / extend patterns from [`audit.md`](./audit.md)).

**Excluded (unless explicitly added later)**

- Full wage tax calculation, bracket tables, or Inspector approval workflows.
- Ledger posting writers and finalized-run locking (may consume outputs from [`payroll-wage-component-engine.md`](./payroll-wage-component-engine.md)).
- Mobile UI (web scope first unless PROJECT-CONTEXT says otherwise).
- Automatic import from external time systems (integrate with [`work-times.md`](./work-times.md) in a later slice).

---

## 3. Actors

| Actor | Role |
|-------|------|
| Tenant payroll admin | Maintains standing instructions and may override period transactions. |
| Tenant payroll operator | As allowed by privilege, enters adjustments and views history. |
| System | Materializes standing instructions into period transactions when the product triggers materialization. |

---

## 4. User Flows

**Happy path — recurring allowance**

1. Operator opens employee payroll inputs for a company.
2. Operator creates a **standing instruction**: component = tenant-defined earning, fixed **amount**, effective from first day of employment, no end date.
3. When pay period P opens (or when “prepare payroll” runs — **exact trigger in §7**), system creates a **`tenant_wage_component_transaction`** for employee E and period P with that amount, `manual_override` = false (or a dedicated “generated” flag if added — **§10**).
4. Payroll run reads period transactions and produces result lines.

**Variation — one-off change for one month**

1. Operator edits the **period transaction** for P only (increase amount), marking **`manual_override`** = true on that row **or** leaving standing instruction unchanged and relying on override semantics defined in §7.
2. Later periods continue to follow the standing instruction unless it is updated.

**Variation — end recurrence**

1. Operator sets **effective_to** on the standing instruction (or sets **inactive**).
2. Materialization for periods after the end date does not create new lines from that instruction; existing finalized periods are unchanged.

**Variation — component deactivated**

1. Standing instruction references a **`tenant_wage_component`** that is later deactivated.
2. Product behavior: block new materialization and surface validation error; **§8** for historical periods.

---

## 5. Data model

**Chosen approach:** **Option A** — new entity **standing instruction** + existing **`tenant_wage_component_transaction`** for period inputs (see agent guide §4).

**Existing (unchanged contract, consumption)**

- **`tenant_wage_component_transaction`**: `tenant_id`, `company_id`, `employee_id`, `pay_period_id`, optional `pay_period_run_id`, `tenant_wage_component_id`, `quantity`, `rate`, `amount`, `manual_override`, `remarks`, timestamps. Engine **v1 reads only these period rows** after materialization completes (no direct read of standing instructions in the engine).

**New: `tenant_employee_payroll_standing_instruction` (proposed name)**

Strict allowed columns (conceptual; Liquibase + entity must match this list unless amended via preflight):

| Column | Type | Notes |
|--------|------|--------|
| id | UUID PK | |
| tenant_id | UUID | required |
| company_id | UUID | required; must match employee’s company |
| employee_id | UUID | required |
| tenant_wage_component_id | UUID | required; must belong to same tenant + company |
| effective_from | date | required |
| effective_to | date | nullable (open-ended) |
| amount | decimal(19,4) | nullable if quantity+rate used |
| quantity | decimal(19,4) | nullable |
| rate | decimal(19,4) | nullable |
| recurrence | enum or varchar | **v1:** `EACH_PAY_PERIOD` only (aligns with company pay frequency via `pay_period`); richer recurrence deferred to §10 |
| active | boolean | soft disable without delete |
| remarks | varchar(500) | optional |
| created_at | instant | |
| updated_at | instant | |

**Validation (conceptual):** Exactly one of: (a) `amount` non-null, or (b) both `quantity` and `rate` non-null. Mutually exclusive with mixing that breaks engine expectations.

**Optional extension (requires explicit approval in this doc + preflight):** FK from `tenant_wage_component_transaction` to `standing_instruction_id` UUID nullable, to mark **generated** rows and support safe re-materialization. If omitted, idempotency uses composite key (employee, period, component) + `manual_override` rules in §7.

**Relationships**

- Employee and company: [`payroll-org-structure.md`](./payroll-org-structure.md).
- Component: [`payroll-wage-component-engine.md`](./payroll-wage-component-engine.md) `tenant_wage_component`.
- Pay period: [`pay-periods.md`](./pay-periods.md).

---

## 6. States and transitions

**Standing instruction**

| State | Meaning |
|-------|---------|
| Draft | Optional only if UX requires review; **v1 may omit Draft** and create Active immediately — decide in implementation per §10 |
| Active | `active = true` and today ∈ [effective_from, effective_to] |
| Ended | `effective_to` in the past or `active = false` |

**Period transaction (existing)**

- May be **system-generated** (from materialization) or **user-created**.
- Transitions when payroll run is finalized may become **read-only** per future payroll-run module; until then, follow engine module deferrals.

---

## 7. Business rules

1. **Company boundary:** Standing instruction and all transactions must reference the **same** `company_id` as the employee.
2. **Component eligibility:** Only **active** `tenant_wage_component` rows for that company may be referenced; statutory platform components follow read-only projection rules from the engine module when exposed to tenants.
3. **Employee onboarding (v1):** When a **new employee** is created via the API, the system auto-creates one **active** standing instruction per company wage component where `active = true` (same set as the wage components screen), with `effective_from = hire_date`, `recurrence = EACH_PAY_PERIOD`, and initial amounts derived from the component’s `default_amount` / calculation method (formula and percentage components get null amounts until the operator fills them in). Creation is **idempotent** per (employee, component). Demo employees seeded by Liquibase receive the same sync on application startup after the demo payroll catalog is provisioned. This does **not** run on employee update.
4. **Materialization trigger (v1 default):** On explicit **“Prepare period”** or **“Generate inputs”** API action for a `pay_period`, idempotently ensure rows exist for each active standing instruction whose effective range overlaps the period’s calendar range. **Alternative:** materialize on pay period creation — pick one in implementation and document in API notes.
5. **Idempotency:** Re-running materialization must **not** duplicate rows for the same (employee, pay_period, tenant_wage_component) unless the standing instruction changed; **must not** overwrite rows with **`manual_override` = true**.
6. **Amount computation:** If standing instruction uses quantity + rate, persisted **amount** on the period transaction = quantity × rate at materialization time (scale per company currency rules).
7. **Engine:** `DefaultPayrollEngine` / `SurinameCountryRuleProvider` consume **only** `tenant_wage_component_transaction` for variable inputs in v1.

---

## 8. Edge cases

- **Overlapping standing instructions** for the same employee and same component: **forbid** overlapping effective ranges while both active; validation error on save.
- **Mid-period hire:** Materialize only if employee’s hire_date (or status) qualifies for that period per [`payroll-org-structure.md`](./payroll-org-structure.md); proration rules **deferred to §10** unless HR provides a rule.
- **Deleted or inactive employee:** No new materialization; standing instructions ended or flagged inactive.
- **Pay period cancelled or reopened:** Define behavior for generated rows (delete vs orphan) in implementation notes when pay-period lifecycle exists.
- **Concurrent materialization:** Use transaction isolation or idempotent upsert to avoid duplicate rows.

---

## 9. UX considerations

- Employee-centric screen: list **standing instructions** and **current period transactions** with clear labels (recurring vs this period).
- Show **effective dates** and **component name/code**.
- When a generated row exists, indicate **source** (from standing instruction) if optional FK is implemented; otherwise show copy-only hint in docs.
- Errors: overlap, inactive component, missing amount/quantity+rate, company mismatch.

---

## 10. Open questions

- **Draft state:** Is v1 create-to-active only?
- **Optional `standing_instruction_id`** on period transaction for traceability and re-materialization — adopt in v1 or defer?
- **Proration** for partial periods and **calendar vs fiscal** alignment with Suriname caps (holiday/bonus annual maxima).
- **Employee attributes** for tax caps (e.g. children): extend `tenant_employee` (**PII / schema review**) vs separate dependent entity vs encode only in remarks (not recommended).
- **Overtime:** Single source vs [`work-times.md`](./work-times.md) vs manual transaction — avoid double counting.
- **Recurrence beyond** `EACH_PAY_PERIOD` (e.g. monthly in a biweekly company): deferred.

---

## 11. Acceptance criteria

| ID | Criterion |
|----|------------|
| AC-1 | Given an active standing instruction and eligible pay period, materialization creates exactly one **non-override** `tenant_wage_component_transaction` per (employee, period, component). |
| AC-2 | Re-running materialization does not change rows with **`manual_override` = true**. |
| AC-3 | Overlapping active standing instructions for the same employee + component are rejected with a validation error. |
| AC-4 | Standing instruction cannot reference a wage component from another company or tenant. |
| AC-5 | API integration tests cover create/update/end standing instruction and materialize-for-period **happy path** and **idempotency**. |
| AC-6 | Privilege checks enforce tenant isolation and chosen privilege codes (documented in API section when implemented). |

---

## Security (implementation note)

- Reuse tenant enforcement patterns from [`security.md`](./security.md).
- Privileges: **`EMPLOYEE_PAYROLL_STANDING_VIEW`** and **`EMPLOYEE_PAYROLL_STANDING_MANAGE`** (`DefinedPrivilege` + Liquibase `data-m19-employee-payroll-standing-privileges-1.xml`).
- **Default roles (clean DB):** **`ADMIN`** role template and demo tenant **Admin** role receive **VIEW + MANAGE**; demo **Viewer** receives **VIEW** only (`data-m19-role-template-employee-payroll-standing-privileges-1.xml` for template grants). New tenants copy templates on registration; default registration template is **ADMIN**.

---

## API (implemented)

**Privileges (Liquibase + `DefinedPrivilege`)**

| Code | Purpose |
|------|---------|
| `EMPLOYEE_PAYROLL_STANDING_VIEW` | List/get standing instructions and period wage component transactions |
| `EMPLOYEE_PAYROLL_STANDING_MANAGE` | Create/update/patch standing instructions, PUT period transactions, materialize for pay period |

**Tenant REST endpoints**

| Method | Path | Privilege |
|--------|------|-----------|
| GET | `/api/v1/payroll-standing-instructions?companyId=&employeeId=` | VIEW |
| GET | `/api/v1/payroll-standing-instructions/{id}` | VIEW |
| POST | `/api/v1/payroll-standing-instructions` | MANAGE |
| PUT | `/api/v1/payroll-standing-instructions/{id}` | MANAGE |
| PATCH | `/api/v1/payroll-standing-instructions/{id}` | MANAGE |
| GET | `/api/v1/wage-component-transactions?companyId=&payPeriodId=&employeeId=&page=&size=` | VIEW |
| PUT | `/api/v1/wage-component-transactions/{id}` | MANAGE |
| POST | `/api/v1/pay-periods/{payPeriodId}/materialize-payroll-inputs` body `{ "companyId": "<uuid>" }` | MANAGE |

**Materialization trigger (v1):** explicit POST `materialize-payroll-inputs` on a pay period (not on pay period creation).

**DTOs (records under `com.wagepayroll.api.dto`)**

- `TenantPayrollStandingInstructionCreateRequest`, `TenantPayrollStandingInstructionPutRequest`, `TenantPayrollStandingInstructionPatchRequest`, `TenantPayrollStandingInstructionRowDto`
- `TenantWageComponentTransactionPutRequest`, `TenantWageComponentTransactionRowDto`
- `TenantMaterializePayrollInputsRequest`, `TenantMaterializePayrollInputsResultDto`

**Representative errors (HTTP)**

| Status | Reason / detail |
|--------|-----------------|
| 400 | `INVALID_BODY`, `AMOUNT_OR_QUANTITY_RATE_REQUIRED`, `INACTIVE_WAGE_COMPONENT`, `WAGE_COMPONENT_COMPANY_MISMATCH`, `EFFECTIVE_TO_BEFORE_FROM`, `UNSUPPORTED_RECURRENCE`, `COMPANY_OR_EMPLOYEE_MISMATCH`, `PAY_PERIOD_COMPANY_MISMATCH` |
| 404 | `EMPLOYEE_NOT_FOUND`, standing instruction / transaction not found |
| 409 | `STANDING_INSTRUCTION_OVERLAP`, `INACTIVE_WAGE_COMPONENT_MATERIALIZATION` |

**Persistence**

- Table `tenant_employee_payroll_standing_instruction` — Liquibase `create-table-tenant-employee-payroll-standing-instruction.xml`
- Unique constraint `uq_twct_tenant_period_employee_component` on `tenant_wage_component_transaction` — same changelog file (no optional `standing_instruction_id` column in v1)

**Backend files**

| Area | Path |
|------|------|
| Entity / repo | `domain/payrollstanding/TenantEmployeePayrollStandingInstructionEntity.java`, `TenantEmployeePayrollStandingInstructionRepository.java` |
| Service | `payrollstanding/TenantPayrollPeriodInputService.java` |
| Controllers | `api/TenantPayrollStandingInstructionsController.java`, `api/TenantWageComponentTransactionsController.java`; materialize action on `api/TenantPayPeriodController.java` |
| Audit | `AuditResourceTypes.TENANT_EMPLOYEE_PAYROLL_STANDING_INSTRUCTION`, actions `EMPLOYEE_PAYROLL_STANDING_INSTRUCTION_*`, `PAYROLL_PERIOD_INPUTS_MATERIALIZED`, `TENANT_WAGE_COMPONENT_TRANSACTION_UPDATED` |
| Integration tests | `api/TenantEmployeePayrollStandingInstructionsIT.java` |

**Web**

- Route `/app/employee-payroll-inputs` — `frontend/src/app/app/employee-payroll-inputs/page.tsx`
- Client API helpers — `frontend/src/lib/api.ts` (fetch/create standing instructions, list transactions, materialize, PUT transaction)
- Navigation label key `nav.employee_payroll_inputs` — Liquibase `data-m19-employee-payroll-standing-nav-1.xml`
