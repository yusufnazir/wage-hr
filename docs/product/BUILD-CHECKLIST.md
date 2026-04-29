# Build checklist — wage-payroll

Use this as the **single progress tracker** for greenfield work. Check items when **done in all agreed clients** (backend + web where applicable + mobile when that milestone includes Flutter), not when “started”.

**Before each milestone:** ensure the matching **module doc** in [MODULE-INDEX.md](./MODULE-INDEX.md) exists and has acceptance criteria, then run `MASTER-FEATURE-END-TO-END` (or manual equivalent) with `@docs` + repo root.

---

## Governance (documentation)

- [x] **Architecture contract** in `docs/prompts/PROJECT-CONTEXT.md` matches commercial and payroll intent (updated).
- [x] **Regenerate** `docs/output/ARCHITECTURE-DEFINITION.md` via Phase 1 (MASTER-FOUNDATION) so it no longer contradicts the contract — *until then, contract wins*. **Last full regeneration:** 2026-04-22 (see **`docs/output/ARCHITECTURE-DEFINITION.md`** §0 for quarterly review date).
- [ ] **Liquibase / API / privilege** work follows `docs/guides/` (no drift) — *re-check when touching backend migrations or APIs*.
- [ ] **Schema discipline:** follow **`docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md`** before any schema/entity/repo change; **`docs/guides/DATA-MODEL-STANDARDS.md`** (justification, PII class, retention, Liquibase only); **no** silent or “helpful” extra fields; strict tables use **allowed lists** in module docs + **`## Proposed Schema Extension (requires PII review)`** for ideas; post-change check *does this violate DATA-MODEL-STANDARDS or the module allowed list?*

---

## Resolved decisions (do not re-litigate without a contract change)

| Topic | Decision |
|-------|----------|
| **Billing** | **PayPal** and **Stripe** in scope from the start of M3; use each provider’s **pay-as-you-go / usage-based** billing primitives as documented in their APIs (metering, invoices, webhooks — detail in `commercial-billing` module). Support **both** so tenants or the platform can choose; exact “default” per deployment is env/config. |
| **Document sharing** | **No** real-time collaborative editing (no OT/CRDT). **Simple** ACL: share a document with **specific users** and/or **roles** (within tenant). **Document hub:** each user sees **their uploads** and **documents shared with them**. **Record attachments:** any business data record can **link** one or more documents (polymorphic or typed link table — specify in `documents-minio` / `document-sharing` modules). |
| **First payroll country** | **Suriname** — first full payroll adapter (rules, tax, layout, legal enums for dependents where applicable). Other countries remain stubs or later adapters. |
| **Subscription vs gating** | An **active subscription** widens the tenant **privilege pool** (what roles may grant) **and** drives **feature flags** (product toggles separate from named privileges). Enforcement uses **both** layers; exact resolver order in architecture: typically authn → tenant → subscription → effective pool + flags → privilege check on handler. |
| **Inbox** | **In-app notifications** plus a unified place for **any message the system sends** (invitation, password reset, billing notices, etc. — surfaced in-app). **Not** external mailbox threading. **Minimize stored PII in message rows:** prefer template/event keys, severity, timestamps, opaque correlation ids, and **references** to the mail provider or job — **not** full email bodies, secrets, reset tokens, or national identifiers in inbox/notification tables unless strictly necessary (then encrypt + tight TTL per `notifications-inbox`). |
| **Retention / deletion / PII** | **Default long retention:** keep authoritative business and **audit** data **at least 10 years** unless law or contract forces earlier action. **Controlled deletion:** support **policy-driven**, **privileged**, **audited** removal or tombstone of eligible rows (never silent bulk drop of audit). **Anonymization:** first-class flows to **irreversibly strip or hash direct identifiers** on person-linked records where deletion is not allowed but identification must end (document which entities support which mode in data model + module docs). **Privacy by design:** **minimize PII** everywhere; **especially messages** — see inbox row and `mail-adapter` / `notifications-inbox`. **Export and erasure** paths remain required for subjects/tenants where policy applies. |

---

## Open decisions

*None pending.* Revisit with legal/DPA for **jurisdiction-specific** minimum/maximum retention, **which** entities may be deleted vs only anonymized, and **tenant-initiated** erasure SLAs.

---

