# Module: Tenant settings

Key/value settings scoped to a **tenant**, editable by tenant administrators. Values must be **non-PII configuration** (theme tokens, feature toggles, display defaults — not employee or payroll data).

## Data — `tenant_setting` (strict)

| Column | Type | Notes |
|--------|------|--------|
| `id` | UUID | PK |
| `tenant_id` | UUID | FK → `tenant.id` |
| `key` | string | Unique per tenant |
| `value_text` | string | Max length 2000; **no PII** |
| `created_at` | timestamp | |
| `updated_at` | timestamp | |

Unique constraint: `(tenant_id, key)`.

## API

- **`GET /api/v1/tenant/settings`** — `@RequiresPrivilege("TENANT_SETTINGS_EDIT")` (read uses same gate as edit for M1; split VIEW privilege later if needed).
- **`PATCH /api/v1/tenant/settings`** — Same privilege; body `{ "entries": [ { "key": "...", "value": "..." } ] }`. Upserts by key; keys must match `[a-z0-9_.-]{1,128}`; values length ≤ 2000.
- **`GET /api/v1/tenant/privileges/pool`** — Same privilege; returns `data.privileges` (sorted global privilege catalog codes). Intended for tenant admin / settings UX role editors.

## Security

- Tenant context required; enforced via `TenantContext` + `@RequiresPrivilege`.
- **Platform superadmin** using tenant routes **outside** normal role grants on **PATCH/POST/PUT/DELETE** must send **`X-Break-Glass-Reason`** (see [`security.md`](./security.md)); successful calls append **`SUPERADMIN_TENANT_ELEVATED_ACCESS`** audit metadata (hashed justification).
- Successful **`PATCH`** writes an **`audit_event`** (`TENANT_SETTINGS_PATCHED`, metadata lists **keys only** — no values) — see [`audit.md`](./audit.md).

## Proposed Schema Extension (requires PII review)

- Typed columns or JSON blob with schema versioning — only if a future setting truly needs structured secrets (prefer env/secret manager instead).
