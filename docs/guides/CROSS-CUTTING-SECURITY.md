# Cross-cutting security

System-wide requirements for cookies, CSRF, reverse proxies, headers, rate limiting, and redirects. Feature prompts often paste an excerpt from architecture output; this guide is the **canonical checklist**.

## Session / cookies (if cookie auth is used)

- Document **auth mechanism** (cookie vs bearer token) and why.
- **Cookie domain** across subdomains (e.g. `.lvh.me` locally, `.example.com` in production).
- **SameSite** and **Secure** strategy aligned with cross-subdomain login and CSRF posture.
- **Logout / session revocation** across subdomains (how sessions are invalidated consistently).

## CSRF (if cookie auth is used)

- Strategy (e.g. SameSite + CSRF token, double-submit, or framework default).
- Which **endpoints** require CSRF protection (especially state-changing mutations).

## Return URL / redirect safety

- Validate **`returnTo`** / redirect targets to prevent **open redirects**.
- **Allowed hosts** — limit to subdomains of `{BASE_DOMAIN}` and/or an explicit allowlist.
- Block **protocol injection** (e.g. `https://evil.com`), path traversal, and header injection in redirect handling.

## Reverse proxy / forwarded headers

- App assumes it may sit behind a **reverse proxy** in production.
- Trust only **forwarded headers** from trusted proxies (`X-Forwarded-Proto`, `X-Forwarded-Host`, etc.).
- Derive **canonical scheme and host** safely for redirects and link generation (no spoofing).

## Baseline security headers

- **Content-Security-Policy (CSP)** — strategy (strictness vs app needs).
- **X-Content-Type-Options**, **Referrer-Policy**, **Permissions-Policy** — at least high-level decisions.

## Auth abuse protection

- **Rate limiting** / brute-force policy for **login** and **password reset** (and similar).
- Define **limits**, **windows**, **lockout/backoff**, and **responses** that do not leak account existence.

## Client-side (web/mobile)

- UI MUST use **only backend-provided** permissions; do not hardcode privilege names for authorization decisions.
- Backend remains the **final authority**; frontend hides/disables only.
