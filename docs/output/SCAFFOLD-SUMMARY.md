# Scaffold summary (Phase 2)

**Generated:** 2026-04-21 — **wage-payroll** monorepo scaffold per `docs/output/ARCHITECTURE-DEFINITION.md` and `docs/templates/2. SCAFFOLD-GENERATOR-PROMPT.md`.

## What was created

### Backend — `backend/`

| Path | Purpose |
|------|---------|
| `backend/pom.xml` | Spring Boot **3.4.1**, Java **21**, Web, Security, Data JPA, Validation, Liquibase, Actuator, AOP; H2 for tests |
| `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/` | Maven Wrapper (downloads Maven on first run) |
| `backend/scripts/` | `fetch-local-jdk.ps1`, `fetch-local-jdk.sh`, `README.md` (from methodology templates) |
| `backend/README.md` | Run/test instructions |
| `backend/application-local.example.yml` | Example local overrides (no secrets) |
| `backend/.gitignore` | `target/`, `.jdk/`, `application-local.yml`, etc. |
| `backend/src/main/java/com/wagepayroll/` | Application, config, `tenant/`, `domain/`, `security/`, `api/`, `liquibase/task/` |
| `backend/src/main/resources/application.yml` | Defaults: port **8300**, MariaDB URL, CORS (incl. `localhost:3007` + `*.lvh.me:3007`), rate limit, security headers |
| `backend/src/main/resources/db/changelog/` | `db.changelog-master.yaml` → `ddl/schema-bootstrap-1.xml` + `dml/data-scaffold-1.xml` |
| `backend/src/test/resources/application-test.yml` | H2 in-memory + Liquibase for integration tests |
| `backend/src/test/java/.../WagePayrollApplicationTests.java` | Context load |
| `backend/src/test/java/.../api/DemoPrivilegedEndpointIT.java` | Privilege + tenant host integration tests |

**Key endpoints**

- `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/csrf`, `GET /api/v1/auth/redirect-check`
- `GET /api/v1/me`
- `GET /api/v1/demo/user-view` — requires privilege `USER_VIEW` + tenant context

### Frontend — `frontend/`

| Path | Purpose |
|------|---------|
| `frontend/package.json` | Next **15.1.6**, React 19; `dev` / `start` on port **3007**; `e2e` scripts |
| `frontend/src/app/` | App Router: root `layout.tsx` (theme provider), marketing `page.tsx`, auth routes (`login`, `register`, `forgot-password`, `reset-password`), tenant **`app/`** subtree (`app/layout.tsx` = **`TenantAppShell`**, `app/page.tsx`, `app/documents`, `app/profile`) |
| `frontend/src/components/theme/` | `AppThemeProvider.tsx` (next-themes), `ThemeToggle.tsx` |
| `frontend/src/styles/tokens-semantic.css` | Semantic CSS variables (light/dark) |
| `frontend/src/lib/api.ts` | CSRF + login fetch helpers (`credentials: 'include'`) |
| `frontend/src/middleware.ts` | Pass-through + `x-wage-host` for debugging |
| `frontend/playwright.config.ts` | Local `baseURL` `http://127.0.0.1:3007` |
| `frontend/e2e/smoke.spec.ts` | Smoke: home + login page |
| `frontend/.env.example` | **`API_BASE_URL`** (server-only BFF → Spring); `NEXT_PUBLIC_*_WEB_ORIGIN` for multi-host dev |

### Mobile — `mobile/`

- `mobile/README.md` — Flutter placeholder (stack reserved; not generated).

### Docs — `docs/modules/`

- `docs/modules/auth.md`, `docs/modules/user.md`, `docs/modules/security.md` — scaffold module notes.

### Root

- `README.md` — quick start and layout
- `.gitignore` — backend `target/`, frontend `.next/`, `node_modules/`, etc.

## Build notes

| Command | Result (this environment) |
|--------|----------------------------|
| `cd frontend && npm run build` | **Succeeded** — Next.js production build |
| `cd backend && .\mvnw.cmd test` | **Not run** — no JDK on PATH / `JAVA_HOME` in agent environment. With **JDK 21** installed or `backend/scripts/fetch-local-jdk.ps1` + `JAVA_HOME` → `backend/.jdk`, run `.\mvnw.cmd test` to verify. |

**Database:** API expects **MariaDB** for normal runs; tests use **H2** + full Liquibase migrations.

## Ports (authoritative)

From `docs/prompts/PROJECT-CONTEXT.md`: API **8300**, Next.js **3007**.
