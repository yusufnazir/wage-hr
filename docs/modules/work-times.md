# Module: Work Times

**Feature slug:** `work-times`
**Related:** [`payroll-org-structure.md`](./payroll-org-structure.md) (`tenant_company` — work times are scoped to a company), [`security.md`](./security.md), [`audit.md`](./audit.md)

---

## 1. Objective

Allow a company to define one or more **work time schedules** that represent the standard working pattern used for employees (e.g. 8.00 hours/day, 5 days/week). This provides a structured baseline for payroll/time calculations and later features such as shifts and attendance.

---

## 2. Scope

**Included:**
- Tenant-scoped CRUD for `tenant_work_time` records, scoped to a company
- Activate / deactivate (soft flag; no hard delete)
- Privilege enforcement: `WORK_TIME_VIEW`, `WORK_TIME_MANAGE`
- Backend persistence + REST API
- Tenant web UI: list per company, create, edit, activate/deactivate
- Liquibase DDL + DML (privileges, navigation)

**Excluded:**
- Shifts (a subsequent feature)
- Employee assignment rules beyond `company_id` scoping (future)
- Time & attendance tracking, clock-ins, overtime calculations (future)
- Mobile UI

---

## 3. Actors

| Actor | Privilege | Capability |
|---|---|---|
| Tenant Payroll Admin | `WORK_TIME_MANAGE` | Create, edit, activate/deactivate work times |
| Tenant Payroll Operator | `WORK_TIME_VIEW` | View work times (read-only) |
| SuperAdmin (tenant context) | All, via same enforcement path | Access only through audited privilege enforcement — no bypass |

---

## 4. User Flows

### 4.1 List Work Times

1. User navigates to **Work Times**.
2. System displays a paginated list of work times for a selected company.
3. Each row shows: Name, Code, Hours/Day, Days/Week, Status (Active/Inactive), Actions.
4. List defaults to Active only; a toggle reveals inactive records.

### 4.2 Create a Work Time

1. User clicks **+ New work time**.
2. Form fields: Company, Name, Code, Hours per day, Work days per week, Description (optional), Active.
3. On submit: server validates required fields and constraints → inserts record → audit event `WORK_TIME_CREATED` → list refreshes.

### 4.3 Edit a Work Time

1. User clicks **Edit**.
2. Form pre-populates existing values.
3. On submit: record updated → audit event `WORK_TIME_UPDATED`.

### 4.4 Activate / Deactivate

1. User clicks **Deactivate** (or **Activate**) on a row.
2. Confirmation dialog.
3. On confirm: `active` flag toggled → audit `WORK_TIME_DEACTIVATED` or `WORK_TIME_ACTIVATED`.

---

## 5. Data Model

### Table: `tenant_work_time` (strict allowed columns)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Generated |
| `tenant_id` | UUID | NOT NULL, FK → `tenant.id` | Tenant isolation boundary |
| `company_id` | UUID | NOT NULL, FK → `tenant_company.id` | Company this schedule belongs to |
| `name` | VARCHAR(120) | NOT NULL | Trimmed; 1–120 chars |
| `code` | VARCHAR(40) | NOT NULL | Unique per company; trimmed |
| `hours_per_day` | DECIMAL(4,2) | NOT NULL | E.g. `8.00` |
| `work_days_per_week` | INT | NOT NULL | Expected range 0–7 |
| `description` | VARCHAR(500) | NULLABLE | Optional |
| `active` | BOOLEAN | NOT NULL, DEFAULT true | Soft flag |
| `created_at` | TIMESTAMP | NOT NULL | Set on insert |
| `updated_at` | TIMESTAMP | NOT NULL | Set on insert/update |

**Indexes / constraints (as shipped):**
- Unique index on `(company_id, code)` (`uidx_tenant_work_time_company_code`)
- Index on `(tenant_id, company_id)` (`idx_tenant_work_time_tenant_company`)

---

## 6. Business Rules

| # | Rule |
|---|---|
| BR-1 | `code` is unique per company (case-insensitive trim). |
| BR-2 | `hours_per_day` must be > 0 and realistically bounded (UI should constrain; server validates). |
| BR-3 | `work_days_per_week` must be within 0..7. |
| BR-4 | Work times are tenant + company scoped; cross-tenant and cross-company access is not allowed. |

---

## 7. Acceptance Criteria

| ID | Criterion |
|---|---|
| AC-1 | A user with `WORK_TIME_MANAGE` can create a work time; it appears active in the list. |
| AC-2 | A user with `WORK_TIME_VIEW` can list work times, but create/update/activate/deactivate returns HTTP 403. |
| AC-3 | `code` uniqueness is enforced per company; duplicates are rejected. |
| AC-4 | Activating/deactivating toggles visibility in the default active list. |
| AC-5 | A work time belonging to another tenant or company cannot be accessed (404/forbidden per API conventions). |

---

## 8. API Reference

### Base path
```
/api/v1/work-times
```

### Endpoints

| Method | Path | Privilege | Description |
|--------|------|-----------|-------------|
| `GET` | `/` | `WORK_TIME_VIEW` | List. Params: `companyId` (optional), `page`, `size`, `sort`, `active` |
| `GET` | `/{id}` | `WORK_TIME_VIEW` | Get one by id |
| `POST` | `/` | `WORK_TIME_MANAGE` | Create → 201 |
| `PUT` | `/{id}` | `WORK_TIME_MANAGE` | Update |
| `PATCH` | `/{id}/active` | `WORK_TIME_MANAGE` | Activate/deactivate (boolean body) |

