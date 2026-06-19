# Module: Pay Periods

**Feature slug:** `pay-periods`
**Related:** [`payroll-org-structure.md`](./payroll-org-structure.md) (`tenant_company` — pay periods are scoped to a company), [`security.md`](./security.md), [`audit.md`](./audit.md)

---

## 1. Objective

Allow a company to define and manage **pay periods** (date ranges, year, and lifecycle status). Pay periods are the time-box foundation for payroll processing, and they support creating **pay period runs** (interim/final) as the execution unit in later milestones.

---

## 2. Scope

**Included:**
- Tenant-scoped CRUD for `tenant_pay_period` records, scoped to a company
- Status transitions via `PATCH /status`
- Listing and creating pay period runs (`tenant_pay_period_run`)
- Generate endpoint to create pay periods from company payroll frequency rules (`POST /companies/{id}/pay-periods/generate`)
- Privilege enforcement:
  - `PAY_PERIOD_VIEW`, `PAY_PERIOD_MANAGE`
  - `PAY_PERIOD_RUN_VIEW`, `PAY_PERIOD_RUN_MANAGE`
- Backend persistence + REST API
- Tenant web UI: list, filters, create/edit, status change, runs panel, generate dialog
- Liquibase DDL + DML (privileges, navigation)

**Excluded:**
- Full payroll calculation/execution (engine phases — see [`../product/PAYROLL-ENGINE-DOCS-INDEX.md`](../product/PAYROLL-ENGINE-DOCS-INDEX.md))
- Locking rules for downstream entities (future)
- Mobile UI

**Included (partial):**
- `POST /pay-periods/{id}/formula-preview` — engine preview (`items`, `employeeBaseTotals`); extended in Phases 2+5 per engine specs

---

## 3. Actors

| Actor | Privilege | Capability |
|---|---|---|
| Tenant Payroll Admin | `PAY_PERIOD_MANAGE` / `PAY_PERIOD_RUN_MANAGE` | Create/update pay periods, change status, create runs |
| Tenant Payroll Operator | `PAY_PERIOD_VIEW` / `PAY_PERIOD_RUN_VIEW` | View pay periods and runs |
| SuperAdmin (tenant context) | All, via same enforcement path | Access only through audited privilege enforcement — no bypass |

---

## 4. Concepts

### 4.1 Pay period lifecycle (status)

Current statuses used by UI/API:
- `READY`
- `OPEN`
- `CLOSED`

Status change is performed via `PATCH /api/v1/pay-periods/{id}/status`.

### 4.2 Pay period runs

A pay period can have multiple runs (e.g. interim corrections, then final). Runs are uniquely numbered per pay period (`run_number`).

---

## 5. User Flows

### 5.1 List Pay Periods

1. User navigates to **Pay Periods**.
2. System displays a paginated list with filters:
   - company (optional)
   - year (optional)
   - status (optional)
3. Rows show year, start/end, status, company, and runs panel.

### 5.2 Create / Edit Pay Period

1. User clicks **New pay period** (or Edit).
2. Fields: Company, Year, Start date, End date, Status.
3. On save: record created/updated; audited; list refreshes.

### 5.3 Change Pay Period Status

1. User selects a new status from the row control.
2. System patches status and refreshes list.

### 5.4 Manage Runs

1. User expands runs for a pay period.
2. User creates a run (`INTERIM` or `FINAL`), which is assigned the next run number.

### 5.5 Generate Pay Periods from Company

1. User opens **Regenerate pay periods**.
2. Select company, year, optional “from period”, years-ahead.
3. System calls `POST /api/v1/companies/{companyId}/pay-periods/generate` and shows how many records were created.

---

## 6. Data Model

### Table: `tenant_pay_period` (strict allowed columns)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Generated |
| `tenant_id` | UUID | NOT NULL, FK → `tenant.id` | Tenant isolation boundary |
| `company_id` | UUID | NOT NULL, FK → `tenant_company.id` | Company this period belongs to |
| `year` | INT | NOT NULL | Calendar year |
| `start_date` | DATE | NOT NULL | Inclusive |
| `end_date` | DATE | NOT NULL | Inclusive |
| `status` | VARCHAR(20) | NOT NULL | `READY` / `OPEN` / `CLOSED` |
| `created_at` | TIMESTAMP | NOT NULL | Set on insert |
| `updated_at` | TIMESTAMP | NOT NULL | Set on insert/update |

