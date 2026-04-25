# Module: commercial plans (feature catalog + plan editor)

**Milestone:** M3. Companion topics: [`commercial-subscriptions.md`](./commercial-subscriptions.md) (subscriptions + gating) and [`commercial-billing.md`](./commercial-billing.md) (PayPal + Stripe billing).

**Authority:** Allowed tables/columns below; follow [`DATA-MODEL-STANDARDS.md`](../guides/DATA-MODEL-STANDARDS.md) and [`SCHEMA-PERSISTENCE-PREFLIGHT.md`](../guides/SCHEMA-PERSISTENCE-PREFLIGHT.md).

---

## Intent

- **Catalog:** stable product feature codes in **`PlanFeatureCode`** enum **and** table **`plan_feature`** (Liquibase seed).
- **Plans:** SuperAdmin defines **`commercial_plan`** rows and attaches **only** catalog features via **`commercial_plan_feature`** (no free-text feature invention). **Delete:** unused plans may be removed with **`DELETE /api/v1/platform/commercial-plans/{id}`** when **no** `tenant_subscription` references the plan (**409** if still in use). **Rename / version history** remain **out of v1** (plan `code` is immutable after create).
- **Subscriptions** assigning a plan to a tenant are specified in [`commercial-subscriptions.md`](./commercial-subscriptions.md) (this module covers plan composition only).

---

## Table: `plan_feature` (allowed columns)

| Column | Nullable | Description |
|--------|----------|-------------|
| `id` | no | UUID PK. |
| `code` | no | Unique; **must** match `PlanFeatureCode.name()`. |
| `sort_order` | no | Display / sort only. |
| `created_at` / `updated_at` | no | Timestamps. |

**Indexes:** unique `code`; optional `(sort_order, code)`.

---

## Table: `commercial_plan` (allowed columns)

| Column | Nullable | Description |
|--------|----------|-------------|
| `id` | no | UUID PK. |
| `code` | no | Unique admin identifier (`^[A-Z][A-Z0-9_]{0,63}$` after trim+uppercase). Immutable after create (enforced by API — no rename endpoint in v1). |
| `sort_order` | no | Ordering among plans. |
| `active` | no | Soft switch; subscriptions may still reference inactive plans (policy TBD in subscriptions module). |
| `created_at` / `updated_at` | no | Timestamps. |
| `stripe_subscription_price_id` | yes | When set, must match Stripe **`price_…`** id used for that plan’s subscription Checkout line item; **globally unique** among plans (including inactive rows). Used to validate tenant **`POST .../stripe/checkout-session`** and for minimal Stripe webhook reconciliation (see [`commercial-billing.md`](./commercial-billing.md)). |
| `paypal_billing_plan_id` | yes | When set, must be a PayPal billing **plan** id (`P-…`, max 128 chars); **globally unique** among plans. Used to validate tenant **`POST .../tenant/billing/paypal/subscription`** **`planId`** (see [`commercial-billing.md`](./commercial-billing.md)). |

**Indexes:** unique `code`; optional `(sort_order, code)`; **unique** `stripe_subscription_price_id` (nullable; DBs allow multiple `NULL`); **unique** `paypal_billing_plan_id` (nullable; DBs allow multiple `NULL`).

**Dev seed:** Liquibase `data-m3-commercial-plan-seed-demo-1` inserts active **`DEMO_STARTER`** ( **`TENANT_CORE`** only) with placeholder Stripe/PayPal ids so **`GET .../tenant/billing/commercial-plans`** is non-empty on fresh installs; replace ids for real sandboxes.

---

## Table: `commercial_plan_feature` (allowed columns)

| Column | Nullable | Description |
|--------|----------|-------------|
| `id` | no | UUID PK. |
| `commercial_plan_id` | no | FK → `commercial_plan.id` (CASCADE delete). |
| `plan_feature_id` | no | FK → `plan_feature.id` (RESTRICT delete). |
| `created_at` / `updated_at` | no | Timestamps. |

**Constraints:** unique `(commercial_plan_id, plan_feature_id)`.

---

## Application rules

- **`PlanFeatureCode`** ↔ `plan_feature` sync: see **`PlanFeatureCatalogSyncIT`**.
- **Plan composition:** `plan_feature_id` values must exist; duplicate IDs in one request are de-duplicated (insert order preserves first occurrence).
- **Empty feature list** on create/replace → **400** `PLAN_FEATURES_REQUIRED`.

---

## API (platform superadmin)

All under **`/api/v1/platform/...`**; same actor gate as other platform routes (`platform_superadmin`).

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/v1/platform/plan-features` | List catalog features. |
| `GET` | `/api/v1/platform/commercial-plans` | List plans with `featureCount`. |
| `GET` | `/api/v1/platform/commercial-plans/{id}` | Plan detail: `planFeatureIds` + `planFeatureCodes` ordered by catalog `sort_order`, then `code`. |
| `POST` | `/api/v1/platform/commercial-plans` | Create plan. Body: `code`, `sortOrder`, `active` (optional, default true), `planFeatureIds` (non-empty), optional `stripeSubscriptionPriceId` (`price_…`), optional `paypalBillingPlanId` (`P-…`). **201 Created**. |
| `PUT` | `/api/v1/platform/commercial-plans/{id}` | Replace `sortOrder`, `active`, and full feature link set. Body: `sortOrder`, `active`, `planFeatureIds` (non-empty), optional `stripeSubscriptionPriceId`, optional `clearStripeSubscriptionPrice` (`true` clears the Stripe price mapping), optional `paypalBillingPlanId`, optional `clearPaypalBillingPlanId` (`true` clears the PayPal plan mapping). |
| `DELETE` | `/api/v1/platform/commercial-plans/{id}` | Delete plan when **not** referenced by any `tenant_subscription`. **204 No Content**; **404** unknown id; **409** `COMMERCIAL_PLAN_IN_USE`. Audits **`COMMERCIAL_PLAN_DELETED`**. |

**Response shapes:**  
- Plan features: `data.features[]` (`id`, `code`, `sortOrder`, `createdAt`, `updatedAt`).  
- Plan list: `data.plans[]` (`id`, `code`, `sortOrder`, `active`, `featureCount`, `stripeSubscriptionPriceId`, `paypalBillingPlanId`).  
- Plan detail / create / replace: `data` = `CommercialPlanDetailDto` (`id`, `code`, `sortOrder`, `active`, `planFeatureIds`, `planFeatureCodes`, `stripeSubscriptionPriceId`, `paypalBillingPlanId`).

---

## Related

- [`../product/BUILD-CHECKLIST.md`](../product/BUILD-CHECKLIST.md) — M3 checklist.
- [`../prompts/PROJECT-CONTEXT.md`](../prompts/PROJECT-CONTEXT.md) — commercial gating intent.

---

## Proposed Schema Extension (requires PII review)

*Empty until proposed.*
