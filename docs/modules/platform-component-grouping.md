# Platform component group templates (SuperAdmin catalog)

## Objective

SuperAdmins maintain **country-scoped default hierarchies** (`platform_component_group_template`, header templates, item templates) that group **platform** wage components under **headers** for UI and documentation. Tenants instantiate **company-scoped** `tenant_component_group` (+ headers/items) referencing **tenant** wage components, optionally linked to a platform group template.

## Actors

- **Platform SuperAdmin** — full CRUD on templates via `/api/v1/platform/component-group-templates`.
- **Tenant users** with wage-component privileges — CRUD on tenant groups via `/api/v1/component-groups` (always pass `companyId`).

## Allowed schema (closed)

### `platform_component_group_template`

| Column | Type | Nullable | Notes |
|--------|------|----------|--------|
| `id` | VARCHAR(36) | no | PK UUID |
| `platform_country_id` | VARCHAR(36) | no | FK → `platform_country.id` |
| `sort_order` | INT | no | Default 0; lower first in lists |
| `active` | BOOLEAN | no | Default true |
| `created_at` | TIMESTAMP | no | |
| `updated_at` | TIMESTAMP | no | |

**PII:** none on structural columns.

### `platform_component_group_template_locale`

| Column | Type | Nullable | Notes |
|--------|------|----------|--------|
| `id` | VARCHAR(36) | no | PK |
| `platform_component_group_template_id` | VARCHAR(36) | no | FK → `platform_component_group_template.id`, **ON DELETE CASCADE** |
| `locale` | VARCHAR(35) | no | Unique per group with `platform_component_group_template_id` |
| `name` | VARCHAR(200) | no | Display name |
| `description` | VARCHAR(500) | yes | Optional |

**PII:** `name` / `description` — **low** (free text labels; operational, not person directory).

### `platform_component_header_template`

| Column | Type | Nullable | Notes |
|--------|------|----------|--------|
| `id` | VARCHAR(36) | no | PK |
| `platform_component_group_template_id` | VARCHAR(36) | no | FK → `platform_component_group_template.id`, **ON DELETE CASCADE** |
| `sort_order` | INT | no | Default 0 |
| `created_at` | TIMESTAMP | no | |
| `updated_at` | TIMESTAMP | no | |

### `platform_component_header_template_locale`

Same shape as group locale; FK → `platform_component_header_template.id`, **ON DELETE CASCADE**.

### `platform_component_item_template`

| Column | Type | Nullable | Notes |
|--------|------|----------|--------|
| `id` | VARCHAR(36) | no | PK |
| `platform_component_header_template_id` | VARCHAR(36) | no | FK → `platform_component_header_template.id`, **ON DELETE CASCADE** |
| `platform_wage_component_template_id` | VARCHAR(36) | no | FK → `platform_wage_component_template.id` |
| `sort_order` | INT | no | Default 0 |
| `created_at` | TIMESTAMP | no | |
| `updated_at` | TIMESTAMP | no | |

**Unique:** `(platform_component_header_template_id, platform_wage_component_template_id)` — one slot per wage component template per header.

### `platform_component_item_template_locale`

Same shape as group locale; FK → `platform_component_item_template.id`, **ON DELETE CASCADE**.

### Tenant tables (company runtime)

- `tenant_component_group` — `tenant_id`, `company_id`, optional `platform_component_group_template_id`, `sort_order`, `active`, timestamps; locales in `tenant_component_group_locale`.
- `tenant_component_header` (+ `tenant_component_header_locale`).
- `tenant_component_item` — FK `tenant_wage_component_id` → `tenant_wage_component.id`; locales in `tenant_component_item_locale`.

## Default components (SR)

Liquibase seed `data-m29-platform-default-component-group-sr-1.xml` defines platform group template **Default components** (`54000000-0000-0000-0000-000000000001`) with 26 line items (template codes `1001`, `1004`–`1027`, `1034`, `1036`–`1038`, `1042`–`1044`). When a tenant company is created with payroll country **SR**, `DefaultPayrollCatalogProvisioningService` provisions all linked tenant wage components and copies the group structure.

The **demo** company (`Demo Payroll NV`, seeded in m20 before m29) receives the same catalog on **first application startup** via `DemoPayrollCatalogInitializer` (see m30 marker changeset). The existing demo base-salary row (`1001`) is kept; the other 25 components and the tenant component group are added. Andre’s standing instruction still references the original demo base-salary component id.

## Business rules

1. **Country:** On create, `platform_country_id` must reference a row with `active = true` and `payroll_enabled = true`.
2. **Wage component template:** `platform_wage_component_template.country_code` must equal the group’s country `iso_alpha2` (via `platform_country`); template must be `active`.
3. **Translations (writes):** Exactly **`en`** and **`nl`** locale rows with non-blank `name`; `description` optional. Unknown write locales → `400`.
4. **Reads:** `locale` query parameter must be **`en`** or **`nl`** (normalized like ledger templates). Resolution order: exact locale → `en` → first available.
5. **Delete group:** Hard delete; DB cascades remove headers, items, and all locale rows.

6. **Optional template on tenant group:** If `platform_component_group_template_id` is set, the template’s country must match the company’s `payroll_country`.
7. **Tenant item wage component:** Must belong to the same `tenant_id` and `company_id` as the parent group.

## API (v1)

### Platform templates (SuperAdmin)

Base path: `/api/v1/platform/component-group-templates`.

| Method | Path | Notes |
|--------|------|--------|
| GET | `/` | Paginated list; `page`, `size`, optional `country` (ISO-3166 alpha-2), `locale` |
| POST | `/` | Create group |
| GET | `/{id}` | Single group + resolved strings for `locale` |
| PUT | `/{id}` | Update `sort_order`, `active`, translations (not country) |
| DELETE | `/{id}` | Delete group (cascade) |
| GET | `/{groupId}/headers` | Paginated headers for group |
| POST | `/{groupId}/headers` | Create header |
| GET | `/{groupId}/headers/{headerId}` | |
| PUT | `/{groupId}/headers/{headerId}` | |
| DELETE | `/{groupId}/headers/{headerId}` | |
| GET | `/{groupId}/headers/{headerId}/items` | Paginated items |
| POST | `/{groupId}/headers/{headerId}/items` | Create item + wage component id |
| GET | `/{groupId}/headers/{headerId}/items/{itemId}` | |
| PUT | `/{groupId}/headers/{headerId}/items/{itemId}` | |
| DELETE | `/{groupId}/headers/{headerId}/items/{itemId}` | |

**Supporting:** `GET /api/v1/platform/wage-components` — paginated catalog for pickers (`country` ISO2 required).

### Tenant groups (`WAGE_COMPONENT_VIEW` / `WAGE_COMPONENT_MANAGE`)

Base path: `/api/v1/component-groups` — **always** include query `companyId`. Nested `headers` and `items` mirror the platform template paths; item payloads use `tenantWageComponentId` instead of `platformWageComponentTemplateId`.

**Errors (examples):** `NOT_FOUND`, `BAD_REQUEST` / stable reason codes, `CONFLICT` (duplicate wage component in header), `UNPROCESSABLE_ENTITY` for country not payroll-enabled, `VALIDATION_COUNTRY_MISMATCH` for wage component vs group country.

## Retention

Platform catalog; retained until SuperAdmin deletes. No tenant PII in these tables.

## Audit

Append-only audit for create/update/delete on platform templates and tenant groups (resource types `PLATFORM_COMPONENT_*_TEMPLATE` and `TENANT_COMPONENT_*`).