## Milestone M0 — Repo and methodology

- [x] Repo root opens in Cursor as **product root** (contains `docs/`, `backend/`, frontend app folder — names per architecture).
- [x] `docs/guides/` present and indexed; `docs/templates/` available for prompts.
- [x] `docs/product/` (this hub) linked from `docs/guides/README.md`.
- [x] Local ports and `BASE_DOMAIN` documented consistently (`PROJECT-CONTEXT` + `LOCAL-DEV-PORTS` + `ARCHITECTURE-DEFINITION.md` intro + env examples).

---

## Milestone M1 — Platform skeleton (ship first)

**Goal:** Any authenticated user can land in the correct tenant context with **correct host rules**, **privilege enforcement**, and **consistent theming / URLs**.

- [x] **Tenancy & routing:** `auth.*` vs `app.*` vs `{tenant}.*`; unknown handle → **404** on `/api/**` with **Problem+JSON**; `X-Tenant-Id` validation on **API** host; env for subdomains — *see [`docs/modules/tenancy-routing.md`](../modules/tenancy-routing.md) + `TenantContextFilter`; guide [`MULTI-TENANCY-AND-ROUTING.md`](../guides/MULTI-TENANCY-AND-ROUTING.md)*.
- [x] **Register / login / forgot password** (email/password); rate limits per security guides — *register + forgot + reset implemented; login existed; see `docs/modules/auth.md` and `password_reset_token` Liquibase*.
- [x] **Multi-tenant membership**; user can belong to many tenants; **roles can differ per tenant** (and later per business unit where modeled) — *`GET /api/v1/me/tenants`, seeded second tenant **`acme`** (admin = Reader, narrower pool than `demo`); web tenant switcher on `/app`; `DataM1SecondTenantAcmeSeed1`*.
- [x] **Privileges:** action+resource catalog; tenant pool; SuperAdmin effective-all via data path (no controller shortcuts); CRUD mapped to privileges (`PRIVILEGE-MODEL`) — *`DefinedPrivilege` + `PrivilegeCatalogSyncIT`; `GET /api/v1/platform/privileges/catalog`; `GET /api/v1/tenant/privileges/pool`; `PermissionService` reads `platform_superadmin` for `hasPrivilege` (membership + globally registered code); `/me` `privileges` unchanged (role+pool); SuperAdmin pool **assignment** API remains under tenant-admin checklist*.
- [x] **SuperAdmin** + break-glass rules + audit when SuperAdmin touches tenant data (policy from contract) — *`X-Break-Glass-Reason` (3–500 chars) required on mutating `@RequiresPrivilege` when `PrivilegeGrant.SUPERADMIN_ELEVATED`; `SUPERADMIN_TENANT_ELEVATED_ACCESS` audit after success; `SuperadminTenantPrivilegeIT` + `SuperadminBreakGlassAuditIT`*.
- [x] **Tenant admin** constrained to **allowed privilege pool**; pool assignment by SuperAdmin — *`PUT /api/v1/platform/tenants/{tenantId}/privilege-pool` + `TENANT_PRIVILEGE_POOL_REPLACED` audit; `PlatformTenantPrivilegePoolIT`*.
- [x] **Global platform settings** (SuperAdmin) + **tenant settings** (tenant admin) — minimal schema + APIs — *`platform_setting`, `tenant_setting`, `user_account.platform_superadmin`; `GET`/`PATCH` `/api/v1/platform/settings`, `GET`/`PATCH` `/api/v1/tenant/settings`; see `platform-settings.md` + `tenant-settings.md`*.
- [x] **i18n plumbing:** locale resolution, message key strategy, shared contract for web + Flutter — *`user_account.preferred_locale`, `GET /api/v1/me` → `locale`, `PATCH /api/v1/me/locale`; web `messages/nav` + `SetHtmlLang`; see [`docs/modules/i18n.md`](../modules/i18n.md)*.
- [x] **Application menu structure** stored in datamodel; API returns effective menu for principal + tenant + entitlements placeholder — *`nav_menu_item`, `GET /api/v1/me/navigation`; feature-flag column deferred — `navigation-menu.md`*.
- [x] **Web:** unique URL per layout; deep-linkable routes; **light/dark** + **custom theme** tokens (`WEB-THEMING-AND-DESIGN-SYSTEM`); **app shell v1** + split auth marketing layout — *primitives + semantic CSS vars + Tailwind map; `next-themes` + FOUC script; `AuthShell` / `AuthSplitLayout` / `AuthMarketingPanel`; **`TenantAppShell`** at `src/app/app/layout.tsx` (sidebar from `GET .../me/navigation`, collapse + mobile drawer, header + user menu with Profile `/app/profile`, forgot-password for password change, locale PATCH, `POST .../auth/logout`); accent token hook; `tenant-web-vertical-slice.md` §3.6*.
- [x] **Audit:** append-only events for sensitive mutations; correlation id; **deletion/anonymization** of other data must **not** remove required audit facts (reference anonymized ids where applicable) — *`audit_event` + `AuditService`; `USER_LOCALE_CHANGED`, `TENANT_SETTINGS_PATCHED`, `PLATFORM_SETTINGS_PATCHED` on successful PATCHes; `docs/modules/audit.md`; FK hardening deferred*.
- [x] **Data lifecycle (privacy by design):** schema and APIs support **controlled deletion**, **anonymization**, and **export**; document entity-level rules; **PII inventory** for high-risk tables (esp. messaging) in module docs — *[`docs/modules/data-lifecycle.md`](../modules/data-lifecycle.md) (inventory + policy matrix); `GET /api/v1/me/privacy/export` + `POST /api/v1/me/privacy/erasure-request` (202 stub + audits); web hooks in `api.ts` + `/app`; `MePrivacyIT`; automated erasure/deletion execution deferred*.
- [x] **E2E:** Playwright covers auth host + tenant host + BFF session relay (`E2E-TESTING-STANDARDS`) — *`frontend/e2e/m1-platform.spec.ts` (login → `demo.*` `/app`, **`PATCH /api/bff/v1/me/locale`** via **header user menu** + nav label assertion, unknown-tenant host); `tenant-vertical-slice.spec.ts` + `smoke.spec`; set **`PLAYWRIGHT_API_BASE_URL`** and **`API_BASE_URL`** on Next, run API + DB for full suite*.

