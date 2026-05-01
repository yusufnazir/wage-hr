# Module: mail templates (platform catalog + i18n)

**Milestone:** M2 extension.  
**Preflight:** [SCHEMA-PERSISTENCE-PREFLIGHT](../guides/SCHEMA-PERSISTENCE-PREFLIGHT.md) + [DATA-MODEL-STANDARDS](../guides/DATA-MODEL-STANDARDS.md) + this file.  
**Related:** [notifications-inbox](./notifications-inbox.md) (notification row still has **no** bodies), [mail-adapter](./mail-adapter.md) (send-time only; this module adds **approved** catalog persistence), [i18n](./i18n.md) (locale tags).

---

## Design intent

- **Catalog** of reusable outbound email layouts: **subject + HTML body** per **locale**, edited by **platform superadmin** (same gate as `GET/PATCH /api/v1/platform/settings`).
- **v1 locales:** `en`, `nl` only. **`nl-sr`** (user preference) resolves to **`nl`** row for email content; if `nl` missing, fall back **`en`**; if `en` missing, use last-resort built-in copy in code (operational safety).
- **Rendered email** remains **ephemeral** at send time; **no** full body on `notification` rows (unchanged allowed column list).
- **`template_version` on `notification`:** unchanged in v1 emit paths; catalog carries its own **`content_version`** string bumped on each successful save for operator traceability (may be wired to notification rows in a later iteration).

---

## PII & content classification

| Store | Classification | Notes |
|-------|----------------|-------|
| `mail_template_locale.subject` | **low** | Operator copy; may include placeholders only (`{{tenantHandle}}`, `{{inviteLink}}`). **Do not** store direct employee PII in templates. |
| `mail_template_locale.body_html` | **low** | Same; treat as **operator-controlled HTML**. Sanitize **outbound** if product adds user-supplied merge fields later. |

---

## Data — `mail_template` (strict)

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `id` | UUID | no | PK |
| `code` | VARCHAR(64) | no | Stable code, e.g. `TENANT_INVITATION`. **Unique** in v1 (platform-only rows). |
| `content_version` | VARCHAR(32) | no | Bumped on each successful content save (deterministic string for audits). |
| `active` | BOOLEAN | no | Default `true`; inactive templates are not used for sends. |
| `created_at` | timestamp | no | |
| `updated_at` | timestamp | no | Used for **optimistic concurrency** on `PUT`. |

**v1.1 (deferred):** nullable `tenant_id` FK → `tenant.id` for tenant overrides; replace uniqueness with `(tenant_id, code)` via migration — not implemented in v1 DDL.

---

## Data — `mail_template_locale` (strict)

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `id` | UUID | no | PK |
| `mail_template_id` | UUID | no | FK → `mail_template.id` |
| `locale` | VARCHAR(8) | no | Allow-list: `en`, `nl` |
| `subject` | VARCHAR(500) | no | Trimmed; non-blank on save when locale row present |
| `body_html` | LONGTEXT | no | Trimmed; non-blank on save when locale row present |
| `created_at` | timestamp | no | |
| `updated_at` | timestamp | no | |

**Unique:** `(mail_template_id, locale)`.

**Validation (API):** `subject` ≤ 500 chars; `body_html` ≤ 256_000 chars; both locales required on **PUT** (replace full set).

---

## Template codes (v1)

| `code` | Used by | Placeholders |
|--------|---------|----------------|
| `TENANT_INVITATION` | `OutboundMailService.sendInvitationEmail` (when template active + locales present) | `{{tenantHandle}}`, `{{inviteLink}}` |
| `EMAIL_VERIFICATION` | `EmailVerificationMailPort` / `OutboundMailService.sendEmailVerificationLink` (registration + resend-verification) | `{{firstName}}`, `{{verifyLink}}`, `{{tenantHandle}}` |
| `PASSWORD_RESET_REQUEST` | `PasswordResetMailPort` / `OutboundMailService.sendPasswordResetLink` (forgot-password) | `{{firstName}}`, `{{resetLink}}`, `{{expiryMinutes}}` |

---

## API — platform superadmin

