# Local development ports (Spring Boot + Next.js)

Methodology **defaults** for local HTTP ports when the stack is **Spring Boot** (API) and **Next.js** (browser app). Products may override; if they do, document the overrides in architecture and **`docs/prompts/PROJECT-CONTEXT.md`** so CORS, E2E, and cookie behavior stay aligned.

---

## Defaults

| Service | Default port | How to override (examples) |
|---------|--------------|------------------------------|
| **Spring Boot** (embedded server) | **8080** | Spring: `server.port`, relaxed env **`SERVER_PORT`**. Prefer **environment** or profile-specific config so CI and docs stay in sync. |
| **Next.js** (`next dev`) | **3000** | **`PORT`** (e.g. `PORT=3001 next dev`) or **`next dev -p 3001`**. |

In **wage prompt-helper**, set these on the **Contract** page (stored in localStorage); they flow into **Run prompts** placeholders `{BACKEND_PORT}` / `{FRONTEND_DEV_PORT}`, **Export PROJECT-CONTEXT.md**, and the **.env example** copy block.

Commit **example** env files (e.g. `.env.example`, `application-local.properties.example`) that show these variables **without secrets**.

---

## Non-default ports (intentional)

**8080** and **3000** are methodology **defaults**, not mandatory ports. Many teams run other pairs (e.g. **8300** / **3007**) to avoid collisions with other apps. That is **correct** as long as everything stays consistent:

- **`docs/prompts/PROJECT-CONTEXT.md`** (and prompt-helper **Contract** export) lists the ports you **actually** use.
- **Spring CORS** allows origins with that **exact** front-end port; **Playwright** `baseURL` matches it.
- Product **README** “run locally” matches the same numbers.

Do not treat this guide’s default table as the ground truth when your repo already chose different ports — update the table in **`PROJECT-CONTEXT.md`** and any architecture snippets that mention example URLs.

---

## Why this matters

- **Browsers** treat `http://auth.lvh.me:3000` and `http://auth.lvh.me:3001` as different origins; changing the Next port requires updating **CORS allowed origins** on the API and any **hardcoded dev URLs** in docs or Playwright config.
- **CORS** on Spring Boot must list the **actual** dev origins your Next app uses (scheme + host + **port**), e.g. `http://auth.lvh.me:3000`, `http://app.lvh.me:3000`, `http://tenant1.lvh.me:3000`, not only `*.lvh.me` without a port if your dev stack always includes a port.
- **E2E** (Playwright): base URLs must match the Next dev port you run (methodology examples often use **3000**; substitute your real port everywhere when it differs).

---

## API vs browser host (local)

Architecture may choose:

- **Browser** on `*.lvh.me:<frontend-port>` talking to **API** on another host/port (e.g. `api.lvh.me:<backend-port>` or `localhost:<backend-port>`), or  
- **Next rewrites / proxy** so the browser only sees one origin.

Either is fine; **document** which pattern the product uses and keep **CORS + cookies + CSRF** consistent with that choice (see [CROSS-CUTTING-SECURITY.md](./CROSS-CUTTING-SECURITY.md), [MULTI-TENANCY-AND-ROUTING.md](./MULTI-TENANCY-AND-ROUTING.md)).

**wage-payroll (this repo):** the browser uses the **Next.js BFF** — same origin **`/api/bff/...`** on the frontend port; Spring is reached only from the Next server via **`API_BASE_URL`** (default `http://127.0.0.1:8300` in development). See `frontend/.env.example` and `docs/modules/tenant-web-vertical-slice.md`.

---

## Related

- JVM wrapper / local JDK layout: [JAVA-BACKEND-TOOLING.md](./JAVA-BACKEND-TOOLING.md).
- Playwright hosts: [E2E-TESTING-STANDARDS.md](./E2E-TESTING-STANDARDS.md).
