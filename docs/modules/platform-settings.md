# Module: Platform settings (global)

Key/value settings for the **whole deployment**, readable/writable only by **platform operators** (see `security.md` — `user_account.platform_superadmin`). Values are **non-PII** configuration (branding defaults, maintenance banners, etc.) except **secrets** stored in values (MinIO keys, mail API password) — treat as **sensitive** operationally.

## Product goals (runtime + presentation)

| Concern | Rule |
|---------|------|
| **MinIO (S3-compatible)** | **Runtime source of truth:** read from `platform_setting` **first**; for any key **not** set in DB, use **`app.storage.minio.*` / env** (Spring `ConfigurationProperties`). Per-key merge. If after merge **endpoint, access key, secret, bucket** are not all non-blank, document storage behavior matches today: write paths that need MinIO return **503** (see `documents-minio.md`). |
| **Mail API** | **One** outbound path for all email: a single abstraction (e.g. service used by forgot-password, notifications, etc.) resolves **`mail.api.*`** with the same **DB → properties** order. **No** ad-hoc HTTP clients per feature. |
| **Application name** | **`platform.application_name`**, fallback to legacy **`platform.product_name`**, then a safe build default if both empty. Use **everywhere the product is named** in UI (shell, titles, emails, system copy). |
| **Date format** | **`platform.date_format`** — one of the allow-listed tokens **or** a custom date-only pattern. Apply to **all user-visible date display** on web (and Flutter when that screen exists); centralize formatting. **ISO-8601** token: use **ISO calendar date** `yyyy-MM-dd` for **date-only** fields unless a screen shows an instant (document in code if exception). Custom patterns must contain exactly one each of `yyyy`, `MM`, `dd` (e.g. `dd-MM-yyyy`) and may use separators `-`, `/`, `.`, spaces. |
| **Base URL** | **`platform.base_url`**: non-empty trimmed string used to build **absolute URLs** in emails, return-to-app links, and **path append** for “open this view” (`normalize(base) + absolutePath` with one **canonical** rule: **strip one trailing slash** from `base` before joining an `absolutePath` that starts with `/`). TLS is enforced by **reverse proxy**; API does not need to verify `https://` if operators use `http` in dev. |
| **i18n** | Platform settings **page** (tab labels, section titles, buttons, helper text) uses the same **message keys / `nav` pattern** as the rest of the multilingual app — no hard-coded English for those strings. |

## Data — `platform_setting` (strict)

| Column | Type | Notes |
|--------|------|--------|
| `id` | UUID | PK |
| `key` | string | Globally unique |
| `value_text` | string | Max length 2000; treat as **sensitive** when keys store API secrets (mail / MinIO). |
| `created_at` | timestamp | |
| `updated_at` | timestamp | |

### Allow-listed keys (non-billing)

`PATCH` rejects unknown keys (except `billing.stripe.enabled` / `billing.paypal.enabled`). Integration keys include:

| Key | Purpose |
|-----|---------|
| `platform.product_name` | Legacy display name |
| `platform.application_name` | Application name (global) |
| `platform.base_url` | Canonical public base URL |
| `platform.date_format` | One of `yyyy-MM-dd`, `dd/MM/yyyy`, `MM/dd/yyyy`, `ISO-8601`, or a custom pattern like `dd-MM-yyyy` (must contain exactly one each of `yyyy`, `MM`, `dd`) |
| `storage.minio.endpoint` | S3-compatible endpoint |
| `storage.minio.access_key` | Access key |
| `storage.minio.secret_key` | Secret key |
| `storage.minio.bucket` | Default bucket |
| `mail.api.base_url` | Mail provider API base URL |
| `mail.api.project_key` | Project / service key |
| `mail.api.username` | API user |
| `mail.api.password` | API password / token |
| `auth.registration.default_role_template_code` | Must match an existing **`role_template.code`** (case-insensitive). Used on **`POST /api/v1/auth/register`** to choose which **copied** tenant role is assigned to the new user. When absent, behavior defaults to **`ADMIN`**. See [`account-registration.md`](./account-registration.md). |

**PATCH validation:** unknown `role_template.code` → **400** (stable error code documented with implementation). Validator lives alongside existing platform settings validators.

## Data — `user_account.platform_superadmin`

