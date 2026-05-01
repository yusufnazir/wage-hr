# Module: Authentication (scaffold)

## Scope

Email/password authentication against `user_account`, HTTP session (cookie) for browser clients, CSRF for state-changing requests when using cookies. **Self-service register**, **email verification**, and **resend verification** are specified in **[`account-registration.md`](./account-registration.md)** (sole contract for those flows, persistence, and platform default-role key). **Forgot / reset password** uses a dedicated table with **no** raw token persistence.

**Summary:** Login requires a **verified** email for accounts created through self-service registration (`email_verified_at` non-null). Seeded / legacy users must have `email_verified_at` set so demos work. Tenant bootstrap on register (templates → tenant roles, membership, first `user_role`) follows **`account-registration.md`** + [`role-admin.md`](./role-admin.md).

## Backend

- **Login:** `POST /api/v1/auth/login` — JSON `{ "email", "password" }`; establishes session; rate-limited (`app.security.rate-limit.*`). **403** `EMAIL_NOT_VERIFIED` when password is valid but email not verified (see [`account-registration.md`](./account-registration.md)).
- **Logout:** `POST /api/v1/auth/logout` — clears security context / session.
- **Register / verify-email / resend-verification:** See **[`account-registration.md`](./account-registration.md)** — **`POST /api/v1/auth/register`**, **`POST /api/v1/auth/verify-email`**, **`POST /api/v1/auth/resend-verification`**.
- **Forgot password:** `POST /api/v1/auth/forgot-password` — JSON `{ "email" }`; always **202** with empty body from enumeration perspective; creates row in `password_reset_token` if user exists; dispatches reset link via `PasswordResetMailPort` (dev: log-only implementation). Rate-limited per IP + email (`app.security.rate-limit.forgot-password-*`).
- **Reset password:** `POST /api/v1/auth/reset-password` — JSON `{ "token", "newPassword" }` (newPassword min 8); **204** on success; invalid/expired token → **400** with stable error code.
- **CSRF:** `GET /api/v1/auth/csrf` — returns token + header name for SPA clients.
- **Redirect safety:** `GET /api/v1/auth/redirect-check?returnTo=` — validates `returnTo` (relative paths or hosts under `BASE_DOMAIN`).

### Table `password_reset_token` (allowed columns only)

| Column | Type | Notes |
|--------|------|--------|
| `id` | UUID PK | Row id. |
| `user_account_id` | UUID FK → `user_account.id` | Not duplicated email. |
| `token_sha256` | CHAR(64) hex | **SHA-256** of raw one-time token (raw token never stored). **Unique** for lookup. |
| `expires_at` | timestamp | e.g. 1 hour from creation. |
| `used_at` | timestamp nullable | Set when consumed. |
| `created_at` | timestamp | Immutable row creation. |

**MUST NOT** add: raw token, email, IP, user-agent blobs, or free-text columns on this table.

## Frontend

- **Pages:** `frontend/src/app/login/page.tsx` — minimal shell; uses `frontend/src/lib/api.ts` (same-origin **`/api/bff/...`**; CSRF handled server-side in the BFF).
- **Register / verify:** `frontend/src/app/register/page.tsx`, `frontend/src/app/verify-email/page.tsx` — see [`account-registration.md`](./account-registration.md).
- **Forgot:** `frontend/src/app/forgot-password/page.tsx` — email only.
- **Reset:** `frontend/src/app/reset-password/page.tsx` — reads `token` from query string; new password + submit.
- **Theming:** `frontend/src/components/theme/*` — light / dark / system via `next-themes` (see `docs/guides/WEB-THEMING-AND-DESIGN-SYSTEM.md`).

## Configuration

- Cookie domain / hosts: `app.host.*` and CORS `app.cors.allowed-origin-patterns` in `backend/src/main/resources/application.yml`.
- Local ports: `docs/prompts/PROJECT-CONTEXT.md` (API **8300**, Next **3007**). Next.js uses **`API_BASE_URL`** (server-only) to reach Spring; see `frontend/.env.example` and `tenant-web-vertical-slice.md` §3.3 (relay cookies).
