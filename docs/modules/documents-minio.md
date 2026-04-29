# Module: documents — MinIO / S3-compatible storage

**Milestone:** M4. Companion: [`document-sharing.md`](./document-sharing.md) (ACL, hub, attachments). **Contract:** [`PROJECT-CONTEXT.md`](../prompts/PROJECT-CONTEXT.md) (documents + MinIO).

**Preflight:** [SCHEMA-PERSISTENCE-PREFLIGHT](../guides/SCHEMA-PERSISTENCE-PREFLIGHT.md) + [DATA-MODEL-STANDARDS](../guides/DATA-MODEL-STANDARDS.md).

**Authority:** Tables **`tenant_document`**, **`document_share`**, **`document_attachment`** are **shipped** with the allowed columns below. **No** object body in MariaDB — bytes live in **MinIO** (or AWS S3-compatible) only.

---

## Product intent

- **Object storage:** one logical **bucket** (configurable name) with **tenant-scoped prefixes** inside keys: `tenants/{tenantId}/documents/{documentId}/{safeFilename}` (exact pattern enforced in application code; **no** `..` path segments).
- **Upload / download:** browser uses **presigned URLs** (PUT for upload, GET for download) minted by the API; the API persists **`tenant_document`** metadata **after** the client PUT succeeds via **`POST .../complete`** (client echoes **`storageKey`** from the upload session).
- **PII:** filenames are **user-supplied**; treat as **low sensitivity** but log/display carefully; virus scan **TBD** (optional hook after upload).

---

## Configuration (environment)

| Key / property | Notes |
|----------------|--------|
| `app.storage.minio.endpoint` | e.g. `http://127.0.0.1:9000` — empty → document **write** paths that need MinIO return **503** until configured. |
| `app.storage.minio.access-key` / `app.storage.minio.secret-key` | API keys for MinIO user scoped to bucket. |
| `app.storage.minio.bucket` | Bucket name; must exist or be auto-created per deployment policy (v1: **expect bucket exists**). |
| `app.storage.minio.region` | Optional; default `us-east-1` for SDK compatibility. |
| `app.storage.minio.verify-object-before-complete` / `MINIO_VERIFY_OBJECT_BEFORE_COMPLETE` | Default **false**. When **true**, **`POST .../complete`** runs **HEAD** on the object and rejects on missing object, size mismatch, or content-type mismatch (when S3 returns a type). Requires reachable MinIO/S3. |
| `app.storage.minio.delete-object-on-soft-delete` / `MINIO_DELETE_OBJECT_ON_SOFT_DELETE` | Default **true**. After **`DELETE .../{id}`** soft-deletes the row, the API **best-effort** deletes the S3 object; failures are **logged** only (DB soft-delete still applies). Set **false** to leave bytes until a future cleanup job. |
| `app.storage.minio.orphan-cleanup.*` / `MINIO_ORPHAN_CLEANUP_*` | Optional **scheduled** job (default **off**). When **`enabled=true`**, lists **`tenants/`** in the bucket (up to **`max-keys-per-run`** per run), deletes S3 objects matching the canonical document key pattern with **no** `tenant_document` row if **`LastModified`** is older than **`min-object-age`**, then retries **S3 delete** for soft-deleted rows older than that age (batch **`soft-deleted-retry-max`**). Uses UTC **`cron`**. |

---

## Allowed persistence (v1)

### Table: `tenant_document` *(shipped)*

| Column | Nullable | Description |
|--------|----------|-------------|
| `id` | no | UUID PK. |
| `tenant_id` | no | FK → `tenant`. |
| `storage_key` | no | Object key inside configured bucket; **unique per tenant** with `tenant_id`. |
| `original_filename` | no | Display name from client; max **255** chars; sanitized for audit display. |
| `content_type` | no | MIME type supplied by client; max **128** chars. |
| `size_bytes` | no | Non-negative; **0** allowed only for edge tests. |
| `uploaded_by_user_id` | no | FK → `user_account` (owner uploader). |
| `created_at` / `updated_at` | no | UTC. |
| `deleted_at` | yes | Soft delete; non-null → object treated as gone for hub. Optional **S3 delete** on soft-delete (see config); **orphan** cleanup for failed uploads or disabled delete remains a **TBD** job. |

