# Module doc index (`docs/modules/`)

Convention: one file per vertical slice — [`../guides/MODULE-DOC-CONVENTION.md`](../guides/MODULE-DOC-CONVENTION.md). Create or expand a row’s module file **when that milestone is about to start**, not all at once (avoids stale specs).

| Slug (file) | Milestone | Status | Notes |
|-------------|-----------|--------|--------|
| `auth.md` | M1 | Exists | Login, logout, CSRF, password reset; register/verify → [`account-registration.md`](../modules/account-registration.md) |
| `account-registration.md` | M1 | Exists | Self-service register (email, password, **tenant handle**), **email verification** before login, **`auth.registration.default_role_template_code`** — [`../modules/account-registration.md`](../modules/account-registration.md) |
| `user.md` | M1 | Exists | Profile, locale, preferences |
| `security.md` | M1 | Exists | Privileges, pools, SuperAdmin |
| `web-auth-session.md` | M1 | Exists | Cookie/session, CSRF, BFF |
| `tenant-web-vertical-slice.md` | M1 | Exists | Tenant **`/app`** shell (sidebar + **`GET .../me/navigation`**, user menu, split auth layout) — [`../modules/tenant-web-vertical-slice.md`](../modules/tenant-web-vertical-slice.md) §3.6 |
| `tenancy-routing.md` | M1 | Exists | Host modes, unknown tenant + `X-Tenant-Id` HTTP rules — [`../modules/tenancy-routing.md`](../modules/tenancy-routing.md) |
| `invitations.md` | M2 | Exists | v1: create/list pending, token accept, `USER_INVITE`; [`../modules/invitations.md`](../modules/invitations.md) |
| `i18n.md` | M1 | Exists | `preferred_locale`, `/me` + `PATCH /me/locale`, client bundles — [`../modules/i18n.md`](../modules/i18n.md) |
| `navigation-menu.md` | M1 | Exists | `nav_menu_item` (+ M3 **`required_plan_feature_code`** gating), `GET /api/v1/me/navigation` — [`../modules/navigation-menu.md`](../modules/navigation-menu.md) |
| `platform-settings.md` | M1 | Exists | `platform_setting`, `platform_superadmin`, platform settings API — [`../modules/platform-settings.md`](../modules/platform-settings.md) |
| `platform-tenant-admin.md` | M1 | Exists | Platform superadmin **tenant registry** list + create + rename (`tenant` row); see [`../modules/platform-tenant-admin.md`](../modules/platform-tenant-admin.md) |
| `tenant-settings.md` | M1 | Exists | `tenant_setting`, tenant settings API — [`../modules/tenant-settings.md`](../modules/tenant-settings.md) |
| `audit.md` | M1 | Exists | `audit_event`, `AuditService` append path; wired to settings + locale PATCH — [`../modules/audit.md`](../modules/audit.md) |
| `data-lifecycle.md` | M1 | Exists | PII inventory, `GET /me/privacy/export`, erasure-request stub — [`../modules/data-lifecycle.md`](../modules/data-lifecycle.md) |
| `mail-adapter.md` | M2 | Exists | Send-time only; v1 **no** extra mail tables — `external_message_id` on notification; [`../modules/mail-adapter.md`](../modules/mail-adapter.md) |
| `notifications-inbox.md` | M2 | Exists | **Canonical PII-strict spec:** entity, services, flow, APIs — [`../modules/notifications-inbox.md`](../modules/notifications-inbox.md) |
| `mail-templates.md` | M2 | Exists | Platform catalog: HTML + i18n (`en`/`nl`) for outbound mail; [`../modules/mail-templates.md`](../modules/mail-templates.md) |
| `documents-minio.md` | M4 | Exists | MinIO/S3 layout, presign phases, `tenant_document` + attachment table — [`../modules/documents-minio.md`](../modules/documents-minio.md) |
| `document-sharing.md` | M4 | Exists | ACL, hub semantics, `document_share` — [`../modules/document-sharing.md`](../modules/document-sharing.md) |
| `commercial-plans.md` | M3 | Complete (M3 v1) | Catalog + **commercial plan** create/replace/**delete unused** (compose from `plan_feature` only); optional Stripe price id on plan — [`../modules/commercial-plans.md`](../modules/commercial-plans.md) |
| `commercial-subscriptions.md` | M3 | Complete (M3 v1) | v1: `tenant_subscription` + platform assign + `GET /me/subscription`; pool/flag materialization **deferred** — [`../modules/commercial-subscriptions.md`](../modules/commercial-subscriptions.md) |
| `commercial-billing.md` | M3 | Complete (M3 v1) | Webhooks; tenant **`GET .../billing/summary`** (**`USER_VIEW`**) + **`GET .../billing/commercial-plans`** (**`TENANT_SETTINGS_EDIT`**); Stripe/PayPal sessions; Next.js **`/app`** billing card; **`billing_usage_event`** POST + **`billing_usage_aggregate`** + **`GET .../usage-aggregates`**; `billing_provider_link`; minimal **`tenant_subscription`** webhook reconcile; dev redirect rules + **`DEMO_STARTER`** seed; **post-M3:** meter push, full B3 reconcile — [`../modules/commercial-billing.md`](../modules/commercial-billing.md) |
| `platform-countries.md` | M5 | Exists | Platform-managed ISO 3166-1 country catalog; superadmin CRUD + tenant read-only; en/nl translations; ISO seed — [`../modules/platform-countries.md`](../modules/platform-countries.md) |
| `platform-bank-templates.md` | M5 | Exists | Platform-managed bank template catalog per country; superadmin CRUD; tenant copy seeded on company create (based on `payroll_country`); tenant admins customise their copies — [`../modules/platform-bank-templates.md`](../modules/platform-bank-templates.md) |
| `payroll-reference-data.md` | M5 | Exists | Exchange rates (full spec — see [`../modules/payroll-reference-data.md`](../modules/payroll-reference-data.md)); countries stub **superseded by `platform-countries.md`**; BUs, BU-scoped roles still stubs |
| `organization-employment.md` | M5 | Planned | Org structure, employment + compensation history |
| `employee-master-dependents.md` | M5 | Planned | Employee, partners, children; country-specific legal enums |
| `leave-management.md` | M5 | Planned | Leave policies, requests, approvals |
| `time-and-attendance.md` | M5 | Planned | Time entries, devices/imports TBD |
| `payroll-engine-country.md` | M5 | Planned | Per-country adapters; **first full: Suriname (SR)**; sandbox; gross/net/tax |
| `employee-self-service.md` | M5 | Planned | ESS portal capabilities and privileges |
| `sso-oidc.md` | Later | Planned | Future iteration; session + tenancy must be stable first |

**Status:** *Planned* = no module file yet or stub only; *Exists* = file in repo (update as you ship); *Complete (M3 v1)* = milestone acceptance met for that vertical (see [`BUILD-CHECKLIST.md`](./BUILD-CHECKLIST.md) Milestone M3).
