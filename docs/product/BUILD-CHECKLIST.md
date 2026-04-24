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
- [x] **Web:** unique URL per layout; deep-linkable routes; **light/dark** + **custom theme** tokens (`WEB-THEMING-AND-DESIGN-SYSTEM`); split login / glass styling as UX layer — *primitives + semantic CSS vars + Tailwind map; `next-themes` + FOUC script; `AuthShell` + glass cards vs `data-layout="app"` chrome; accent token hook; `tenant-web-vertical-slice.md` §3.6*.
- [x] **Audit:** append-only events for sensitive mutations; correlation id; **deletion/anonymization** of other data must **not** remove required audit facts (reference anonymized ids where applicable) — *`audit_event` + `AuditService`; `USER_LOCALE_CHANGED`, `TENANT_SETTINGS_PATCHED`, `PLATFORM_SETTINGS_PATCHED` on successful PATCHes; `docs/modules/audit.md`; FK hardening deferred*.
- [x] **Data lifecycle (privacy by design):** schema and APIs support **controlled deletion**, **anonymization**, and **export**; document entity-level rules; **PII inventory** for high-risk tables (esp. messaging) in module docs — *[`docs/modules/data-lifecycle.md`](../modules/data-lifecycle.md) (inventory + policy matrix); `GET /api/v1/me/privacy/export` + `POST /api/v1/me/privacy/erasure-request` (202 stub + audits); web hooks in `api.ts` + `/app`; `MePrivacyIT`; automated erasure/deletion execution deferred*.
- [x] **E2E:** Playwright covers auth host + tenant host + BFF session relay (`E2E-TESTING-STANDARDS`) — *`frontend/e2e/m1-platform.spec.ts` (login → `demo.*` `/app`, `PATCH /api/bff/v1/me/locale` + nav assertion, unknown-tenant host); `tenant-vertical-slice.spec.ts` + `smoke.spec`; set **`PLAYWRIGHT_API_BASE_URL`** and **`API_BASE_URL`** on Next, run API + DB for full suite*.

---

## Milestone M2 — Invitations and messaging

**Canonical notification data rules:** [`docs/modules/notifications-inbox.md`](../modules/notifications-inbox.md) (allowed columns, `NotificationService` only, no persisted bodies).

- [x] **Tenant invitations (backend v1):** invite by email + role; token **accept**; `USER_INVITE`; **decline / cancel / expiry job / “existing user” path** still open — [`docs/modules/invitations.md`](../modules/invitations.md).
- [x] **Mail integration (v1 adapter):** `MailSendPort` + logging implementation; **no** message body persistence; **HTTP/SMTP provider + idempotency** still open — [`docs/modules/mail-adapter.md`](../modules/mail-adapter.md).
- [x] **Notifications (in-app) + inbox (backend v1):** `notification` row + `GET/PATCH /api/v1/me/notifications`; **categories**, richer pagination, and full mail↔notification id trace still open — [`docs/modules/notifications-inbox.md`](../modules/notifications-inbox.md).

---

## Milestone M3 — Commercial (plans, gating, billing)

**Goal:** **Predefined plan features** (known to application code) drive **gating**; SuperAdmin can compose plans; subscriptions and billing integrated per decisions above.

- [ ] **Plan feature catalog** in code (enum or constants) mirrored in DB for admin UI and reporting.
- [ ] **Plan editor** (SuperAdmin): compose plans from predefined features only.
- [ ] **Subscriptions** per tenant; state machine; link to tenant entitlements / privilege pool updates as designed.
- [ ] **Gating:** subscription widens **tenant privilege pool** and **feature flags**; single documented resolver order (e.g. authn → tenant → subscription → effective pool + flags → privilege → handler).
- [ ] **Billing:** **PayPal** + **Stripe** adapters; **usage / pay-as-you-go** models; webhooks; reconciliation; PCI/hosted-field boundaries per provider — *sub-milestones in `commercial-billing` module*.

---

## Milestone M4 — Documents (MinIO)

- [ ] **MinIO** (or S3-compatible) integration; upload/download; virus scan hook TBD optional.
- [ ] **Metadata + tenant ACL**; versioning policy if required.
- [ ] **Sharing (no real-time collab):** grant access by **user** and/or **role**; **document list** = owned + shared-with-me.
- [ ] **Attachments:** generic **link document ↔ arbitrary business record** (entity type + id or equivalent); enforce tenant + privilege on resolve.

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

1. Mark checkboxes here.
2. Update [MODULE-INDEX.md](./MODULE-INDEX.md) status column.
3. If schema or public API changed, refresh OpenAPI / migration notes as per team process.
4. If contract assumptions changed, update **Architecture contract** in `PROJECT-CONTEXT.md` and schedule **architecture file** regeneration.
