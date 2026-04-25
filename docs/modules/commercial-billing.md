# Module: commercial billing (PayPal + Stripe)

**Milestone:** M3. Depends on [`commercial-plans.md`](./commercial-plans.md) and [`commercial-subscriptions.md`](./commercial-subscriptions.md). **Product gating** (privileges + `planFeatureCodes`) stays in the subscriptions / gating path — billing does **not** replace that resolver.

**Preflight:** [SCHEMA-PERSISTENCE-PREFLIGHT](../guides/SCHEMA-PERSISTENCE-PREFLIGHT.md) + [DATA-MODEL-STANDARDS](../guides/DATA-MODEL-STANDARDS.md) + this file.

**Authority:** New billing tables require an **allowed-column list** in this file + Liquibase before merge. **`billing_webhook_receipt`** and **`billing_provider_link`** are **shipped** (see **Allowed persistence**). Further tables remain **Proposed Schema Extension** until promoted.

---

## M3 engineering exit (v1)

**Counted as M3 code-complete** in [`BUILD-CHECKLIST.md`](../product/BUILD-CHECKLIST.md): Stripe + PayPal **webhooks** (idempotency + minimal **`tenant_subscription`** reconcile per **First paying customer / Path B**), **`billing_provider_link`**, platform **`billing.*.enabled`** flags, tenant **`GET .../billing/summary`** (**`USER_VIEW`**) and **`GET .../billing/commercial-plans`** (**`TENANT_SETTINGS_EDIT`**), Stripe **Checkout** + **Billing Portal** + PayPal **subscribe** APIs, **`POST .../billing/usage-events`** + **`billing_usage_aggregate`** + read **`GET .../usage-aggregates`**, Next.js **`/app`** billing UX (privilege-aware), Liquibase **`DEMO_STARTER`** seed, dev **`BillingRedirectUrlPolicy`** rules for local hosts.

**Explicitly after M3** (product may pull forward per contract): **usage meter push** to Stripe/PayPal; **B3** full reconciliation job; **`external_synced`** semantics for external billing sync; invoice PDFs / tax / dunning; tables listed only under **Proposed Schema Extension** in this file.

---

## Product intent

- Support **both** **PayPal** and **Stripe** so a deployment (or tenant) can use either provider; defaults and feature flags are **configuration**, not a hard-coded single vendor.
- Prefer each provider’s **usage / pay-as-you-go** (metering, usage records, invoices where applicable) over hand-rolled “fake subscription” billing logic in app code.
- **Webhooks** are the source of truth for payment state, subscription lifecycle events from the provider, and **reconciliation** against our `tenant_subscription` (or future provider-mirror rows). Handlers must be **idempotent** (at-least-once delivery).
- **PCI:** never persist **card numbers**, CVV, or full **Stripe/PayPal secret keys** in the application database. Use **hosted checkout**, **Payment Element**, **Billing Agreement** / **Subscriptions** APIs, or equivalent so sensitive card data stays on the provider. Store only **opaque** provider ids (customer id, subscription id, agreement token id) and **non-secret** metadata.

---

## Boundaries vs other modules

| Topic | Owner |
|-------|--------|
| Which **commercial plan** and **subscription status** we consider active for the product | [`commercial-subscriptions.md`](./commercial-subscriptions.md) + `tenant_subscription` |
| **Money movement**, dunning, tax/VAT specifics, invoices PDFs | This module + provider dashboards |
| **Privilege / feature gating** | Subscriptions + `PlanFeaturePrivilegeWiring` / `PermissionService` |

**Reconciliation rule (conceptual):** provider-reported “paid + entitled” state should eventually **drive or confirm** tenant subscription state; until automated reconciliation ships, SuperAdmin may still **manually** assign `tenant_subscription` (current v1).

---

## First paying customer (scope cut)

**Not everything in this module is required to take money once.** For the **first paying customer**, prioritize:

