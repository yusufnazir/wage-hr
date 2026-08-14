# Module: Payroll Organization Structure

Tenant-scoped organizational model for payroll operations with this hierarchy:

Tenant -> Company -> Department -> Job -> Employee

This module also introduces Employee Group as a non-structural employee classification layer.

---

## Objective

Provide a scalable, tenant-safe foundation for payroll organization data and API operations:
- Company as legal payroll entity and tax boundary
- Department and Job as structural hierarchy
- Employee as workforce record assigned to one structural chain
- Employee Group as independent classification dimension for reporting and payroll segmentation

---

## Scope

Included:
- Backend persistence model for:
  - Company
  - Department
  - Job
  - EmployeeGroup
  - Employee
- Tenant-safe REST APIs:
  - /companies
  - /departments
  - /jobs
  - /employee-groups
  - /employees
- Pagination, filtering, and sorting
- Validation and cross-company isolation rules

Excluded (this slice):
- Country-specific payroll calculation rules
- Payroll run execution
- Mobile UI implementation

---

## Actors

| Actor | Access | Description |
|---|---|---|
| Tenant payroll admin | Manage | Creates and maintains org/payroll structure |
| Tenant payroll operator | View/manage by privilege | Reads and updates employee/org records as allowed |
| SuperAdmin | Via same privilege enforcement path | Access only through audited elevation policy |

---

## Data Model (strict allowed columns)

### 1) Company (tenant_company)

- id (UUID, PK)
- tenant_id (UUID, required)
- name
- legal_name
- registration_number
- tax_id
- payroll_country (ISO-3166-1 alpha-2)
- currency (ISO-4217)
- payroll_frequency
- timezone
- date_format
- contact_email
- contact_phone
- address_line1
- address_line2
- city
- state_region
- postal_code
- country
- active
- created_at
- updated_at

Rules:
- Company belongs to one tenant
- tax_id unique per tenant

### 2) Department (tenant_department)

- id (UUID, PK)
- tenant_id (UUID, required)
- company_id (UUID, required)
- name
- code
- description
- parent_department_id (nullable)
- manager_employee_id (nullable)
- active
- created_at
- updated_at

Rules:
- Department belongs to one company
- code unique per company
- parent_department_id, when set, must reference department in same company and tenant
- manager_employee_id, when set, must reference employee in same company and tenant

### 3) Job (tenant_job)

- id (UUID, PK)
- tenant_id (UUID, required)
- company_id (UUID, required)
- department_id (UUID, required)
- title
- code
- description
- salary_type (HOURLY or MONTHLY)
- default_salary (nullable)
- default_hourly_rate (nullable)
- standard_hours_per_week (nullable)
- job_level (nullable)
- job_category (nullable)
- active
- created_at
- updated_at

Rules:
- Job belongs to one department
- code unique per company
- salary_type drives salary field validation

### 4) Employee Group (tenant_employee_group)

- id (UUID, PK)
- tenant_id (UUID, required)
- company_id (UUID, required)
- name
- code
- description
- active
- created_at
- updated_at

Rules:
- Employee Group belongs to one company
- code unique per company
- no parent/group hierarchy allowed
- no structural relationship to department tree

### 5) Employee (tenant_employee)

- id (UUID, PK)
- tenant_id (UUID, required)
- company_id (UUID, required)
- department_id (UUID, required)
- job_id (UUID, required)
- employee_group_id (UUID, optional — assign later when groups exist)
- badge_number (nullable, operator-assigned label, unique per company when set)
- first_name
- last_name
- id_number (nullable, national/passport ID — see PII classification §SE-1)
- gender (nullable, enum: MALE / FEMALE / OTHER / PREFER_NOT_TO_SAY)
- date_of_birth (nullable)
- place_of_birth (nullable)
- nationality (nullable, ISO-3166-1 alpha-2)
- civil_state (nullable, enum: SINGLE / MARRIED / DOMESTIC_PARTNERSHIP / DIVORCED / WIDOWED)
- hire_date
- resignation_date (nullable; last working day — drives active=false transition after final payroll)
- email
- phone
- address_street (nullable)
- address_number (nullable)
- address_city (nullable)
- address_postal_code (nullable)
- address_country (nullable, ISO-3166-1 alpha-2)
- status (enum: DRAFT | ACTIVE | ON_LEAVE | SUSPENDED | TERMINATED)
- active
- created_at
- updated_at

