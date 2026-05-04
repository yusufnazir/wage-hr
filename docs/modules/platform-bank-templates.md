# Module: Platform Bank Templates

**Feature slug:** `platform-bank-templates`
**Milestone:** M5
**Related:** [`platform-countries.md`](./platform-countries.md) (`platform_country.payroll_enabled` — only payroll-enabled countries are eligible for bank templates), [`payroll-org-structure.md`](./payroll-org-structure.md) (`tenant_company.payroll_country` — triggers the copy), [`platform-settings.md`](./platform-settings.md) (operator role pattern), [`security.md`](./security.md) (`platform_superadmin`), [`audit.md`](./audit.md)

---

## Implementation Snapshot (M11)

Implemented backend + web (mobile excluded) in this repository.

- Backend API and services:
  - `backend/src/main/java/com/wagepayroll/api/PlatformBankTemplatesController.java`
  - `backend/src/main/java/com/wagepayroll/api/TenantBankTemplatesController.java`
  - `backend/src/main/java/com/wagepayroll/banktemplate/PlatformBankTemplateService.java`
  - `backend/src/main/java/com/wagepayroll/banktemplate/TenantBankTemplateService.java`
  - `backend/src/main/java/com/wagepayroll/banktemplate/BankTemplateCopyService.java` (invoked from `TenantPayrollOrgService.createCompany`)
- Backend persistence:
  - `backend/src/main/java/com/wagepayroll/domain/banktemplate/PlatformBankTemplateEntity.java`
  - `backend/src/main/java/com/wagepayroll/domain/banktemplate/TenantBankTemplateEntity.java`
  - `backend/src/main/java/com/wagepayroll/domain/banktemplate/PlatformBankTemplateRepository.java`
  - `backend/src/main/java/com/wagepayroll/domain/banktemplate/TenantBankTemplateRepository.java`
- Liquibase:
  - `backend/src/main/resources/db/changelog/ddl/schema-m11-platform-bank-templates-1.xml`
  - `backend/src/main/resources/db/changelog/dml/data-m11-platform-bank-template-privileges-1.xml`
  - `backend/src/main/resources/db/changelog/dml/data-m11-platform-bank-templates-seed-1.xml`
  - `backend/src/main/resources/db/changelog/dml/data-m11-bank-templates-nav-1.xml`
  - `backend/src/main/java/com/wagepayroll/liquibase/task/DdlM11PlatformBankTemplates1.java`
  - `backend/src/main/java/com/wagepayroll/liquibase/task/DataM11PlatformBankTemplatePrivileges1.java`
  - `backend/src/main/java/com/wagepayroll/liquibase/task/DataM11PlatformBankTemplatesSeed1.java`
- Web (Next.js):
  - `frontend/src/app/app/platform-bank-templates/page.tsx`, `new/page.tsx`, `[id]/edit/page.tsx`
  - `frontend/src/app/app/bank-templates/page.tsx`, `[id]/edit/page.tsx`
  - `frontend/src/lib/api.ts`
  - `frontend/src/messages/nav.ts`
  - `backend/src/main/java/com/wagepayroll/api/NavigationController.java` (platform sidebar item)
- Test coverage:
  - `backend/src/test/java/com/wagepayroll/api/PlatformBankTemplatesIT.java`
  - `backend/src/test/java/com/wagepayroll/api/TenantBankTemplatesIT.java`

---

## 1. Objective

Provide a **platform-managed global catalog of bank templates per country** that defines standard bank and account information formats used for payroll disbursement. When a **company is created** inside a tenant, the system automatically **copies all active platform bank templates** for the company's `payroll_country` into the tenant's own bank template collection (scoped to that company). Tenant admins can then view and customize their copies as needed.

Platform SuperAdmin manages the catalog. Tenant users manage their company-scoped copies.

---

## 2. Scope