---

## Milestone M2 — Invitations and messaging

**Canonical notification data rules:** [`docs/modules/notifications-inbox.md`](../modules/notifications-inbox.md) (allowed columns, `NotificationService` only, no persisted bodies).

- [x] **Tenant invitations (backend v1):** invite by email + role; token **accept**; `USER_INVITE`; **decline / cancel / expiry job / “existing user” path** still open — [`docs/modules/invitations.md`](../modules/invitations.md).
- [x] **Mail integration (v1 adapter):** `MailSendPort` + logging implementation; **no** message body persistence; **HTTP/SMTP provider + idempotency** still open — [`docs/modules/mail-adapter.md`](../modules/mail-adapter.md).
- [x] **Notifications (in-app) + inbox (backend v1):** `notification` row + `GET/PATCH /api/v1/me/notifications`; **categories**, richer pagination, and full mail↔notification id trace still open — [`docs/modules/notifications-inbox.md`](../modules/notifications-inbox.md).

---

## Milestone M3 — Commercial (plans, gating, billing)

**Goal:** **Predefined plan features** (known to application code) drive **gating**; SuperAdmin can compose plans; subscriptions and **billing v1** (hosted checkout, webhooks, minimal reconcile, usage persistence) are integrated per [`commercial-billing.md`](../modules/commercial-billing.md).

**M3 engineering exit:** With **Billing (M3 v1)** checked below, **M3 is code-complete** in this repo for commercial + billing **product code**. Unchecked items under **First paying customer — billing slice** are **deployment / ops** (live keys, smoke tests, optional Path A runbook)—they do **not** block starting **Milestone M4** (Documents / MinIO).

#### M3 commercial — shipped modules

