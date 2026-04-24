# Module: notifications & inbox (PII-strict)

**Milestone:** M2 (with M1 foundations: auth, tenant, privileges).  
**Design authority:** This document refines the contract in `docs/prompts/PROJECT-CONTEXT.md` and **MUST** be followed for schema and code review. If anything here conflicts with older prose, **this module wins** for notifications.

**Global rules:** [SCHEMA-PERSISTENCE-PREFLIGHT](../guides/SCHEMA-PERSISTENCE-PREFLIGHT.md) + [`DATA-MODEL-STANDARDS`](../guides/DATA-MODEL-STANDARDS.md) — less-data default, **forbidden** columns not in the allowed list, schema control, Liquibase only, and **`## Proposed Schema Extension (requires PII review)`** for any extra idea.

---

## Strict design intent

1. **Long-lived truth** (payroll, financial totals, audit) is retained **10+ years** in **domain tables** — not duplicated inside notification rows.
2. **Notifications and inbox** hold **minimal or no PII**: no bodies, subjects, names, emails, salary, or free-form text.
3. The system **supports future GDPR-style deletion and anonymization** (nullable foreign keys, no content duplication, correlation resolves through privileged domain APIs).
4. **No message bodies or sensitive payloads** in the application database for email or in-app notification storage.

---

## Mandatory storage rules (notification row)

### MUST ONLY store (payload / channel metadata)

| Field | Type | Notes |
|--------|------|--------|
| `notification_type` | enum (DB: `VARCHAR` constrained or enum table) | Stable code, e.g. `PAYROLL_RUN_LOCKED`. **Encodes intent**; pairing with `correlation_id` MUST be unambiguous per type (see below). |
| `template_version` | string | e.g. `2026-04-22`, semver, or hash of template bundle — for deterministic rendering. |
| `correlation_id` | UUID, non-null in v1 DB | Pointer to **one** domain aggregate per `notification_type` (see binding table). **Semantics depend on `notification_type`** (document each enum member). |
| `external_message_id` | string, nullable | Provider id after send — **only** mail traceability the DB keeps for email channel. |
| `status` | enum | Lifecycle: e.g. `PENDING`, `QUEUED`, `SENT`, `FAILED`, `READ` (or split channel state — see optional table below). |
| `created_at`, `updated_at` | timestamps | As per data model standards. |

### Routing (required for product; not “message content”)

These identifiers **route** and **authorize** inbox queries. They are **not** substitutes for storing names or email content.

| Field | Type | Notes |
|--------|------|--------|
| `tenant_id` | UUID | Tenant scope. |
| `recipient_user_id` | UUID | Who may see this row. **Anonymization:** nullable after user anonymized, or FK replaced per policy; row may remain for aggregate reporting with recipient nulled. |

**Rule:** Anything that could carry **variable human-readable content** belongs in **templates + domain APIs**, not in these columns.

### MUST NOT store

- Message **body**, **subject**, snippets, or **free-form text** of any kind.
- **Names, emails, phone, salary, national ids**, or other PII.
- Serialized “props” JSON with user-defined strings (forbidden unless a future **approved** extension adds a **non-PII** binary blob — default **no**).

---

## `notification_type` and `correlation_id` (binding rule)

- **`notification_type` is an enum in application code** (single source of truth); DB enforces allowed values (check constraint or lookup table **without** descriptive text that could become a PII sink — use code only).
- For each `notification_type`, document in this file (or generated appendix):

  | `notification_type` | `correlation_id` points to | Resolved via (API) |
  |---------------------|-----------------------------|---------------------|
  | `TENANT_JOINED` | `tenant_invitation.id` | Invitation row / tenant shell (membership already exists) |
  | *(example)* `PAYROLL_RUN_STATUS` | `payroll_run.id` | `GET /v1/payroll-runs/{id}` (privileged) |

**Correlation rules (M2):** invitation-driven notifications use **`tenant_invitation.id`**. Future **tenant-wide** events may use `tenant.id`; **user-scoped** events may use `user_account.id`. Each `notification_type` documents exactly one semantics; **`correlation_id` is required** (non-null) for every emitted row in v1.

- If a notification is **not** tied to a domain row, `correlation_id` is **null** and the type MUST be self-sufficient for template choice (e.g. `SYSTEM_MAINTENANCE_WINDOW` with no PII — still **no** free text in DB).

---

## Rendering rule (web / Flutter / email)

1. **Server returns** only: `notification_type`, `template_version`, `correlation_id`, `status`, timestamps, `external_message_id` (if any), plus routing ids for the current user.
2. **User-visible strings** are built by:
   - selecting a **template** from `notification_type` + `template_version`;
   - loading **domain data** using `correlation_id` through **existing authorized REST APIs** (never by embedding domain fields into the notification row).
