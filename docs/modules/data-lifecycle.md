# Module: Data lifecycle & privacy (M1 foundation)

**Feature slug:** `data-lifecycle`  
**Related:** [`audit.md`](./audit.md), [`user.md`](./user.md), [`auth.md`](./auth.md), [`DATA-MODEL-STANDARDS.md`](../guides/DATA-MODEL-STANDARDS.md), architecture retention language in `PROJECT-CONTEXT.md`

## 1. Goals (M1)

- Document **PII-bearing tables** shipped in the platform skeleton and **allowed columns** (classification).
- Provide a **machine-readable subject export** for the authenticated principal (no secrets).
- Provide an **erasure request** API that **logs audit** only — automated fulfillment, workflow queues, and DPA-specific SLAs are **out of scope for M1**.

## 2. APIs

| Method | Path | Auth | Notes |
|--------|------|------|--------|
| GET | `/api/v1/me/privacy/export` | Session | JSON `data.export` — see §4. Appends `audit_event` **`SUBJECT_DATA_EXPORTED`**. |
| POST | `/api/v1/me/privacy/erasure-request` | Session | Optional JSON `{ "note": "…" }` (note ≤ 500 chars). **202** `data.status=accepted`. Appends **`SUBJECT_ERASURE_REQUESTED`**; metadata may include `noteLength` + `noteSha256` (never raw note). **400** `ERASURE_NOTE_TOO_LONG`. CSRF required. |

Tenant context is **not** required (export is global identity + all memberships).

## 3. PII inventory (M1 schema)

Classification: **`none`** | **`low`** | **`sensitive`** (per `DATA-MODEL-STANDARDS.md`).

| Table | Tenant scope | PII / notes |
|-------|--------------|-------------|
| `user_account` | Global | **sensitive:** `email`; **low:** `id`, `preferred_locale`, timestamps; **none:** `platform_superadmin`; **never export** `password_hash` (credentials). |
| `membership` | Yes | **low:** links `user_id` ↔ `tenant_id`. |
| `user_role` | Yes | **low:** role assignment. |
| `role` | Yes | **none:** role names are tenant labels, not person data. |
| `tenant` | Yes | **none:** org handle/name (business identifiers). |
| `tenant_setting` | Yes | **low / sensitive (value-dependent):** keys are controlled; `value_text` must remain **non-PII** per `tenant-settings.md` — treat as **sensitive** if misused. |
| `platform_setting` | Global | **none** by contract (non-PII config). |
| `password_reset_token` | Global | **sensitive:** ties to account recovery; short TTL; never export. |
| `audit_event` | Optional tenant | **low / sensitive (metadata-dependent):** minimize PII in `metadata_json`; append-only; retention ≥ contract. |
| `nav_menu_item` | Yes | **none:** menu structure. |
| `privilege`, `role_privilege` | Mixed | **none:** authorization catalog. |

**Messaging / inbox (M2+):** canonical rules live in [`notifications-inbox.md`](./notifications-inbox.md) — high-risk for stored PII; not in M1 DDL beyond this inventory pointer.

## 4. Export payload (`exportSchemaVersion` = 1)

Top-level keys under `data.export`:

- `exportSchemaVersion` (int)
- `generatedAt` (ISO-8601 UTC)
- `account` — `userId`, `email`, `preferredLocale`, `platformSuperadmin`, `createdAt`, `updatedAt`
- `tenantMemberships` — same shape as `GET /api/v1/me/tenants` (`id`, `handle`, `name`, `roles[]`)

## 5. Deletion, anonymization, export (policy matrix)

| Entity / area | Delete in M1? | Anonymize | Export in M1? |
|---------------|---------------|-----------|----------------|
| `user_account` | No automated API | Planned: pseudonymize email + strip sessions (future module) | Yes (fields §4; no password) |
| `audit_event` | **No** (append-only; legal retention) | Surrogate ids in metadata when business rows anonymized | No direct subject bulk export of all audit rows |
| `password_reset_token` | Operational expiry only | — | Never |
| Tenant business data (payroll, M5+) | Policy-driven later | Entity-specific | Per jurisdiction / product |

## 6. Tests

- `backend/src/test/java/com/wagepayroll/api/MePrivacyIT.java`

## 7. Proposed Schema Extension (requires PII review)

- `data_subject_request` queue table (kind, status, payload, SLA timestamps) when erasure workflow is automated.