- [x] **Plan feature catalog (M3 v1):** `PlanFeatureCode` enum + `plan_feature` table + seed + `GET /api/v1/platform/plan-features` (superadmin) — [`docs/modules/commercial-plans.md`](../modules/commercial-plans.md).
- [x] **Plan editor (M3 v1):** SuperAdmin `GET`/`POST`/`PUT`/`DELETE` **`/api/v1/platform/commercial-plans`** composes **`commercial_plan`** + **`commercial_plan_feature`** from **`plan_feature`** only; **`DELETE`** allowed when no `tenant_subscription` references the plan; audit **`COMMERCIAL_PLAN_DELETED`** — [`docs/modules/commercial-plans.md`](../modules/commercial-plans.md). **Rename plan code / version history** remain **out of v1** (by design).
- [x] **Subscriptions (M3 v1 persistence + read):** `tenant_subscription`; SuperAdmin `GET`/`PUT /api/v1/platform/tenants/{tenantId}/subscription`; `GET /api/v1/me/subscription` — [`docs/modules/commercial-subscriptions.md`](../modules/commercial-subscriptions.md); `TenantSubscriptionsIT`.
- [x] **Gating (M3 v1):** runtime **effective pool ceiling** = `tenant_privilege_allowance` **∪** subscription-derived privileges (`PlanFeaturePrivilegeWiring`); **`GET /api/v1/me`** adds **`planFeatureCodes`** for active subscription; **`nav_menu_item.required_plan_feature_code`** + **`GET /api/v1/me/navigation`** filter — [`docs/modules/commercial-subscriptions.md`](../modules/commercial-subscriptions.md), [`docs/modules/navigation-menu.md`](../modules/navigation-menu.md); `SubscriptionGatingIT`, `NavigationAndSettingsIT`. **Separate persisted “feature flag” tables** (non–plan-feature toggles) remain **out of scope** until product asks for them.
- [x] **Billing (M3 v1 — code-complete slice):** **PayPal** + **Stripe** hosted paths, webhooks, PCI boundaries, tenant usage persistence — **spec:** [`docs/modules/commercial-billing.md`](../modules/commercial-billing.md) § *M3 engineering exit*. **Shipped:** `billing_webhook_receipt`; `billing_provider_link` + SuperAdmin `GET`/`PUT .../platform/tenants/{tenantId}/billing-provider-links[...]`; `POST /api/v1/billing/webhooks/stripe` (signed, idempotent); `POST /api/v1/billing/webhooks/paypal` (idempotent; **`tenant_resolution_state`** + metadata; optional **`PAYPAL_WEBHOOK_VERIFY`**); `platform_setting` `billing.stripe.enabled` / `billing.paypal.enabled`; env `STRIPE_WEBHOOK_SECRET`; integration tests (`BillingWebhooksIT`, `BillingProviderLinkIT`, `BillingPaypalWebhooksIT`, `BillingPaypalWebhookSignatureIT`, `BillingPaypalWebhookSubscriptionReconcileIT`, Stripe/PayPal subscription reconcile ITs). **Tenant APIs:** **`POST .../tenant/billing/usage-events`** + **`billing_usage_event`**; **`billing_usage_aggregate`** + **`BillingUsageAggregationService`** + optional UTC **`BillingUsageAggregationScheduler`** + **`GET .../tenant/billing/usage-aggregates`** (`external_synced*` reserved); PayPal **`POST .../tenant/billing/paypal/subscription`**; **`GET .../tenant/billing/summary`** (**`USER_VIEW`**) + **`GET .../tenant/billing/commercial-plans`** (**`TENANT_SETTINGS_EDIT`**); Stripe **checkout-session** + **billing-portal-session**; **`BillingRedirectUrlPolicy`** (`http` for localhost / 127.0.0.1 / `*.lvh.me` when **`STRIPE_ALLOW_INSECURE_CHECKOUT_URLS`**); Liquibase **`DEMO_STARTER`** seed; **Next.js `/app`** billing card (checkout / PayPal / portal + privilege-aware catalog); Playwright admin + viewer billing tests. **Post-M3 product (not blocking M4):** provider **meter push**; **B3** full reconciliation job; invoice PDFs / tax / dunning; extra tables from module **Proposed Schema**.

#### First paying customer — billing slice (operations / go-live)