**Included:**
- Full CRUD for `platform_bank_template` records, restricted to platform superadmin
- Activate / deactivate flag on platform templates (soft-disable; no hard delete once referenced)
- Liquibase DDL changesets (both tables + indexes)
- Liquibase DML seed: initial bank templates for Suriname (`SR`) — the first full payroll country
- Liquibase DML: `BANK_TEMPLATE_VIEW` and `BANK_TEMPLATE_MANAGE` privileges
- **Automatic copy** on company create: when `POST /api/v1/tenant/companies` succeeds, all active `platform_bank_template` rows for `payroll_country` are copied into `tenant_bank_template` (scoped to `tenant_id` + `company_id`)
- Tenant-facing API: list, view, and update (customise) their own `tenant_bank_template` records
- Privilege enforcement (`BANK_TEMPLATE_VIEW`, `BANK_TEMPLATE_MANAGE`) on tenant endpoints
- Audit logging (platform create/update/activate/deactivate; tenant update)
- Superadmin web UI: list (paginated, filterable by country) + create/edit/activate/deactivate
- Tenant web UI: list per company, edit (customise) own templates

**Excluded:**
- Mobile (web + backend only for this milestone)
- Bank template validation of actual employee bank account numbers (separate payroll engine concern)
- Live bank account connectivity or disbursement execution
- Tenant-created bank templates from scratch (tenant admins may only customise platform-seeded copies)
- Cross-company bank template sharing within a tenant
- Automatic re-sync of tenant copies when a platform template is updated (tenant copies are independent after initial copy)

---

## 3. Actors

| Actor | Access | Capability |
|---|---|---|
| Platform SuperAdmin | `platform_superadmin = true` | Full CRUD, activate/deactivate on `platform_bank_template` |
| Tenant Admin | `BANK_TEMPLATE_MANAGE` | Update (customise) their company's `tenant_bank_template` records |
| Tenant Staff | `BANK_TEMPLATE_VIEW` | View their company's `tenant_bank_template` records |
| SuperAdmin (tenant context) | All, via same enforcement path | Access only through audited privilege enforcement — no bypass |

---

## 4. User Flows

### 4.1 SuperAdmin — List Platform Bank Templates

1. SuperAdmin navigates to **Platform → Bank Templates** in the platform admin sidebar.
2. System displays a paginated table of all platform bank templates, sorted by `country_code` ASC, then `name` ASC.
3. A filter allows narrowing by country (ISO alpha-2 code).
4. An **Active only / All** toggle defaults to **All** for superadmin.
5. Each row shows: Country, Name, Bank Name, SWIFT/BIC, Bank Code, Status badge (Active / Inactive), Actions (Edit, Activate/Deactivate).

### 4.2 SuperAdmin — Create Platform Bank Template

1. SuperAdmin clicks **+ Add Bank Template**.
2. A form opens with fields: Country (required, dropdown of `payroll_enabled` platform countries), Name (required), Bank Name (optional), SWIFT/BIC (optional), Bank Code (optional), Account Number Format (optional), Active (default: true).
3. On submit, system validates required fields and format rules.
4. On success: template appears in the list; audit event recorded.
5. On error: inline field-level errors shown.

### 4.3 SuperAdmin — Edit Platform Bank Template

1. SuperAdmin clicks **Edit** on a template row.
2. Form pre-populates. All fields except `country_code` and `id` are editable.
3. `country_code` is **read-only** after creation (changing country would invalidate the classification).
4. On success: list refreshes; audit event recorded.

### 4.4 SuperAdmin — Deactivate / Activate

1. SuperAdmin clicks **Deactivate** (or **Activate**) on a template row.
2. Confirmation dialog: *"Deactivate '[Name]'? Future company creations for [Country] will not receive a copy of this template."*
3. On confirm: `active` flag toggled; audit event recorded.
4. Deactivated templates are **not copied** to new companies but remain visible to superadmin with an "Inactive" badge.
5. Existing tenant copies are **not affected** by later deactivation of the source template.

### 4.5 Tenant Admin — View Company Bank Templates

1. Tenant admin navigates to **Settings → Bank Templates** within a company context.
2. System displays a paginated list of `tenant_bank_template` records for the selected company.
3. Records are pre-populated from the platform catalog at company creation time.
4. Each row shows: Name, Bank Name, SWIFT/BIC, Bank Code, Account Number Format, Active status, Actions.

