# Module: Tenancy and host routing (M1)

**Feature slug:** `tenancy-routing`  
**Related:** [`../guides/MULTI-TENANCY-AND-ROUTING.md`](../guides/MULTI-TENANCY-AND-ROUTING.md), [`web-auth-session.md`](./web-auth-session.md), [`tenant-web-vertical-slice.md`](./tenant-web-vertical-slice.md)

This module is the **product-specific** contract for how the API resolves **host mode**, **tenant handle**, and **tenant id** per request. Narrative and edge-case discussion stay in the guide; **behavior and HTTP codes** below are authoritative for implementation.

---

## Host modes (`Host` header)

Configuration: `app.host.base-domain`, `app.host.auth-subdomain`, `app.host.app-subdomain`, `app.host.reserved-subdomains-extra` (see `AppHostProperties`).

| Mode | Typical host (local) | Tenant context |
|------|----------------------|----------------|
| **AUTH** | `{auth}.{base}` e.g. `auth.lvh.me` | None |
| **APP** | `{app}.{base}` e.g. `app.lvh.me` | None **unless** optional `X-Tenant-Id` (same resolution rules as API) |
| **ADMIN** | `admin.{base}` e.g. `admin.lvh.me` | None from host; optional `X-Tenant-Id` for platform operator **tenant lens**. **API:** authenticated users who are **not** platform superadmins receive **403** on `/api/**` (permitAll auth/public routes unchanged). |
| **API** | `api.{base}` (reserved subdomain) | Optional via `X-Tenant-Id` (see below) |
| **TENANT** | `{handle}.{base}` e.g. `demo.lvh.me` | Resolved from **handle** → `tenant` row; if **`X-Tenant-Id`** is also present and valid, it **overrides** host handle (superadmin BFF lens on a tenant subdomain). |
| **UNKNOWN** | Bare base, wrong TLD, etc. | None |

Port suffixes on `Host` are ignored for parsing (`demo.lvh.me:3007` → handle `demo`).

Forwarded host: `X-Forwarded-Host` is preferred over `Host` when present (trust proxy configuration is deployment-owned).

---

## Unknown or invalid tenant

### Tenant subdomain (`TENANT` mode)

- Subdomain is **not** reserved and is treated as a tenant **handle**.
- If **no** `tenant` row exists for that handle: respond **404** for **`/api/**`** requests with **`Content-Type: application/problem+json`** (RFC 7807 `ProblemDetail`, includes `code: UNKNOWN_TENANT`).
- Non-API paths: servlet container default **404** (HTML) is acceptable for M1 (no browser HTML API on unknown tenant in normal flows).

Rationale: **404** on public routes reduces casual tenant enumeration vs **401**.

### API subdomain (`API` mode) — `X-Tenant-Id`

- Header **absent** or blank: tenant context **unset** (callers that require tenant must enforce themselves, e.g. `@RequiresPrivilege` + `TenantContext`).
- Header **present**, not a valid UUID: **400** `application/problem+json`, `code: INVALID_TENANT_ID_HEADER`.
- Header **present**, valid UUID, **no** matching `tenant` row: **404** `application/problem+json`, `code: UNKNOWN_TENANT_ID`.

---

## Membership vs tenant existence

A **valid** tenant handle with **no membership** for the signed-in user is **not** a routing error: routing succeeds; authorization or `/me` payload reflects membership (e.g. `AccessDenied` on privileged routes, or empty privileges where designed).

---

## API — tenant directory (M1)

- **`GET /api/v1/me/tenants`** — Session-authenticated; **does not** require `TenantContext`. Returns `data.tenants`: array of `{ id, handle, name, roles }` where `roles` are **role names** assigned to the principal in that tenant (sorted). Tenants are sorted by **handle**. Used for **tenant switcher** UX (deep-link to `{handle}.{BASE_DOMAIN}` /app).

---

## Data touched

- Read-only: `tenant`, `membership`, `user_role`, `role` (no schema changes in this module for M1).

---

## Web (Next.js) expectations

