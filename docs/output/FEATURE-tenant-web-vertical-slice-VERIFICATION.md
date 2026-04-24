# Verification — tenant web vertical slice

**Module:** `docs/modules/tenant-web-vertical-slice.md`  
**Feature slug:** `tenant-web-vertical-slice`

## Automated

| Area | Command | Notes |
|------|---------|--------|
| Backend | `cd backend && ./mvnw.cmd test` | Includes `MeEndpointIT` (Host header + `/api/v1/me`), existing `DemoPrivilegedEndpointIT`. |
| Frontend build | `cd frontend && npm run build` | |
| Frontend lint | `cd frontend && npm run lint` | |
| Playwright (Next only) | `cd frontend && npm run e2e` | Starts Next via `npx next dev -p $PLAYWRIGHT_PORT` (default **3007**). If dev already uses 3007: `set PLAYWRIGHT_PORT=3108` then `npm run e2e`. Tests use `http://demo.lvh.me:<port>/app` (requires `*.lvh.me` → 127.0.0.1). |
| Playwright + API | `cd frontend && set PLAYWRIGHT_API_BASE_URL=http://127.0.0.1:8300&& npm run e2e` (Windows) | Start API + DB first; Next must have **`API_BASE_URL`** (see `.env.example`). Browser traffic uses **`/api/bff/...`** only; **`PLAYWRIGHT_API_BASE_URL`** gates tests that need a live Spring instance. |

## Manual smoke (full stack)

Prerequisites: MariaDB + Liquibase migrated, backend on **8300**, frontend `npm run dev` on **3007**, `frontend/.env.local` with **`API_BASE_URL=http://127.0.0.1:8300`** (and optional `NEXT_PUBLIC_*_WEB_ORIGIN` defaults). The browser uses **`/api/bff/...`** only.

1. Open `http://auth.lvh.me:3007/login`, sign in as **admin@demo.lvh.me** / **ChangeMe!1**.
2. Expect redirect to `http://demo.lvh.me:3007/app` (after `redirect-check` succeeds).
3. On `/app`: **Email** `admin@demo.lvh.me`, **Tenant handle** `demo`, **Privileges** includes `USER_VIEW`, demo section shows **USER_VIEW granted** (or equivalent message from API). With second tenant seed: **Your tenants** lists **acme** and **demo**; **acme** `/app` shows fewer nav items (VIEW-only role).
4. Open `http://nosuchtenant.lvh.me:3007/app` while logged in: expect **Unknown tenant** copy (API **404** from `TenantContextFilter`).
5. Sign out (if implemented) or clear cookies; open `http://demo.lvh.me:3007/app` — expect **Sign in** link to auth login URL.

## Session relay (local dev)

The **browser** never talks to Spring. Next.js stores **`wp_bff_j`** / **`wp_bff_x`** (HttpOnly relay for `JSESSIONID` / `XSRF-TOKEN`) with **`Domain=.lvh.me`** when the request `Host` contains `lvh.me`, so **`auth.*`** and **`{tenant}.*`** share the same session for BFF calls. Spring’s real session cookies are exchanged only between the Next server and **`API_BASE_URL`**.

## Playwright port

`frontend/playwright.config.ts` uses **`PLAYWRIGHT_PORT`** (default **3007**) for the `next dev` command and for tenant URLs in `e2e/tenant-vertical-slice.spec.ts`. If port 3007 is already in use, set `PLAYWRIGHT_PORT` to a free port (e.g. **3108**); default **`CORS_ORIGINS`** in `backend/src/main/resources/application.yml` includes `http://*.lvh.me:3108` for that case.