1. **One provider path first** — **Stripe** subscription Checkout + Billing Portal + webhook signing is the default smallest path; add **PayPal** only if that customer needs it.
2. **Link + checkout** — `billing_provider_link` for Stripe + tenant **`checkout-session`** / **`billing-portal-session`** with platform `billing.stripe.enabled` and secrets set (`configuration` + **APIs** table in this file).
3. **Entitlement** — either **SuperAdmin `tenant_subscription`** after you confirm payment in Stripe (**Path A**, already allowed by the reconciliation rule above) or the **shipped minimal Path B:** on first-time ingest of **`checkout.session.completed`** (subscription mode, `client_reference_id` = tenant UUID, `metadata.commercial_plan_id`, linked Stripe `customer`), the API sets **`tenant_subscription`** to **`ACTIVE`** for that plan when the plan is **active** and its **`stripe_subscription_price_id`** matches the configured mapping; **`customer.subscription.deleted`** → **`CANCELLED`** (plan id preserved); **`customer.subscription.updated`** with **`active`**/**`trialing`** (subscription **`metadata.commercial_plan_id`** when present and consistent with the first line item **price** id, else **price-only** lookup on **`commercial_plan.stripe_subscription_price_id`**) → **`ACTIVE`**; terminal statuses **`canceled`**, **`unpaid`**, **`incomplete_expired`** on that event → **`CANCELLED`**. Checkout sets **`subscription_data.metadata.commercial_plan_id`** on the created Subscription (and session metadata). Duplicate Stripe event ids do **not** re-apply side effects. Other Stripe event types remain **no-op** for subscription rows until explicitly extended. **PayPal (minimal Path B, shipped):** first-time **`BILLING.SUBSCRIPTION.ACTIVATED`** or **`BILLING.SUBSCRIPTION.RE-ACTIVATED`** with linked payer and **`resource.custom_id`** `WAGE|<tenant>|<commercial_plan>` → **`ACTIVE`** when the plan is **active** and tenant matches; **`BILLING.SUBSCRIPTION.CANCELLED`**, **`EXPIRED`**, or **`SUSPENDED`** → **`CANCELLED`**. Duplicate **`PayPal-Transmission-Id`** values do **not** re-apply side effects.

**Defer unless PAYG is in the first contract:** `POST .../usage-events` provider push, **`external_synced`** external billing sync, full **B3** reconciliation job, and **proposed** mirror/invoice tables.

**Checklist:** open [`BUILD-CHECKLIST.md`](../product/BUILD-CHECKLIST.md) → **Milestone M3** → subsection **“First paying customer — billing slice”**.

---

## Configuration (environment)

Document **per deployment** (not committed secrets):

| Key area | Notes |
|----------|--------|
| **Stripe** | `STRIPE_SECRET_KEY` → `app.billing.stripe.secret-key` (Checkout + Billing Portal); `STRIPE_WEBHOOK_SECRET` → webhook signing; optional `STRIPE_ALLOW_INSECURE_CHECKOUT_URLS` → allow `http` redirect URLs for **localhost**, **127.0.0.1**, and `*.lvh.me` hosts only (local dev / Playwright); publishable key for client if needed; optional `STRIPE_ACCOUNT_ID` for Connect later. |
| **PayPal** | `PAYPAL_WEBHOOK_ID` → `app.billing.paypal.webhook-id`; `PAYPAL_API_BASE` → `app.billing.paypal.api-base` (optional, defaults to sandbox API host); `PAYPAL_CLIENT_ID` / `PAYPAL_CLIENT_SECRET` → `app.billing.paypal.client-id` / `client-secret` (required when verify is on); optional `PAYPAL_WEBHOOK_VERIFY` → `app.billing.paypal.verify-signature` (**default `false`**: accepts webhooks with `PayPal-Transmission-Id` + JSON `event_type`; **`true`**: calls PayPal `verify-webhook-signature` using **`PayPal-Transmission-*`**, **`PayPal-Cert-Url`**, **`PayPal-Auth-Algo`** + raw body; **503** if verify is on but client id/secret are unset). |
| **Routing** | Which provider is **default** for new checkout; whether **tenant** can choose (product decision — default: platform-config only until UI exists). |

---

## Webhooks (shared requirements)

1. **Dedicated HTTP routes** (e.g. `/api/v1/billing/webhooks/stripe`, `/api/v1/billing/webhooks/paypal`) — **no** session cookie auth; verify **provider signature** (Stripe signing secret; PayPal: optional **`verify-webhook-signature`** REST call when `app.billing.paypal.verify-signature=true`, otherwise transmission id + JSON validation only for dev/sandbox).
2. **Idempotency:** unique **provider event id** (or hash of canonical payload fields) persisted before side effects; duplicate delivery → **200** with no duplicate writes.
3. **Payload minimization:** prefer storing **event id + status + correlation ids**, not full raw JSON, unless audit policy explicitly requires truncated/redacted archives (then follow PII class + retention in DATA-MODEL-STANDARDS).
4. **Tenant resolution:** map provider **customer id** (Stripe) or **payer id** (PayPal, from webhook `resource`) → `tenant_id` via **`billing_provider_link`** (`BillingProviderLinkRepository.findByProviderAndExternalCustomerId`); **global uniqueness** on (`provider`, `external_customer_id`) so one customer id cannot attach to two tenants. Unknown mapping → log + dead-letter (no guess from email alone).

---

## Usage / metering (PAYG)

- **Report usage** to the provider (or to a usage record that the provider invoices against) using **stable metric keys** aligned with product language (e.g. seats, payroll runs, storage GB); exact catalog is **product** + `plan_feature` where relevant.
- **Idempotency keys** on usage submissions to avoid double billing on retries.
- **Clocks:** use **UTC** for period boundaries; document how invoice periods align with `tenant_subscription` renewal if both exist.
- **Shipped (v1 persistence):** tenant **`POST /api/v1/tenant/billing/usage-events`** writes **`billing_usage_event`** only (no Stripe/PayPal meter API calls yet).
- **Internal aggregation (no provider sync yet):** application code can run **`BillingUsageAggregationService.recomputeDailyAggregatesForTenant`** to rebuild **`billing_usage_aggregate`** from events. **Optional scheduler (default on in `application.yml`, off in `test` profile):** **`BillingUsageAggregationScheduler`** runs a **UTC cron** (`app.billing.usage-aggregation.daily-cron`, default `0 15 2 * * *`) and recomputes **only the previous UTC calendar day** for tenants that had at least one event that day (`BILLING_USAGE_AGGREGATION_SCHEDULED=false` disables). **Today’s** UTC day is not closed by that job until the next run; use manual recompute if same-day aggregates are required. Strategy is **deterministic per UTC day**: for each `(tenant_id, period_start)` day bucket, **delete** existing aggregate rows for that tenant + day, then **insert** one row per `metric_key` from **`sum(quantity)`** over events with **`recorded_at ∈ [period_start, period_end)`** (`period_end` = next UTC midnight). **Idempotency:** rerunning recompute for the same date range yields the same totals; **usage rows** remain unique on **`(tenant_id, idempotency_key)`**, so retries cannot double-count in the sum. **`external_synced` / `external_synced_at`** are reserved for a future external billing sync job (not implemented yet); each recompute **resets** those fields on replaced rows so a future sync can treat recomputed periods as pending until pushed again.

---

## APIs (phased — specify before coding)

| Phase | Actor | Purpose |
|-------|-------|---------|
| **B0** | Platform SuperAdmin (session + CSRF) | **Shipped (partial):** `platform_setting` keys `billing.stripe.enabled`, `billing.paypal.enabled` (`0` \| `1`, seeded `0`); validated on `PATCH /api/v1/platform/settings`. **`billing_provider_link`:** `GET /api/v1/platform/tenants/{tenantId}/billing-provider-links`; `PUT` same path + `/{provider}` where `provider` is `stripe` or `paypal` (case-insensitive); body `{ "externalCustomerId": "…" }`; audit `BILLING_PROVIDER_LINK_UPSERTED`. |
| **B1** | Authenticated tenant user on tenant host | **`GET /api/v1/tenant/billing/summary`** (**`USER_VIEW`**) — `{ summary: { stripeBillingEnabled, paypalBillingEnabled, stripeCustomerLinked, paypalCustomerLinked, subscription: null \| { status, commercialPlanId, commercialPlanCode } } }` from `platform_setting` + `billing_provider_link` + **`tenant_subscription`** / **`commercial_plan`** (no secrets, no provider customer ids). **`GET /api/v1/tenant/billing/commercial-plans`** (**`TENANT_SETTINGS_EDIT`**) — `{ plans: [ CommercialPlanListItem… ] }` where each row is an **active** `commercial_plan` (same fields as the platform list item: `id`, `code`, `sortOrder`, `active`, `featureCount`, `stripeSubscriptionPriceId`, `paypalBillingPlanId`) for tenant checkout UI (provider ids restricted to settings editors). Tenant admin (`TENANT_SETTINGS_EDIT`) **Stripe (shipped v1):** `POST /api/v1/tenant/billing/stripe/checkout-session` — body `{ commercialPlanId, priceId, successUrl, cancelUrl }` → `{ url }` (hosted Checkout **subscription** for linked Stripe customer). **`commercialPlanId`** must reference an **active** `commercial_plan` whose **`stripe_subscription_price_id`** equals **`priceId`** (so Checkout session metadata and **`subscription_data.metadata`** match webhook reconciliation). `POST /api/v1/tenant/billing/stripe/billing-portal-session` — body `{ returnUrl }` → `{ url }`. Stripe sessions require platform `billing.stripe.enabled=1`, `STRIPE_SECRET_KEY`, and SuperAdmin-linked **`billing_provider_link`** for `stripe`. **403** if Stripe billing disabled; **503** if secret unset; **404** if no Stripe customer link or unknown plan; **400** on bad URLs / `priceId` / plan mismatch / inactive plan / missing Stripe price on plan. **PayPal (shipped v1):** `POST /api/v1/tenant/billing/paypal/subscription` — body `{ commercialPlanId, planId, returnUrl, cancelUrl }` (`planId` = PayPal billing **plan** id `P-…`; **`commercialPlanId`** = DB `commercial_plan`, must be **active**). When **`commercial_plan.paypal_billing_plan_id`** is set (SuperAdmin plan editor), **`planId`** must match it (Stripe **`priceId`** parity); when unset, any valid PayPal **`P-…`** is accepted. Create sends PayPal **`custom_id`** `WAGE|<tenantId>|<commercialPlanId>` (≤127 chars) for **`BILLING.SUBSCRIPTION.ACTIVATED`** reconcile. → `{ approvalUrl }` (`POST /v1/billing/subscriptions`, `APPROVAL_PENDING`). Requires `billing.paypal.enabled=1` and `PAYPAL_CLIENT_ID` / `PAYPAL_CLIENT_SECRET` / `PAYPAL_API_BASE`. **403** if PayPal billing disabled; **503** if credentials unset; **404** if commercial plan missing; **400** on bad PayPal plan id / plan mismatch / inactive commercial plan / bad redirect URLs; **502** if PayPal response has no `approve` link. |
| **B2** | Provider | **Stripe (shipped):** `POST /api/v1/billing/webhooks/stripe` — body + `Stripe-Signature`; `STRIPE_WEBHOOK_SECRET`; idempotent `billing_webhook_receipt` with **`tenantResolutionState`** (`RESOLVED` \| `UNRESOLVED_INSUFFICIENT_DATA` \| `UNRESOLVED_NO_MATCH`), reason codes, resolver version **`StripeTenantResolverV1`**, raw payload + event type. After a **new** receipt insert, **minimal** `tenant_subscription` updates for **`checkout.session.completed`**, **`customer.subscription.updated`** ( **`active`**/**`trialing`** entitlement on via metadata **or** Stripe **price** id; **`canceled`**/**`unpaid`**/**`incomplete_expired`** entitlement off), and **`customer.subscription.deleted`** (see **First paying customer** / Path B). **PayPal (shipped):** `POST /api/v1/billing/webhooks/paypal` — same resolution model + **`PayPalTenantResolverV1`** (payer paths: `resource.payer.payer_id` → `resource.payer_id` → `resource.subscriber.payer_id`). After a **new** (non-duplicate) receipt with **`RESOLVED`** tenant, **minimal** `tenant_subscription` updates for **`BILLING.SUBSCRIPTION.ACTIVATED`** / **`RE-ACTIVATED`** (`resource.custom_id` vs payer-link tenant, active plan) and **`BILLING.SUBSCRIPTION.CANCELLED`** / **`EXPIRED`** / **`SUSPENDED`**. JSON response: **`tenantResolutionState`**, **`tenantResolutionReasonCode`**, **`tenantResolutionMissingFieldPath`**, **`tenantResolutionResolverVersion`** (no boolean `tenantResolved`). **`app.billing.paypal.verify-signature=true`:** PayPal **`verify-webhook-signature`** + OAuth; **503** / **400** as before. **CSRF disabled**; **permitAll**. |
| **B3** | Platform / job | **Reconciliation** job: compare provider state vs `tenant_subscription`; optional notifications via [`notifications-inbox.md`](./notifications-inbox.md). **TBD** |
| **Usage v1** | Tenant admin (`TENANT_SETTINGS_EDIT`) | **`POST /api/v1/tenant/billing/usage-events`** — body `{ metricKey, quantity, idempotencyKey }` → `{ received, duplicate }` (same idempotency semantics as webhooks). Persists **`billing_usage_event`** with unique (`tenant_id`, `idempotency_key`). Allowed **`metricKey`** values: `PAYROLL_RUN`, `COMMERCIAL_SEAT_DAY`, `DOCUMENT_STORAGE_GB` (extend **`BillingMetricKey`** in code). **400** on unknown metric / non-positive quantity. **Push to Stripe/PayPal usage meters** — **TBD**. |
| **Usage aggregates (read)** | Tenant admin (`TENANT_SETTINGS_EDIT`) | **`GET /api/v1/tenant/billing/usage-aggregates`** — query: optional **`metricKey`**, **`periodStart`**, **`periodEnd`** (ISO local dates, UTC day boundaries on `period_start`; inclusive range of days). Response **`{ aggregates: [ { metricKey, periodStart, periodEnd, totalQuantity, lastAggregatedAt, externalSynced, externalSyncedAt } ] }`**. Omitted dates default to the **last 30 UTC days**. **400** on invalid date strings. Rows come from **`billing_usage_aggregate`** (populated by internal recompute, not by this GET). |

