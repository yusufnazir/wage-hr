# Module doc index (`docs/modules/`)

Convention: one file per vertical slice — [`../guides/MODULE-DOC-CONVENTION.md`](../guides/MODULE-DOC-CONVENTION.md). Create or expand a row’s module file **when that milestone is about to start**, not all at once (avoids stale specs).

| Slug (file) | Milestone | Status | Notes |
|-------------|-----------|--------|--------|
| `auth.md` | M1 | Exists | Extend for register / forgot-password flows as built |
| `user.md` | M1 | Exists | Profile, locale, preferences |
| `security.md` | M1 | Exists | Privileges, pools, SuperAdmin |
| `web-auth-session.md` | M1 | Exists | Cookie/session, CSRF, BFF |
| `tenant-web-vertical-slice.md` | M1 | Exists | Demo path — evolve into real tenancy shell |
| `tenancy-routing.md` | M1 | Exists | Host modes, unknown tenant + `X-Tenant-Id` HTTP rules — [`../modules/tenancy-routing.md`](../modules/tenancy-routing.md) |
| `invitations.md` | M2 | Exists | v1: create/list pending, token accept, `USER_INVITE`; [`../modules/invitations.md`](../modules/invitations.md) |
| `i18n.md` | M1 | Exists | `preferred_locale`, `/me` + `PATCH /me/locale`, client bundles — [`../modules/i18n.md`](../modules/i18n.md) |
| `navigation-menu.md` | M1 | Exists | `nav_menu_item`, `GET /api/v1/me/navigation` — [`../modules/navigation-menu.md`](../modules/navigation-menu.md) |
| `platform-settings.md` | M1 | Exists | `platform_setting`, `platform_superadmin`, platform settings API — [`../modules/platform-settings.md`](../modules/platform-settings.md) |
| `tenant-settings.md` | M1 | Exists | `tenant_setting`, tenant settings API — [`../modules/tenant-settings.md`](../modules/tenant-settings.md) |
| `audit.md` | M1 | Exists | `audit_event`, `AuditService` append path; wired to settings + locale PATCH — [`../modules/audit.md`](../modules/audit.md) |
| `data-lifecycle.md` | M1 | Exists | PII inventory, `GET /me/privacy/export`, erasure-request stub — [`../modules/data-lifecycle.md`](../modules/data-lifecycle.md) |
| `mail-adapter.md` | M2 | Exists | Send-time only; v1 **no** extra mail tables — `external_message_id` on notification; [`../modules/mail-adapter.md`](../modules/mail-adapter.md) |
| `notifications-inbox.md` | M2 | Exists | **Canonical PII-strict spec:** entity, services, flow, APIs — [`../modules/notifications-inbox.md`](../modules/notifications-inbox.md) |
| `documents-minio.md` | M4 | Planned | Buckets, metadata, ACL; **record↔document links** (any entity) |
| `document-sharing.md` | M4 | Planned | ACL by user/role; hub (mine + shared); no real-time collab |
| `commercial-plans.md` | M3 | Planned | Plan feature catalog (code + DB), plan editor |
| `commercial-subscriptions.md` | M3 | Planned | Subscription lifecycle; materializes **wider privilege pool** + **feature flags** |
| `commercial-billing.md` | M3 | Planned | **PayPal** + **Stripe**; usage/PAYG; webhooks; reconciliation |
| `payroll-reference-data.md` | M5 | Planned | Countries, currencies, business units, BU-scoped roles |
| `organization-employment.md` | M5 | Planned | Org structure, employment + compensation history |
| `employee-master-dependents.md` | M5 | Planned | Employee, partners, children; country-specific legal enums |
| `leave-management.md` | M5 | Planned | Leave policies, requests, approvals |
| `time-and-attendance.md` | M5 | Planned | Time entries, devices/imports TBD |
| `payroll-engine-country.md` | M5 | Planned | Per-country adapters; **first full: Suriname (SR)**; sandbox; gross/net/tax |
| `employee-self-service.md` | M5 | Planned | ESS portal capabilities and privileges |
| `sso-oidc.md` | Later | Planned | Future iteration; session + tenancy must be stable first |

**Status:** *Planned* = no module file yet or stub only; *Exists* = file in repo (update as you ship).