Rules:
- Employee belongs to exactly one company, department, and job; employee group is optional
- department and job must belong to same company and tenant; employee_group when set must belong to same company and tenant
- email unique per company when present
- badge_number unique per company when set (NULLs allowed; not unique among themselves)
- nationality and address_country, when set, must be valid ISO-3166-1 alpha-2 codes present in `platform_country`
- gender and civil_state, when set, must match the closed enums listed above
- resignation_date, when set, must be on or after hire_date
- **DRAFT** — onboarding in progress (create wizard). `active` is always `false`. `department_id`, `job_id`, and `hire_date` may be null until onboarding completes. Draft employees are **excluded from payroll materialization**, standing-instruction auto-provision, and default pay-period employee selection.
- Completing onboarding (`POST /employees/{id}/complete-onboarding`) validates employment fields, sets a non-draft status (default `ACTIVE`), sets `active` (default `true`), and provisions standing instructions.

### 6) Employee Compensation (tenant_employee_compensation)

One compensation record per employee (1:1). Stores the base wage configuration that drives payroll.

- id (UUID, PK)
- tenant_id (UUID, required)
- company_id (UUID, required; denormalized for tenant/company scoping)
- employee_id (UUID, required, unique — 1:1 with tenant_employee)
- currency_code (CHAR(3), ISO-4217, must exist in `platform_currency`)
- wage_type (enum: PER_HOUR / PER_PERIOD / PER_MONTH / PER_YEAR)
- wage_amount (DECIMAL(18,4), > 0)
- work_time_id (UUID, nullable; references tenant_work_time in same company/tenant)
- apply_taxes (boolean, default true)
- apply_tax_exempt (boolean, default false)
- apply_aov (boolean, default true)
- notes (VARCHAR(500), nullable)
- created_at
- updated_at

Rules:
- Exactly one compensation record per employee (unique on employee_id)
- work_time_id, when set, must belong to the same company and tenant as the employee
- currency_code must be active in `platform_currency`
- wage_amount must be strictly positive
- Derived equivalents (hourly / period / monthly / yearly) are **not** persisted — they are computed on read from wage_type, wage_amount, work_time hours/day × days/week, and company payroll_frequency
- Idempotent upsert via PUT (no separate create path)

---

## API

Base path: /api/v1
Tenant context from host/subdomain and enforced server-side.

### Companies
- GET /companies
- POST /companies
- GET /companies/{id}
- PUT /companies/{id}
- PATCH /companies/{id}/active

### Departments
- GET /departments?companyId={uuid}
- POST /departments
- GET /departments/{id}
- PUT /departments/{id}
- PATCH /departments/{id}/active

### Jobs
- GET /jobs?companyId={uuid}
- POST /jobs
- GET /jobs/{id}
- PUT /jobs/{id}
- PATCH /jobs/{id}/active

### Employee Groups
- GET /employee-groups?companyId={uuid}
- POST /employee-groups
- GET /employee-groups/{id}
- PUT /employee-groups/{id}
- PATCH /employee-groups/{id}/active

### Employees
- GET /employees?companyIds={uuid,uuid?}&departmentId={uuid?}&jobId={uuid?}&employeeGroupId={uuid?}&active={true|false}*&firstName={text?}&lastName={text?}
- POST /employees
- GET /employees/{id}
- PUT /employees/{id}
- DELETE /employees/{id} — hard-delete employee and related configuration rows; **always allowed for DRAFT**; blocked with **409** when payroll result lines, pay-period payments, or ledger postings exist (use deactivate instead)
- POST /employees/{id}/complete-onboarding — finalize a DRAFT employee (requires full employment; provisions standing instructions)
- PATCH /employees/{id}/status
- PATCH /employees/{id}/active

### Employee Compensation
- GET /employees/{id}/compensation — returns the compensation record plus computed derived rates (hourly / period / monthly / yearly); 404 when not yet set
- PUT /employees/{id}/compensation — idempotent upsert of the compensation record