### 4.6 Tenant Admin — Edit (Customise) a Tenant Bank Template

1. Tenant admin clicks **Edit** on a template row.
2. Form pre-fills with current values. `country_code` and `platform_bank_template_id` are read-only.
3. Editable fields: `name`, `bank_name`, `swift_bic`, `bank_code`, `account_number_format`, `active`.
4. On success: list refreshes; audit event `TENANT_BANK_TEMPLATE_UPDATED` recorded.

### 4.7 Copy on Company Create (internal — no user action)

1. When `POST /api/v1/tenant/companies` succeeds (transaction commits), `BankTemplateCopyService.copyForCompany(tenantId, companyId, payrollCountry)` is called within the same transaction.
2. All `platform_bank_template` rows where `country_code = payrollCountry` AND `active = true` are fetched.
3. For each, a corresponding `tenant_bank_template` row is inserted with the same field values plus `tenant_id`, `company_id`, and `platform_bank_template_id`.
4. If no platform templates exist for the country, no rows are inserted — not an error.
5. This operation is **idempotent by design**: a company is only created once; no re-copy mechanism in v1.

---

## 5. Data Model

### Table: `platform_bank_template` (strict)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Generated |
| `country_code` | CHAR(2) | NOT NULL | ISO 3166-1 alpha-2 (e.g. `SR`). Stored uppercase. **Immutable after create.** Must reference an active `platform_country.iso_alpha2` with `payroll_enabled = true`. |
| `name` | VARCHAR(150) | NOT NULL | Template name (e.g. "Standard Bank Transfer — Hakrinbank"). Trimmed; 1–150 chars. |
| `bank_name` | VARCHAR(150) | NULLABLE | Full bank name (e.g. "Hakrinbank N.V."). Trimmed; max 150 chars. |
| `swift_bic` | VARCHAR(11) | NULLABLE | SWIFT/BIC code. When provided must match `^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$`. |
| `bank_code` | VARCHAR(30) | NULLABLE | Local bank identifier (country-specific). Trimmed; max 30 chars. |
| `account_number_format` | VARCHAR(100) | NULLABLE | Descriptive pattern or regex hint for account number validation (e.g. `^\d{10}$`). Max 100 chars. |
| `active` | BOOLEAN | NOT NULL, DEFAULT true | When false: excluded from future company-creation copies; visible in superadmin list only. |
| `created_at` | TIMESTAMP | NOT NULL | Set on insert. |
| `updated_at` | TIMESTAMP | NOT NULL | Set on insert and update. |

**Indexes:**
- Index on `(country_code, active)` for efficient copy-on-create lookups.
- Index on `country_code`.

> **No PII** — this table contains only banking reference data.

---

### Table: `tenant_bank_template` (strict)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Generated |
| `tenant_id` | UUID | NOT NULL, FK → `tenant.id` | Set from `TenantContext`. |
| `company_id` | UUID | NOT NULL, FK → `tenant_company.id` | The company for which this template was copied. |
| `platform_bank_template_id` | UUID | NULLABLE, FK → `platform_bank_template.id` | Source template; NULL if created manually in a future iteration. In v1 all copies are platform-sourced. |
| `country_code` | CHAR(2) | NOT NULL | Copied from source; read-only. ISO alpha-2 uppercase. |
| `name` | VARCHAR(150) | NOT NULL | Editable by tenant admin. Trimmed; 1–150 chars. |
| `bank_name` | VARCHAR(150) | NULLABLE | Editable. Same validation as platform table. |
| `swift_bic` | VARCHAR(11) | NULLABLE | Editable. Same format validation as platform table. |
| `bank_code` | VARCHAR(30) | NULLABLE | Editable. Trimmed; max 30 chars. |
| `account_number_format` | VARCHAR(100) | NULLABLE | Editable. Max 100 chars. |
| `active` | BOOLEAN | NOT NULL, DEFAULT true | Tenant can deactivate a template they do not use. |
| `created_at` | TIMESTAMP | NOT NULL | Set on insert (time of copy). |
| `updated_at` | TIMESTAMP | NOT NULL | Set on insert and update. |