Boolean column on `user_account` (default `false`). When `true`, the principal may call **platform** APIs documented in this file (`PlatformOperatorService`). For **tenant** HTTP APIs, the same flag is consulted inside `PermissionService.hasPrivilege` (see [`security.md`](./security.md)) so operators are not special-cased in controllers. **Break-glass** metadata and audit for sensitive tenant data remain a separate checklist item.

## API — operator (platform superadmin)

- **`GET /api/v1/platform/privileges/catalog`** — Authenticated; **no tenant context**. **403** if not platform superadmin. Response `data.entries`: `{ code, action, resource, description }[]` sorted by `code`.
- **`GET /api/v1/platform/settings`** — Authenticated; **no tenant context** required. **403** if not platform superadmin. Returns all rows (including secret values — restrict via role only).
- **`PATCH /api/v1/platform/settings`** — Same; body `{ "entries": [ { "key": "...", "value": "..." } ] }`; keys `[a-z0-9_.-]{1,128}`; values ≤ 2000. **`platform.date_format`** must be an allow-listed token **or** a custom pattern (`yyyy`, `MM`, `dd` each exactly once; separators only); unknown keys **400** (`PlatformIntegrationSettingsValidator` + billing validator).
- **`PUT /api/v1/platform/tenants/{tenantId}/privilege-pool`** — Removed from API surface. Route is not registered.
- **Tenant registry (list / create / rename):** see [`platform-tenant-admin.md`](./platform-tenant-admin.md) — **`GET/POST /api/v1/platform/tenants`**, **`GET/PATCH /api/v1/platform/tenants/{tenantId}`**.

## API — consumers (all signed-in users in tenant context)

Extend **`GET /api/v1/me`** (when `TenantContext` is present) with **non-secret** display fields, for example:

- `applicationName` — string (resolved: `application_name` → `product_name` → default)
- `dateFormat` — string, same value space as `platform.date_format` (token or custom pattern; or omit if unknown and client uses ISO fallback — prefer always set when key exists in DB/defaults)
- `publicBaseUrl` — string for link building in the client (from `platform.base_url` + defaults)

Exact JSON names must match `frontend` `MePayload` / API contract after implementation; type definitions live in `frontend/src/lib/api.ts` and backend DTO/Map in `MeController` or a small shared resolver service.

**Security:** no secrets (MinIO, mail password) in `/me`.

## API — unauthenticated (login / auth shell)

**Requirement:** unauthenticated pages that show the product name and need a **return / marketing base URL** must be able to read **at least** `applicationName` and `publicBaseUrl` **without** a session.

**Implemented:** **`GET /api/v1/platform/public-surface`** — **permitAll** in `SecurityConfiguration`; response `data`: **`applicationName`**, **`publicBaseUrl`**, **`dateFormat`** (non-secret). **No** PII, no secrets.

**Alternative:** only if the team agrees, Next.js read-only env for dev — **not** preferred when DB is source of truth for operators.

**CSRF:** safe for GET; no state change.

## Web

- **Route:** **`/app/platform-settings`** (existing). **Nav:** `NavigationController` injects item when `me.platformSuperadmin` (synthetic). **Layout:** `TenantAppShell` child.
- **Tabs:** General (application name, base URL, date format), MinIO, Mail — **i18n** for labels via message keys.
- **Save:** per-tab `PATCH` with only that tab’s keys (existing pattern).

## Mobile (Flutter)

- **Operator UI** for platform settings is **out of scope** unless product asks for a dedicated console app.
- **In scope for parity:** consume **`applicationName` / `dateFormat` / `publicBaseUrl`** from the same **authenticated** source as web (`/me` or a documented bootstrap) wherever Flutter shows dates and product title.

## Security (cross-cutting)

- Session + CSRF for mutating APIs (platform `PATCH` unchanged).
- Platform-only routes use `PlatformOperatorService.requirePlatformSuperadmin`.
- Successful **`PATCH`** to platform settings → **`audit_event`** `PLATFORM_SETTINGS_PATCHED`, **keys only** in metadata — [`audit.md`](./audit.md).

## Acceptance criteria (v1 of this “runtime” slice)

