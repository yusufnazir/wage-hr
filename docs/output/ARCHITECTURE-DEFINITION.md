# wage-payroll — System architecture

**Regenerated:** 2026-04-22 (Phase 1 / contract reconciliation: `PROJECT-CONTEXT.md` architecture contract + `BUILD-CHECKLIST.md` milestones + implemented Next.js BFF).

**Authority:** This document is derived from **`docs/prompts/PROJECT-CONTEXT.md`** (architecture contract — **wins on conflict**) and **`docs/product/BUILD-CHECKLIST.md`** (phased delivery + resolved decisions). Per-feature **allowed columns** and flows live in **`docs/modules/{feature-slug}.md`**.

---

## 0. Governance — keep this file from silently diverging

| Rule | Action |
|------|--------|
| **Precedence** | **`PROJECT-CONTEXT.md`** § *Architecture contract* overrides this file if they disagree until you regenerate. |
| **Regenerate after** | Any **material** edit to that contract block; any milestone that changes **global** tenancy, auth surfacing, or API edge shape; or when **`BUILD-CHECKLIST.md`** *Governance* calls for it. |
| **How** | Run **`docs/prompts/MASTER-FOUNDATION-TO-FEATURES.md`** Phase 1 (architecture pass), or manually reconcile every section below with the contract + checklist, then commit. |
| **Calendar review** | At least **quarterly**: diff this file against **`PROJECT-CONTEXT.md`** § *Architecture contract* (next suggested date **2026-07-22**). If only minor drift, patch in a PR; if scope shifted, update the **contract first**, then regenerate this file. |

Do **not** treat this path as optional documentation: prompts and scaffold assume **`docs/output/ARCHITECTURE-DEFINITION.md`** exists and is current (see **`docs/output/README.md`**).

---

## 1. Product identity & stack

**Project name:** wage-payroll

**One-line summary (from contract):** Multi-tenant HR/payroll SaaS: tenant-scoped operations, reporting, employee self-service, time and leave, country-based payroll engines, subscriptions and billing, and strong platform governance (privileges, audit, SuperAdmin).

**Product context (from `PROJECT-CONTEXT` identity table):** Multi-tenant HR/payroll SaaS for operations and reporting; customer admins manage org data per region; internal operations dashboard for tenants, billing, and support.

**Primary users:** Staff across **multiple tenants** with **different roles per tenant** (and per **business unit** where modeled); **tenant admins**; **employees** (ESS); **platform SuperAdmins** / internal ops.

**Stack:** Spring Boot (backend), Next.js (frontend), Flutter (mobile), MariaDB, **MinIO** (documents), **external HTTP mail API** (adapter). **Methodology:** `docs/guides/` (Liquibase, DATA-MODEL-STANDARDS, SCHEMA-PERSISTENCE-PREFLIGHT, privileges, routing, security, API conventions, E2E, theming).

**Local dev (this repo):** Spring Boot API **8300**, Next.js **3007**, `BASE_DOMAIN=lvh.me`, `AUTH_SUBDOMAIN=auth`, `APP_SUBDOMAIN=app` — see `docs/prompts/PROJECT-CONTEXT.md` § *Local development ports* and `docs/guides/LOCAL-DEV-PORTS.md`.

---

## 2. System overview

- **Purpose:** Multi-tenant **HR/payroll SaaS**: tenant-scoped operations, reporting, employee self-service, time and leave, **country-based payroll** (adapters; **Suriname first**), **subscriptions and billing** (PayPal + Stripe, usage/PAYG), documents (MinIO), notifications/inbox (PII-minimal), and platform governance (privileges, SuperAdmin, audit, controlled deletion and anonymization).
- **Key workflows (high level):** Register/login/forgot password on **auth** host; tenant resolution from host (with **BFF forwarding** in web dev — see §6); tenant picker when multiple memberships; **invitations** (M2); **RBAC** from tenant role grants plus feature/menu gating; **plan catalog and subscriptions** (M3); **billing** webhooks and reconciliation; **payroll runs** (M5+); **documents** (M4); **notifications** without persisted bodies; **audit** on sensitive actions; **SuperAdmin** catalog and break-glass.
- **v1 scope boundaries (contract — MANDATORY):**
  - **SaaS billing / subscriptions (PayPal + Stripe, usage-based):** **in product scope**, delivered per **`BUILD-CHECKLIST` milestone M3** (phased, not absent).
  - **Live bank / payroll disbursement** (employer money movement): **out of scope** (distinct from customer billing for the SaaS).
  - **OIDC / enterprise SSO:** **deferred** to **M7**; email/password (and session/token models) in earlier milestones.
  - **Regional / payroll rules:** **adapter boundary** — no single monolith “all countries” engine; **first full country: Suriname (SR)**; other countries as separate adapters/modules.
