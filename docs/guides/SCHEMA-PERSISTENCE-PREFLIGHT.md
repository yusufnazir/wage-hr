# Schema & persistence preflight (mandatory)

Read this **before** generating or changing **any** database schema, JPA entity, repository, or persistence-related DTO that maps to stored columns.

**Default principle:** → **If it is not explicitly allowed, it is NOT included.**

**Strict rule:** → **No module spec = no implementation.**

---

## 0. Identify the schema authority (do this first)

When generating Liquibase, entities, or persistence code:

1. **Identify the feature** (e.g. notifications, payroll, employee — one vertical slice).
2. **Confirm the single spec file:** `docs/modules/{feature-slug}.md` (exact path, e.g. [`../modules/notifications-inbox.md`](../modules/notifications-inbox.md)).
3. Use **ONLY that file** as **schema authority** for this work: allowed tables, columns, and persistence rules live there.
4. **Do not** merge assumptions, columns, or patterns from **other** module docs, other domains, or “how we did it elsewhere” unless **this** module explicitly tells you to (e.g. “store only `external_message_id` per mail-adapter” as a cross-reference, not a license to add mail tables while implementing notifications).
5. If the module doc is **missing**, **empty of allowed schema**, or **ambiguous** for what you were asked to build → **STOP** and request clarification or **author/update** `docs/modules/{feature-slug}.md` first — **do not invent** schema in code or Liquibase.

---

## 1. Attach and follow (Cursor / human)

Include in context (attach `@` paths or open beside the prompt):

1. [`DATA-MODEL-STANDARDS.md`](./DATA-MODEL-STANDARDS.md) — less-data rule, schema control, PII classification, Liquibase as source of truth, **Proposed Schema Extension** process.
2. **Exactly one** module doc — the path confirmed in **§0**: `docs/modules/{feature-slug}.md`. That file is the **sole** allowed-column / allowed-table contract for this change.

Do not generate schema from memory when the module defines a closed list. Do not substitute a different module’s spec because it “seems similar.”

---

## 2. Strict implementation rule

- **Only** fields (DB columns, entity properties, persisted JSON keys) that are **explicitly allowed** in those documents may be implemented.
- **Do not** add “helpful,” “convenience,” debug, or speculative columns or properties.

---

## 3. Extension process (when more data seems necessary)

- **Do not** put the extra data in the implementation.
- Instead, add a section (in the module doc or PR description) titled exactly:

  **`## Proposed Schema Extension (requires PII review)`**

  and include at minimum:

  - **justification**
  - **PII classification** (`none` / `low` / `sensitive`)
  - **retention impact** (and deletion / anonymization if relevant)

- No Liquibase changeset for the new column until that proposal is accepted.

(See also the template block under **Contributors and AI-assisted changes** in [`DATA-MODEL-STANDARDS.md`](./DATA-MODEL-STANDARDS.md).)

---

## 4. PII safety

- **Assume any free-text field may contain PII** unless the spec explicitly constrains it to a non-PII code set (e.g. short enum string with no user input).
- **Avoid** `TEXT`, `VARCHAR` without max length for user content, `JSON` blobs for arbitrary maps, and similar unless **explicitly allowed** and classified.

---

## 5. Database control

- **All** schema changes are defined **only** via **Liquibase** migrations in the repo ([`LIQUIBASE-RULES.md`](./LIQUIBASE-RULES.md)).
- **No** implicit schema expansion (e.g. Hibernate `ddl-auto=update` on shared DBs, manual prod `ALTER` not reflected in changelogs).

---

## 6. Validation step (before returning / merging)

Answer:

> **Does this violate [`DATA-MODEL-STANDARDS.md`](./DATA-MODEL-STANDARDS.md) or the single authoritative `docs/modules/{feature-slug}.md` allowed list (§0)?**

- If **yes** → remove the violation and align to spec (or stop and output **only** a **Proposed Schema Extension** instead of code).
- If **unsure** → **default to less data**; do not add the field.

---

## Related

- [`MODULE-DOC-CONVENTION.md`](./MODULE-DOC-CONVENTION.md) — module docs carry allowed lists for strict tables.
- [`../prompts/README.md`](../prompts/README.md) — attach `docs/guides/` + `docs/modules/` when running feature prompts.
