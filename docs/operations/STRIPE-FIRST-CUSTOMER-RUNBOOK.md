# Runbook: first Stripe subscription (tenant SaaS)

Use this when **Path B** auto-reconcile from webhooks is enabled (see [`commercial-billing.md`](../modules/commercial-billing.md) § *First paying customer*). For **Path A** (manual `tenant_subscription`), skip webhook-driven entitlement and set subscription in the platform UI after you verify payment in Stripe Dashboard.

## 1. Platform configuration

1. Set **`STRIPE_SECRET_KEY`**, **`STRIPE_WEBHOOK_SECRET`**, and (production) **`STRIPE_ALLOW_INSECURE_CHECKOUT_URLS=false`** on the API host.
2. Turn on **`billing.stripe.enabled`** (`platform_setting` or SuperAdmin settings PATCH per `platform-settings.md`).
3. Register the webhook endpoint **`POST /api/v1/billing/webhooks/stripe`** in Stripe Dashboard with the signing secret above.

## 2. Commercial plan

1. Ensure an **active** `commercial_plan` row with **`stripe_subscription_price_id`** equal to the Stripe **Price** id used in Checkout (`price_…`).
2. Replace the Liquibase **`DEMO_STARTER`** placeholder price in non-dev environments if you still use that seed row.

## 3. Tenant linkage

1. Create or resolve the Stripe **Customer** for the tenant.
2. SuperAdmin: **`PUT .../platform/tenants/{tenantId}/billing-provider-links/stripe`** with `{ "externalCustomerId": "<cus_…>" }`.

## 4. Tenant purchase

1. Tenant admin (**`TENANT_SETTINGS_EDIT`**): open tenant web **`/app`**, pick the plan, **Subscribe with Stripe Checkout** (or call **`POST .../tenant/billing/stripe/checkout-session`** with HTTPS success/cancel URLs in production).
2. After success, confirm **`checkout.session.completed`** and subscription lifecycle events appear in logs / `billing_webhook_receipt` as expected.

## 5. Verification

1. **`GET /api/v1/me`** on the tenant host: **`planFeatureCodes`** and privileges match the subscribed commercial plan (`PlanFeaturePrivilegeWiring`).
2. **`GET /api/v1/tenant/billing/summary`**: subscription snapshot shows **ACTIVE** and correct plan id/code when reconciled.

## 6. Self-serve billing portal

1. With customer linked and Stripe Billing Portal configured in Stripe Dashboard, tenant admin uses **Open Stripe billing portal** on **`/app`** (or **`POST .../stripe/billing-portal-session`**).

## References

- Module: [`commercial-billing.md`](../modules/commercial-billing.md)
- Checklist: [`BUILD-CHECKLIST.md`](../product/BUILD-CHECKLIST.md) → *First paying customer — billing slice*