Privileges: GET requires `EMPLOYEE_VIEW`, PUT requires `EMPLOYEE_MANAGE` (reuses the existing employee privilege pair — no new resource).

API behavior:
- list endpoints require pagination (page, size) and support sorting
- all list/query operations are tenant-scoped
- all company-scoped operations enforce company belongs to current tenant

### ER Diagram (textual)

Tenant
  1 -> * Company
Company
  1 -> * Department
  1 -> * Job (through Department)
  1 -> * EmployeeGroup
  1 -> * Employee
Department
  1 -> * Job
Job
  1 -> * Employee
EmployeeGroup
  1 -> * Employee

### Example payloads

Create company request:

```json
{
  "name": "Acme Suriname",
  "legalName": "Acme Suriname N.V.",
  "registrationNumber": "SR-REG-123",
  "taxId": "SR-TAX-99887",
  "payrollCountry": "SR",
  "currency": "SRD",
  "payrollFrequency": "MONTHLY",
  "timezone": "America/Paramaribo",
  "dateFormat": "yyyy-MM-dd",
  "contactEmail": "payroll@acme.sr",
  "contactPhone": "+5970000000",
  "active": true
}
```

Create employee request:

```json
{
  "companyId": "f14eb2e0-4a1f-4f91-a8e1-bf7e338e3ab9",
  "departmentId": "6be95e8e-6eb5-403f-9566-8f9ee64ea1ea",
  "jobId": "ec2e7be0-5b49-4576-a2c6-3e08abff8394",
  "employeeGroupId": "6f0559ec-20df-4e0f-ae23-7e0bdcb4af53",
  "firstName": "Asha",
  "lastName": "Ramdien",
  "hireDate": "2026-04-01",
  "email": "asha.ramdien@acme.sr",
  "status": "ACTIVE",
  "active": true
}
```

List response envelope:

```json
{
  "data": {
    "data": [],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 0,
      "totalPages": 0
    }
  },
  "meta": {
    "requestId": "tenant.company.listed"
  }
}
```

---

## Security

- Every query is scoped by tenant_id at repository/service level
- No cross-tenant reads/writes
- No cross-company linking via DB constraints and service validation
- Privileges required by resource:
  - COMPANY_VIEW / COMPANY_MANAGE
  - DEPARTMENT_VIEW / DEPARTMENT_MANAGE
  - JOB_VIEW / JOB_MANAGE
  - EMPLOYEE_GROUP_VIEW / EMPLOYEE_GROUP_MANAGE
  - EMPLOYEE_VIEW / EMPLOYEE_MANAGE

---

## Validation Rules

Company:
- name, legal_name, tax_id, payroll_country, currency, payroll_frequency, timezone, date_format required
- payroll_country must be ISO-2 uppercase
- currency must be ISO-3 uppercase

Department:
- company_id, name, code required
- parent and manager must be same tenant/company

Job:
- company_id, department_id, title, code, salary_type required
- salary_type in {HOURLY, MONTHLY}
- default_salary and default_hourly_rate are optional (null-safe); salary UI fields are deferred

Employee Group:
- company_id, name, code required
- no hierarchy fields

Employee:
- company_id, department_id, job_id, first_name, last_name, hire_date, status required
- employee_group_id optional; when set, referenced group must belong to same company and tenant

---

## Edge Cases

- Attempt to reference department/job/group from another company -> 400
- Attempt to use company from another tenant -> 404
- Duplicate codes inside a company -> 409
- Duplicate company tax_id in tenant -> 409
- Employee PATCH status with invalid status -> 400
- Department parent cycle prevention can be added in follow-up slice (current: disallow direct self-parent)

---

## Acceptance Criteria

1. Data model exists for all five entities with UUID keys and tenant scoping.
2. Company-level payroll and tax fields are persisted and validated.
3. Department -> Job -> Employee structural chain is enforced.
4. EmployeeGroup remains non-structural and independent from departments.
5. All REST routes are tenant-scoped and paginated on list endpoints.
6. Service and DB constraints prevent cross-company and cross-tenant linkage.
7. Privileges are seeded and enforced on endpoints.
8. Build and tests pass after integration.

---

## Web UI

