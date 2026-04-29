# Module: Platform tenant admin (registry)

**Feature slug:** `platform-tenant-admin`  
**Related:** [`platform-settings.md`](./platform-settings.md) (operator role), [`tenancy-routing.md`](./tenancy-routing.md) (handle rules, unknown tenant), [`commercial-subscriptions.md`](./commercial-subscriptions.md) (`GET/PUT .../platform/tenants/{tenantId}/subscription`), [`security.md`](./security.md) (`platform_superadmin`).

Platform **superadmins** list all tenants and **create** or **rename** (display name) tenant records. **v1:** no **delete** or **archive** flag (hard deletes are unsafe with FK graph). **Handle is immutable after create** (changing handle breaks bookmarks, host routing, and invitations).

## Data — `tenant` (read/write subset)

Uses existing `tenant` entity (`TenantEntity` in backend) columns only:

| Column | Notes |
|--------|--------|
| `id` | UUID PK |
| `handle` | Unique, lowercase, **1–64** chars; must not match **reserved** subdomains (same set as `SubdomainParser.reserved` + `app.host.*`). Pattern: `^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$` |
| `name` | Display name, **1–255** chars (trimmed) |
| `created_at` / `updated_at` | Set on create; `updated_at` on PATCH |

**No** new Liquibase for v1.

## API

All routes: **authenticated**; **no** `TenantContext` required. **`PlatformOperatorService.requirePlatformSuperadmin`**.

| Method | Path | Body / query | Success | Errors |
|--------|------|--------------|---------|--------|
| `GET` | `/api/v1/platform/tenants` | `page` (0-based, default 0), `size` (default 20, max 100) | **200** `data`: `{ items: PlatformTenantRow[], totalElements, page, size, totalPages }` sorted by **handle** asc | **403** |
| `POST` | `/api/v1/platform/tenants` | `{ "handle": "...", "name": "..." }` | **201** `data.tenant` row | **400** invalid handle / name; **409** duplicate handle; **403** |
| `GET` | `/api/v1/platform/tenants/{tenantId}` | — | **200** `data.tenant` | **404**; **403** |
| `PATCH` | `/api/v1/platform/tenants/{tenantId}` | `{ "name": "..." }` (required non-blank) | **200** `data.tenant` | **400** blank name; **404**; **403** |

**Out of v1:** `DELETE`, `PATCH` on `handle`, soft-`active` flag.

## Handle validation

- Normalize to **lowercase** before store and duplicate check.
- Reject if handle is in the **reserved** set (`auth`, `app`, `api`, `www`, `admin`, … + `app.host.auth-subdomain`, `app-subdomain`, `reserved-subdomains-extra`).
- Reject if pattern does not match product regex above.

## Audit

| `action_code` | When |
|---------------|------|
| `PLATFORM_TENANT_CREATED` | After successful `POST` (metadata: `handle`) |
| `PLATFORM_TENANT_UPDATED` | After successful `PATCH` (metadata: `fields` e.g. `["name"]`) |

`resource_type`: `TENANT`, `resource_id`: tenant UUID, `tenant_id`: same tenant.

## Web (Next.js)

- **`/app/platform-tenants`** — list + pagination + create form (`fetchPlatformTenants`, `postPlatformTenant`); platform superadmin only.
- **`/app/platform-tenants/[tenantId]`** — editor: **handle** read-only, **name** editable (`fetchPlatformTenant`, `patchPlatformTenantName`).
- **Nav:** synthetic item `nav.platform_tenants` when `me.platformSuperadmin` (`NavigationController`, sort order **29**, before platform settings).
- **i18n:** `frontend/src/messages/nav.ts` — keys `nav.platform_tenants`, `platformTenants.*`.

## Mobile (Flutter)

- **Out of scope** for operator CRUD unless a dedicated console app is requested; no change required for v1.

## Implementation pointers (repo)

- Backend: `PlatformTenantRegistryController`, `PlatformTenantRegistryService`, `TenantHandleValidator`, DTOs `PlatformTenantRowDto`, `PlatformTenantCreateRequest`, `PlatformTenantPatchRequest`; `TenantRepository.findAllByOrderByHandleAsc`.
- Frontend: `frontend/src/app/app/platform-tenants/page.tsx`, `[tenantId]/page.tsx`; API helpers in `frontend/src/lib/api.ts`; nav + i18n in `frontend/src/messages/nav.ts`; synthetic nav in `NavigationController`.
- Tests: `PlatformTenantRegistryIT`; navigation expectations updated in `NavigationAndSettingsIT`, `MeTenantsIT`.

## Acceptance criteria

1. Non-superadmin **403** on all four routes.
2. Superadmin **GET** list returns seeded tenants sorted by handle; pagination fields consistent.
3. **POST** creates tenant; second POST with same handle **409**.
4. **POST** with handle `auth` or `api` **400** (reserved).
5. **PATCH** updates `name` and `updated_at`; **GET** one returns fresh data.
6. Audit rows appended for create and patch with **non-PII** metadata.