**Indexes:**
- Index on `(tenant_id, company_id)` for tenant-scoped company queries.
- Index on `tenant_id`.
- Index on `company_id`.

> **No PII** — this table contains only banking reference data.

---

## 6. Business Rules

| # | Rule |
|---|------|
| BR-1 | `country_code` on `platform_bank_template` must reference an existing `platform_country.iso_alpha2` with `payroll_enabled = true`. If the country is not found or not payroll-enabled → **422 COUNTRY_NOT_PAYROLL_ENABLED**. |
| BR-2 | `country_code` on `platform_bank_template` is **immutable** after creation. Return **400** if client sends it on edit. |
| BR-3 | `name` is required, non-blank, max 150 characters (trimmed). |
| BR-4 | `swift_bic`, when provided, must match the SWIFT/BIC pattern `^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$` (uppercase). Return **400** `INVALID_SWIFT_BIC` if violated. |
| BR-5 | `currency_code`, when provided, must be a 3-letter uppercase string. Further validation against a known list is deferred; v1 validates format only. |
| BR-6 | Deactivating an already-inactive template → **409 BANK_TEMPLATE_ALREADY_INACTIVE**. Activating an already-active template → **409 BANK_TEMPLATE_ALREADY_ACTIVE**. |
| BR-7 | Platform templates **cannot be hard-deleted** (referenced by tenant copies). Deactivation is the only removal mechanism. |
| BR-8 | On company create: fetch all active `platform_bank_template` rows for `payroll_country`; copy each into `tenant_bank_template`. If zero active templates exist for the country, no error is raised. |
| BR-9 | Tenant edit: `country_code` and `platform_bank_template_id` are **immutable** on `tenant_bank_template`. Return **400** if client sends these fields. |
| BR-10 | Tenant scope: all `tenant_bank_template` operations must be scoped to `tenant_id` from `TenantContext` and the supplied `company_id` must belong to the same tenant. Return **404** if company is not found in the tenant. |
| BR-11 | Platform superadmin check: `platform_superadmin = true` on caller's `user_account`. **403** if not (matching pattern from `platform-settings.md`). |

---

## 7. States & Transitions

**`platform_bank_template`:**
```
active = true  ──[deactivate]──►  active = false
active = false ──[activate]────►  active = true
```

**`tenant_bank_template`:**
```
active = true  ──[tenant deactivates]──►  active = false
active = false ──[tenant activates]────►  active = true
```

Neither table supports hard delete.

---

## 8. Privileges

| Code | Purpose |
|---|---|
| `BANK_TEMPLATE_VIEW` | Read access: list and single record on tenant endpoints |
| `BANK_TEMPLATE_MANAGE` | Write access: update/activate/deactivate tenant copies (logically implies VIEW) |

Both privileges must be registered in the **global privilege catalog** via a Liquibase DML changeset so SuperAdmin inherits them automatically. Tenant admins may then expose either or both to their tenant's roles.

Platform-level endpoints use `PlatformOperatorService.requirePlatformSuperadmin()` — no separate privilege code for platform CRUD.

---

## 9. API

### 9.1 Platform SuperAdmin — `/api/v1/platform/bank-templates`

All routes: **authenticated**; **no** `TenantContext` required. `PlatformOperatorService.requirePlatformSuperadmin()`. Responses follow `ApiResponse` / `ProblemDetail` conventions.

