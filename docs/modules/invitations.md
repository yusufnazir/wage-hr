# Module: tenant invitations

**Milestone:** M2 (with [`notifications-inbox.md`](./notifications-inbox.md) + [`mail-adapter.md`](./mail-adapter.md)).  
**Authority:** Allowed columns and flows here; persistence follows [`DATA-MODEL-STANDARDS.md`](../guides/DATA-MODEL-STANDARDS.md) and Liquibase under `backend/src/main/resources/db/changelog/`.

---

## Intent

- Allow a privileged tenant admin to **invite by email** a person to join the tenant with a **specific role**.
- **Accept** creates or reuses a `user_account`, ensures **membership** + `user_role`, marks the invitation **accepted**, clears the pending dedupe key, and emits in-app **`TENANT_JOINED`** (see inbox module; `correlation_id` = this invitation’s `id`).
- **Outbound email** for the invite link uses **`MailSendPort`** (v1: `LoggingMailSendPort` in dev/test; no persisted email body).

---

## What is implemented (v1) vs not

| Area | Status |
|------|--------|
| Create + list **pending** invitations | Implemented |
| **Accept** with token + password | Implemented |
| **At most one PENDING** invite per `(tenant_id, normalized email)` | DB unique on `pending_dedup_key` + idempotent `POST` |
| **Idempotent create** | Second `POST` with same tenant + email while pending returns same `invitationId`, `idempotentReplay: true`, **no** duplicate row, **no** second outbound mail |
| Email **trim + lowercase + ASCII format** check | Implemented (`INVALID_EMAIL` if bad) |
| `expires_at` **enforced on accept** (`410` / `INVITE_EXPIRED`) | Implemented |
| **Scheduled** expiry (job moving `PENDING` → `EXPIRED`) | **Not** implemented |
| Cancel / decline / resend | **Not** implemented |
| Accept path | **Primary:** new user (register on accept). **Also:** existing account **not yet** a member of this tenant, with **password** verification (`INVALID_PASSWORD` if wrong). |

---

## Privileges

| Code | Who (v1) | Purpose |
|------|-----------|---------|
| `USER_INVITE` | Demo **Admin** role (seeded) | `POST` / `GET` `/api/v1/tenant/invitations` |

Pool + `role_privilege` are seeded for the demo tenant; other tenants need the same pattern when product expands.

---

## Data: `tenant_invitation` (allowed columns)

| Column | Notes |
|--------|--------|
| `id` | UUID PK |
| `tenant_id` | FK tenant |
| `invited_email` | Normalized (trimmed, lowercased ASCII) |
| `inviter_user_id` | Actor who created the row |
| `role_id` | Tenant role granted on accept |
| `token_hash` | SHA-256 hex of secret token (never store plain token) |
| `status` | `PENDING`, `ACCEPTED`, … (string in DB) |
| `pending_dedup_key` | When `PENDING`: `tenant_id + ':' + invited_email` (unique); **null** when not pending |
| `expires_at` | Default **7 days** from create; checked on accept only |
| `accepted_user_id` | Set when accepted |
| `created_at`, `updated_at` | Standard |

**Indexes:** unique `token_hash` (global); unique `pending_dedup_key` (sparse via nulls when not pending); non-unique `(tenant_id, invited_email)` for lookups where present.

Anything outside this list is a **schema extension** (PII review + new changelog).

---

## API (v1)

Host must resolve tenant (e.g. `demo.lvh.me`) like other tenant APIs.

| Method | Path | Auth | Body / response |
|--------|------|------|-----------------|
| `POST` | `/api/v1/tenant/invitations` | Session + `USER_INVITE` | Body: `email`, `roleId`. Response: `invitationId`, `expiresAt`, `idempotentReplay` (boolean). Optional `devPlainToken` only in allowed non-prod profiles (below). |
| `GET` | `/api/v1/tenant/invitations` | Session + `USER_INVITE` | Pending invitations (`invitations[]`). |
| `POST` | `/api/v1/auth/invitations/accept` | Anonymous | Body: `token`, `password` (min 8). Success: membership + role + `TENANT_JOINED` notification. |

**CSRF:** Accept is excluded from CSRF so email links work without a session. Treat the token as a secret capability URL.

---

## Dev / test: plain token (`devPlainToken`)

- **Config:** `app.invitation.expose-plain-token` or env **`APP_INVITATION_EXPOSE_PLAIN_TOKEN`** (default **false**).
- **Hard rule:** `devPlainToken` is returned **only** when the flag is true **and** Spring active profiles include **`dev`**, **`test`**, or **`local`**. In any other profile (e.g. production), the flag is **ignored** and startup logs a **warning**.
- When honored, startup also logs a **warning** that responses may contain raw tokens.

---

## Mail

- **`TenantInvitationService`** calls **`MailSendPort`** with an `InvitationEmailRequest` (to, tenant handle, opaque token for URL building in the adapter).
- **No** full message body stored in MariaDB; provider id may later align with `notification.external_message_id` if product unifies outbound tracing (invitation mail is still separate from the `TENANT_JOINED` notification row).

---

## Related

- In-app inbox: [`notifications-inbox.md`](./notifications-inbox.md) — `TENANT_JOINED` + `correlation_id` = **`tenant_invitation.id`**.
- Outbound: [`mail-adapter.md`](./mail-adapter.md).

---

## Deferred (not v1)

- Decline / cancel / resend; **scheduled** expiry job; richer “existing member” UX.
- Web UI for invite list and accept page (BFF + CSRF for authenticated flows; accept stays public POST).

---

## Proposed Schema Extension (requires PII review)

*Empty until a change is proposed.*
