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
- Web UI implementation

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
- employee_group_id (UUID, required)
- first_name
- last_name
- date_of_birth
- hire_date
- email
- phone
- status
- active
- created_at
- updated_at

Rules:
- Employee belongs to exactly one company, department, job, and employee group
- department, job, employee_group must all belong to same company and tenant
- email unique per company when present

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
- GET /employees?companyId={uuid}&departmentId={uuid?}&jobId={uuid?}&employeeGroupId={uuid?}&status={status?}
- POST /employees
- GET /employees/{id}
- PUT /employees/{id}
- PATCH /employees/{id}/status
- PATCH /employees/{id}/active

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
- MONTHLY requires default_salary > 0
- HOURLY requires default_hourly_rate > 0

Employee Group:
- company_id, name, code required
- no hierarchy fields

Employee:
- company_id, department_id, job_id, employee_group_id, first_name, last_name, hire_date, status required
- referenced records must belong to same company and tenant

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

## Proposed Schema Extension (requires PII review)

None in this slice.
