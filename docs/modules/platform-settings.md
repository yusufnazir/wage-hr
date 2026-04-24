# Module: Platform settings (global)

Key/value settings for the **whole deployment**, readable/writable only by **platform operators** (see `security.md` — `user_account.platform_superadmin`). Values are **non-PII** configuration (branding defaults, maintenance banners, etc.).

## Data — `platform_setting` (strict)

| Column | Type | Notes |
|--------|------|--------|
| `id` | UUID | PK |
| `key` | string | Globally unique |
| `value_text` | string | Max length 2000; **no PII** |
| `created_at` | timestamp | |
| `updated_at` | timestamp | |

## Data — `user_account.platform_superadmin`

Boolean column on `user_account` (default `false`). When `true`, the principal may call **platform** APIs documented in this file (`PlatformOperatorService`). For **tenant** HTTP APIs, the same flag is consulted inside `PermissionService.hasPrivilege` (see [`security.md`](./security.md)) so operators are not special-cased in controllers. **Break-glass** metadata and audit for sensitive tenant data remain a separate checklist item.

## API

- **`GET /api/v1/platform/privileges/catalog`** — Authenticated; **no tenant context**. **403** if not platform superadmin. Response `data.entries`: `{ code, action, resource, description }[]` sorted by `code` (action/resource from `DefinedPrivilege`; unknown rows would return null action/resource until the enum is updated).
- **`GET /api/v1/platform/settings`** — Authenticated; **no tenant context** required. **403** if not platform superadmin.
- **`PATCH /api/v1/platform/settings`** — Same; body `{ "entries": [ { "key": "...", "value": "..." } ] }`; keys `[a-z0-9_.-]{1,128}`; values ≤ 2000.
- **`PUT /api/v1/platform/tenants/{tenantId}/privilege-pool`** — Authenticated platform superadmin; body `{ "codes": [ "USER_VIEW", ... ] }` (**non-empty**); each code must exist in global `privilege`. Replaces all `tenant_privilege_allowance` rows for the tenant. **200** `data.privileges` (sorted applied codes). **404** if tenant id unknown; **400** for unknown privilege code or empty list. Writes **`audit_event`** `TENANT_PRIVILEGE_POOL_REPLACED` (see [`audit.md`](./audit.md)).

## Security

- Session + CSRF as for other mutating APIs.
- Platform-only routes use `PlatformOperatorService.requirePlatformSuperadmin`; tenant routes use `@RequiresPrivilege` + `PermissionService` (see `security.md`).
- Successful **`PATCH`** writes **`audit_event`** (`PLATFORM_SETTINGS_PATCHED`, **keys only** in metadata) — [`audit.md`](./audit.md).

## Proposed Schema Extension (requires PII review)

- Elevation TTL, second-person approval, or dedicated `platform_operator` join table for multi-user audit.
