# Multi-tenancy and routing

Host-based routing, tenant identity, and login flows. Keep environment-driven configuration (`BASE_DOMAIN`, `AUTH_SUBDOMAIN`, `APP_SUBDOMAIN`, etc.).

## Tenant identity

- Resolve **tenant context** per request (e.g. `tenant_id` in session, claims, or resolved from host).
- Enforce tenant isolation at the **data access** layer (queries scoped by `tenant_id`).

## Subdomain strategy

- Tenant is derived from the **`Host`** header subdomain (e.g. `{tenant}.example.com`).
- Maintain a **reserved / blocked** subdomain list (`www`, `api`, `admin`, `auth`, `app`, etc.) and parsing rules.
- Provide a **fallback** for non-subdomain clients (header or path) with clear precedence rules.

## Local development

- Local multi-tenant dev MUST support **`*.lvh.me`** (or equivalent) so subdomains behave like production.
- Default **HTTP ports** for the common Spring Boot + Next.js stack: **8080** (API) and **3000** (Next dev) — see [LOCAL-DEV-PORTS.md](./LOCAL-DEV-PORTS.md). Subdomain URLs in docs and tests include the port when the dev server uses one (e.g. `http://auth.lvh.me:3000`).

## Auth vs app vs tenant hosts

- **Login** runs on `{AUTH_SUBDOMAIN}.{BASE_DOMAIN}` (default: `auth`).
- **After login:**
  - If tenant has a handle: redirect to `{tenantHandle}.{BASE_DOMAIN}`.
  - If tenant has **no** handle: redirect to `{APP_SUBDOMAIN}.{BASE_DOMAIN}` (default: `app`).
- Configuration MUST be **environment-driven** (env vars), including reserved subdomains.

### Redirect edge cases

- User visits `{tenant}.{BASE_DOMAIN}` while **logged out** → redirect to **auth** with a safe **return URL** (validated).
- User visits **auth** while **already logged in** → redirect to tenant or app host as appropriate.
- **wage-payroll (Next.js):** anonymous users are steered to the **auth** host for **`/`** and auth UI paths; **`GET /me`** on the client decides redirects for **`/`** and **401** on **`/app`** (cookie presence alone is insufficient for stale relay cookies or **403** on **admin.***). See [`docs/modules/tenancy-routing.md`](../modules/tenancy-routing.md) (section **Web** → **Anonymous entry, `/`, and login**).

## Unknown or invalid tenant handle

- Policy for unauthenticated requests to a **non-existent** tenant handle (e.g. **404** vs **401**): state explicitly; **404** on public routes reduces tenant enumeration.

**wage-payroll implementation:** see [`/docs/modules/tenancy-routing.md`](../modules/tenancy-routing.md) for HTTP status, `application/problem+json` on `/api/**`, and `X-Tenant-Id` rules on the API host.

## Multi-tenant membership

- If a user may belong to **multiple** tenants: define post-login **tenant picker** rules, default tenant, and host switching (e.g. picker on `app` host vs last-used cookie).

## Tenant handle rules

- Allowed characters, length, case normalization, uniqueness.
- **Rename:** whether old handles keep working (redirect) or break; how bookmarks/links are handled.

## Host-based route constraints

- Which routes exist on **auth** host vs tenant/app hosts.
- Prevent tenant content from being served on `auth.*` by mistake (frontend routing + backend exposure).

## Commercial / plans (if applicable)

- How plans and entitlements relate to hosts is **not** defined here; see architecture contract and commercial sections.
