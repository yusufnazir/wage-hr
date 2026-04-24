# Data model standards

Canonical rules for relational schema in multi-tenant SaaS generated with this methodology. Align Liquibase DDL with these rules (see [LIQUIBASE-RULES.md](./LIQUIBASE-RULES.md) for changelog mechanics).

**Before generating schema, entities, or persistence logic:** follow [SCHEMA-PERSISTENCE-PREFLIGHT.md](./SCHEMA-PERSISTENCE-PREFLIGHT.md): identify **one** `docs/modules/{feature-slug}.md` as **sole** schema authority, attach it + this guide — **no module spec = no implementation**; do not merge other module specs.

**Default principle:** → **If it is not explicitly allowed, it is NOT included.**

## Default data minimization (“less data”)

- The system follows a strict **less data** default: **only** columns that are **explicitly allowed** for that table in the **authoritative schema** (Liquibase + documented contract in `docs/modules/{feature}.md` or architecture) may exist.
- **Any column not explicitly allowed is FORBIDDEN** until it goes through **schema control** below.
- If something seems useful but is not in the allowed list, treat it as **out of scope** for the migration until approved — do **not** add it “just in case.”

## Schema control (adding or widening columns)

Adding a **new** column, widening a type, or adding an unbounded `TEXT`/`JSON`/`LOB` that could hold user content requires **all** of the following:

1. **Explicit justification** — what product requirement needs this data on this row (not “convenience for debugging”).
2. **PII classification** for the column: **`none`** | **`low`** (pseudonymous ids, internal codes) | **`sensitive`** (anything that can identify a person, describe their life, finances, health, etc.). **Sensitive columns MUST be flagged** in the module doc and in code review; they MUST NOT be added silently.
3. **Retention impact** — which retention / deletion / anonymization policy applies; alignment with `docs/prompts/PROJECT-CONTEXT.md` and the feature module.
4. **Approval via schema change** — a **new Liquibase** changeset (DDL only in changelog XML per [LIQUIBASE-RULES.md](./LIQUIBASE-RULES.md)); no Flyway-at-runtime, no manual hot columns in prod, no “JPA auto-ddl” in shared environments.

**PII rule:** Treat **all free-text** (unbounded `TEXT`, large `VARCHAR`, arbitrary `JSON`) as **potentially containing PII** unless the spec explicitly limits values to non-PII codes. If a field **may** contain PII, it **MUST** be classified as **`low`** or **`sensitive`**, documented, and reviewed. **Never** add such columns without classification and module doc update. **Avoid** free-text columns unless **explicitly allowed** in the module contract.

## Database source of truth

- The **committed database schema** (as expressed by **Liquibase** changelogs) is the **source of truth** for what exists.
- **All** structural changes go through **Liquibase migrations** in the repo; **no** implicit or ad-hoc schema expansion (no undocumented `ALTER TABLE`, no relying on Hibernate `ddl-auto=update` for team databases).

## Contributors and AI-assisted changes

- **Do not** introduce entity fields, DTO properties, JSON API fields, or DB columns **beyond** the defined contract for that feature.
- If additional data seems useful, **do not** add it in the same change. Put it under a separate proposal using this heading in a PR or module doc:

### Proposed Schema Extension (requires PII review)

- **Table / entity:** …
- **Proposed column(s):** …
- **Justification:** …
- **PII classification (none / low / sensitive):** …
- **Retention / deletion / anonymization impact:** …
- **Liquibase changeset id (when approved):** …

Until that block is accepted and a Liquibase changeset is merged, the extension is **not** implemented.

**Validation (mandatory before returning generated code):** Ask: *Does this violate this document or the feature module’s allowed list?* If yes, **fix before returning**; if the need is real but unapproved, output **only** the **Proposed Schema Extension** block, not new columns in implementation.

## Primary key

- Every table MUST have a primary key column named **`id`**.
- The `id` MUST be unique, non-null, and the primary identifier of the row.

## Identifier type

- Use **one** consistent type across all tables:
  - **UUID** (preferred), or
  - **Long** (auto-increment).
- Mixing UUID and Long across tables is NOT allowed unless explicitly approved.

## Base entity (tenant-scoped data)

Tenant-scoped entities MUST extend a common base that includes at minimum:

| Column | Purpose |
|--------|---------|
| `id` | Primary key (see above) |
| `tenant_id` | Tenant isolation |
| `created_at` | Audit |
| `updated_at` | Audit |

Adjust naming to match your stack (e.g. `createdAt` in JPA) but the data model MUST enforce tenant scoping at the persistence layer.

## Constraints

- No table without an **`id`** column.
- **Composite primary keys** are NOT allowed unless explicitly approved for exceptional cases.

## Relationship to features

- New features MUST NOT introduce ad hoc primary key shapes.
- Global tables (no `tenant_id`) are rare; document them explicitly in architecture when used.

## Lifecycle, controlled deletion, anonymization, and PII

Aligned with **`docs/prompts/PROJECT-CONTEXT.md`** (retention, messaging, compliance):

- **Controlled deletion:** where rows may be removed, prefer **explicit lifecycle fields** (`deleted_at`, `deletion_reason`, actor) or hard delete only for classes documented in the feature module; **never** delete or rewrite **append-only audit** to hide history.
- **Anonymization:** for person-linked tables that support it, document columns cleared or replaced (e.g. hash, literal `ANONYMIZED`) and how **foreign keys** into audit/events remain valid (surrogate stable ids).
- **PII minimization:** avoid storing duplicate copies of large or sensitive payloads (full emails, raw payment objects, secrets); store **references** to external systems, **template keys**, and non-sensitive metadata unless a column is justified and protected (encryption, short TTL).
- **Module doc requirement:** each feature that holds PII or messages MUST state **what is stored** (explicit **allowed column list** per table where the product is strict), **retention class**, and **delete vs anonymize** behavior. Tables with a closed schema (e.g. notifications) **must not** gain columns outside that list without **Schema control** above.