---

## Security & privileges

- **SuperAdmin** (or dedicated `BILLING_*` privileges when introduced) for platform billing config and manual reconciliation tools.
- **Tenant-scoped** billing read/write uses normal tenant context + least privilege. **`GET .../tenant/billing/summary`** requires **`USER_VIEW`**; **`GET .../tenant/billing/commercial-plans`** (Stripe/PayPal catalog ids) requires **`TENANT_SETTINGS_EDIT`** (same as checkout / portal / usage POSTs).
- **Webhooks:** signature verification only; rate-limit and IP allowlisting optional per ops.

---

## Allowed persistence (v1)

### Table: `billing_webhook_receipt` *(shipped)*

| Column | Nullable | Description |
|--------|----------|-------------|
| `id` | no | UUID PK. |
| `provider` | no | `STRIPE` \| `PAYPAL`, length 16. |
| `provider_event_id` | no | Idempotency key: Stripe event id (`evt_…`); PayPal `PayPal-Transmission-Id`, length 255. |
| `received_at` | no | Server time webhook accepted. |
| `processed_at` | yes | Set equal to `received_at` on ingest v1 (no async pipeline yet). |
| `processing_error` | yes | Reserved for handler failures. |
| `tenant_id` | yes | FK → `tenant`, set only when `tenant_resolution_state = RESOLVED`. |
| `raw_payload` | yes | Full raw JSON body at ingest (legacy rows may be null). |
| `event_type` | yes | Provider event type string (`event_type` / Stripe `type`). |
| `tenant_resolution_state` | no | `RESOLVED` \| `UNRESOLVED_INSUFFICIENT_DATA` \| `UNRESOLVED_NO_MATCH`. |
| `tenant_resolution_reason_code` | yes | Stable machine code (e.g. `payer_missing`, `billing_provider_link_not_found`). |
| `tenant_resolution_missing_field_path` | yes | JSON path hint when insufficient data (e.g. `resource.payer`). |
| `tenant_resolution_resolver_version` | yes | e.g. `PayPalTenantResolverV1`, `StripeTenantResolverV1`. |