**Intent:** one real tenant can **pay for a subscription** and **receive the right plan / gating** with a **defined** ops story — without building the entire dual-provider PAYG + reconciliation program first. **Default recommendation:** treat **Stripe subscriptions** as the **first** money path; defer **PayPal subscribe** and **usage meter push** unless this customer explicitly requires them. See also [`commercial-billing.md` § First paying customer](../modules/commercial-billing.md#first-paying-customer-scope-cut) and **[`STRIPE-FIRST-CUSTOMER-RUNBOOK.md`](../operations/STRIPE-FIRST-CUSTOMER-RUNBOOK.md)** (Stripe go-live + verification).

- [ ] **Stripe live readiness:** live **`STRIPE_SECRET_KEY`**, live **`STRIPE_WEBHOOK_SECRET`**, Dashboard **webhook endpoint** pointed at **`POST /api/v1/billing/webhooks/stripe`**, **`billing.stripe.enabled=1`**, correct **Checkout/Billing Portal** return URLs for production.
- [ ] **Customer link:** SuperAdmin (or scripted equivalent) has set **`billing_provider_link`** for **Stripe** for that tenant’s **`external_customer_id`** before the tenant uses Checkout/Portal (see module doc).
- [x] **Tenant purchase path (dev / tenant web):** Next.js **`/app`** fetches **`GET .../tenant/billing/summary`** with **`USER_VIEW`** (all signed-in tenant users with that privilege, including demo **Viewer**). **`GET .../tenant/billing/commercial-plans`** requires **`TENANT_SETTINGS_EDIT`**; admins see the plan picker (Liquibase **`DEMO_STARTER`** when no other active plans), **Stripe Checkout**, **PayPal subscribe**, and **Stripe Customer Portal** when provider/plan preconditions match; return URLs use **`?billing=`** query hints. **`BillingRedirectUrlPolicy`**: HTTPS in production; with **`STRIPE_ALLOW_INSECURE_CHECKOUT_URLS`**, **`http`** allowed for **localhost**, **127.0.0.1**, and **`*.lvh.me`** (see module doc).
- [ ] **Tenant purchase path (production go-live):** same APIs with **live** Stripe keys, **HTTPS** return URLs only (no insecure flag), real **`price_…`** / PayPal **`P-…`** ids on the commercial plan, linked Stripe customer, and operators satisfied smoke tests on production-like config.
- [ ] **Entitlement after payment (choose and document one):**
  - [ ] **Path A — manual v1 (fastest):** written **runbook**: after Stripe shows an active subscription for the linked customer, SuperAdmin sets **`tenant_subscription`** to **`ACTIVE`** with the correct **`commercial_plan_id`** (and any renewal dates you track); webhooks remain **ingest-only** for audit. **OR**
  - [x] **Path B — minimal auto-reconcile (shipped):** **`checkout.session.completed`** (subscription mode, `client_reference_id` = tenant id, `metadata.commercial_plan_id`, resolved customer) → **`tenant_subscription` `ACTIVE`** when plan is active and **`commercial_plan.stripe_subscription_price_id`** matches Checkout; **`customer.subscription.updated`** (`active`/`trialing` + subscription metadata + price match, or **price-only** plan lookup when metadata absent → **`ACTIVE`**; `canceled`/`unpaid`/`incomplete_expired` → **`CANCELLED`**); **`customer.subscription.deleted`** → **`CANCELLED`**; duplicate Stripe event ids are no-ops for side effects. Extend cautiously for more Stripe types. **PayPal:** **`BILLING.SUBSCRIPTION.ACTIVATED`** / **`RE-ACTIVATED`** (linked payer + `resource.custom_id` `WAGE|<tenant>|<commercial_plan>`) → **`ACTIVE`**; **`BILLING.SUBSCRIPTION.CANCELLED`** / **`EXPIRED`** / **`SUSPENDED`** → **`CANCELLED`**; duplicate **`PayPal-Transmission-Id`** are no-ops for side effects.
- [ ] **Verification:** `GET /api/v1/me` shows expected **`planFeatureCodes`** / effective privileges for that tenant after subscription is active; smoke test on **production-like** config (can still use Stripe test mode until go-live).
- [ ] **Defer after customer #1 unless required:** **usage meter** upload to Stripe/PayPal; **full** reconciliation across all webhook types; invoice PDFs / tax / dunning automation; extra **proposed** billing tables from the module doc. *(PayPal **subscription** approval is already exposed from tenant **`/app`** when the plan has a PayPal billing plan id; treat as optional vs Stripe for customer #1.)*

---

## Milestone M4 — Documents (MinIO)

- [x] **MinIO** (or S3-compatible) **presigned** upload (`POST .../upload-sessions` + `POST .../complete`) and download (`GET .../{id}/download-url`); **503** when `MINIO_*` / `app.storage.minio.*` incomplete. Virus scan hook TBD optional.
- [x] **Metadata + tenant ACL (v1 slice):** Liquibase **`tenant_document`**, **`document_share`**, **`document_attachment`**; privileges **`DOCUMENT_VIEW`** / **`DOCUMENT_EDIT`**; **`GET /api/v1/tenant/documents`** hub (**`DOCUMENT_VIEW`**) = owned ∪ shared (user + role shares); module specs [`documents-minio.md`](../modules/documents-minio.md), [`document-sharing.md`](../modules/document-sharing.md).
- [x] **Sharing (no real-time collab):** **`GET/POST .../tenant/documents/{id}/shares`**, **`DELETE .../shares/{shareId}`**; v1 **uploader-only** mutation; hub unchanged.
- [x] **Attachments:** **`GET .../attachments`**, **`POST/DELETE .../attachments/{attachmentId}`**; `entity_type` + `entity_id`; unique per document+entity; list requires document read access.
- [x] **Tenant web:** Next.js **`/app/documents`** (BFF); demo nav row **`/app/documents`** + **`nav.documents`** (**`DOCUMENT_VIEW`**); hub + upload + **soft delete** + shares/attachments panel for uploaders.
- [x] **M4 cleanup:** Optional **scheduled orphan S3 job** (`MINIO_ORPHAN_CLEANUP_ENABLED`, UTC cron, **`min-object-age`**, list cap + **soft-deleted S3 delete retry**). Covers failed **`complete`** (object without row) and **soft-delete** paths where S3 delete was off or failed. **Also shipped:** optional **HeadObject** on **`complete`** (`MINIO_VERIFY_OBJECT_BEFORE_COMPLETE`); **best-effort S3 delete** on soft-delete (`MINIO_DELETE_OBJECT_ON_SOFT_DELETE`, default on).

---

## Milestone M5 — Payroll / HR domain

**Goal:** Configurable **multi-currency**, **business units**, **country-based payroll** with **sandbox**, gross/net/tax pipeline. **First full country implementation: Suriname (SR)**; other countries as add-on adapters later.

- [ ] **Reference data:** currencies; countries; **multiple business units per tenant**; **roles per business unit** where applicable.
- [ ] **Organization structure**; **employment history**; **compensation history**.
- [ ] **Employee master**; **partners and children**; **legal status per country** (extensible enums / tables).
- [ ] **Leave** request workflow; **time and attendance** baseline.
- [ ] **Employee self-service** portal (scoped privileges).
- [ ] **Payroll sandbox** (simulate runs without production side effects).
- [ ] **Gross** and **net** calculation pipeline with **tax rules per country**; **country-specific layouts** as adapter + UI contract.
- [ ] **Suriname (SR):** first complete adapter (tax, gross/net, payroll UI slice, sandbox parity); legal enums for dependents as required for SR.
- [ ] **Shareable URLs** preserving filters/state where product requires it (web module acceptance criteria).

---

## Milestone M6 — Mobile parity (ongoing)

- [ ] Flutter: auth, tenant selection, session model (tokens), i18n, theme — **per module** parity with web for each shipped milestone.
- [ ] Push notifications (if adopted) tied to M2/M3 decisions.

---

## Milestone M7 — SSO (later)

- [ ] OIDC / enterprise SSO behind feature flag; **no shortcut** around tenant or privilege checks.
- [ ] Module `sso-oidc.md` completed; linked identity to existing user records.

---

## Explicitly out of product scope (unless contract changes)

- **Live bank execution** / moving money as payroll disbursement (separate from SaaS billing if ever added).
- **Real-time multi-user document editing** (operational transform / CRDT-style collab); simple share + view/download is in scope.

---

## When a milestone completes

1. Mark checkboxes here (for **M3**, billing is **[x]** when the **M3 v1** slice is shipped; go-live rows may stay open).
2. Update [MODULE-INDEX.md](./MODULE-INDEX.md) status column.
3. If schema or public API changed, refresh OpenAPI / migration notes as per team process.
4. If contract assumptions changed, update **Architecture contract** in `PROJECT-CONTEXT.md` and schedule **architecture file** regeneration.
