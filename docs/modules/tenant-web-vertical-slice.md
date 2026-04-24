# Tenant web vertical slice (post-login app shell)

**Feature slug:** `tenant-web-vertical-slice`  
**Status:** implemented (web + backend tests + Playwright partial)  
**Goal:** Close the loop between **auth host login**, **tenant subdomain browsing**, **session cookies**, and **privileged API calls** so the architecture is proven end-to-end before building payroll features.

**Related docs:** [`tenancy-routing.md`](./tenancy-routing.md) (host + HTTP contract), `docs/guides/MULTI-TENANCY-AND-ROUTING.md`, `docs/modules/web-auth-session.md`, `docs/guides/API-CONVENTIONS.md`, `docs/prompts/PROJECT-CONTEXT.md`  
**Verification:** `docs/output/FEATURE-tenant-web-vertical-slice-VERIFICATION.md`

---

## 1. Why this exists

The repository already had:

- **Backend:** Session-based auth, CSRF, `TenantContextFilter`, `GET /api/v1/me`, `GET /api/v1/demo/user-view` (`USER_VIEW`).
- **Frontend:** Home + `/login` calling Spring **only via** the Next.js BFF (`/api/bff/...`); relay cookies, not direct API origin in the browser.

This feature adds the **tenant app shell** at **`/app`**, login **redirect-check** + navigation to the demo tenant, integration tests for **`/me`**, Playwright coverage (Next-only + optional API), and verification notes.

**Mobile:** still out of scope (no Flutter deliverable for this slug).

---

## 2. Product outcome (definition of done)

A developer can follow documented steps and observe:

1. Open **`http://auth.lvh.me:3007/login`**, sign in with a seeded user (see §5).
2. After successful login, browser navigates to **`http://demo.lvh.me:3007/app`** when `redirect-check` allows that URL.
3. **`GET /api/bff/v1/me`** (from `/app`, same-origin to Next) returns **`tenantHandle: "demo"`** and non-empty **`privileges`** for the admin user on the demo host (Next proxies to Spring with **`X-Forwarded-Host`**).
4. **`GET /api/bff/v1/demo/user-view`** succeeds for that user (same relay session + tenant + `USER_VIEW`).
5. Unknown tenant host → **404** from backend; `/app` shows the **unknown tenant** message when the stack is reachable.

---

## 3. Technical context (read before coding)

### 3.1 Hosts and ports (local)

- **Base domain:** `lvh.me` → `127.0.0.1`.
- **Frontend dev:** port **3007**.
- **Spring API:** **8300** — reached only from the **Next.js server** via **`API_BASE_URL`** (see `frontend/.env.example`). The browser never embeds this origin.

### 3.2 CORS and credentials

- `app.cors.allowed-origin-patterns` still allows `*.lvh.me:3007` for **direct** API access (tools, mobile, future use). **Product web UI:** the browser calls **`/api/bff/...`** same-origin with **`credentials: "same-origin"`**; CORS is not involved for that traffic.

### 3.3 Session relay (local dev)

Spring issues **`JSESSIONID` / `XSRF-TOKEN`** to the Next server. The BFF mirrors values into HttpOnly **`wp_bff_j` / `wp_bff_x`** cookies for the browser, with **`Domain=.lvh.me`** when the request host matches `*.lvh.me` (override via **`BFF_SESSION_COOKIE_DOMAIN`**). Cross-subdomain navigation (`auth.*` → `demo.*`) therefore shares one login without exposing the API URL to the client.

### 3.4 Tenant resolution

- **`TenantContextFilter`:** tenant from **`X-Forwarded-Host`** when present (set by the BFF to the browser `Host`), else `Host` / server name; `demo.lvh.me` → handle `demo`; unknown handle → **404**.
- **`MeController`:** `/api/v1/me` — email, `tenantHandle`, `privileges` (empty when no tenant context).
- **`DemoController`:** `/api/v1/demo/user-view` — requires **`USER_VIEW`**.

### 3.5 CSRF

Spring still enforces CSRF for mutating **`/api/v1/**`** calls. The **BFF** prefetches **`GET /api/v1/auth/csrf`** server-side before each proxied **POST/PATCH/PUT/DELETE**, then forwards the header Spring expects. The browser does not handle CSRF tokens.

### 3.6 Theming (light / dark / system)

Follow **`docs/guides/WEB-THEMING-AND-DESIGN-SYSTEM.md`**.

- **Tokens:** `frontend/src/styles/tokens-primitives.css` (raw palette) → `tokens-semantic.css` (semantic CSS variables, including **`--color-accent`** for future tenant/white-label overrides) → Tailwind `theme.extend` maps utilities to `var(--…)`.
- **Mode:** `next-themes` (`AppThemeProvider`) + **`beforeInteractive`** script in `src/app/layout.tsx` to reduce flash of wrong theme from `localStorage` / `prefers-color-scheme`.
- **Layouts:** **`data-layout="auth"`** — `AuthShell` + glass cards on home, login, register, forgot/reset password. **`data-layout="app"`** — tenant `/app` uses a **sticky opaque header** (app chrome), not the auth glass treatment.
- **Toggle:** `ThemeToggle` cycles **Light → Dark → System** (`data-testid="theme-toggle"`).