**Unique:** (`provider`, `provider_event_id`).

### Table: `billing_provider_link` *(shipped)*

| Column | Nullable | Description |
|--------|----------|-------------|
| `id` | no | UUID PK. |
| `tenant_id` | no | FK → `tenant`. |
| `provider` | no | `STRIPE` \| `PAYPAL`, length 16. |
| `external_customer_id` | no | Opaque provider customer / payer id, length 255. |
| `created_at` | no | |
| `updated_at` | no | |

**Unique:** (`tenant_id`, `provider`) — at most one link per provider per tenant. **Unique:** (`provider`, `external_customer_id`) — global id per billing account; upsert conflicts → **409** `BILLING_EXTERNAL_CUSTOMER_IN_USE`.

### Table: `billing_usage_event` *(shipped)*

| Column | Nullable | Description |
|--------|----------|-------------|
| `id` | no | UUID PK. |
| `tenant_id` | no | FK → `tenant`. |
| `metric_key` | no | Stable metric code (`BillingMetricKey` enum wire value), length 64. |
| `quantity` | no | Positive decimal (e.g. fractional GB for storage). |
| `idempotency_key` | no | Caller-supplied idempotency key, length 255. |
| `recorded_at` | no | UTC ingest time. |

**Unique:** (`tenant_id`, `idempotency_key`). **Index:** (`tenant_id`, `recorded_at`). **No** PII columns.