- **Compliance & data (contract):**
  - **GDPR-oriented:** export, erasure where required, documentation; legal applicability with counsel.
  - **Data residency:** single primary region per deployment (assumption) unless product decides multi-region.
  - **Retention:** default **≥10 years** for authoritative **business** and **audit** data unless law or contract forces otherwise; **controlled deletion** and **anonymization** per contract + `DATA-MODEL-STANDARDS`; **PII minimization** especially **notifications** (`docs/modules/notifications-inbox.md`).
  - **SuperAdmin** access to tenant confidential data: **break-glass only**, full audit + justification metadata.

---

## 3. Phased delivery (reference)

Aligned with **`docs/product/BUILD-CHECKLIST.md`** (detail and checkboxes there):

| Phase | Focus |
|-------|--------|
| **M0** | Repo, methodology, docs hub |
| **M1** | Platform skeleton: tenancy, auth, privileges, settings shell, i18n, menu API, audit, web theming/URLs, E2E baseline |
| **M2** | Invitations, mail adapter, notifications + inbox |
| **M3** | Plans, subscription state, gating, billing (PayPal + Stripe, usage/PAYG) |
| **M4** | Documents + MinIO + ACL sharing + record attachments |
| **M5** | Payroll/HR domain, country adapters, sandbox, gross/net/tax |
| **M6** | Flutter parity per shipped area |
| **M7** | SSO/OIDC |

**Explicitly out of product scope (unless contract amended):** live bank execution / payroll disbursement as money movement; **real-time multi-user document editing** (OT/CRDT). **Deferred:** OIDC/enterprise SSO until M7 foundations are stable.

---

## 4. Module breakdown

- **Identity & access:** Email/password, register, forgot password; **web:** session + CSRF (Spring) with **Next.js BFF** hiding API origin from browser (§6); **mobile:** tokens; future OIDC (M7).
- **Tenancy & routing:** Tenant handle subdomain, `app.*`, `auth.*`; membership; tenant picker; last-used tenant policy; **`TenantContextFilter`** uses **`X-Forwarded-Host`** when present (BFF) else **`Host`**.
- **Authorization:** Privilege catalog (global), tenant role grants, SuperAdmin data-driven “all privileges”; subscription contributes feature/menu gating via plan features.
- **Commercial (M3):** Plan feature codes (predefined in code + mirrored in DB), plan editor, subscription state, **PayPal + Stripe**, webhooks, reconciliation.
- **Messaging:** Outbound mail via **external HTTP API**; in-app notifications + inbox per **`notifications-inbox.md`**; **mail-adapter** — no persisted email bodies in app DB by default.
- **Documents (M4):** MinIO storage, metadata, ACL, share by user/role, **record↔document** links; no real-time collaborative editing.
- **Platform settings:** Global (SuperAdmin), per-tenant (tenant admin).
- **Navigation:** Menu tree in DB; visibility by privilege + subscription-driven flags.
- **Notifications domain:** Central **`NotificationService`** only writes notification rows; strict column set in module doc.
- **Core payroll / HR (M5+):** Org structure, employment/compensation history, employee + dependents (legal status per country), leave, time and attendance, ESS, **sandbox payroll**, gross/net/tax, **SR adapter first**.
- **Audit & compliance hooks:** Append-only audit; correlation id; compatible with anonymization (references/surrogates).
- **i18n:** API + web + Flutter.
- **API surface:** Versioned REST, problem+json, OpenAPI as team convention.
- **Clients:** Next.js (**BFF + relay cookies** for browser → Spring), Flutter (opaque refresh + access tokens).

**Cross-module dependencies:** Tenancy + authn before authorization; **commercial** feeds plan feature flags consumed by menu/feature gates; **notifications** depend on identity + mail port but **must not** store PII payloads; **documents** depend on MinIO and tenant ACL; payroll modules depend on tenancy, privileges, and country adapters.

---

## 5. Multi-tenancy strategy

