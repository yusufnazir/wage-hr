# Module: Account registration + email verification

**Feature slug:** `account-registration`  
**Sole authority:** This file is the **contract** for self-service registration with **user-chosen tenant handle**, **email as username**, **email verification before first login**, and **platform-configurable default role** (from **role templates**) for the registering user’s first `user_role` assignment.

**Related (do not duplicate schema here):** [`auth.md`](./auth.md) (login, logout, CSRF, password reset tables), [`role-admin.md`](./role-admin.md) (template copy into tenant roles), [`platform-settings.md`](./platform-settings.md) (`platform_setting` allow-list), [`user.md`](./user.md) (`user_account` / membership), [`mail-adapter.md`](./mail-adapter.md) (send-time only; no raw tokens in DB), [`invitations.md`](./invitations.md) (separate join path for invited users).

---

## 1) Objective

Allow a **new** user to create an account from the **auth host**: they supply **email** (login identifier), **password**, and **tenant handle**. The system creates **`user_account`**, a **new tenant**, **membership**, copies **all** role templates into tenant roles (unchanged from today’s bootstrap), assigns the registering user a **single** tenant role determined by **`auth.registration.default_role_template_code`** (see §6), persists a **one-time email verification token**, and sends a **verification email**. The account is **not** allowed to establish a session until **`user_account.email_verified_at`** is set by a successful verification. Platform operators can change the default role template code via existing platform settings APIs.

---

## 2) Scope

### In scope

- **Web (Next.js, auth host):** `/register` — first name, last name, email, password, tenant handle, required checkboxes for **terms of service** and **privacy policy** (links to `/terms-of-service`, `/privacy-policy`); post-submit UX (“check your email”); `/verify-email` — reads raw `token` from query (or hash fragment if team prefers; document chosen pattern), submits verification to API; login page handles **`EMAIL_NOT_VERIFIED`** with link to resend flow.
- **Mobile (Flutter):** Equivalent **register**, **verify** (deep link → API), and **login** error handling for **`EMAIL_NOT_VERIFIED`**; reuse same JSON APIs and stable error codes.
- **Backend:** Extend registration; add verify + resend endpoints; gate **login** on verified email; mail dispatch for verification (same outbound path as forgot-password — merge + adapter per `platform-settings.md`).
- **Persistence:** `user_account.email_verified_at`; new `email_verification_token` table (strict column list below); Liquibase per `LIQUIBASE-RULES.md`.
- **Configuration:** New `platform_setting` key **`auth.registration.default_role_template_code`** (§6).
- **Seeds / demo users:** All seeded `user_account` rows must have **`email_verified_at` non-null** so existing demos and ITs keep working without extra steps.

### Out of scope

- **OIDC / SSO** registration (M7).
- Changing **invitation** token flows or invitation emails (`invitations.md`).
- **Editing role templates** from this feature (remains `role-admin.md`).
- **Re-sending** verification with a different email address than the one registered (not supported).
- **Admin-created tenants** via platform registry (`platform-tenant-admin.md`) — unaffected; this module covers **self-service registration** path only.

---

## 3) Actors

| Actor | Role |
|-------|------|
| Anonymous visitor | Submits registration; opens verification link; may request resend by email. |
| Platform SuperAdmin | Sets **`auth.registration.default_role_template_code`** via `PATCH /api/v1/platform/settings`. |
| Mail provider | Receives outbound send via existing adapter; **no** new durable mail tables (see `mail-adapter.md`). |

---

## 4) User flows

### 4.1 Happy path (web or mobile)

1. User opens **register** on **auth host** (web) or equivalent screen (Flutter).
2. Enters **email**, **password** (min 8, max per `RegisterRequest` / product), **tenant handle** (normalization + validation **same rules** as `TenantHandleValidator` + uniqueness against `tenant.handle`).
3. **`POST /api/v1/auth/register`** → **201**; body indicates **`pending_verification`** and returns **`tenantHandle`** for display (“Your organization: `https://{handle}.{BASE_DOMAIN}`”) — **no session** created.
4. User receives email with link to **`{publicBaseUrl}/verify-email?token={raw}`** (or auth app route equivalent; **`publicBaseUrl`** from platform branding resolution).
5. Verify page loads; client calls **`POST /api/v1/auth/verify-email`** with `{ "token" }`.
6. On **204**, show success; redirect to **login**.
7. **`POST /api/v1/auth/login`** succeeds only when password valid **and** `email_verified_at` is non-null.

### 4.2 Resend verification

1. From login or dedicated “Didn’t get email?” link, user enters **email**.
2. **`POST /api/v1/auth/resend-verification`** → **202** empty body **always** (anti-enumeration: same response whether user exists, is already verified, or unknown).
3. If an **unverified** account exists for that email, issue a **new** token (TTL fresh), **invalidate prior unused** verification tokens for that `user_account_id`, and send mail.

