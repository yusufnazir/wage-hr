# E2E testing standards (Playwright)

Minimum expectations for web UIs built under this methodology. **Web Prompt (5)** and scaffold validation require these patterns.

## Scope

- **Playwright** for browser E2E (or equivalent if architecture mandates otherwise — then document the deviation).

### wage-payroll defaults

- Next dev / Playwright app port **3007** (`PLAYWRIGHT_PORT` overrides if 3007 is busy).
- API-backed browser tests: set **`PLAYWRIGHT_API_BASE_URL`** (e.g. `http://127.0.0.1:8300`) and start the Spring Boot API + DB; configure Next with **`API_BASE_URL`** so `/api/bff/...` can proxy. See `frontend/e2e/m1-platform.spec.ts` (includes **M3 billing**: `billing-plan-picker` for admin, `billing-plans-forbidden` for viewer) and `docs/output/FEATURE-tenant-web-vertical-slice-VERIFICATION.md`.

## Per feature

- **Happy path** — authorized user completes the main flow.
- **Permission-denied path** — user lacks privilege; UI and/or API behavior matches architecture.

## Multi-tenant subdomain coverage

- Run at least one scenario against **`http://tenant1.lvh.me:<port>`** and one against **`http://tenant2.lvh.me:<port>`** (or your configured dev hosts). Methodology default for Next.js dev is **port 3000** — see [LOCAL-DEV-PORTS.md](./LOCAL-DEV-PORTS.md); use the port your app actually serves.
- **Assert tenant isolation** where applicable (data or visibility differs between tenants).

## Cross-subdomain auth / session

- Start on **`http://auth.lvh.me:<port>`** (default **3000** for Next dev unless overridden), log in, verify **host switches** to tenant or app host per architecture.
- If **cookie auth**:
  - Assert cookie **domain** and attributes suit subdomains (e.g. domain `.lvh.me`, HttpOnly, SameSite/Secure per policy).
  - After switching from `auth.*` to `{tenant|app}.*`, authenticated requests work **without re-login**.
  - If CSRF applies server-side (BFF): assert a **state-changing** request still succeeds (e.g. locale **PATCH** returns **204** through `/api/bff/...`).

## Redirect safety

- One test with an **invalid `returnTo`** (or equivalent) — **no open redirect** to external host.
- One test with a **valid** `returnTo` — **safe** redirect to allowed host.

## Selectors

- Prefer **accessible** selectors (`getByRole`, `getByLabel`) or stable **`data-testid`** — avoid brittle CSS-only selectors unless unavoidable.

## Documentation

- Record which base URLs, tenants, and permissions each test uses so failures are diagnosable.