3. If the domain row is **gone or inaccessible**, templates show a **generic safe fallback** (“This item is no longer available”) — still **no** stored explanation text on the notification.

---

## Mail integration rule

1. **Email HTML/text is composed at send-time only** (in memory or inside worker process).
2. After send, the application persists **only** `external_message_id` on the notification (or on a child delivery row — see optional model). **Full email bodies MUST NOT** be written to MariaDB.
3. The **mail provider** is the system of record for message content; retries and support use provider tooling + `external_message_id`.

---

## Architecture rules

1. **Central `NotificationService`** (application service): **all** inserts/updates to notification tables go through this service. **No** direct repository/DAO writes from controllers or random listeners.
2. **`notification_type`**: Java/Kotlin enum (or equivalent) mirrored to DB constraint; Liquibase seeds **only** codes, not user text.
3. **`correlation_id`**: links to domain data **without duplication**; resolution only via domain services / APIs.
4. **Loose coupling for anonymization:** notification rows reference `recipient_user_id` and `correlation_id` by **id only**; no embedded copies of domain fields. Domain tables own PII lifecycle.

---

## 1. Entity model

### 1.1 Core table: `notification`

| Column | Nullable | Description |
|--------|----------|-------------|
| `id` | no | PK UUID. |
| `tenant_id` | no | Tenant. |
| `recipient_user_id` | yes* | Inbox owner. *Nullable when anonymization policy clears recipient link while retaining row. |
| `notification_type` | no | Enum code. |
| `template_version` | no | Template bundle version. |
| `correlation_id` | no | Domain FK surrogate per type (required for all emits in v1; NOT NULL in DB). |
| `external_message_id` | yes | Mail provider id post-send. |
| `status` | no | Lifecycle enum. |
| `created_at` / `updated_at` | no | Audit timestamps. |
| `read_at` | yes | Inbox UX; **not** a content field. |

**Indexes:** `(tenant_id, recipient_user_id, created_at DESC)`, `(tenant_id, correlation_id)`, unique optional on `(external_message_id)` if provider guarantees uniqueness per tenant.

**No other columns** on `notification` without an **architecture amendment** and explicit PII review.

### 1.2 Optional: `notification_delivery` (only if multi-channel state pollutes one row)

Use **only** if email vs push vs SMS need independent states. Same PII rules: **no bodies**, optional `external_message_id` per channel.

| Column | Notes |
|--------|--------|
| `id`, `notification_id`, `channel` enum, `status`, `external_message_id`, timestamps | Same constraints: no PII payloads. |

Default for v1: **single row** on `notification` with `external_message_id` for email is enough.

---

## 2. Service interfaces (conceptual)

Language-agnostic contracts; implement in Spring as `@Service` + interfaces.

### 2.1 `NotificationService` (mandatory entry point)

```text
interface NotificationService {

  /** Validates command against allow-list; persists notification row(s); schedules outbound email if applicable. */
  void emit(NotificationEmitCommand command);

  /** Idempotent mark-read for inbox. */
  void markRead(NotificationId id, UserId reader, TenantId tenantId);
}
```

```text
record NotificationEmitCommand(
  TenantId tenantId,
  UserId recipientUserId,
  NotificationType notificationType,
  String templateVersion,
  CorrelationId correlationId,   // optional/null
  boolean requestEmailChannel    // if true, pipeline sends mail and sets external_message_id when done
) {}
```

**Hard rules:**

- `NotificationEmitCommand` **must not** accept `String` free text, `Map` of arbitrary props, subject, or body.
- Email generation receives **only** resolved in-memory DTOs from **domain read services** inside the same transaction/session as send — those DTOs are **not** persisted as notification columns.

### 2.2 `NotificationType` (enum)

```text
enum NotificationType {
  TENANT_JOINED,
  // examples — add in Liquibase + code when shipped
  PAYROLL_RUN_LOCKED,
  BILLING_USAGE_THRESHOLD
}
```

Each enum constant has: **allowed correlation target**, **default template_version**, **whether email is allowed**.

### 2.3 `MailSendPort` (outbound; no body persistence)

```text
interface MailSendPort {

  /** Returns provider-assigned message id only. Body built in memory inside adapter implementation. */
  ExternalMessageId send(MailMessage message);
}

record MailMessage(
  EmailAddress to,           // used only in transit; NOT written to notification table
  RenderedEmailBody body   // ephemeral; exists only for SMTP/API call
) {}
```