- **`tenant_id`:** UUID on all tenant-scoped rows; present in server session/token claims and request context after resolution.
- **Resolution order (conceptual):** (1) Parse **`X-Forwarded-Host`** (set by the Next.js **BFF** when present) else **`Host`** / server name → reserved vs tenant handle → `tenant_id`; (2) other trusted forwarded headers per **`CROSS-CUTTING-SECURITY`** / `app.forwarding.trust-proxy` when behind an edge; (3) authenticated user’s memberships must include resolved tenant (else **403**), except auth/app-picker routes.
- **Subdomains:** `{tenantHandle}.{BASE_DOMAIN}`; **`{AUTH_SUBDOMAIN}`** (`auth`) for login/register/reset; **`{APP_SUBDOMAIN}`** (`app`) for tenant picker and tenants without handle; **`api.{BASE_DOMAIN}`** for REST at the **public** edge (contract). **Reserved list** env-driven (`auth`, `app`, `api`, `www`, `static`, etc.).
- **Local dev:** `*.lvh.me` with ports **3007** (Next) and **8300** (Spring); CORS and Playwright documented alongside **`LOCAL-DEV-PORTS.md`**.
- **Post-login redirect:** Tenant with handle → tenant host; without handle → `app.*`. **Open-redirect guard:** `GET /api/v1/auth/redirect-check` (also reachable via **`/api/bff/v1/auth/redirect-check`** from the browser).
- **Unknown tenant handle (public):** **404** on `/api/**` (anti-enumeration).
- **Multi-membership:** Picker on **app** host; optional last-used tenant cookie on **`.{BASE_DOMAIN}`**.
- **Handle rules:** Lowercase `a-z`, `0-9`, hyphen; length 3–32; normalized storage; globally unique among active tenants; rename policy per implementation.
- **Isolation:** Mandatory `tenant_id` predicate on tenant data; no cross-tenant application joins; SuperAdmin break-glass explicit code paths with audit.
- **Global tables:** Privilege catalog, plan feature definitions, platform config — per `DATA-MODEL-STANDARDS` (no silent extra columns).

---

## 6. Security architecture

### 6.1 Privilege model

- **PRIVILEGE = ACTION + RESOURCE**; stable **UPPER_SNAKE** codes in DB; enforced on every sensitive endpoint.
- Examples (illustrative): `USER_INVITE`, `USER_VIEW`, `USER_EDIT`, `ROLE_MANAGE`, `TENANT_SETTINGS_EDIT`, `NOTIFICATION_VIEW`, `NOTIFICATION_MARK_READ`, `SUBSCRIPTION_MANAGE`, `BILLING_MANAGE`, `DOCUMENT_VIEW`, `PAYROLL_RUN_CREATE`, `AUDIT_VIEW`, `BREAK_GLASS_APPLY`, …

### 6.2 SuperAdmin

- Effective **all** privileges via **catalog attachment** or equivalent data-driven rule; **same** authorization API as tenants — **no** `if (superAdmin) return true` in controllers.

### 6.3 Privilege catalog and role grants

- **Global catalog:** all product privileges (Liquibase-seeded).
- **Tenant roles:** tenant admins assign catalog privileges to roles via `role_privilege`.

### 6.4 Enforcement strategy

- **Backend:** Authn → tenant resolution → **privilege** check (role grants / superadmin elevation) → handler. Deny by default.
- **Frontend/mobile:** Effective permissions from API; UI hide/disable only.
- **Web session (Spring):** Session + **CookieCsrfTokenRepository** for unsafe HTTP methods. **Browser (wage-payroll web):** does **not** call Spring directly; Next.js **Route Handler** `**/api/bff/**` proxies to Spring using server-only **`API_BASE_URL`**, prefetches **`GET /api/v1/auth/csrf`** before mutating verbs, forwards **`X-Forwarded-Host`** from the browser `Host`, and sets HttpOnly relay cookies **`wp_bff_j` / `wp_bff_x`** (`Domain=.lvh.me` in `*.lvh.me` dev by default). See `docs/modules/tenant-web-vertical-slice.md` §3.3, `frontend/.env.example`.
- **Flutter:** Short-lived access + refresh (opaque preferred); no cross-site cookie reliance on Spring from the mobile app.
- **Logout:** Server-side session invalidation (+ refresh revocation as applicable).
- **Return URL / redirects:** Allowlist `*.BASE_DOMAIN` and safe schemes; no raw user-controlled `Location`.

### 6.5 Host-based route constraints

- **`auth.*`:** login, register, forgot password, static auth assets.
- **Tenant / `app.*`:** main app HTML; **tenant APIs** consumed by browser **via BFF** on the same Next origin in current implementation.
- **Guards:** Next middleware + Spring security / host guards so tenant APIs reject wrong host class.

### 6.6 Commercial model & plan-based entitlements

- **Subscriptions and plans:** **Yes** (M3). Predefined **plan feature codes** in code mirrored in DB.
- **Active subscription (contract):** Sets **feature flags** for gating and menu. Resolver order: **authn → tenant → privilege → handler** for authorization, with plan features evaluated where feature/menu gates are configured.
- **Billing:** **PayPal** + **Stripe**, usage/PAYG; webhooks; PCI boundaries per provider — **`docs/modules/commercial-billing.md`** when implemented.

