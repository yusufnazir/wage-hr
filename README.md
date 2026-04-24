# wage-payroll

Multi-tenant HR/payroll SaaS scaffold (Spring Boot + Next.js + MariaDB; Flutter mobile path reserved).

## Repository layout

| Path | Purpose |
|------|---------|
| `docs/` | Methodology, architecture contract, guides |
| `backend/` | Spring Boot API (`wage-payroll-api`) |
| `frontend/` | Next.js 15 app (dev port **3007**) |
| `mobile/` | Flutter placeholder |

## Local ports

Aligned with `docs/prompts/PROJECT-CONTEXT.md`:

- API: **8300**
- Next.js: **3007** (`npm run dev` in `frontend/`)

Use `*.lvh.me` for subdomain testing (e.g. `http://auth.lvh.me:3007`, `http://demo.lvh.me:3007`).

## Java 21 and JAVA_HOME

The backend targets **Java 21** (see `backend/pom.xml`). The Maven Wrapper (`mvnw`) needs a JDK on your machine. It reads **`JAVA_HOME`**, or falls back to **`java` on your `PATH`**. If neither is set correctly, you will see: *The JAVA_HOME environment variable is not defined correctly*.

**What to point at:** `JAVA_HOME` must be the **JDK root**—the folder that contains `bin`, `conf`, and `lib` (not the `java.exe` file inside `bin`).

### Install a JDK

Install a **JDK 21** build, for example [Eclipse Temurin 21](https://adoptium.net/) or Microsoft’s OpenJDK. Note the install directory; you will use it below.

### Cursor / VS Code: `JAVA_HOME` for this repo (no Windows system variable)

The repo root **`.vscode/settings.json`** already sets **`JAVA_HOME`** (and the Java language server JDK) to **`D:\Tools\Eclipse Adoptium\jdk-21.0.3.9-hotspot`** for Windows terminals. **`backend/mvnw.cmd`** sets the same **`JAVA_HOME`** so Maven uses that JDK even outside the IDE. Change those paths if your JDK lives elsewhere.

**Important:** After changing `.vscode/settings.json`, **open a new integrated terminal** (existing terminals do not pick up the change). Then run the backend from `backend/` as usual.

### Optional: Java language support in the IDE

If you use the **Extension Pack for Java**, **`java.jdt.ls.java.home`** is set in **`.vscode/settings.json`** next to **`terminal.integrated.env.windows`**. The Maven Wrapper still reads **`JAVA_HOME`** in the shell; this workspace keeps those aligned.

### Other ways to set `JAVA_HOME`

- **Current PowerShell session only:** `$env:JAVA_HOME = 'D:\Tools\Eclipse Adoptium\jdk-21.0.3.9-hotspot'`
- **Local JDK inside the repo:** run `backend/scripts/fetch-local-jdk.ps1` and set `JAVA_HOME` to `backend\.jdk` (see `docs/guides/JAVA-BACKEND-TOOLING.md`).
- **All applications on Windows:** System Properties → Environment Variables → `JAVA_HOME` (user or system).

## Quick start

1. **Database:** MariaDB; create schema `wagepayroll` and set `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` or edit `backend/application-local.yml` (see `backend/application-local.example.yml`).
2. **JDK 21:** Install a JDK 21 and ensure `JAVA_HOME` (or `PATH`) is set so `mvnw` can find it—see [Java 21 and JAVA_HOME](#java-21-and-java_home) above (including Cursor/VS Code workspace settings).
3. **Backend:** `cd backend && .\mvnw.cmd spring-boot:run`
4. **Frontend:** `cd frontend && npm install && npm run dev`
5. **API URL for the Next.js server:** copy `frontend/.env.example` to `frontend/.env.local` and set **`API_BASE_URL`** (e.g. `http://127.0.0.1:8300`). The browser only calls **`/api/bff/...`** on the frontend origin; it never receives the Spring base URL.

## Build verification

- **Frontend:** `cd frontend && npm run build` — production build.
- **Backend:** `cd backend && .\mvnw.cmd test` — requires JDK 21 on `PATH` or `JAVA_HOME`.
- **Playwright (optional):** start API on **8300**, then e.g. `cd frontend && set PLAYWRIGHT_API_BASE_URL=http://127.0.0.1:8300&& npm run e2e` — see `docs/guides/E2E-TESTING-STANDARDS.md` and `docs/output/FEATURE-tenant-web-vertical-slice-VERIFICATION.md`.

See `docs/output/SCAFFOLD-SUMMARY.md` and `docs/output/SECURITY-INFRA-SUMMARY.md` for paths and details.

**Tenant web vertical slice (auth → demo tenant `/app`):** manual and automated checks are in [`docs/output/FEATURE-tenant-web-vertical-slice-VERIFICATION.md`](docs/output/FEATURE-tenant-web-vertical-slice-VERIFICATION.md); behavior contract is [`docs/modules/tenant-web-vertical-slice.md`](docs/modules/tenant-web-vertical-slice.md).
