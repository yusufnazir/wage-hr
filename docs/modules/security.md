# Module: Security infrastructure (scaffold)

## Privilege model

- **Privilege** = catalog row (`privilege.code`, e.g. `USER_VIEW`, `USER_EDIT`, `TENANT_SETTINGS_EDIT`, `DOCUMENT_VIEW`, `DOCUMENT_EDIT`) plus **action + resource** in application code (`DefinedPrivilege` enum — must stay in sync with Liquibase rows; see `PrivilegeCatalogSyncIT`).
- **Tenant privileges:** role-based grants from `role_privilege` via assigned tenant roles (`user_role`). **`GET /api/v1/tenant/privileges/pool`** lists the sorted global privilege catalog for role editor UIs (`@RequiresPrivilege("TENANT_SETTINGS_EDIT")`).
- **Roles:** `role` per tenant; `role_privilege` grants privileges to roles; `user_role` assigns roles to users in a tenant.
- **Platform SuperAdmin and tenant APIs:** `user_account.platform_superadmin` is read from the database by `PermissionService`. For tenant-scoped `@RequiresPrivilege`, a platform superadmin satisfies checks for privileges registered in `privilege` when normal role grants deny (`PrivilegeGrant.SUPERADMIN_ELEVATED` — same `PermissionService` path, no controller shortcut). **`GET /api/v1/me` → `privileges`** lists role-derived tenant privileges; **`planFeatureCodes`** lists plan feature codes for **`ACTIVE`** subscription (see [`commercial-subscriptions.md`](./commercial-subscriptions.md)).
- **Break-glass (M1):** For **mutating** HTTP methods (`POST`, `PUT`, `PATCH`, `DELETE`) on `@RequiresPrivilege` routes when access is **only** via superadmin elevation, clients **must** send header **`X-Break-Glass-Reason`** (trimmed length **3–500**). Missing or invalid length → **403** (`BREAK_GLASS_REASON_REQUIRED` / `BREAK_GLASS_REASON_LENGTH`). **GET/HEAD** elevated reads do not require the header. After a **successful** handler, **`PrivilegeAuthorizationAspect`** appends **`SUPERADMIN_TENANT_ELEVATED_ACCESS`** with metadata: `privilege`, `method`, `path`, and for mutating requests `reasonLength` + `reasonSha256` (UTF-8 SHA-256 hex of the justification — not the raw text in audit metadata).
- **Platform operator APIs:** `platform_superadmin` gates **`GET /api/v1/platform/privileges/catalog`**, **`/api/v1/platform/settings`**, **`GET/POST /api/v1/platform/tenants`**, and **`GET/PATCH /api/v1/platform/tenants/{tenantId}`** (see [`platform-settings.md`](./platform-settings.md), [`platform-tenant-admin.md`](./platform-tenant-admin.md)).

## Enforcement

- **Annotation:** `@RequiresPrivilege("CODE")` — AOP (`PrivilegeAuthorizationAspect`) calls `PermissionService.hasPrivilege(userId, tenantId, code)`; requires `TenantContext` with `tenant_id` except where endpoints are explicitly global (future).
- **HTTP:** Spring Security session for API; CORS + CSRF cookie/header for browser flows.
- **Headers:** baseline security headers via `SecurityHeadersFilter` (`Content-Security-Policy`, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`).
- **Abuse:** `LoginAttemptService` — in-memory lockout by IP+email against login (configurable thresholds).

## Liquibase

- **DDL:** `backend/src/main/resources/db/changelog/ddl/schema-bootstrap-1.xml` — changeset ids `schema-*-1`.
- **DML:** `backend/src/main/resources/db/changelog/dml/data-scaffold-1.xml` — changeset id `data-scaffold-1`, class `com.wagepayroll.liquibase.task.DataScaffoldSeed1` (CustomTaskChange / methodology CustomDataTaskChange pattern).

## Redirects & proxy

- **Redirects:** `RedirectUrlValidator` — same-site / relative `returnTo` only (see `backend/.../security/RedirectUrlValidator.java`).
- **Forwarded headers:** `server.forward-headers-strategy` from env; document `TRUST_PROXY` + Nginx in deployment (see `docs/guides/CROSS-CUTTING-SECURITY.md`).

## Audit (cross-cutting)

- Sensitive **state-changing** APIs append **`audit_event`** rows via `AuditService` (see [`audit.md`](./audit.md)); request **correlation id** from `RequestIdFilter` when present.

## Tests

- `backend/src/test/java/com/wagepayroll/api/DemoPrivilegedEndpointIT.java` — `USER_VIEW` gated route with/without tenant host and role coverage.
- `backend/src/test/java/com/wagepayroll/security/PrivilegeCatalogSyncIT.java` — Liquibase `privilege` rows ↔ `DefinedPrivilege` enum.
- `backend/src/test/java/com/wagepayroll/api/PlatformPrivilegeCatalogIT.java` — platform catalog **403/200**.
- `backend/src/test/java/com/wagepayroll/api/TenantPrivilegePoolIT.java` — tenant pool listing.
- `backend/src/test/java/com/wagepayroll/api/TenantDocumentsIT.java` — `DOCUMENT_VIEW` hub (`GET /api/v1/tenant/documents`).
- `backend/src/test/java/com/wagepayroll/api/TenantDocumentsUploadDownloadIT.java` — presigned upload session + complete + download URL.
- `backend/src/test/java/com/wagepayroll/api/TenantDocumentsStorageDisabledIT.java` — **503** when MinIO properties unset.
- `backend/src/test/java/com/wagepayroll/api/TenantDocumentSharesAndAttachmentsIT.java` — document shares + attachments API.
- `backend/src/test/java/com/wagepayroll/api/TenantDocumentSoftDeleteIT.java` — **`DELETE …/tenant/documents/{id}`** soft delete + hub.
- `backend/src/test/java/com/wagepayroll/api/SuperadminTenantPrivilegeIT.java` — platform superadmin may use tenant-gated mutating API on a narrow-role tenant **with** `X-Break-Glass-Reason`.
- `backend/src/test/java/com/wagepayroll/audit/SuperadminBreakGlassAuditIT.java` — elevation audit + reason hash.
- `backend/src/test/java/com/wagepayroll/api/MePrivacyIT.java` — subject export + erasure-request stub.