- **Tenant app** pages call the API with `credentials: "include"`.
- When the **browser** called the API directly with a base URL like **`http://localhost:8300`**, the HTTP **`Host`** header on the API request was **`localhost`**, so the filter did **not** see `{tenant}.lvh.me`. **Current product default:** the Next.js **BFF** (`/api/bff/...`) forwards the browser **`Host`** as **`X-Forwarded-Host`**, and `TenantContextFilter` prefers that header—so tenant resolution matches the page origin without exposing the API origin to the client. **Other mitigations:** run the API behind a reverse proxy that forwards `Forwarded` / `X-Forwarded-*` with **`app.forwarding.trust-proxy=true`**, or use **API** host mode with a valid **`X-Tenant-Id`** header. **Integration tests** set `Host: demo.lvh.me` on MockMvc requests to mirror production-like routing.
- **`GET /api/v1/me`** returning **404** `UNKNOWN_TENANT` on a tenant host → treat as **unknown tenant** (show a dedicated message; do not loop login).

### Anonymous entry, `/`, and login (authoritative for `frontend/`)

Implementation lives in **`frontend/src/middleware.ts`**, **`frontend/src/app/page.tsx`**, **`frontend/src/app/login/page.tsx`**, **`frontend/src/components/shell/TenantAppShell.tsx`**, **`frontend/src/lib/web-origins.ts`**.

1. **Middleware (cookie hint only)**  
   - If there is **no** **`wp_bff_j`** BFF relay cookie: **`/`** redirects to **`{NEXT_PUBLIC_AUTH_WEB_ORIGIN}/login`** (on **non-auth** hosts and on the **auth** host, so `/` is never a static anonymous “home” for first paint).  
   - If **`wp_bff_j`** is present, **`/`** is not redirected here (the cookie may be **stale** or the user may be **forbidden** on **ADMIN** host—see (2)).  
   - **`/login`**, **`/register`**, **`/verify-email`**, **`/forgot-password`**, **`/reset-password`**, **`/terms-of-service`**, **`/privacy-policy`** on a **non-auth** host redirect to the same path on **`NEXT_PUBLIC_AUTH_WEB_ORIGIN`** (see [`account-registration.md`](./account-registration.md) for verify flow).  
   - **`x-wage-host`** is set on all matched requests.

2. **Root `/` (`app/page.tsx`)** — client **`GET /api/bff/v1/me`**  
   - **Any failure** (`401`, **`403`** on **admin.*** for non–platform-superadmin, other status, network error) → **`window.location.replace(authLoginUrlWithReturnTo(current URL))`** so the user always gets the **real login** on the auth host (no intermediate “access denied” card on `/`).  
   - **Success** → platform superadmin → **`{NEXT_PUBLIC_ADMIN_WEB_ORIGIN}/app`**, else **`defaultTenantAppUrl()`**, each gated by **`GET /api/v1/auth/redirect-check`**; if the check fails, fall back to auth login with **`returnTo`**.

3. **Tenant app shell (`/app` layout)**  
   - **`GET /me`** **401** → **`window.location.replace`** to **`{auth}/login`** with optional **`returnTo`** (validated after sign-in). Brief “Redirecting…” only if an edge case leaves internal phase `unauthenticated`.

4. **Login page**  
   - **Mount:** if **`/me`** already succeeds, redirect using the same post-login rules as submit (so a **viewer** who hit **`admin.`** → auth login with **`returnTo`** is sent to **demo `/app`**, not stuck on the form).  
   - **`returnTo`:** use when **`redirect-check`** allows it, **except** when **`returnTo`** targets the **admin** web origin and the user is **not** a platform superadmin → use **`defaultTenantAppUrl()`** instead (prevents redirect loops).

---

## Future (not M1)

- **Default / last-used tenant** cookie and post-login **auto pick** when the user belongs to more than one tenant (`MULTI-TENANCY-AND-ROUTING.md`).
- **Strict** API mode: require `X-Tenant-Id` for all tenant-scoped routes (env flag).