| Method | Path | Query / Body | Success | Errors |
|---|---|---|---|---|
| `GET` | `/api/v1/platform/bank-templates` | `page` (0-based, default 0), `size` (default 20, max 100), `country` (optional alpha-2 filter), `active` (optional boolean filter) | **200** `data`: `{ items: PlatformBankTemplateRow[], page, size, totalElements, totalPages }` | **403** |
| `POST` | `/api/v1/platform/bank-templates` | `{ countryCode, name, bankName?, swiftBic?, bankCode?, accountNumberFormat?, currencyCode?, active? }` | **201** `data.template` | **400** validation; **403**; **422 COUNTRY_NOT_PAYROLL_ENABLED** |
| `GET` | `/api/v1/platform/bank-templates/{id}` | — | **200** `data.template` | **404**; **403** |
| `PUT` | `/api/v1/platform/bank-templates/{id}` | All mutable fields (same shape as POST minus `countryCode`) | **200** `data.template` | **400** (including attempt to change `countryCode`); **404**; **403** |
| `PATCH` | `/api/v1/platform/bank-templates/{id}/activate` | — | **200** `data.template` | **409 BANK_TEMPLATE_ALREADY_ACTIVE**; **404**; **403** |
| `PATCH` | `/api/v1/platform/bank-templates/{id}/deactivate` | — | **200** `data.template` | **409 BANK_TEMPLATE_ALREADY_INACTIVE**; **404**; **403** |

**`PlatformBankTemplateRow`:** `{ id, countryCode, name, bankName, swiftBic, bankCode, accountNumberFormat, currencyCode, active, createdAt, updatedAt }`.

---

### 9.2 Tenant — `/api/v1/tenant/bank-templates`

All routes: **tenant-scoped** (`TenantContext` from subdomain). Privilege-gated.

| Method | Path | Query / Body | Success | Errors |
|---|---|---|---|---|
| `GET` | `/api/v1/tenant/bank-templates` | `companyId` (required UUID), `page` (default 0), `size` (default 20, max 100), `active` (optional boolean filter) | **200** `data`: `{ items: TenantBankTemplateRow[], page, size, totalElements, totalPages }` | **400** missing `companyId`; **403**; **404** company not in tenant |
| `GET` | `/api/v1/tenant/bank-templates/{id}` | — | **200** `data.template` | **404**; **403** |
| `PUT` | `/api/v1/tenant/bank-templates/{id}` | `{ name, bankName?, swiftBic?, bankCode?, accountNumberFormat?, currencyCode?, active }` (all mutable fields) | **200** `data.template` | **400** (including attempt to change `countryCode` or `platformBankTemplateId`); **404**; **403** |
| `PATCH` | `/api/v1/tenant/bank-templates/{id}/activate` | — | **200** `data.template` | **409 BANK_TEMPLATE_ALREADY_ACTIVE**; **404**; **403** |
| `PATCH` | `/api/v1/tenant/bank-templates/{id}/deactivate` | — | **200** `data.template` | **409 BANK_TEMPLATE_ALREADY_INACTIVE**; **404**; **403** |

**`TenantBankTemplateRow`:** `{ id, companyId, platformBankTemplateId, countryCode, name, bankName, swiftBic, bankCode, accountNumberFormat, currencyCode, active, createdAt, updatedAt }`.

Privilege guards:
- `GET` (list + single): `@RequiresPrivilege("BANK_TEMPLATE_VIEW")`
- `PUT`, `PATCH` activate/deactivate: `@RequiresPrivilege("BANK_TEMPLATE_MANAGE")`

---

## 10. Edge Cases

| Scenario | Expected behaviour |
|---|---|
| Company created with `payroll_country` having no active platform templates | No `tenant_bank_template` rows inserted; no error |
| Platform template deactivated after company copy was made | Existing `tenant_bank_template` rows are **unaffected** |
| Platform template PUT changes `countryCode` | **400** — `countryCode` is immutable |
| Tenant PUT sends `countryCode` or `platformBankTemplateId` | **400** — these fields are immutable on tenant copies |
| `companyId` in list query belongs to a different tenant | **404** — tenant isolation enforced |
| `swift_bic` provided with invalid format (lowercase, wrong length) | **400 INVALID_SWIFT_BIC** |
| Duplicate activate / deactivate | **409 BANK_TEMPLATE_ALREADY_ACTIVE** / `BANK_TEMPLATE_ALREADY_INACTIVE` |
| Unknown `id` on GET or PUT | **404** |
| SuperAdmin calls tenant endpoints | Same privilege enforcement path — no bypass |
| `payroll_country` on newly created company references a country with no `payroll_enabled` flag | Covered by BR-1 on company create — company creation validation should ensure `payroll_country` is valid; copy simply finds no templates if country has no active templates (no error) |