---

## 4. Seed data (for manual testing)

| Item | Value |
|------|--------|
| Demo tenant handle | **`demo`** |
| Second tenant (M1) | **`acme`** — same admin user; **Reader** role (**`USER_VIEW`** only); narrower privilege pool than `demo` |
| Admin user | **`admin@demo.lvh.me`** / **`ChangeMe!1`** |
| Privilege | **`USER_VIEW`** (and others on `demo`) via roles |

---

## 5. API surface (backend)

| Method | Path | Auth | Tenant | Notes |
|--------|------|------|--------|--------|
| GET | `/api/v1/auth/csrf` | Public | — | CSRF token for POST |
| POST | `/api/v1/auth/login` | Public | — | Establishes session |
| GET | `/api/v1/auth/redirect-check?returnTo=` | Public | — | **204** allowed, **400** invalid open redirect |
| GET | `/api/v1/me` | Authenticated | From `Host` / API header | JSON `ApiResponse` with `email`, `locale`, `tenantHandle`, `privileges`, `platformSuperadmin` |
| GET | `/api/v1/me/tenants` | Authenticated | — | All memberships: `tenants[]` with `handle`, `name`, `roles` |
| PATCH | `/api/v1/me/locale` | Authenticated | — | Body `{ "locale": "nl-sr" }`; **204**; CSRF |
| GET | `/api/v1/demo/user-view` | Authenticated | Required | **`USER_VIEW`** or **403** |

**Tests:** `backend/src/test/java/com/wagepayroll/api/MeEndpointIT.java`, `MeTenantsIT.java`, `MeLocalePatchIT.java`, `com.wagepayroll.audit.AuditAppendIT.java`, `DemoPrivilegedEndpointIT.java`.

---

## 6. Web flows

| Flow | Behavior |
|------|----------|
| Auth login | `http://auth.lvh.me:3007/login` → POST login → **`redirect-check`** on `http://demo.lvh.me:3007/app` → **`window.location.assign`** if **204**; else message with manual link. |
| Tenant shell | `http://{tenant}.lvh.me:3007/app` loads **`/app`**, fetches **`/api/bff/v1/me`**, tenants, demo user-view (+ navigation); **tenant switcher** when the user has more than one tenant; **locale** `<select>` calls **`PATCH /api/bff/v1/me/locale`**. |
| Unauthenticated | **`/api/bff/v1/me`** returns **401** → show **Sign in** `<a href={authLoginUrl()}>` (cross-host). |
| Unknown tenant | **`/api/bff/v1/me`** returns **404** → unknown-tenant copy. |
| Backend unreachable / other errors | Message plus **Sign in** link so users are not stuck off-host. |
| Home | Link **Demo tenant app** → `defaultTenantAppUrl()` (`NEXT_PUBLIC_DEFAULT_TENANT_WEB_ORIGIN` / `http://demo.lvh.me:3007/app`). |

**Env:** see `frontend/.env.example` — **`API_BASE_URL`** (server-only), optional **`BFF_SESSION_COOKIE_DOMAIN`**, and **`NEXT_PUBLIC_AUTH_WEB_ORIGIN`**, **`NEXT_PUBLIC_DEFAULT_TENANT_WEB_ORIGIN`** for multi-host URLs in the client.

**Code:** `frontend/src/app/api/bff/[...path]/route.ts`, `frontend/src/lib/server/*`, `frontend/src/app/app/page.tsx`, `frontend/src/lib/api.ts`, `frontend/src/lib/web-origins.ts`, `frontend/src/app/login/page.tsx`, `frontend/src/app/page.tsx`, `frontend/src/components/shell/AuthShell.tsx`, `frontend/src/components/theme/*`, `frontend/src/styles/tokens-*.css`.

---

## 7. Mobile (Phase 3)

**Not implemented** — module explicitly out of scope; no Flutter screens for this slug.

---

## 8. Acceptance checklist

- [x] Login at auth host works; BFF relay cookies (`wp_bff_*`, see verification doc).
- [x] **`demo.lvh.me:3007/app`** loads tenant shell; **`/me`** shows **`tenantHandle: "demo"`** and privileges when session + API available.
- [x] **`/api/v1/demo/user-view`** succeeds for admin on tenant host (manual + backend IT).
- [x] Unknown tenant → **404** + UI message (manual / Playwright with API).
- [x] Manual / automated verification documented (`FEATURE-tenant-web-vertical-slice-VERIFICATION.md`, README link).

---

## 9. Changelog (doc maintenance)

