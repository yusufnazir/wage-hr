# Module: User & membership

## Data model

- **`user_account`** — global identity (`id` UUID, `email`, `password_hash`, `platform_superadmin`, `preferred_locale`, **`first_name`** / **`last_name`** (nullable `VARCHAR(100)`; set on self-service registration — see [`account-registration.md`](./account-registration.md)), **`email_verified_at`** (nullable timestamp; self-service registration leaves null until verified), timestamps). See [`i18n.md`](./i18n.md) for locale semantics and APIs.
- **`membership`** — `(tenant_id, user_id)` links users to tenants (tenant-scoped row). Allowed columns:
  - `id` (UUID), `tenant_id`, `user_id`, `created_at`, `updated_at`
  - `status` (`VARCHAR(32)`, not null, default `ACTIVE`) — tenant membership lifecycle for this tenant; v1 values: `ACTIVE` only in product flows (read-only in tenant UI unless a future change adds suspend).
  - `last_active_at` (`TIMESTAMP`, nullable) — last time the principal had authenticated tenant API activity for this tenant (debounced server touch, ~60s).
- **`user_role`** — assigns a `role` within a tenant to a user.

### Proposed Schema Extension (requires PII review) — **accepted for tenant directory v1**

The following was approved and implemented for the tenant user directory (email, last active, status, roles):

| Table | Column | PII | Retention |
|-------|--------|-----|-----------|
| `membership` | `status` | none (short enum code) | Same as membership row |
| `membership` | `last_active_at` | low (activity timestamp) | Updated on activity; cleared when row removed per membership lifecycle |

## Web (Next.js)

- **`/app/profile`** — read-only view of email, locale, and tenant handle (same data as **`GET /api/v1/me`**).
- **`/app/users`** — tenant user directory: columns **email**, **last active** (`last_active_at`), **status**, **roles**; page size **20**; query params `email` (contains, case-insensitive), `status` (exact), `role` (exact role **name**); sort token `sort` = `EMAIL_ASC` \| `EMAIL_DESC` \| `LAST_ACTIVE_*` \| `STATUS_*` \| `ROLES_*` (roles sort key = lexicographically lowest role name in tenant; tie-break `user_id` ascending on server).
- **`/app/users/[userId]`** — `user_account` UUID. **`USER_EDIT`**: edit email and replace tenant `user_role` assignments; **cannot** change own roles (UI + server). Own email may change. **`USER_VIEW`** only: may open **own** id for read-only detail; **no** row links from the list for viewers (manual URL to another user → **403**).
- **`/app/users/new`** — reserved; v1 placeholder (“not available”) until create/invite shares the edit form.
- **Header user menu** (all routes under shared **`/app`** layout): Profile, change-password link to **`/forgot-password`** (email reset flow), language shortcuts, **Logout** (**`POST /api/v1/auth/logout`** via BFF then redirect to auth **`/login`**).

## API

- **`GET /api/v1/me`** — authenticated; returns `userId` (`user_account.id`), `email`, `locale` (from `preferred_locale`), `privileges` (effective privilege codes in resolved tenant), `tenantHandle` when tenant context is present, `tenantId`, `platformSuperadmin`, branding fields when tenant context is present.
- **`GET /api/v1/me/tenants`** — authenticated; all tenant memberships + role names for the principal (see [`tenancy-routing.md`](./tenancy-routing.md)).
- **`PATCH /api/v1/me/locale`** — authenticated; updates `preferred_locale` (see [`i18n.md`](./i18n.md)); audit `USER_LOCALE_CHANGED` (see [`audit.md`](./audit.md)).
- **`GET /api/v1/me/privacy/export`** — authenticated; machine-readable bundle for the principal (see [`data-lifecycle.md`](./data-lifecycle.md)); audit `SUBJECT_DATA_EXPORTED`.
- **`POST /api/v1/me/privacy/erasure-request`** — authenticated; optional `{ "note": "…" }` (≤ 500 chars); **202** stub (audit `SUBJECT_ERASURE_REQUESTED`); CSRF. Fulfillment TBD.

### Tenant user administration (current tenant)

Base path: **`/api/v1/tenant/users`** (requires tenant host or `X-Tenant-Id` as resolved by [`tenancy-routing.md`](./tenancy-routing.md)).

| Method | Path | Privilege | Description |
|--------|------|-------------|-------------|
| GET | `/api/v1/tenant/users` | `USER_VIEW` | Paginated list: `page` (0-based), `size` (1–20, default 20), `sort`, optional `email`, `status`, `role`. Response `data`: `items[]` with `userId`, `email`, `status`, `lastActiveAt`, `roleNames`; `totalElements`, `page`, `size`, `totalPages`. |
| GET | `/api/v1/tenant/users/role-options` | `USER_VIEW` | Role options for filters/editors. Response `data.roles[]`: `{ id, name }` (sorted). |
| GET | `/api/v1/tenant/users/{userId}` | `USER_EDIT` **or** (`USER_VIEW` **and** `{userId}` = principal) | Detail: `user` with `roleAssignments`, `assignableRoles` (empty unless caller has `USER_EDIT`). **404** if user not in tenant. **403** if another user’s detail without `USER_EDIT`. |
| PATCH | `/api/v1/tenant/users/{userId}` | `USER_EDIT` | Body JSON: optional `email`, optional `roleIds` (full replacement of tenant roles). **400** `CANNOT_CHANGE_OWN_ROLES` if principal targets self and `roleIds` differs from current. **409** `EMAIL_IN_USE` on duplicate email. **404** if not a member. Audits: `TENANT_USER_EMAIL_UPDATED`, `TENANT_USER_ROLES_REPLACED`. |

**Implementation pointers:** `TenantUsersController`, `TenantUserAdminService`, `MembershipActivityInterceptor` (touches `last_active_at`), Liquibase `schema-m6-membership-status-last-active-1.xml`, DTOs under `api/dto/TenantUser*.java`.

## Tenant context

Resolved by `TenantContextFilter` from:

1. Tenant handle on `Host` (e.g. `demo.lvh.me`), or  
2. `X-Tenant-Id` header when `Host` is the `api.*` pattern.

Without tenant context, `/me` returns privileges as an empty list (auth-only view).

## Seeds

Demo users (see `backend/.../liquibase/task/DataScaffoldSeed1.java`):

| Email | Password (dev) | Notes |
|--------|----------------|--------|
| `admin@demo.lvh.me` | `ChangeMe!1` | Admin role — `USER_VIEW`, `USER_EDIT`, `TENANT_SETTINGS_EDIT` |
| `viewer@demo.lvh.me` | `ChangeMe!1` | Viewer — `USER_VIEW` only |
| `nocode@demo.lvh.me` | `ChangeMe!1` | Membership, no roles (permission checks fail) |

## Verification (tenant user directory v1)

- **Backend:** `DemoPrivilegedEndpointIT` (list privilege), `TenantUsersIT`, `SuperadminTenantLensIT` (empty Acme list after membership removal), `MeEndpointIT` (`userId` on `/me`).
- **Frontend:** `npm run build`; dashboard privilege probe uses `GET /api/v1/tenant/users?page=0&size=1`; Playwright `m1-platform.spec.ts` asserts tenant user directory probe text on `/app`.
