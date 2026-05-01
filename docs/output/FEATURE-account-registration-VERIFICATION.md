# Verification: account registration + email verification

## Backend

- **`RegistrationTenantBootstrapIT`** — register with `tenantHandle` creates unverified user + verification token row; verify with token from captured mail URL then login succeeds; login before verify returns **403**; wrong password after verify returns **401**.
- Full suite: `backend` → `./mvnw.cmd test`.

## Web

- **`npm run build`** (Next.js) — includes `/register` (email, password, handle), `/verify-email` (query `token` auto-verify + manual token + resend form), `/login` (**EMAIL_NOT_VERIFIED** messaging + link to verify).
- **Middleware** — `/verify-email` treated as auth-surface path (redirect to auth origin when needed).

## Mobile (Flutter)

- Repository uses **`mobile/README.md`** placeholder only — no Flutter app in-tree. When a client exists: mirror `POST /api/v1/auth/register` (body includes `tenantHandle`), `verify-email`, `resend-verification`, and login **403** handling per [`docs/modules/account-registration.md`](../modules/account-registration.md).

## Manual smoke (local)

1. Open `http://auth.lvh.me:3007/register` (or your `NEXT_PUBLIC_AUTH_WEB_ORIGIN`).
2. Register with first/last name, a new email, password, unused handle, and both **terms** + **privacy** checkboxes → success message mentions tenant URL; no session until verify.
3. Check API logs for `[email-verification]` line with `verifyUrl` (when mail API not configured) or use real mail adapter.
4. Open `verifyUrl` or paste token on `/verify-email` → success.
5. Sign in on `/login` → redirects as before.
6. Demo `admin@demo.lvh.me` / `ChangeMe!1` still signs in (seed + Liquibase backfill set `email_verified_at`).

## Platform setting

- **`PATCH /api/v1/platform/settings`** with `auth.registration.default_role_template_code` must match a `role_template.code` (case-insensitive) or returns **400** `UNKNOWN_ROLE_TEMPLATE_CODE`.