1. **MinIO:** `MinioStorageProperties` (or the storage gateway) resolves each of endpoint / access / secret / bucket from **`platform_setting`** when present, else from **`app.storage.minio.*`**. Document upload/complete still **503** when all four are blank after merge.
2. **Mail:** One service used for all sends; it resolves `mail.api.*` **DB → properties** (or env-backed properties).
3. **`GET /api/v1/me`** in tenant context returns **`applicationName`**, **`dateFormat`**, **`publicBaseUrl`** (exact names in implementation) derived from the same resolution rules; **not** the raw platform PATCH surface for non-admins.
4. **Unauthenticated** read for **`applicationName`** + **`publicBaseUrl`** via a **permitAll** `GET` (as above) used by **login** and **forgot-password** (or equivalent) pages; **CORS** unchanged for BFF.
5. **Base URL** joining: one documented normalization (trailing slash) and used in **email** link builders and client deep links.
6. **Web** shell / document title and **date** displays use the shared **name** and **date format** from **me** (or a single `DateFormat` provider).
7. **i18n:** new strings for platform settings page live under the project’s i18n files for **all** supported locales (or at least the same as nav).
8. **Regression:** `403` for non-superadmin on `GET/ PATCH /api/v1/platform/settings`; `PATCH` audit with keys only; unknown key **400**.

## Implementation pointers (code)

| Area | Location |
|------|-----------|
| Public surface API | `backend/src/main/java/com/wagepayroll/api/PlatformPublicSurfaceController.java` |
| Security `permitAll` | `backend/src/main/java/com/wagepayroll/security/SecurityConfiguration.java` (`GET /api/v1/platform/public-surface`) |
| Branding resolution (DB → props) | `backend/src/main/java/com/wagepayroll/settings/PlatformBrandingService.java`; URL join rule: `PlatformUrlJoin.java` |
| MinIO merge + gateway | `MinioSettingsMergeService.java` (`StorageState`: derived, not persisted), `MinioDocumentStorageGateway.java` (**503** `STORAGE_NOT_CONFIGURED` when state is not `STORAGE_READY`) |
| Mail merge + single outbound adapter | `MailApiProperties` (`app.mail.api.*` in `application.yml`), `MailApiSettingsMergeService.java`, `OutboundMailService.java` (implements `MailSendPort` + `PasswordResetMailPort`; HTTP POST `{baseUrl}/send` when all four mail keys resolved, else log-only) |
| `/me` tenant fields | `backend/src/main/java/com/wagepayroll/api/MeController.java` |
| Web: `/me` types + public fetch + URL join | `frontend/src/lib/api.ts`, `frontend/src/lib/public-url.ts`, `frontend/src/lib/user-date-format.ts` |
| Web: shell / auth branding | `frontend/src/components/shell/TenantAppShell.tsx`, `AuthSplitLayout.tsx`, `AuthMarketingPanel.tsx` |
| Web: platform settings i18n | `frontend/src/messages/nav.ts` (`platformSettings.*` keys), `frontend/src/app/app/platform-settings/page.tsx` |
| E2E | `frontend/e2e/m1-platform.spec.ts` (public surface on login) |

## Source of Truth Rules

- **Database (`platform_setting`)** is the only **authoritative** store for deployment configuration keys defined in this module.
- **Merge services** (`MinioSettingsMergeService`, `MailApiSettingsMergeService`, and branding resolution in `PlatformBrandingService`) are **deterministic transformation layers only**: they read DB first, then apply Spring `ConfigurationProperties` / env as **fallback** per key.
- **Spring properties** are **runtime cache values** bound from env or YAML — they are **not** persisted configuration and must **never** be treated as the source of truth when a row exists in `platform_setting` for the same key.
- **No service** may treat Spring properties alone as persisted configuration; effective values always come from the merge path documented here.

## Architecture Guardrails

- **Never** reintroduce logging-only or placeholder mail implementations alongside production sends; outbound mail goes through **`OutboundMailService`** implementing **`MailSendPort`** (and password reset port) with merge rules applied.
- **Never** bypass the platform-settings merge layer for keys owned by this module (MinIO, mail API, branding).
- **All** configuration for those concerns must flow through the platform-settings resolution path (DB → properties merge).
- The **`platform_setting`** table remains the **only** durable source of truth for stored keys; property files and env supply defaults and non-DB overrides only where merge rules allow.

## Proposed Schema Extension (requires PII review)

- No new tables required for this slice; **`platform_setting`** remains the store.
- Elevation TTL, second-person approval, or dedicated `platform_operator` join table for multi-user audit.