Base: `/api/v1/platform/mail-templates`  
**Auth:** session; **403** if not `platform_superadmin` (same as platform settings).

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/v1/platform/mail-templates` | List catalog rows: `id`, `code`, `contentVersion`, `active`, `updatedAt`. |
| `GET` | `/api/v1/platform/mail-templates/{id}` | One row + `locales[]` (`locale`, `subject`, `bodyHtml`). |
| `PUT` | `/api/v1/platform/mail-templates/{id}` | Replace `active` + all locales. Body: `{ "ifUpdatedAt": "<ISO-8601 instant>", "active": true, "locales": [ { "locale": "en", "subject": "...", "bodyHtml": "..." }, ... ] }`. **409** `MAIL_TEMPLATE_CONFLICT` if `ifUpdatedAt` ≠ current `updated_at`. **400** on validation errors. |

**Audit:** `PLATFORM_MAIL_TEMPLATES_UPDATED` with metadata `templateId`, `code` (no body).

---

## Send path integration

- `MailTemplateCatalogService` loads **active** template by `code`, resolves locale (`nl-sr` → `nl` → `en`), applies simple `{{key}}` replacement from a **Map** supplied by the caller.
- Invitation flow uses `TENANT_INVITATION` with `{{tenantHandle}}` and `{{inviteLink}}`.
- Email verification flow (registration + resend-verification) uses `EMAIL_VERIFICATION` with `{{firstName}}`, `{{verifyLink}}`, `{{tenantHandle}}`.
- Forgot-password flow uses `PASSWORD_RESET_REQUEST` with `{{firstName}}`, `{{resetLink}}`, `{{expiryMinutes}}`; `expiryMinutes` is sourced from the same runtime config used to write `password_reset_token.expires_at`.
- If catalog row missing, inactive, or no usable locale row (`nl`/`en`) → **fallback** to existing hard-coded English/plain text behavior in `OutboundMailService`.
- HTTP mail payload includes **`html`** when a rendered catalog body exists; otherwise it stays text-only.

---

## Web

- **Routes:** `/app/platform-mail-templates` (list), `/app/platform-mail-templates/{id}` (edit).
- **Gate:** `me.platformSuperadmin`; non-superadmin sees same pattern as platform settings (message + back link).
- **Edit UX:** tabs `en` / `nl`; subject field; **HTML source** textarea; **Preview** iframe with `sandbox=""` and `srcDoc` for isolated render.
- **i18n:** UI chrome uses `nav.platform_mail_templates` and keys under `mailTemplates.*` in `frontend/src/messages/nav.ts` (`en` + `nl`).

---

## Mobile

**N/A** until a Flutter app ships operator screens.

---

## Acceptance (summary)

- Superadmin can list, load, and update both locales; optimistic lock prevents silent overwrite.
- Non-operator **403** on all three endpoints.
- Invitation email uses catalog when valid; **no** notification schema drift; provider call receives optional `html`.

---

## Liquibase

- DDL: `schema-mail-templates-1.xml`
- Seed: `data-mail-templates-seed-1.xml` (one `TENANT_INVITATION` template + `en`/`nl` rows)
- Seed: `data-mail-templates-seed-2.xml` (`EMAIL_VERIFICATION` + `PASSWORD_RESET_REQUEST`, each with `en` + `nl` rows)

---

## Implementation pointers (repo)

| Area | Location |
|------|-----------|
| DDL / seed | `backend/src/main/resources/db/changelog/ddl/schema-mail-templates-1.xml`, `dml/data-mail-templates-seed-1.xml` |
| JPA entities | `backend/src/main/java/com/wagepayroll/domain/mailtemplate/` |
| Platform API | `backend/src/main/java/com/wagepayroll/api/PlatformMailTemplateController.java` |
| Catalog + admin service | `backend/src/main/java/com/wagepayroll/mail/MailTemplateCatalogService.java`, `PlatformMailTemplateService.java` |
| Invitation send integration | `backend/src/main/java/com/wagepayroll/mail/OutboundMailService.java`, `invitation/TenantInvitationService.java` |
| Navigation (superadmin) | `backend/src/main/java/com/wagepayroll/api/NavigationController.java` |
| Web list / edit | `frontend/src/app/app/platform-mail-templates/page.tsx`, `[id]/page.tsx` |
| API client | `frontend/src/lib/api.ts` (`fetchPlatformMailTemplates`, `fetchPlatformMailTemplate`, `putPlatformMailTemplate`) |
| i18n keys | `frontend/src/messages/nav.ts` (`nav.platform_mail_templates`, `mailTemplates.*`) |
| IT | `backend/src/test/java/com/wagepayroll/api/PlatformMailTemplatesIT.java` |
| E2E smoke | `frontend/e2e/m1-platform.spec.ts` (describe “Mail templates — platform list”) |