---

## 7. Data model (high level)

**Standards:** UUID `id`; tenant-scoped tables include `tenant_id`, `created_at`, `updated_at` per **`DATA-MODEL-STANDARDS`**. **Liquibase-only** DDL; **`SCHEMA-PERSISTENCE-PREFLIGHT`** + **one `docs/modules/{feature-slug}.md`** per feature.

**Core platform entities (illustrative):** `tenant`, `user`, `user_tenant_membership`, `role`, `privilege`, `role_privilege`, `user_role`, session/refresh, `platform_setting`, `tenant_setting`, menu tables, `audit_event`, `invitation`.

**Commercial (M3):** `plan_definition`, `plan_feature`, `subscription`, metering/billing tables — per module docs before implementation.

**Notifications:** **`notifications-inbox.md`** allowed columns only.

**Documents (M4):** `document`, `document_share`, `entity_document_link`.

**Payroll / HR (M5+):** Suriname-first adapter shape per payroll modules.

---

## 8. API structure & web BFF

- **Contract (target public origin):** **`https://api.{BASE_DOMAIN}/v1/`** for versioned REST (single public base; errors problem+json per **`API-CONVENTIONS`**).
- **Implementation (Spring Boot in repo):** Controllers are mounted at **`/api/v1/...`**. A production ingress may rewrite the public path to match the contract, or the contract URL may be refined — **do not** silently assume the edge strips `/api` without documenting it.
- **Implementation (Next.js web):** The browser calls **`/api/bff/v1/...`** on the **Next** origin only; the server forwards to **`API_BASE_URL`** (e.g. `http://127.0.0.1:8300` in development). Spring CORS for `*.lvh.me:3007` remains relevant for **direct** API access (tools, future clients), not for default UI traffic.
- **Success envelope:** `{ "data": …, "meta": { "requestId": … } }` where used.
- **Errors:** RFC 7807 **problem+json**; stable `code` / correlation ids; no stack traces in prod.
- **Proxy trust:** When Spring sits behind a trusted edge, set forward-headers strategy and **`app.forwarding.trust-proxy`** per **`CROSS-CUTTING-SECURITY`**. The BFF always sets **`X-Forwarded-Host`** from the incoming browser host for tenant resolution.
- **Rate limits:** Login and password reset throttled (per `application.yml`); **429** with generic message.

### 8.1 Web CRUD view convention

- **Default pattern for new web CRUD features:** build a **listing page** and separate **create/edit pages** (route-based forms), instead of modal-heavy CRUD.
- **Why:** keeps URLs shareable/bookmarkable, improves validation and accessibility, and scales better for larger forms.
- **Routes:** list at `/app/{resource}`; create at `/app/{resource}/new`; edit at `/app/{resource}/{id}/edit`.
- **Modals:** reserved for lightweight, single-purpose actions (confirmations, tiny patches), not full entity forms.

---

## 9. Key design decisions

- **Contract wins:** This file follows **`PROJECT-CONTEXT.md`**; if an older paragraph here disagrees, **fix this file** or update the contract first.
- **Phased delivery:** M1 → M2 → … → M7 as in **`BUILD-CHECKLIST.md`**.
- **Suriname:** First full payroll country adapter.
- **Notifications:** **`NotificationService`** only writer; shape locked by **`notifications-inbox.md`**.
- **Theming:** Tokens per **`WEB-THEMING-AND-DESIGN-SYSTEM`**; auth shell glass/split presentation on web.
- **Schema governance:** **`DATA-MODEL-STANDARDS`** + **`SCHEMA-PERSISTENCE-PREFLIGHT`**; **`## Proposed Schema Extension (requires PII review)`** for non-allowed columns.
- **Dual gating:** Pool + feature flags — complexity accepted per contract.

---

## 10. Migration strategy (Liquibase)

- **Layout:** `backend/src/main/resources/db/changelog/` with **`ddl/`** and **`dml/`**, master changelog per **`LIQUIBASE-RULES.md`**.
- **DDL vs DML:** DDL in XML; DML via **Java `CustomDataTaskChange`** — no raw SQL DML in changelogs.
- **IDs:** `schema-{entity}-{n}` / `data-{entity}-{n}`; **append-only** — never edit executed changesets.

---

*End of architecture document. **Regenerate** per §0 when the contract or global API/auth/tenancy assumptions change; next calendar review suggested **2026-07-22**.*