All five resources (Companies, Departments, Jobs, Employee Groups, Employees) follow the **route-based CRUD** pattern from `docs/guides/WEB-THEMING-AND-DESIGN-SYSTEM.md` §8:

| Route | Purpose |
|-------|---------|
| `/app/companies` | List + company filter |
| `/app/companies/new` | Create form |
| `/app/companies/{id}/edit` | Edit form |
| `/app/departments` | List + company filter |
| `/app/departments/new` | Create form |
| `/app/departments/{id}/edit` | Edit form |
| `/app/jobs` | List + company filter |
| `/app/jobs/new` | Create form |
| `/app/jobs/{id}/edit` | Edit form |
| `/app/employee-groups` | List + company filter |
| `/app/employee-groups/new` | Create form |
| `/app/employee-groups/{id}/edit` | Edit form |
| `/app/employees` | Card list + multi-select company/status filters + name search |
| `/app/employees/new` | **Create wizard** (stepper): Personal information → Contact information → Employment → Compensation (incl. work time / hours setup) → Payment information → User account |
| `/app/employees/{id}/edit` | Edit form (tabbed: Employee / Employment / Compensation / Contact / Documents) |

### Employee create wizard (v1)

Route `/app/employees/new` is a **horizontal stepper**, not a single-page form:

1. **Personal information** — name, ID, demographics, badge (optional).
2. **Contact information** — email, phone, address.
3. **Employment** — company, department, job, optional employee group, hire date, target status after completion.
4. **Compensation** — wage, currency, **work time (hours setup)**, statutory toggles. Optional; saved when continuing if any compensation fields are filled.
5. **Payment information** — payment destinations panel (requires `EMPLOYEE_PAYMENT_VIEW`; same as edit tab).
6. **User account** — optional toggle **Create user account for this employee** (default off). When enabled on **Finish**, requires contact email. If `user_id` is already set, skip. Otherwise resolve or create `user_account`, add tenant membership + **Employee** role, set `tenant_employee.user_id`. **New users** receive `EMPLOYEE_ACCOUNT_ACTIVATION` email with activation link (`/activate-account?token=…`) to set password. **Existing users** receive `EMPLOYEE_ACCOUNT_LINKED` (no activation link). Activation: `POST /api/v1/auth/employee-account/activate` (anonymous, CSRF-exempt).

**Draft persistence:** Each **Next** saves the employee with `status=DRAFT` and `active=false` (create on first save, then PUT). Draft rows appear on the employee list with **Continue setup**. Resume via `/app/employees/new?draft={id}`. **Finish** calls `POST /employees/{id}/complete-onboarding`, which activates the employee for payroll.

Users may navigate back to completed steps. Incomplete drafts cannot participate in payroll or receive standing instructions until onboarding is finished.

### Feedback and confirmation rules

Follow `docs/guides/WEB-THEMING-AND-DESIGN-SYSTEM.md` §9 for all mutating actions:

- **Create / update:** show a success toast on redirect back to the list page.
- **Toggle active/inactive (soft delete):** show a **confirmation dialog** before calling the API. Dialog title: `"Deactivate {resource}?"`. After confirmation, show a success toast and refresh the list.
- **Hard delete (if introduced):** always confirm with a destructive-styled dialog; show a success toast after.
- **Employee delete:** list cards and edit profile expose **Delete** (`DELETE /employees/{id}`) with confirmation. Draft employees can always be removed. Completed employees with payroll history receive **409** — use **Deactivate** instead.
- Error conditions: display an inline error or error toast; never silently fail.

### Privilege-gated UI

- List pages visible to any user with `{RESOURCE}_VIEW`.
- New / Edit buttons shown only when `{RESOURCE}_MANAGE` is present in `me.privileges`.
- Toggle active button shown only to `{RESOURCE}_MANAGE` holders.

---

## Proposed Schema Extension (requires PII review)

The following extensions were applied to support a richer employee details view and to enable payroll compensation (`PROD-CONTEXT`: payroll cannot run without a base wage configuration). All entries below are reflected in the strict allowed-column lists above; this section records the justification, PII classification, and retention impact required by `docs/guides/DATA-MODEL-STANDARDS.md`.

### SE-1 — Personal and address fields on `tenant_employee`

