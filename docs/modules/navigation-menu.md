# Module: Navigation menu (tenant)

Effective **application menu** for the signed-in principal in the **current tenant** context. Visibility is driven by **effective privileges** (and later by subscription **feature flags** — resolver order per architecture: not enforced in M1 code paths beyond privilege).

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
| `created_at` | timestamp | |
| `updated_at` | timestamp | |

**Not stored:** feature-flag requirements in M1 (document under **Proposed Schema Extension** when subscription gating lands).

## API

- **`GET /api/v1/me/navigation`** — Session-authenticated; **requires tenant context** (tenant subdomain or `X-Tenant-Id` on API host). Response envelope `data.items`: array of **tree** nodes (`id`, `path`, `labelKey`, `sortOrder`, `children`). **M1 rule:** each row is included only if it passes the privilege filter; a node is attached under `parent_id` only when the parent is also visible; otherwise it is promoted to a **root** node.

## Security

- Same session + CSRF rules as other `/api/v1/**` routes.
- No PII in menu rows.

## Web / Flutter

- Web consumes tree to render nav; map `label_key` to strings client-side per [`i18n.md`](./i18n.md). Flutter later uses the same contract and bundles.

## Proposed Schema Extension (requires PII review)

- `required_feature_code` (nullable) — subscription feature flag gating when `commercial-subscriptions` ships.