---

## 11. Audit

All write operations append a record to `audit_event` via `AuditService`.

### Platform operations

| Operation | `action_code` | Metadata fields |
|---|---|---|
| Create | `PLATFORM_BANK_TEMPLATE_CREATED` | `countryCode`, `name` |
| Update | `PLATFORM_BANK_TEMPLATE_UPDATED` | `id`, changed fields (old + new) |
| Activate | `PLATFORM_BANK_TEMPLATE_ACTIVATED` | `id`, `countryCode`, `name` |
| Deactivate | `PLATFORM_BANK_TEMPLATE_DEACTIVATED` | `id`, `countryCode`, `name` |

`resource_type`: `PLATFORM_BANK_TEMPLATE`, `resource_id`: template UUID, `tenant_id`: null (platform-level event).

### Tenant operations

| Operation | `action_code` | Metadata fields |
|---|---|---|
| Update | `TENANT_BANK_TEMPLATE_UPDATED` | `id`, `companyId`, changed fields (old + new values) |
| Activate | `TENANT_BANK_TEMPLATE_ACTIVATED` | `id`, `companyId`, `countryCode` |
| Deactivate | `TENANT_BANK_TEMPLATE_DEACTIVATED` | `id`, `companyId`, `countryCode` |

`resource_type`: `TENANT_BANK_TEMPLATE`, `resource_id`: template UUID, `tenant_id`: from `TenantContext`.

> **No PII** in audit metadata — all fields are banking reference data.

---

## 12. Web

### Platform (SuperAdmin)

- **Route:** `/app/platform-bank-templates` — list page: paginated table, country filter dropdown, active/all toggle, "+ Add Bank Template" button. Superadmin only.
- **Route:** `/app/platform-bank-templates/new` — create form.
- **Route:** `/app/platform-bank-templates/[id]/edit` — edit form (pre-populated); activate/deactivate action.
- **Nav:** Synthetic item `nav.platform_bank_templates` appears in platform sidebar when `me.platformSuperadmin === true` (after Platform Countries in sort order).
- **i18n:** UI chrome uses `platformBankTemplates.*` message keys — all labels, buttons, page title, column headers in `frontend/src/messages/nav.ts` or equivalent; no hardcoded English strings.
- **Country dropdown:** populated from `GET /api/v1/platform/countries?active=true` filtered to `payroll_enabled` countries (or a dedicated endpoint). Shows alpha-2 + name.
- **Deactivate/Activate confirmation:** modal with template name before calling the API.

### Tenant

- **Route:** `/app/bank-templates` — list page: company selector (or company-context URL param), paginated table, active/all toggle.
- **Route:** `/app/bank-templates/[id]/edit` — edit form (read-only `countryCode`, all other mutable fields editable); activate/deactivate action.
- **Privilege-gated UI:**
  - `BANK_TEMPLATE_VIEW` only: table visible; no Edit / Activate / Deactivate controls.
  - `BANK_TEMPLATE_MANAGE`: full controls visible and enabled.
- **i18n:** Keys under `bankTemplates.*` for UI chrome.

---

## 13. Mobile (Flutter)

**Out of scope** for this milestone. No Flutter screens required for v1.

---

## 14. Liquibase

Follow `docs/guides/LIQUIBASE-RULES.md` for all changesets.

### DDL — `schema-m11-platform-bank-templates-1.xml`

Java task class: `DdlM11PlatformBankTemplates1`

