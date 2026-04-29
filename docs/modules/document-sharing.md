# Module: document sharing & attachments

**Milestone:** M4. **Storage:** [`documents-minio.md`](./documents-minio.md). **Out of scope:** real-time collaborative editing (see `PROJECT-CONTEXT.md`).

**Preflight:** [SCHEMA-PERSISTENCE-PREFLIGHT](../guides/SCHEMA-PERSISTENCE-PREFLIGHT.md) + [DATA-MODEL-STANDARDS](../guides/DATA-MODEL-STANDARDS.md).

---

## Product intent

- **Hub:** each user sees **documents they uploaded** plus **documents shared with them** (by **user id** or by **role id** within the tenant). Implemented as **`GET /api/v1/tenant/documents`** (see `documents-minio.md` D1).
- **Sharing:** **`GET/POST/DELETE`** on **`/api/v1/tenant/documents/{id}/shares`** (**`DOCUMENT_EDIT`**) — v1 **uploader-only** (see access table below).
- **Attachments:** **`document_attachment`** via **`GET/POST/DELETE`** on **`/api/v1/tenant/documents/{id}/attachments`**; list uses **`DOCUMENT_VIEW`** when the document is readable; mutations use **`DOCUMENT_EDIT`** + uploader. **Per-domain** checks on `entity_type`/`entity_id` ship when payroll/HR modules wire them.

---

## Access rules (v1)

| Action | Privilege |
|--------|-----------|
| List hub (`GET /tenant/documents`) | **`DOCUMENT_VIEW`** |
| Upload session / complete / download URL | **`DOCUMENT_EDIT`** / **`DOCUMENT_VIEW`** (see `documents-minio.md`) |
| List/create/delete **shares** (`.../documents/{id}/shares`) | **`DOCUMENT_EDIT`** + **uploader-only** (v1) |
| List **attachments** (`.../documents/{id}/attachments`) | **`DOCUMENT_VIEW`** + readable document |
| Create/delete **attachments** | **`DOCUMENT_EDIT`** + **uploader-only**; **target-entity privilege** when domain modules enforce it (not wired in generic API v1) |
| Soft-delete **document** (`DELETE …/documents/{id}`) | **`DOCUMENT_EDIT`** + **uploader-only** |

**Effective read:** user can read a document if **not deleted** and (**owner** OR **share to user** OR **share to one of caller’s roles in tenant**).

---

## Web (Next.js)

- **Shipped:** tenant **`/app/documents`** — hub list, download, optional upload (**`DOCUMENT_EDIT`** + MinIO + **CORS**), **Remove** (soft delete) and **Sharing & record links** panel (shares + attachments) for **owned** documents; page is a child of **`TenantAppShell`** (sidebar from **`GET /api/v1/me/navigation`** includes **`nav.documents`** when entitled). Nav **`nav.documents`** (**`DOCUMENT_VIEW`**) on demo; labels in `frontend/src/messages/nav.ts`. Shell details: **`tenant-web-vertical-slice.md`** §3.6.

---

## Related

- [`documents-minio.md`](./documents-minio.md), [`security.md`](./security.md).
