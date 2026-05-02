# FEATURE payroll-org-structure Verification

## Scope

Full-stack verification (backend + web frontend) for payroll org structure:
- `tenant_company` → `/app/companies`
- `tenant_department` → `/app/departments`
- `tenant_job` → `/app/jobs`
- `tenant_employee_group` → `/app/employee-groups`
- `tenant_employee` → `/app/employees`

## Phase 1 — Backend (complete)

### Entities / repositories / service / controller
- `TenantCompanyEntity`, `TenantDepartmentEntity`, `TenantJobEntity`, `TenantEmployeeGroupEntity`, `TenantEmployeeEntity`
- `PayrollOrgService` — full CRUD with cross-tenant isolation + cross-entity FK validation
- `PayrollOrgController` — all CRUD endpoints, active-toggle, employee status PATCH
- Liquibase: `schema-m5-payroll-org-structure-1.xml`, `data-m5-payroll-org-privileges-1.xml`
- Privileges: `COMPANY_VIEW/MANAGE`, `DEPARTMENT_VIEW/MANAGE`, `JOB_VIEW/MANAGE`, `EMPLOYEE_GROUP_VIEW/MANAGE`, `EMPLOYEE_VIEW/MANAGE`

### Automated checks

- [x] `cd backend && ./mvnw.cmd test` → **217 tests, 0 failures**

## Phase 2 — Web Frontend (complete)

### Files created / modified
- `frontend/src/lib/api.ts` — API types + fetch functions for all 5 resources
- `frontend/src/messages/nav.ts` — nav key entries + ~200 UI label strings
- `frontend/src/app/app/companies/page.tsx` — CRUD list+modal, privilege `COMPANY_MANAGE`
- `frontend/src/app/app/departments/page.tsx` — CRUD list+modal, company filter, privilege `DEPARTMENT_MANAGE`
- `frontend/src/app/app/jobs/page.tsx` — CRUD list+modal, company+dept cascade, conditional salary fields, privilege `JOB_MANAGE`
- `frontend/src/app/app/employee-groups/page.tsx` — CRUD list+modal, company filter, privilege `EMPLOYEE_GROUP_MANAGE`
- `frontend/src/app/app/employees/page.tsx` — CRUD list+modal, cascading selectors, status modal, privilege `EMPLOYEE_MANAGE`

### Liquibase nav items
- `dml/data-m5-payroll-org-nav-1.xml` — inserts 5 `nav_menu_item` rows (IDs `50000000-…-0010` through `…-0014`)
- `NavigationMenuService.java` — fallback `defaultMenuWhenTenantRowsMissing` updated with all 5 routes

### Automated checks
- [x] `cd frontend && npx tsc --noEmit` → **0 errors**
- [x] `cd backend && ./mvnw.cmd test` → **217 tests, 0 failures** (includes updated `NavigationAndSettingsIT` counts)

## Manual smoke checks

1. Create company under tenant A → verify it appears in `/app/companies` list.
2. Create department under that company → verify company filter works.
3. Create job → verify company/department cascade selector.
4. Create employee group → confirm company filter.
5. Create employee → confirm cascading company→dept→job→group selectors.
6. Change employee status via the Status modal.
7. Deactivate an entity via the Activate/Deactivate toggle.
8. Attempt to create employee with department from a different company; expect API rejection.
9. Verify nav sidebar shows Companies, Departments, Jobs, Employee Groups, Employees for a user with VIEW privileges.
10. Verify privilege-restricted UI buttons are hidden without MANAGE privilege.