### Table: `billing_usage_aggregate` *(shipped — internal rollups)*

| Column | Nullable | Description |
|--------|----------|-------------|
| `id` | no | UUID PK. |
| `tenant_id` | no | FK → `tenant`. |
| `metric_key` | no | Same codes as `billing_usage_event.metric_key`. |
| `period_start` | no | UTC start of **daily** bucket (inclusive). |
| `period_end` | no | UTC start of next day (exclusive upper bound for event selection). |
| `total_quantity` | no | `DECIMAL(19,6)` — sum of event quantities in the bucket. |
| `last_aggregated_at` | no | When this row was last written by recompute. |
| `external_synced` | no | Default `false`; reserved for future provider / meter sync. |
| `external_synced_at` | yes | When marked synced externally (not used in v1). |

**Unique:** (`tenant_id`, `metric_key`, `period_start`). **Index:** (`tenant_id`, `period_start`).

---

## Proposed Schema Extension (requires PII review)

*Further billing tables (e.g. provider usage mirror, invoice snapshots) remain **proposed** until listed in **Allowed persistence** above.*

---

## Implementation sub-milestones (suggested order)

1. **Module + config:** env + `platform_setting` billing toggles — **partially shipped** (`billing.*.enabled` keys).
2. **Provider ↔ tenant mapping:** `billing_provider_link` + platform SuperAdmin APIs — **shipped**.
3. **Stripe:** Webhook ingest + idempotency table — **partially shipped**; hosted Checkout + Billing Portal session APIs — **partially shipped** (subscription checkout + portal URL only); Customer Portal configuration in Stripe Dashboard still required.
4. **PayPal:** Webhook ingest; optional **`verify-webhook-signature`** + OAuth when `PAYPAL_WEBHOOK_VERIFY=true` — **shipped**; tenant **`POST .../tenant/billing/paypal/subscription`** (billing plan subscribe + **`custom_id`**, → `approvalUrl`) — **partially shipped** (PayPal plan must exist in PayPal account; link payer to tenant via SuperAdmin + webhooks).
5. **Reconciliation:** map webhook events → `tenant_subscription` updates (with audit) where safe; manual override remains — **partially shipped** for Stripe **`checkout.session.completed`** + **`customer.subscription.updated`** + **`customer.subscription.deleted`**, and PayPal **`BILLING.SUBSCRIPTION.ACTIVATED`** / **`RE-ACTIVATED`** + **`CANCELLED`** / **`EXPIRED`** / **`SUSPENDED`**.
6. **Usage reporting:** tenant **`POST .../usage-events`** + **`billing_usage_event`** — **partially shipped**; **daily `billing_usage_aggregate` recompute** + **`GET .../usage-aggregates`** — **shipped (internal only)**; domain-event emitters + provider meter upload — **TBD**.

---

## Related

- [`commercial-plans.md`](./commercial-plans.md), [`commercial-subscriptions.md`](./commercial-subscriptions.md)
- [`audit.md`](./audit.md) — financial state changes should append audit facts (amounts optional per policy).
- [`notifications-inbox.md`](./notifications-inbox.md) — billing notices as templates + correlation ids.
- [`../product/BUILD-CHECKLIST.md`](../product/BUILD-CHECKLIST.md) — M3 billing checkbox.
