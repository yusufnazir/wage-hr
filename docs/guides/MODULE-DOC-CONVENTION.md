# Module documentation convention

Per-feature documentation lives under **`/docs/modules/`** in the product repository.

## File naming

- **`/docs/modules/{feature}.md`** — where `{feature}` matches the feature slug (e.g. `invoice-export`, `user-admin`). For Liquibase / entities / persistence, this file is the **sole schema authority** for that slug unless superseded by an explicit human decision — see **`docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md`** (**no module spec = no implementation**).

## What to include

- **Backend / API** — endpoints, payloads, errors (when relevant).
- **Web** — main flows, permission-based UI behavior (Prompt 5).
- **Mobile** — screens and API usage (Prompt 6).
- **Security** — which privileges apply; link to architecture if non-obvious.
- **Data** — for strict tables, an **explicit allowed column list** (or reference to one); any column outside that list is forbidden until **`docs/guides/DATA-MODEL-STANDARDS.md`** schema control + Liquibase approval. Use **`## Proposed Schema Extension (requires PII review)`** in the module doc (or PR) for ideas — not silent schema drift. Before implementation, follow **`docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md`**.

## Updates

- **Prompt 4** (backend feature) → create or extend the module doc.
- **Prompt 5** (web) → add web flows and UI behavior.
- **Prompt 6** (mobile) → add mobile-specific behavior.

Keep docs **close to the code**; avoid duplicating full API contracts — link to OpenAPI or generated docs when available.