| Date | Change |
|------|--------|
| 2026-04-22 | Implemented `/app` shell, `web-origins` + API helpers, post-login `redirect-check`, `MeEndpointIT`, Playwright `tenant-vertical-slice.spec.ts`, verification doc, README + PROJECT-CONTEXT updates; later same day: **BFF** (`/api/bff/...`), **`API_BASE_URL`** server-only, relay cookies, **`X-Forwarded-Host`** for tenants. |

---

## 10. AI implementation prompt (copy-paste)

*(Historical — implementation complete; use for regressions or adjacent features.)*

```text
You are implementing the "tenant web vertical slice" feature documented in docs/modules/tenant-web-vertical-slice.md. Read that file fully first, then implement.

OBJECTIVE
Prove end-to-end: login on the auth subdomain, browse the demo tenant subdomain on the Next.js dev server, and call the existing Spring Boot APIs GET /api/v1/me and GET /api/v1/demo/user-view with the same browser session cookie. Show results in a minimal tenant-facing page.

CONSTRAINTS
- Work primarily in frontend/ (Next.js App Router). Backend changes only if tenant resolution needs a new forwarded header when using the BFF.
- Browser calls only **`/api/bff/...`** (same-origin); Next server uses **`API_BASE_URL`** to reach Spring and forwards **`X-Forwarded-Host`** from the browser `Host`.
- Relay cookies **`wp_bff_j` / `wp_bff_x`** with **`Domain=.lvh.me`** for cross-subdomain sessions in local dev.
- Seed data: tenant handle "demo", user admin@demo.lvh.me / ChangeMe!1 (see DataScaffoldSeed1).
- Use real hosts in testing: http://auth.lvh.me:3007 and http://demo.lvh.me:3007 (lvh.me resolves to 127.0.0.1).

IMPLEMENTATION TASKS
1) Add API helpers (in frontend/src/lib/api.ts or a sibling module) for authenticated GET /api/bff/v1/me and GET /api/bff/v1/demo/user-view using fetch + credentials same-origin. Parse ApiResponse JSON shape { data, meta } matching backend ApiResponse.
2) Add a tenant app page/route (choose a clear path) that renders me payload and optionally demo/user-view result; handle loading and errors; if 401, show link to auth login URL.
3) Ensure navigation story: after login page success, user can reach demo tenant URL (link from login or home with full URL http://demo.lvh.me:3007/...). Prefer safe redirects consistent with /api/v1/auth/redirect-check if you implement redirect-after-login.
4) If session is not shared across subdomains: verify BFF relay cookie **Domain** (`.lvh.me`) and **`API_BASE_URL`** reachability from the Next server. Verify in browser DevTools (Application → Cookies on `*.lvh.me`).
5) Update docs: add a short "Manual verification" subsection to docs/modules/tenant-web-vertical-slice.md §9 changelog or root README with exact URLs and expected JSON fields.

DO NOT add payroll domain tables or mobile code. Keep UI minimal and consistent with existing Tailwind/theme components.

VERIFICATION
Manually: npm run dev on frontend, backend running with DB + Liquibase migrated; hit auth login then demo host page; confirm tenantHandle and privileges on /me and success on /demo/user-view.
```

---

## 11. References (code)

| Area | Location |
|------|----------|
| BFF proxy + relay cookies | `frontend/src/app/api/bff/[...path]/route.ts`, `frontend/src/lib/server/spring-bff-cookies.ts`, `frontend/src/lib/server/upstream-base.ts` |
| Login + API client | `frontend/src/lib/api.ts`, `frontend/src/app/login/page.tsx` |
| Web origins | `frontend/src/lib/web-origins.ts` (`tenantWebAppUrlForHandle`) |
| Tenant shell | `frontend/src/app/app/page.tsx` |
| Me API | `backend/src/main/java/com/wagepayroll/api/MeController.java`, `com.wagepayroll.tenant.TenantDirectoryService.java` |
| Demo API | `backend/src/main/java/com/wagepayroll/api/DemoController.java` |
| Me IT | `backend/src/test/java/com/wagepayroll/api/MeEndpointIT.java` |
| Tenant filter | `backend/src/main/java/com/wagepayroll/tenant/TenantContextFilter.java` |
| Security / CSRF | `backend/src/main/java/com/wagepayroll/security/SecurityConfiguration.java` |
| CORS | `backend/src/main/java/com/wagepayroll/config/WebConfiguration.java`, `application.yml` `app.cors` |
| Seed users / tenant | `DataScaffoldSeed1.java`, `DataM1SecondTenantAcmeSeed1.java` |
| E2E | `frontend/e2e/m1-platform.spec.ts` (auth → demo `/app`, locale PATCH, unknown host), `tenant-vertical-slice.spec.ts`, `smoke.spec` — `PLAYWRIGHT_PORT` (default **3007**), **`PLAYWRIGHT_API_BASE_URL`** for API-backed cases; `playwright.config.ts` starts `next dev`. |
