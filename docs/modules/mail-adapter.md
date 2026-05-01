# Module: mail adapter (outbound)

**Milestone:** M2.  
**Preflight:** [SCHEMA-PERSISTENCE-PREFLIGHT](../guides/SCHEMA-PERSISTENCE-PREFLIGHT.md) + [DATA-MODEL-STANDARDS](../guides/DATA-MODEL-STANDARDS.md) + this file + [notifications-inbox](./notifications-inbox.md) (for `external_message_id` on the notification row).

---

## Design intent

- Send email via **external HTTP mail API** at **send-time only**; compose HTML/text **in memory** in the adapter.
- **Do not** persist full email bodies, subjects copied from user content, or raw provider payloads in our database.
- **Traceability:** after send, persist **only** provider identifiers as already specified on the **notification** row (`external_message_id`) unless this module is later extended with a **separate** outbox table via approved schema (see below).

**v1 default:** **No dedicated `mail_*` persistence table** required — the notification row holds `external_message_id`. **Exception (approved):** reusable **operator-authored** layouts live in **`mail_template` / `mail_template_locale`** per [`mail-templates.md`](./mail-templates.md) (catalog only; **not** per-send bodies). If you introduce an outbox or retry queue, you **must** add an **allowed column list** here and a Liquibase changeset first.

---

## Allowed persistence (v1)

| Store | Where | Allowed fields |
|-------|--------|------------------|
| Provider message id | `notification.external_message_id` | Per [notifications-inbox](./notifications-inbox.md) only |
| Template catalog | `mail_template`, `mail_template_locale` | Per [mail-templates](./mail-templates.md) (operator subject/HTML only) |

No other persisted mail artifact in v1 without **Proposed Schema Extension** beyond the above.

---

## Service boundary

- **`MailSendPort`** (or equivalent) accepts ephemeral render result + recipient routing; returns `ExternalMessageId`.
- Caller (**`NotificationService`**) updates `notification.external_message_id` — not random controllers.

---

## Proposed Schema Extension (requires PII review)

*Use this heading for any new table/column (e.g. outbox, provider event log). Do not implement until approved + Liquibase.*

| Field / change | *(empty)* |
|----------------|-----------|
| **Justification** | |
| **PII classification (none / low / sensitive)** | |
| **Retention impact** | |
| **Liquibase changeset (when approved)** | |

---

## Related

- [notifications-inbox](./notifications-inbox.md) — strict notification row; email channel metadata.
- [LIQUIBASE-RULES](../guides/LIQUIBASE-RULES.md) — DDL/DML process.