- **Table / entity:** `tenant_employee` / `TenantEmployeeEntity`
- **Proposed column(s):**
  - `badge_number` (VARCHAR(64), nullable, unique per company)
  - `id_number` (VARCHAR(64), nullable)
  - `gender` (VARCHAR(32), nullable, closed enum)
  - `place_of_birth` (VARCHAR(120), nullable)
  - `nationality` (CHAR(2), nullable, ISO-3166-1 alpha-2)
  - `civil_state` (VARCHAR(32), nullable, closed enum)
  - `resignation_date` (DATE, nullable)
  - `address_street` (VARCHAR(160), nullable)
  - `address_number` (VARCHAR(32), nullable)
  - `address_city` (VARCHAR(120), nullable)
  - `address_postal_code` (VARCHAR(32), nullable)
  - `address_country` (CHAR(2), nullable, ISO-3166-1 alpha-2)
- **Justification:** Tax filings, social-premium reporting (AOV/AVBZ), and bank pay-out templates require legally identifying personal data (ID number, civil state) and residential address. Badge number provides the human-friendly operator label used across paper artefacts and timekeeping devices. Resignation date is required to drive the active-state lifecycle (final payroll triggers `active=false`).
- **PII classification:**
  - `id_number` → **sensitive** (strong identifier; treat under same retention as tax-relevant payroll records)
  - `address_street`, `address_number`, `address_city`, `address_postal_code`, `address_country` → **sensitive** (residential locator)
  - `place_of_birth`, `gender`, `nationality`, `civil_state`, `resignation_date`, `badge_number` → **low** (categorical / non-locating / operator label)
- **Retention / deletion / anonymization impact:** Lifecycle follows the parent employee record per `docs/modules/data-lifecycle.md` — when an employee is anonymized or hard-deleted, all of these columns must be cleared in the same operation. No new bespoke retention rule; the `tenant_employee` purge path scrubs the new sensitive columns alongside `first_name`, `last_name`, `email`. Audit-trail copies remain redacted per existing audit-redaction policy.
- **Liquibase changeset id (applied):**
  - `db/changelog/ddl/alter-table-tenant-employee-personal-details.xml`
  - `db/changelog/dml/data-m21-demo-tenant-employee-badges-1.xml` (demo backfill only)

### SE-2 — New table `tenant_employee_compensation`

- **Table / entity:** `tenant_employee_compensation` / `TenantEmployeeCompensationEntity`
- **Proposed column(s):** see §6 above for the strict allowed list.
- **Justification:** Payroll cannot run without a base wage configuration per employee (wage type, amount, currency, work-time reference). A separate 1:1 table keeps PII-sensitive compensation isolated from the employee master record (independent privilege gating in the future, simpler audit slicing) and lets derived rates remain non-persisted (computed on read). Tax / premium toggles are stored here so the same employee can move between premium regimes without losing wage history.
- **PII classification:** **sensitive** for the whole table — salary data is one of the most heavily-protected personal data categories under Suriname labour and tax legislation. Access is restricted to holders of `EMPLOYEE_MANAGE` (manage) / `EMPLOYEE_VIEW` (read), inheriting the existing employee privilege pair.
- **Retention / deletion / anonymization impact:** Row lifecycle is bound 1:1 to the parent employee. When the employee record is anonymized, the compensation row must be deleted (not anonymized — there is no operational reason to retain wage detail past employee anonymization). Historical wage values needed for legal retention are captured separately by the payroll-result audit trail (`docs/modules/employee-periodic-payroll-transactions.md`), not by this configuration row.
- **Liquibase changeset id (applied):**
  - `db/changelog/ddl/create-table-tenant-employee-compensation.xml`
  - `db/changelog/dml/data-m22-demo-tenant-employee-compensation-1.xml` (demo seed only)

### Follow-ups

- Wire the `tenant_employee` purge path to scrub the new sensitive columns and to delete the matching `tenant_employee_compensation` row in the same transaction. Tracked under `docs/modules/data-lifecycle.md`.
- Re-evaluate whether compensation deserves its own privilege pair (`COMPENSATION_VIEW` / `COMPENSATION_MANAGE`) once HR-vs-payroll separation-of-duties is in scope; for now the existing `EMPLOYEE_*` pair applies.