### Table: `tenant_pay_period_run` (strict allowed columns)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Generated |
| `tenant_id` | UUID | NOT NULL, FK → `tenant.id` | Tenant isolation boundary |
| `pay_period_id` | UUID | NOT NULL, FK → `tenant_pay_period.id` | Parent pay period |
| `run_type` | VARCHAR(20) | NOT NULL | `INTERIM` / `FINAL` |
| `run_number` | INT | NOT NULL | Unique per pay period |
| `created_at` | TIMESTAMP | NOT NULL | |
| `updated_at` | TIMESTAMP | NOT NULL | |

**Indexes / constraints (as shipped):**
- Index on `(tenant_id, company_id, year)` (`idx_tenant_pay_period_tenant_company_year`)
- Unique on `(pay_period_id, run_number)` (`uidx_tenant_pay_period_run_period_number`)

---

## 7. Business Rules

| # | Rule |
|---|---|
| BR-1 | Pay periods are tenant + company scoped. |
| BR-2 | `start_date` must be <= `end_date`. |
| BR-3 | Overlapping pay periods for the same company should be prevented (server validation). |
| BR-4 | `run_number` increments per pay period and is unique within that period. |

---

## 8. Acceptance Criteria

| ID | Criterion |
|---|---|
| AC-1 | A user with `PAY_PERIOD_MANAGE` can create/edit pay periods. |
| AC-2 | A user with `PAY_PERIOD_VIEW` can list and view details but cannot write (403). |
| AC-3 | Status can be patched with `PAY_PERIOD_MANAGE`. |
| AC-4 | A user with `PAY_PERIOD_RUN_MANAGE` can create runs; run numbers increment. |
| AC-5 | A user with `PAY_PERIOD_RUN_VIEW` can list runs but cannot create (403). |
| AC-6 | Generate endpoint creates pay periods for a company (returns created count). |

---

## 9. API Reference

### Base paths
```
/api/v1/pay-periods
/api/v1/pay-period-runs
```

### Endpoints — pay periods

| Method | Path | Privilege | Description |
|--------|------|-----------|-------------|
| `GET` | `/api/v1/pay-periods` | `PAY_PERIOD_VIEW` | List. Params: `companyId`, `year`, `status`, `page`, `size`, `sort` |
| `GET` | `/api/v1/pay-periods/{id}` | `PAY_PERIOD_VIEW` | Get one by id |
| `POST` | `/api/v1/pay-periods` | `PAY_PERIOD_MANAGE` | Create → 201 |
| `PUT` | `/api/v1/pay-periods/{id}` | `PAY_PERIOD_MANAGE` | Update |
| `PATCH` | `/api/v1/pay-periods/{id}/status` | `PAY_PERIOD_MANAGE` | Patch status |
| `GET` | `/api/v1/pay-periods/{id}/runs` | `PAY_PERIOD_RUN_VIEW` | List runs for one pay period |

### Endpoints — pay period runs

| Method | Path | Privilege | Description |
|--------|------|-----------|-------------|
| `GET` | `/api/v1/pay-period-runs/{id}` | `PAY_PERIOD_RUN_VIEW` | Get run |
| `POST` | `/api/v1/pay-period-runs` | `PAY_PERIOD_RUN_MANAGE` | Create run → 201 |

### Generate

| Method | Path | Privilege | Description |
|--------|------|-----------|-------------|
| `POST` | `/api/v1/companies/{companyId}/pay-periods/generate` | `PAY_PERIOD_MANAGE` | Generate pay periods (returns created count) |

### Payroll engine (shipped / planned)

| Method | Path | Privilege | Status |
|--------|------|-----------|--------|
| `POST` | `/api/v1/pay-periods/{id}/formula-preview` | `PAY_PERIOD_VIEW` | **Shipped** — `items`, `employeeBaseTotals` |
| `POST` | `/api/v1/pay-periods/{id}/materialize-payroll-inputs` | `EMPLOYEE_PAYROLL_STANDING_MANAGE` | **Shipped** |
| `POST` | `/api/v1/pay-periods/{periodId}/runs/{runId}/finalize` | `PAY_PERIOD_RUN_MANAGE` | **Shipped** — persists `tenant_payroll_result_line`; 409 `RUN_ALREADY_FINALIZED` on duplicate |
| `GET` | `/api/v1/pay-period-runs/{runId}/result-lines` | `PAY_PERIOD_RUN_VIEW` | **Shipped** — optional `employeeId` filter |
| `GET` | `/api/v1/employees/{id}/payroll-ytd?taxYear=` | `PAY_PERIOD_VIEW` | **Shipped** Phase 5 — YTD accumulator rows |

**Formula preview response:** `componentSource` (`TENANT` \| `PLATFORM`), optional `platformWageComponentId` (Phase 2), and `employeeNetPay` map per employee (Phase 5). Finalize response includes the same `employeeNetPay` map on `item`.