- Create `platform_bank_template` with all columns listed in §5 Data Model.
- Create `tenant_bank_template` with all columns listed in §5 Data Model.
- Add FK `tenant_bank_template.tenant_id` → `tenant.id`.
- Add FK `tenant_bank_template.company_id` → `tenant_company.id`.
- Add FK `tenant_bank_template.platform_bank_template_id` → `platform_bank_template.id` (nullable).
- Add index `idx_platform_bank_template_country_active` on `(country_code, active)` for copy-on-create lookups.
- Add index `idx_platform_bank_template_country` on `country_code`.
- Add index `idx_tenant_bank_template_tenant_company` on `(tenant_id, company_id)`.
- Add index `idx_tenant_bank_template_tenant` on `tenant_id`.
- Add index `idx_tenant_bank_template_company` on `company_id`.

### DML — `data-m11-platform-bank-template-privileges-1.xml`

Java task class: `DataM11PlatformBankTemplatePrivileges1`

- Insert `BANK_TEMPLATE_VIEW` into `privilege` (global pool, category `payroll_reference`).
- Insert `BANK_TEMPLATE_MANAGE` into `privilege` (global pool, category `payroll_reference`).
- Assign both to the SuperAdmin role (or global catalog per `PRIVILEGE-MODEL.md`).

### DML — `data-m11-platform-bank-templates-seed-1.xml`

Java task class: `DataM11PlatformBankTemplatesSeed1`

Seed the Suriname (`SR`) initial bank templates. At minimum (expand as payroll adapter spec evolves):

| `country_code` | `name` | `bank_name` | `swift_bic` | `currency_code` | `active` |
|---|---|---|---|---|---|
| `SR` | Standard Bank Transfer — Hakrinbank | Hakrinbank N.V. | `HAKRSR22` | `SRD` | `true` |
| `SR` | Standard Bank Transfer — DSB Bank | De Surinaamsche Bank N.V. | `DSBLSR22` | `SRD` | `true` |
| `SR` | Standard Bank Transfer — Finabank | Finabank N.V. | `FINLSRSS` | `SRD` | `true` |
| `SR` | Standard Bank Transfer — RBC Royal Bank | RBC Royal Bank (Suriname) N.V. | `ROYCSR22` | `SRD` | `true` |

Seed is **idempotent** — uses `preconditions` or `onFail="MARK_RAN"` to skip if rows already exist for `SR`.

---

## 15. Acceptance Criteria

| ID | Criterion |
|---|---|
| AC-1 | Liquibase DDL creates `platform_bank_template` and `tenant_bank_template` with all specified columns, constraints, and indexes. |
| AC-2 | Liquibase DML inserts `BANK_TEMPLATE_VIEW` and `BANK_TEMPLATE_MANAGE` privileges into the global catalog. |
| AC-3 | Liquibase seed inserts Suriname (`SR`) initial platform bank templates; re-run is idempotent. |
| AC-4 | `GET /api/v1/platform/bank-templates` returns paginated list to superadmin; **403** for non-superadmin. |
| AC-5 | `POST /api/v1/platform/bank-templates` creates a template; validates required fields; **422** for non-payroll-enabled country; **403** for non-superadmin. |
| AC-6 | `PUT /api/v1/platform/bank-templates/{id}` updates mutable fields; **400** if `countryCode` is sent; **404** on unknown id; **403** for non-superadmin. |
| AC-7 | `PATCH .../activate` and `.../deactivate` toggle `active` flag on platform template; **409** on no-op; audit event recorded. |
| AC-8 | On `POST /api/v1/tenant/companies` success, all active `platform_bank_template` records for the company's `payroll_country` are copied into `tenant_bank_template` within the same transaction. |
| AC-9 | Company created with a `payroll_country` that has no active platform templates → company created successfully, zero `tenant_bank_template` rows inserted, no error. |
| AC-10 | `GET /api/v1/tenant/bank-templates?companyId={uuid}` returns paginated list to user with `BANK_TEMPLATE_VIEW`; **403** without privilege; **404** if `companyId` does not belong to tenant. |
| AC-11 | `PUT /api/v1/tenant/bank-templates/{id}` updates mutable fields; **400** if `countryCode` or `platformBankTemplateId` sent; **404** on unknown id or cross-tenant access; **403** without `BANK_TEMPLATE_MANAGE`. |
| AC-12 | Tenant activate/deactivate on `tenant_bank_template`: same 200 / 409 / 403 / 404 rules as platform variant; audit event `TENANT_BANK_TEMPLATE_ACTIVATED` / `TENANT_BANK_TEMPLATE_DEACTIVATED` recorded. |
| AC-13 | Deactivating a platform template does **not** affect existing `tenant_bank_template` rows. |
| AC-14 | Platform superadmin web list renders at `/app/platform-bank-templates`; visible only to superadmin; country filter and active/all toggle work. |
| AC-15 | Tenant web list renders at `/app/bank-templates`; `BANK_TEMPLATE_VIEW` only → no Edit controls; `BANK_TEMPLATE_MANAGE` → Edit and activate/deactivate visible. |
| AC-16 | All UI chrome uses message keys; no hardcoded English strings in components. |
| AC-17 | Audit events recorded for: platform template created, updated, activated, deactivated; tenant template updated, activated, deactivated. |

