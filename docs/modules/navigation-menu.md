# Module: Navigation menu (tenant)

Effective **application menu** for the signed-in principal in the **current tenant** context. Visibility is driven by **effective privileges** and, when set on a row, by **subscription plan feature codes** (`required_plan_feature_code` vs active `tenant_subscription` per [`commercial-subscriptions.md`](./commercial-subscriptions.md)).

## Data — `nav_menu_item` (strict)

**Allowed columns only** (no free-form labels in DB — use i18n keys):

| Column | Type | Notes |
|--------|------|--------|
| `id` | UUID | PK |
| `tenant_id` | UUID | FK → `tenant.id` |
| `parent_id` | UUID, nullable | FK → `nav_menu_item.id`; must reference a row in the **same** tenant (enforced in application layer) |
| `path` | string | App route path (e.g. `/app`, `/app/users`) |
| `label_key` | string | Message / i18n key (not user-entered copy) |
| `sort_order` | int | Sibling order |
| `required_privilege_code` | string, nullable | If set, user must have this `privilege.code` in the current tenant (and allowance + role) to see the item |
| `required_plan_feature_code` | string, nullable | If set, must match a **`plan_feature.code`**; the item is shown only when the tenant’s **`tenant_subscription`** is **`ACTIVE`** and the linked commercial plan includes that feature code (same codes as **`GET /api/v1/me`** `planFeatureCodes`). Evaluated **after** the privilege filter. |
| `created_at` | timestamp | |
| `updated_at` | timestamp | |

## API

- **`GET /api/v1/me/navigation`** — Session-authenticated; **requires tenant context** (tenant subdomain or `X-Tenant-Id` on API host). Response envelope `data.items`: array of **tree** nodes (`id`, `path`, `labelKey`, `sortOrder`, `children`). **Visibility:** each row must pass the **privilege** filter (`required_privilege_code`) **and**, when set, the **plan-feature** filter (`required_plan_feature_code` vs active subscription’s plan features per [`commercial-subscriptions.md`](./commercial-subscriptions.md)). A node is attached under `parent_id` only when the parent is also visible; otherwise it is promoted to a **root** node.

**Web consumption (wage-payroll):** the Next.js tenant app renders the same tree in the **left sidebar** inside **`TenantAppShell`** (`frontend/src/components/shell/AppSidebar.tsx`). Each `path` must be a real App Router URL (e.g. `/app`, `/app/documents`); the dashboard page may still echo the tree for debugging — see **`tenant-web-vertical-slice.md`** §3.6.

## Security

- Same session + CSRF rules as other `/api/v1/**` routes.
- No PII in menu rows.

## Web / Flutter

- Web consumes tree to render nav; map `label_key` to strings client-side per [`i18n.md`](./i18n.md). Flutter later uses the same contract and bundles.

## Proposed Schema Extension (requires PII review)

*Empty — `required_plan_feature_code` is now in the **allowed** table above.*