**Indexes:** unique (`tenant_id`, `storage_key`); index (`tenant_id`, `uploaded_by_user_id`, `created_at`); index (`tenant_id`, `deleted_at`).

### Table: `document_share` *(shipped — see also document-sharing.md)*

| Column | Nullable | Description |
|--------|----------|-------------|
| `id` | no | UUID PK. |
| `tenant_id` | no | FK → `tenant`. |
| `document_id` | no | FK → `tenant_document`. |
| `grantee_user_id` | yes | FK → `user_account`; **either** this **or** `grantee_role_id` set (enforced in application). |
| `grantee_role_id` | yes | FK → `role`; same tenant. |
| `created_by_user_id` | no | FK → `user_account`. |
| `created_at` | no | UTC. |

**Indexes:** (`tenant_id`, `document_id`); (`tenant_id`, `grantee_user_id`); (`tenant_id`, `grantee_role_id`).

### Table: `document_attachment` *(shipped — see also document-sharing.md)*

| Column | Nullable | Description |
|--------|----------|-------------|
| `id` | no | UUID PK. |
| `tenant_id` | no | FK → `tenant`. |
| `document_id` | no | FK → `tenant_document`. |
| `entity_type` | no | Stable code (e.g. `PAYROLL_RUN`, `EMPLOYMENT`) — max **64** chars; **enum validation in code** as features appear. |
| `entity_id` | no | UUID string of the business row; same tenant scope. |
| `created_by_user_id` | no | FK → `user_account`. |
| `created_at` | no | UTC. |

**Unique:** (`tenant_id`, `document_id`, `entity_type`, `entity_id`) — one link per document per entity instance.

---

## APIs (phased)

| Phase | Purpose |
|-------|---------|
| **D1 (shipped)** | **`GET /api/v1/tenant/documents`** (**`DOCUMENT_VIEW`**) — document **hub**: owned **union** user/role shares; excludes soft-deleted. |
| **D2 (shipped)** | **`POST /api/v1/tenant/documents/upload-sessions`** (**`DOCUMENT_EDIT`**) — returns `{ documentId, storageKey, uploadUrl, uploadMethod, expiresAt, requiredHeaders }` for a **presigned PUT** to MinIO/S3. **`POST /api/v1/tenant/documents/complete`** (**`DOCUMENT_EDIT`**) — persists **`tenant_document`** after upload; body must echo **`storageKey`** matching canonical layout for `documentId` + `originalFilename`. Optional **HEAD** verify before insert when **`verify-object-before-complete`** is **true**. **503** if `app.storage.minio.*` is incomplete. |
| **D3 (shipped)** | **`GET /api/v1/tenant/documents/{documentId}/download-url`** (**`DOCUMENT_VIEW`**) — presigned **GET** when caller may read the document (owner or share). **503** if storage not configured. |
| **D3b (shipped)** | **`DELETE /api/v1/tenant/documents/{documentId}`** (**`DOCUMENT_EDIT`**, **uploader-only**) — sets **`deleted_at`** (soft delete); hub and download deny. When **`delete-object-on-soft-delete`** is **true** (default), **best-effort** S3 **DeleteObject** after commit; failures logged only. |
| **D4 (shipped)** | **Shares:** `GET/POST /api/v1/tenant/documents/{id}/shares`, `DELETE .../shares/{shareId}` (**`DOCUMENT_EDIT`**, **uploader-only** v1). **Attachments:** `GET .../{id}/attachments` (**`DOCUMENT_VIEW`** + readable doc), `POST/DELETE .../attachments/{attachmentId}` (**`DOCUMENT_EDIT`**, uploader-only). Rules in [`document-sharing.md`](./document-sharing.md). |

---

## Security & privileges

- **`DOCUMENT_VIEW`:** list hub + future download presign read.
- **`DOCUMENT_EDIT`:** upload complete, share, attach, soft-delete own documents (exact matrix in `document-sharing.md`).

---

## Tenant web (Next.js)

- **`/app/documents`** (same BFF session and shared **`/app`** layout / sidebar shell as the dashboard) — hub, download, optional upload; see [`document-sharing.md`](./document-sharing.md) and **`tenant-web-vertical-slice.md`** §3.6.

## Related

- [`document-sharing.md`](./document-sharing.md), [`security.md`](./security.md), [`BUILD-CHECKLIST.md`](../product/BUILD-CHECKLIST.md) Milestone M4.
