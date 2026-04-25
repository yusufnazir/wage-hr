# Module: commercial subscriptions

**Milestone:** M3. Depends on [`commercial-plans.md`](./commercial-plans.md). **Billing** is [`commercial-billing.md`](./commercial-billing.md) (money + webhooks; separate from gating).

**Authority:** Allowed columns for `tenant_subscription` below; follow [`DATA-MODEL-STANDARDS.md`](../guides/DATA-MODEL-STANDARDS.md).

---

## Intent (v1 slice)

- Each **tenant** has at most **one** persisted subscription row (v1 simplification): links the tenant to a **`commercial_plan`** and a **status** lifecycle enum.
- **Platform SuperAdmin** assigns or updates the plan via platform API. **Additionally (M3):** Stripe **`checkout.session.completed`** / **`customer.subscription.deleted`** webhooks may **automatically** set `tenant_subscription` to **`ACTIVE`** / **`CANCELLED`** when tenant resolution succeeds and payload checks pass (see [`commercial-billing.md`](./commercial-billing.md) Path B); audit **`TENANT_SUBSCRIPTION_STRIPE_RECONCILED`**.
- **Authenticated tenant members** can **read** the effective subscription (plan + resolved feature codes) for the **current tenant context** (`Host` / tenant routing).
- **Gating (v1):** `tenant_privilege_allowance` rows are **not** auto-mutated when a subscription changes. At **authorization** time, the **effective privilege pool ceiling** is the **union** of stored allowances and **subscription-derived** privilege codes (see **Gating resolver** below). **Product feature flags** for the client are the plan’s **`planFeatureCodes`**, exposed on **`GET /api/v1/me`** as `planFeatureCodes` when the subscription is **`ACTIVE`** (empty list otherwise). Optional **`nav_menu_item.required_plan_feature_code`** (or separate flag tables) remain **out of scope** until proposed.

---

## Gating resolver (v1)

**Order (tenant-scoped checks):** authentication → tenant context → membership → **effective pool** (allowances **∪** subscription-derived privileges from **`PlanFeaturePrivilegeWiring`**) → role grants on that pool → handler. **Platform SuperAdmin** elevation (global registered privilege) remains as documented in [`security.md`](./security.md).

- **Subscription-derived privileges:** only when `tenant_subscription.status = ACTIVE`. Plan features come from the linked `commercial_plan` + `commercial_plan_feature` → `plan_feature.code`. Each code is mapped in application code (`com.wagepayroll.plans.PlanFeaturePrivilegeWiring`) to zero or more **`privilege.code`** values that exist in the global catalog. **Example shipped mapping:** `HR_ESSENTIALS` → `USER_INVITE`. Unmapped / unknown plan feature codes are ignored for privilege expansion.
- **`GET /api/v1/me`:** returns effective `privileges` (role ∩ effective pool) and **`planFeatureCodes`** from the active subscription (or `[]`).
- **`GET /api/v1/tenant/privileges/pool`:** returns the **effective ceiling** (sorted union), same definition as pool checks in `PermissionService`.
- **Navigation:** items use `required_privilege_code` **and** optional **`required_plan_feature_code`** on `nav_menu_item` (see [`navigation-menu.md`](./navigation-menu.md)); widening the effective pool can reveal privilege-gated items once the user’s role already grants the underlying privilege; plan-feature-gated items appear only when the **active** subscription’s plan includes that feature code.

---

## Table: `tenant_subscription` (allowed columns)

| Column | Nullable | Description |
|--------|----------|-------------|
| `id` | no | UUID PK. |
| `tenant_id` | no | Tenant; **unique** (one row per tenant in v1). |
| `commercial_plan_id` | no | FK → `commercial_plan.id`. |
| `status` | no | `ACTIVE` or `CANCELLED` (string in DB). |
| `created_at` | no | |
| `updated_at` | no | |

**Indexes / FK:** unique `tenant_id`; FK to `commercial_plan` (`RESTRICT` on plan delete while referenced).

**No other columns** (billing period, provider ids, metered usage, etc.) without **Proposed Schema Extension**.

---

## APIs (v1)

| Method | Path | Actor | Purpose |
|--------|------|-------|---------|
| `GET` | `/api/v1/platform/tenants/{tenantId}/subscription` | Platform **superadmin** | Returns subscription + resolved `planCode` + `planFeatureCodes`, or **404** if none. |
| `PUT` | `/api/v1/platform/tenants/{tenantId}/subscription` | Platform **superadmin** | Upsert body: `commercialPlanId`, `status` (`ACTIVE` \| `CANCELLED`). Validates tenant + plan exist; `ACTIVE` requires `commercial_plan.active == true`. |
| `GET` | `/api/v1/me/subscription` | Authenticated + **tenant context** | Same payload shape under `data.subscription`, or **`subscription`: null** if unassigned. |

---

## Related

- [`commercial-plans.md`](./commercial-plans.md) — plans and features.
- [`commercial-billing.md`](./commercial-billing.md) — PayPal / Stripe, webhooks, metering (money path).
- [`../product/BUILD-CHECKLIST.md`](../product/BUILD-CHECKLIST.md) — M3.

---

## Proposed Schema Extension (requires PII review)

*Empty until proposed.*
