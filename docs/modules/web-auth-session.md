# Web auth session and navigation guards

**Feature slug:** `web-auth-session`  
**Applies to:** Web app (e.g. Next.js App Router). Adjust paths if your router differs.  
**Related guides (product repo):** `docs/guides/CROSS-CUTTING-SECURITY.md`, `docs/guides/MULTI-TENANCY-AND-ROUTING.md`, `docs/guides/WEB-THEMING-AND-DESIGN-SYSTEM.md` (auth shell layout + theme persistence)

---

## 1. Goal

When a user opens a **protected** part of the web app **without a valid session**, the browser must **automatically** be sent to the login entry point **`/auth/login`** (or your product’s equivalent path—keep it one canonical URL).

When a user **has** a valid session, protected routes load normally. No redirect loops.

---

## 2. Scope (this document)

Implement or refine the following in one coherent change set:

| Area | Behavior |
|------|----------|
| Session detection | Treat “no session” as: missing/invalid/expired session cookie or token refresh failure—follow your architecture’s session model. |
| Browser navigation | Visiting a protected URL without a session → **302/307** (or client redirect where appropriate) to `/auth/login`. |
| Deep links | After login, user can return to the originally requested path when safe (see §5). |
| Public surface | Explicit list of routes that **must not** require a session (see §4). |
| Auth routes | Login, password reset, OAuth callbacks, etc.—must not redirect to themselves in a loop. |

**Optional extensions** (same doc; implement if in scope for your sprint):

- Logout clears session and redirects to `/auth/login` or a public landing page.
- Session refresh on activity or before expiry (silent refresh if your stack supports it).
- “Remember this device” only if product policy allows—document cookie lifetime implications.
- Read-only “semi-public” pages behind a feature flag—list paths explicitly.

---

## 3. Out of scope (unless you explicitly add it)

- Backend implementation of login API (track under a separate backend module doc if needed).
- Mobile app navigation (separate module for Prompt 6).
- Changing identity provider or OAuth client registration.
- Full CSP/CORS policy rewrite—link to architecture; only touch what this feature needs.

---

## 4. Route classification

Maintain a **single source of truth** in code (e.g. one list or matcher function) used by middleware and tests.

### 4.1 Public routes (no session required)

Examples—**replace** with your real paths:

- `/auth/login`, `/auth/register`, `/auth/forgot-password`, `/auth/reset-password`
- OAuth/callback paths your IdP uses, e.g. `/auth/callback`, `/api/auth/callback` (if applicable)
- Static/legal: `/terms`, `/privacy`, `/health` (if exposed on this host)
- Marketing home if it exists on the same app: `/` (only if product requires anonymous access)

**Rule:** Any route **not** listed as public is **protected** by default.

### 4.2 Protected routes

Everything under app areas that require an account, e.g. `/app`, `/dashboard`, `/settings`, tenant-scoped paths—**define your prefixes**.

**Rule:** Unauthenticated access → redirect to `/auth/login` with optional `returnTo` (§5).

### 4.3 API routes from the browser

- **BFF / same-origin API** used with cookies: unauthenticated → **401 JSON** or redirect policy **consistent** with how your frontend handles fetch (avoid half the app redirecting and half returning 401 without handling).
- **Document the chosen rule** in this module and stick to it.

---

## 5. Return URL after login (`returnTo`)

**Preferred pattern:** redirect to  
`/auth/login?returnTo=<encoded-path-and-query>`

**Requirements:**

1. **Allowlist** `returnTo` to same-origin **path** only (relative paths starting with `/`). Reject absolute URLs, other hosts, and `javascript:` schemes.
2. After successful login, redirect to the decoded path if allowlisted; otherwise fall back to a safe default (e.g. `/app` or tenant home per architecture).
3. Do not put secrets in `returnTo`.
4. If your app uses **subdomains** (auth vs tenant host), follow architecture: login may run on `auth.` host and post-login redirect must respect tenant handle vs `app` host—do not invent behavior here; **reference** `MULTI-TENANCY-AND-ROUTING.md`.

---

## 6. Implementation notes (web)

Typical patterns (pick what matches your stack; document what you chose):

1. **Edge or Node middleware** — runs early; cheap session check (e.g. presence of signed session cookie or lightweight JWT validation if architecture mandates). Redirect GET navigations to `/auth/login`; be careful with **prefetch** and **static assets**.
2. **Layout / route group** — secondary guard for client-only navigation if middleware cannot see all state.
3. **Avoid flash of protected content** — for sensitive UIs, prefer server-side gating or loading state until session is known.

**Anti-patterns:**

- Protecting only “menu links” but leaving deep URLs open.
- Redirecting **API** POST/PUT to HTML login without the client handling it.
- Infinite redirect: login page treated as protected, or session cookie sent but invalid without clearing it.

---

## 7. Security checklist (minimum)

- [ ] **HTTPS** in production; cookies `Secure`, `HttpOnly`, `SameSite` per architecture.
- [ ] **CSRF** for cookie-based mutating requests if your stack uses cookies for session.
- [ ] **No open redirects** — §5 allowlist enforced server-side and on any redirect endpoint.
- [ ] **Rate limiting** on login and session endpoints (architecture / infra).
- [ ] **Logging** — log redirect due to missing session at debug level; avoid logging tokens.

---

## 8. Testing / acceptance

**Manual**

1. Logged out: open a protected URL → lands on `/auth/login`; address bar shows optional safe `returnTo`.
2. Log in → redirected to intended path when `returnTo` was set; default when not.
3. Logged in: open protected URL → no redirect to login.
4. Public routes: accessible logged out; no redirect loop with `/auth/login`.
5. Invalid session cookie (simulate expired): same as logged out; no loop.

**Automated (recommended)**

- E2E: unauthenticated visit to protected route → `/auth/login`; post-login navigation per `docs/guides/E2E-TESTING-STANDARDS.md`.

---

## 9. AI handoff hint

When asking an AI to implement this: attach **this file**, **`docs/output/ARCHITECTURE-DEFINITION.md`** (or current architecture excerpt for auth hosts), and **`docs/guides/CROSS-CUTTING-SECURITY.md`**. Request a short PR description and list of changed files when done.
