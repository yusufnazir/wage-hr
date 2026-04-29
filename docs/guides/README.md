# Methodology guides

Copy this folder into **each product repository** as `docs/guides/` when bootstrapping a new project (from your methodology repo). Prompts reference these files by path; **include the relevant files in AI context** (attach or open beside the prompt) so the model applies the full rules.

| File | Use |
|------|-----|
| [LIQUIBASE-RULES.md](./LIQUIBASE-RULES.md) | Liquibase changelog layout, DDL/DML naming, CustomDataTaskChange for DML |
| [SCHEMA-PERSISTENCE-PREFLIGHT.md](./SCHEMA-PERSISTENCE-PREFLIGHT.md) | **Read before schema/entity/repo work:** pick **one** `docs/modules/{slug}.md` as **sole** authority — **no module spec = no implementation**; no cross-module merge; **Proposed Schema Extension**; validation |
| [DATA-MODEL-STANDARDS.md](./DATA-MODEL-STANDARDS.md) | Less-data default, **allowed-schema / forbidden extra columns**, PII classification + retention for new columns, Liquibase as schema source of truth, **Proposed Schema Extension** template |
| [PRIVILEGE-MODEL.md](./PRIVILEGE-MODEL.md) | Privilege = action + resource, pools, SuperAdmin, enforcement |
| [MULTI-TENANCY-AND-ROUTING.md](./MULTI-TENANCY-AND-ROUTING.md) | Subdomains, auth vs app host, tenant handle, `lvh.me`, redirects |
| [CROSS-CUTTING-SECURITY.md](./CROSS-CUTTING-SECURITY.md) | Cookies, CSRF, proxy headers, security headers, rate limits, redirect safety |
| [WEB-THEMING-AND-DESIGN-SYSTEM.md](./WEB-THEMING-AND-DESIGN-SYSTEM.md) | Light/dark, design tokens, swappable palettes and full themes, scaffold vs feature UI — *product:* tenant **`/app`** chrome + auth split layout cross-ref **`../modules/tenant-web-vertical-slice.md`** §3.6 |
| [FILTER-FIELD-STANDARDS.md](./FILTER-FIELD-STANDARDS.md) | Application-wide standard for chip-based filter fields + URL-state mapping |
| [API-CONVENTIONS.md](./API-CONVENTIONS.md) | Public API base URL, versioning, success/error shapes, canonical links |
| [E2E-TESTING-STANDARDS.md](./E2E-TESTING-STANDARDS.md) | Playwright: subdomain, auth, cookie/CSRF, redirect tests |
| [MODULE-DOC-CONVENTION.md](./MODULE-DOC-CONVENTION.md) | Per-feature `/docs/modules/{feature}.md` documentation |
| [JAVA-BACKEND-TOOLING.md](./JAVA-BACKEND-TOOLING.md) | Maven Wrapper (`.mvn`) at backend root like Spring Boot; optional gitignored `.jdk/` for local JDK without global `PATH` |
| [LOCAL-DEV-PORTS.md](./LOCAL-DEV-PORTS.md) | Default **8080** (Spring Boot) and **3000** (Next.js dev); env overrides; CORS / E2E implications |

**Product hub (wage-payroll):** [BUILD-CHECKLIST.md](../product/BUILD-CHECKLIST.md) (milestones + checkboxes), [MODULE-INDEX.md](../product/MODULE-INDEX.md) (planned module slugs). **Contract:** `docs/prompts/PROJECT-CONTEXT.md` overrides stale architecture output until Phase 1 is re-run.

Architecture and feature prompts assume these standards unless the architecture contract explicitly overrides them.