---

## 16. Implementation Notes

| Area | Expected location |
|---|---|
| DDL / seed | `backend/src/main/resources/db/changelog/ddl/schema-m11-platform-bank-templates-1.xml` |
| DML privileges | `backend/src/main/resources/db/changelog/dml/data-m11-platform-bank-template-privileges-1.xml` |
| DML seed | `backend/src/main/resources/db/changelog/dml/data-m11-platform-bank-templates-seed-1.xml` |
| Liquibase Java tasks | `backend/src/main/java/com/wagepayroll/liquibase/task/DdlM11PlatformBankTemplates1.java`, `DataM11PlatformBankTemplatePrivileges1.java`, `DataM11PlatformBankTemplatesSeed1.java` |
| JPA entities | `backend/src/main/java/com/wagepayroll/domain/banktemplate/PlatformBankTemplateEntity.java`, `TenantBankTemplateEntity.java`, `PlatformBankTemplateRepository.java`, `TenantBankTemplateRepository.java` |
| Platform API + service | `backend/src/main/java/com/wagepayroll/api/PlatformBankTemplatesController.java`, `backend/src/main/java/com/wagepayroll/banktemplate/PlatformBankTemplateService.java` |
| Tenant API + service | `backend/src/main/java/com/wagepayroll/api/TenantBankTemplatesController.java`, `backend/src/main/java/com/wagepayroll/banktemplate/TenantBankTemplateService.java` |
| Copy-on-create service | `backend/src/main/java/com/wagepayroll/banktemplate/BankTemplateCopyService.java` (called from `TenantPayrollOrgService.createCompany`) |
| Web — platform | `frontend/src/app/app/platform-bank-templates/page.tsx`, `new/page.tsx`, `[id]/edit/page.tsx` |
| Web — tenant | `frontend/src/app/app/bank-templates/page.tsx`, `[id]/edit/page.tsx` |
| API client | `frontend/src/lib/api.ts` (`fetchPlatformBankTemplates`, `postPlatformBankTemplate`, `putPlatformBankTemplate`, `patchActivatePlatformBankTemplate`, `patchDeactivatePlatformBankTemplate`, `fetchTenantBankTemplates`, `fetchTenantBankTemplate`, `putTenantBankTemplate`, `patchActivateTenantBankTemplate`, `patchDeactivateTenantBankTemplate`) |
| i18n | `frontend/src/messages/nav.ts` (`nav.platform_bank_templates`, `platformBankTemplates.*`, `bankTemplates.*`) |
| Integration tests | `backend/src/test/java/com/wagepayroll/api/PlatformBankTemplatesIT.java`, `TenantBankTemplatesIT.java` |

---

## 17. Proposed Schema Extension (requires PII review)

- **Table / entity:** `tenant_bank_template`
- **Proposed column(s):** `sort_order` (INTEGER) — display ordering for tenants who want to reorder templates.
- **Justification:** Useful for tenant admins who want to pin their primary bank at the top. Not needed for v1.
- **PII classification:** none
- **Retention / deletion / anonymization impact:** none
- **Liquibase changeset id (when approved):** TBD