### 4.3 Login before verify

1. User submits correct email + password but `email_verified_at` is null.
2. **`POST /api/v1/auth/login`** → **403** with stable problem code **`EMAIL_NOT_VERIFIED`** (and optional machine-readable hint to use resend — **no** distinct messages for “wrong password” vs “unverified” for wrong password case; only after **successful password validation** return **403** — acknowledges small information leak: possessor of password learns account is unverified).

### 4.4 Forgot password before verify

**Allowed:** **`POST /api/v1/auth/forgot-password`** behaves as today for that email (user may reset password while unverified). After reset, user must still **verify email** before login.

---

## 5) Data model (schema authority)

### 5.1 `user_account` — columns (strict)

| Column | Type | Notes |
|--------|------|--------|
| `email_verified_at` | TIMESTAMP NULL | **NULL** = not verified; **non-null** = verified. Set **once** on successful verification (idempotent verify: if already verified, treat as success **204** without consuming a second token). |
| `first_name` | VARCHAR(100) NULL | Required on **self-service register** (trimmed, 1–100 chars). Nullable for other flows (e.g. invitation) until populated. |
| `last_name` | VARCHAR(100) NULL | Same as `first_name`. |

Further columns on `user_account` require a **Proposed Schema Extension** review.

### 5.2 Table `email_verification_token` (allowed columns only)

| Column | Type | Notes |
|--------|------|--------|
| `id` | UUID PK | Row id. |
| `user_account_id` | UUID FK → `user_account.id` | Not null. |
| `token_sha256` | CHAR(64) hex | **SHA-256** of raw one-time token; raw token **never** stored. **Unique** for lookup. |
| `expires_at` | timestamp | Not null; **TTL 24 hours** from creation (product default; document in `application.yml` if configurable). |
| `used_at` | timestamp nullable | Set when consumed successfully. |
| `created_at` | timestamp | Immutable row creation. |

**MUST NOT** add: raw token, email plaintext, IP, user-agent blobs, or free-text columns.

**Concurrency:** At most one **valid** unused token per user at resend time: implementation either **marks prior rows used** or **deletes** prior unused rows before insert — document chosen approach in code comments only if non-obvious.

### 5.3 Tenant bootstrap + role assignment

On successful registration (before verification):

1. Create **`user_account`** with `email_verified_at = NULL`, password hash, normalized email (trim + lower case; same as today), **`first_name`** / **`last_name`** (trimmed), and require **`agreeToTermsOfService`** + **`agreeToPrivacyPolicy`** both **true** in the register API body (**400** `REGISTRATION_CONSENT_REQUIRED` otherwise). Consent is not persisted as separate columns (acknowledgment at signup time only).
2. Create **`tenant`** with **`handle` = client-supplied normalized handle** (validated); **409 `TENANT_HANDLE_TAKEN`** if handle exists.
3. Create **`membership`** (`ACTIVE`) and copy **all** role templates to tenant roles + `tenant_privilege_allowance` union (same semantics as `RegistrationService` today).
4. Resolve **default role template** (§6): find `role_template` by **code**; map to the **copied tenant `role.id`** for that template; insert **`user_role`** for the new user to that role. If template code missing or template not found at registration time → **500** `ROLE_TEMPLATE_CONFIGURATION_INVALID` (or **400** if product prefers failing closed before write — pick one in implementation; document in OpenAPI).
5. Create **`email_verification_token`** row and send email.

**Email uniqueness:** **409 `EMAIL_ALREADY_REGISTERED`** if normalized email exists (verified or not — product choice: **block** duplicate email always).

---

## 6) Platform configuration

| Key | Value semantics |
|-----|-----------------|
| **`auth.registration.default_role_template_code`** | Non-empty string; must match an existing **`role_template.code`** in the database (**case-insensitive**) at **registration** time when the key is present. |

**Default when key is absent:** treat as **`ADMIN`** (must exist per `role-admin.md` bootstrap expectations).

**PATCH validation:** `PATCH /api/v1/platform/settings` must reject values that do not match any `role_template.code` (**400** stable code, e.g. `UNKNOWN_ROLE_TEMPLATE_CODE`). Allow-list this key in `platform-settings.md` and backend validator alongside existing keys.

---

## 7) API contract

