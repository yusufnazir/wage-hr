# Module: User & membership (scaffold)

## Data model

- **`user_account`** — global identity (`id` UUID, `email`, `password_hash`, `platform_superadmin`, `preferred_locale`, timestamps). See [`i18n.md`](./i18n.md) for locale semantics and APIs.
- **`membership`** — `(tenant_id, user_id)` links users to tenants (tenant-scoped row).
- **`user_role`** — assigns a `role` within a tenant to a user.

## API

- **`GET /api/v1/me`** — authenticated; returns `email`, `locale` (from `preferred_locale`), `privileges` (effective privilege codes in resolved tenant), `tenantHandle` when tenant context is present, `platformSuperadmin`.
- **`GET /api/v1/me/tenants`** — authenticated; all tenant memberships + role names for the principal (see [`tenancy-routing.md`](./tenancy-routing.md)).
- **`PATCH /api/v1/me/locale`** — authenticated; updates `preferred_locale` (see [`i18n.md`](./i18n.md)); audit `USER_LOCALE_CHANGED` (see [`audit.md`](./audit.md)).
- **`GET /api/v1/me/privacy/export`** — authenticated; machine-readable bundle for the principal (see [`data-lifecycle.md`](./data-lifecycle.md)); audit `SUBJECT_DATA_EXPORTED`.
- **`POST /api/v1/me/privacy/erasure-request`** — authenticated; optional `{ "note": "…" }` (≤ 500 chars); **202** stub (audit `SUBJECT_ERASURE_REQUESTED`); CSRF. Fulfillment TBD.

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