Adapter persists **only** returning id onto `notification.external_message_id` via `NotificationService` callback or same unit of work orchestrator.

### 2.4 Internal domain events

Domain modules raise events, e.g. `PayrollRunLockedEvent`. A **single** `NotificationEventListener` (or per-aggregate small listeners) maps:

`PayrollRunLockedEvent` → `notificationService.emit(new NotificationEmitCommand(..., PAYROLL_RUN_LOCKED, v, payrollRunId, true))`

**No** listener writes notification tables directly.

---

## 3. Example flow (event → notification → email)

1. **Domain:** `PayrollRunService` completes `lockRun(runId)` and publishes `PayrollRunLockedEvent(runId, tenantId, actorUserId)`.
2. **Listener:** `PayrollNotificationListener` loads **recipient ids** from domain policy (e.g. approvers) using payroll APIs — **not** stored on event if that would duplicate PII; ideally event carries **only ids** (`runId`, `tenantId`, `recipientUserIds` as UUID list from prior join in service layer).
3. **NotificationService.emit:** For each recipient, insert `notification` row: `notification_type=PAYROLL_RUN_LOCKED`, `template_version`, `correlation_id=runId`, `status=PENDING`, `external_message_id=null`.
4. **Email branch:** If `requestEmailChannel`:
   - `PayrollRunQueryService` loads run summary **in memory** for template rendering.
   - `MailTemplateRenderer` produces `RenderedEmailBody` (ephemeral).
   - `MailSendPort.send` → provider returns `ext_abc123`.
   - Update same row: `external_message_id=ext_abc123`, `status=SENT` (or `FAILED` on error).
5. **UI:** Client calls `GET /v1/me/notifications`, then for each row with `correlation_id`, calls payroll API to render title — **subject lines in UI** are client-side from template + API, not from notification row.

---

## 4. API endpoints (minimal)

All responses **must** expose only the allowed fields. **No** `message`, `title`, or `details` string fields in JSON.

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/v1/me/notifications` | List for current user + tenant host. Query: `limit` (default 50, max 100), `offset` (default 0). Response `data`: **`items`**, **`total`**, **`limit`**, **`offset`** — strict DTO fields on each item only. |
| `PATCH` | `/api/v1/me/notifications/{id}/read` | Sets `read_at` + `status=READ` (idempotent). |
| *none* | *no client POST that creates body* | Creation is **internal** via `NotificationService.emit` from trusted domain code only. |

**Optional internal** (platform network only): `POST /internal/v1/notifications/emit` with **fixed JSON schema** mirroring `NotificationEmitCommand` — no arbitrary maps; prefer **no** public HTTP and use in-process listeners only.

**Privileges:** e.g. `NOTIFICATION_VIEW` (own), `NOTIFICATION_MARK_READ` (own). Admin “view all tenant notifications” is a **separate** privileged read that still returns **only** strict columns.

---

## 5. Web / mobile behavior

- Inbox list: show **skeleton** from type + version; **hydrate** labels via batched domain fetches (respect N+1: batch by `correlation_id` + type).
- Deep link: `/app/notifications/{id}` loads notification, then navigates to domain screen using `correlation_id` when type implies a route.
- **Dark/light:** templates use design tokens; no user content in notification row.

---

## 6. Liquibase / review checklist

- [x] No column on `notification` outside the allowed set (+ routing ids + `read_at` if used). *(M2 changelog + entities)*
- [x] No `@Lob`, `TEXT`, or `JSON` for user content on notification tables. *(as implemented)*
- [x] All writers of notification rows go through `NotificationService`. *(M2 — no controller direct writes)*
- [ ] Mail adapter integration tests assert **no** insert of body into DB (spy on JDBC or use repository tests).

---

## 7. Security notes

- **IDOR:** `GET /me/notifications` must filter `recipient_user_id = current user` and `tenant_id` resolved from host/session.
- **Correlation leakage:** Domain APIs used for hydration must enforce **same** tenant and privilege as viewing the notification.

---

## Related docs

- `docs/prompts/PROJECT-CONTEXT.md` — product contract.
- `docs/guides/DATA-MODEL-STANDARDS.md` — lifecycle, PII minimization, **schema control**, Liquibase as source of truth.
- `docs/modules/mail-adapter.md` — outbound send; **no body persistence**; v1 id on notification row only.

---

## Proposed Schema Extension (requires PII review)

*Do not implement columns or APIs below until this block is approved and a Liquibase changeset exists.*

| Field / change | *(empty until proposed)* |
|----------------|-------------------------|
| **Justification** | |
| **PII classification (none / low / sensitive)** | |
| **Retention impact** | |
| **Liquibase changeset (when approved)** | |