All paths under **`/api/v1/auth/`** unless noted. Host: **`auth.{BASE_DOMAIN}`** for browser BFF (existing pattern).

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/auth/register` | permitAll | Body JSON: **`email`**, **`password`**, **`tenantHandle`**, **`firstName`**, **`lastName`**, **`agreeToTermsOfService`** (boolean), **`agreeToPrivacyPolicy`** (boolean). Validates email, password, handle, names (1–100 chars trimmed non-empty), both consents **true**. **201** `ApiResponse` with `data`: **`status`**: `"pending_verification"`, **`tenantHandle`**. **409** `EMAIL_ALREADY_REGISTERED`, **409** `TENANT_HANDLE_TAKEN`, **400** validation / **`REGISTRATION_CONSENT_REQUIRED`**. **No session.** |
| POST | `/api/v1/auth/verify-email` | permitAll | Body: **`{ "token" }`** — raw token from email. **204** on success. **400** invalid/expired/already-used token (stable code, e.g. `VERIFICATION_TOKEN_INVALID`). If already verified: **204** (idempotent). |
| POST | `/api/v1/auth/resend-verification` | permitAll | Body: **`{ "email" }`**. **202** empty / constant body. Rate-limit per IP + email (mirror forgot-password limits). |
| POST | `/api/v1/auth/login` | permitAll | Unchanged request shape. **403** + `EMAIL_NOT_VERIFIED` when password matches but email not verified. |

**SecurityConfiguration:** permitAll for new endpoints; CSRF: state-changing **POST** from SPA must follow existing BFF CSRF rules (`auth.md`).

---

## 8) Email

- **Verification mail:** subject + body with **HTTPS** link only; **no** persistence of body (log-only dev adapter may log **masked** link per existing mail patterns).
- **Port:** extend existing mail abstraction (e.g. new method on `PasswordResetMailPort` sibling or shared **`EmailVerificationMailPort`**) — **one** outbound implementation (`OutboundMailService`).

---

## 9) UX notes

- **Register:** Collect first/last name; require both legal checkboxes with links to terms and privacy pages; show chosen tenant URL pattern using returned handle + `publicBaseUrl` / env doc.
- **Verify:** Clear success / failure; expired token message + link to resend.
- **Login:** Map **403** `EMAIL_NOT_VERIFIED` to “Confirm your email” + CTA to resend.
- **i18n:** All new strings via project message bundles (web + Flutter).

---

## 10) Acceptance criteria

1. **Register** with new email + unique handle → **201**, `email_verified_at` null, token row exists, email dispatch invoked (or log-only in dev).
2. **Verify** with valid token → **204**, `email_verified_at` set, token `used_at` set; subsequent verify with same token → **400** or **204** per §5.3 idempotent rule.
3. **Login** before verify with correct password → **403** `EMAIL_NOT_VERIFIED`.
4. **Login** after verify → **200**/session as today.
5. **Duplicate email** → **409** `EMAIL_ALREADY_REGISTERED`; **duplicate handle** → **409** `TENANT_HANDLE_TAKEN`.
6. **Default role:** With platform key set to **`EMPLOYEE`** (template exists), new user’s **`user_role`** points to copied **Employee** tenant role; with key absent, behavior matches **`ADMIN`** template role.
7. **Seeded users** (e.g. `admin@demo.lvh.me`) have `email_verified_at` set → login ITs unchanged.
8. **Resend** rate-limited; prior unused token invalidated when new token issued.
9. **Web E2E** (smoke): register → verify path mocked or against dev mail log; or integration test on API layer if E2E mail unavailable.
10. **Flutter:** Register + verify + login flow documented; minimal widget test or manual checklist in `docs/output/FEATURE-account-registration-VERIFICATION.md` if used.

---

## 11) Implementation pointers (expected touchpoints)

| Area | Location (expected) |
|------|---------------------|
| Registration + bootstrap | `RegistrationService`, `AuthController`, DTOs `RegisterRequest`, `VerifyEmailRequest`, `ResendVerificationRequest` |
| Verification + resend | `EmailVerificationService`, `EmailVerificationMailPort`, `OutboundMailService`, `AuthController` |
| Login gate | `AuthController` (password check then `email_verified_at`; **403** `EMAIL_NOT_VERIFIED`) |
| Liquibase | `ddl/schema-account-registration-1.xml`, `dml/data-account-registration-email-verified-backfill-1.xml` (`DataEmailVerifiedAtBackfill1`) |
| Seeds | Scaffold unchanged; backfill sets `email_verified_at` for pre-feature rows |
| Platform PATCH allow-list | `PlatformIntegrationSettingsValidator`, `PlatformSettingsService` (`UNKNOWN_ROLE_TEMPLATE_CODE`), `platform-settings.md` |
| Web | `frontend/src/app/register/page.tsx`, `verify-email/page.tsx`, `login/page.tsx`, `frontend/src/lib/api.ts`, `frontend/src/middleware.ts` |
| Middleware public routes | `frontend/src/middleware.ts` + `web-auth-session.md` list |
| Flutter | N/A in repo (`mobile/README.md`); parity checklist in `docs/output/FEATURE-account-registration-VERIFICATION.md` |

---

## 12) Open questions

*None for v1 — resolve in implementation only if architecture review requires a different idempotency code for verify.*
