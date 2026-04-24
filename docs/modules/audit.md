# Module: Audit (append-only events)

**Retention (contract):** authoritative **audit** data is kept **at least 10 years** unless law or contract forces earlier action. **Controlled deletion** of other entities must **not** remove audit facts required for accountability; use **reference surrogates** or tombstone metadata where business rows are anonymized (see architecture + `DATA-MODEL-STANDARDS.md`).

## Principles

- **Append-only** in the application layer: only **INSERT** `audit_event` rows; no updates or deletes through product services.
- **Minimize PII** in `metadata_json`: prefer **action codes**, **resource identifiers** (UUIDs), **setting keys** without values, and **correlation** (`correlation_id` / request id). Do not store secrets, full message bodies, or national identifiers in audit rows unless explicitly approved with encryption/TTL.
- **Correlation:** `correlation_id` carries the HTTP **`X-Request-Id`** (or server-generated id from `RequestIdFilter`) when the event originates from a web/API request.

## Data — `audit_event` (strict)

| Column | Type | Notes |
|--------|------|--------|
| `id` | UUID | PK |
| `occurred_at` | timestamp | UTC; when the event was recorded |
| `tenant_id` | UUID, nullable | Tenant scope when applicable; **null** for global user or platform events |
| `actor_user_id` | UUID, nullable | Authenticated principal; **null** reserved for future system-generated events |
| `action_code` | string | Stable code, e.g. `USER_LOCALE_CHANGED`, `TENANT_SETTINGS_PATCHED`, `PLATFORM_SETTINGS_PATCHED` |
| `resource_type` | string | e.g. `USER_ACCOUNT`, `TENANT_SETTING`, `PLATFORM_SETTING` |
| `resource_id` | string, nullable | Primary key as string when a single resource applies |
| `correlation_id` | string, nullable | Request correlation id |
| `metadata_json` | string, nullable | Max 2000 chars; **non-PII** JSON (e.g. `{"keys":["a.b"]}` without values, or `{"locale":"nl"}`) |

**Referential integrity (target):** `tenant_id` → `tenant.id` **ON DELETE SET NULL**; `actor_user_id` → `user_account.id` **ON DELETE SET NULL** (supports controlled delete/anonymize on business rows without dropping audit facts). M1 DDL ships **without** these FKs to keep Liquibase + H2 test runs simple; add them in a dedicated follow-up changeset for MariaDB production.

## M1 write paths

| `action_code` | When |
|---------------|------|
| `USER_LOCALE_CHANGED` | After successful `PATCH /api/v1/me/locale` |
| `TENANT_SETTINGS_PATCHED` | After successful `PATCH /api/v1/tenant/settings` |
| `PLATFORM_SETTINGS_PATCHED` | After successful `PATCH /api/v1/platform/settings` |
| `SUPERADMIN_TENANT_ELEVATED_ACCESS` | After successful tenant-scoped `@RequiresPrivilege` handler when access was **only** via platform superadmin elevation (`PrivilegeAuthorizationAspect`) |
| `TENANT_PRIVILEGE_POOL_REPLACED` | After successful `PUT /api/v1/platform/tenants/{tenantId}/privilege-pool` (metadata: sorted `privilege` codes) |
| `SUBJECT_DATA_EXPORTED` | After successful `GET /api/v1/me/privacy/export` (metadata: `exportSchemaVersion` only) |
| `SUBJECT_ERASURE_REQUESTED` | After accepted `POST /api/v1/me/privacy/erasure-request` (optional `noteLength` + `noteSha256`) |

## API

- **No public read API in M1** (reporting / operator UI later). Verification via repository or SQL in tests.

## Proposed Schema Extension (requires PII review)

- `ip_hash`, `user_agent_class`; optional linkage to external ticketing when break-glass metadata stores hashes only.
