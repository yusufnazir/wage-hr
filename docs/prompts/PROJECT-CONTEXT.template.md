# Project context (filled values for templates & masters)

**Purpose:** Numbered templates under `docs/templates/` still use **placeholders** (`{PROJECT_NAME}`, `[PASTE FULL CONTRACT HERE]`, …) so the **prompt-helper** can substitute them when you click *Run prompts*. When you drive the repo with **`@docs`** and **master prompts**, the model needs the **same facts in plain text** without hunting the Contract UI.

**What to do:** In your **product** repository, copy this file to **`docs/prompts/PROJECT-CONTEXT.md`**, fill every section, and **commit** it. Refresh it when the contract or domains change. **Shortcut:** in **wage prompt-helper**, open **Contract** → **Export PROJECT-CONTEXT.md** (copy or, in Electron, save into the chosen product `docs/prompts/`).

**How to use with Cursor:** attach **`@docs/prompts/PROJECT-CONTEXT.md`** together with **`@docs/prompts/MASTER-…`** and **`@docs`** (see `docs/prompts/README.md`).

**Single source of truth:** The **Architecture contract** block in this file (especially **v1 scope** — billing, OIDC, regions) overrides any older **`docs/output/ARCHITECTURE-DEFINITION.md`**. After you change the contract, **re-run Prompt 1** (or hand-edit architecture) so both stay aligned; contradictions confuse scaffold and feature prompts.

---

## Product identity

| Field | Value (replaces template token / intent) |
|-------|-------------------------------------------|
| **Project name** | *(same as `{PROJECT_NAME}`)* |
| **One-line summary** | |
| **Primary users / tenants** | |

---

## Technical stack

| Field | Value |
|-------|--------|
| **Backend** | *(e.g. Spring Boot — `{BACKEND}`)* |
| **Frontend** | *(e.g. Next.js — `{FRONTEND}`)* |
| **Mobile** | *(e.g. Flutter — `{MOBILE}`)* |
| **Database** | *(e.g. MariaDB — `{DATABASE}`)* |

---

## Multi-tenant & hosts (subdomains)

Used wherever templates mention `{BASE_DOMAIN}`, `{AUTH_SUBDOMAIN}`, `{APP_SUBDOMAIN}`, or cross-cutting subdomain text.

| Setting | Value |
|---------|--------|
| **BASE_DOMAIN** | *(e.g. `lvh.me` local, `example.com` prod)* |
| **AUTH_SUBDOMAIN** | *(default `auth`)* |
| **APP_SUBDOMAIN** | *(default `app`)* |

Short notes (redirects, unknown tenant handle, etc.):

*(paste or summarize from your architecture contract / cross-cutting block)*

---

## Local development ports

Methodology defaults for **Spring Boot + Next.js** (see `docs/guides/LOCAL-DEV-PORTS.md`). If you use **wage prompt-helper**, the Contract page fills these for export. Otherwise change the table if your product uses different ports; keep **CORS** and **Playwright** base URLs in sync.

| Service | Port | Override (examples) |
|---------|------|----------------------|
| Spring Boot API | 8080 | `SERVER_PORT` / `server.port` |
| Next.js (`next dev`) | 3000 | `PORT` / `next dev -p` |

Example browser URL: `http://auth.lvh.me:3000/...` (front-end port).

---

## Architecture contract (full text)

**This block replaces `[PASTE FULL CONTRACT HERE]` in templates.**

Paste the **assembled architecture contract** markdown here (same content you would put in prompt-helper Contract → *Generate*, or export from your tool).

```markdown
(paste below this line)
```

---

## Existing architecture output (optional, Prompt 1 result)

**Replaces `[PASTE ARCHITECTURE OUTPUT]`** when you already ran architecture and store it in-repo.

Either:

- **Path:** `docs/output/ARCHITECTURE-DEFINITION.md` *(recommended — then write “see file” here)*, **or**
- **Inline paste** below:

```markdown
(optional paste)
```

---

## Cross-cutting excerpt (optional)

Paste the **system-wide** paragraph you use for prompts 3–6 (subdomains, cookies/CSRF, proxy, headers, rate limits, redirects) if it is **not** only inside `ARCHITECTURE-DEFINITION.md`.

```markdown
(optional paste)
```

---

## Feature work (for `MASTER-FEATURE-END-TO-END` only)

| Field | Value |
|-------|--------|
| **Feature slug / module doc path** | e.g. `docs/modules/invoice-export.md` |
| **Feature name** | *(replaces `{FEATURE_NAME}`)* |
| **What the feature should do** | *(replaces `{WHAT THIS FEATURE SHOULD DO}` — short paragraph)* |

---

## API contract from backend (optional, for web/mobile prompts)

**Replaces `[PASTE API CONTRACT OUTPUT FROM BACKEND PROMPT]`** when Prompt 4 already produced it.

- **Path:** *(e.g. `docs/output/API-CONTRACT-last.md`)* **or** paste below.

```markdown
(optional)
```

---

## Methodology notes (optional)

Anything you would put in prompt-helper **Additional methodology notes** (product-specific, still reference `docs/guides/` for rules).

```text
(optional)
```
