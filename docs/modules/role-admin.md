# Module: Tenant roles + platform role templates

**Feature slug:** `role-admin`  
**Actors:** Tenant Admin, Platform SuperAdmin (via tenant lens), Tenant Viewer (read-only when granted)

This module defines:

- Tenant-scoped role administration (list + separate edit view)
- Platform-scoped **role templates** (platform superadmin CRUD) used when creating new tenants

It supports:

- **Tenant admins** working on a tenant host (`{tenant}.{BASE_DOMAIN}`) with normal membership-based authorization
- **Platform SuperAdmins** working on the operator host (`admin.{BASE_DOMAIN}`) with a tenant lens (`X-Tenant-Id`) and the existing **break-glass** / elevation rules (see [`superadmin-tenant-lens.md`](./superadmin-tenant-lens.md), [`security.md`](./security.md))

Out of scope v1: role delete/archive, role cloning, per-business-unit roles, bulk user role assignment from the roles screen, editing templates (templates are view-only).

Out of scope v1: role delete/archive, role cloning, per-business-unit roles, bulk user role assignment from the roles screen.

---

## 1) Objective

Provide a clear, safe way to **view** and (when authorized) **edit** tenant roles and their granted privileges so tenant admins can manage access control without relying on seeds or backend-only knowledge.

Also provide a platform superadmin view into the **role templates** used for new tenants, so operators can verify what a new tenant will start with.

---

## 2) Scope

### In scope (v1)

- Tenant-scoped **roles listing**
- Tenant-scoped **role edit view** (separate route)
  - Rename a role
  - Replace role’s granted privileges (within the tenant’s effective pool ceiling)
- Privilege-driven UI:
  - **ROLE_VIEW**: can view list + detail read-only
  - **ROLE_EDIT**: can edit role name/privileges and create roles
- Works for platform superadmin via tenant lens (non-member elevation follows existing rules and break-glass for mutation)
- Platform superadmin role templates:
  - List templates and show their privilege sets
  - Create new templates and edit existing templates (templates affect **future tenant creation only**)
  - **Read-only operator matrix** (all templates × catalog labels): [`platform-tenant-privileges.md`](./platform-tenant-privileges.md) — web route `/app/platform-role-templates/privileges` (linked from the role templates list on `admin.{BASE_DOMAIN}`).
- Tenant creation behavior:
  - When a **new tenant is created as part of new-account registration**, the template roles are copied into the tenant roles.
  - The registering user becomes a member of that tenant and is assigned **one** tenant role copied from the template identified by **`auth.registration.default_role_template_code`** in **`platform_setting`** (default **`ADMIN`** when the key is absent). Full API, verification gate, and persistence: **[`account-registration.md`](./account-registration.md)**.

### Explicitly out of scope (v1)

- Delete a role (hard delete unsafe; archive semantics TBD)
- Create/edit privileges catalog (platform feature)
- Assign/remove roles from users in this screen (remains in user admin: [`user.md`](./user.md))
- Business-unit-scoped roles
- (Option A) Templates do not auto-sync to existing tenants; they are copied only when a tenant is created.

---

## 3) Data model (conceptual)

Existing tenant-scoped entities:

- **`role`**: `{ id, tenant_id, name, created_at, updated_at }`
- **`role_privilege`**: `{ id, tenant_id, role_id, privilege_id }`
- **`privilege`** (global catalog): `{ id, code, action, resource, ... }`
- **`tenant_privilege_allowance`** (+ subscription derived): defines the **effective pool ceiling** of privilege codes assignable in the tenant (see [`security.md`](./security.md))

### Platform role templates (new persistence)

Role templates are a small, platform-owned dataset. They are **not tenant-scoped**; they are used as the blueprint when creating a tenant.

- **`role_template`**: `{ id, code, display_name, created_at, updated_at }`
- **`role_template_privilege`**: `{ id, role_template_id, privilege_id }`

Templates:

- **Admin** template (`code`: `ADMIN`) — required for healthy tenant bootstrap; **default** first membership role for **self-service registration** when **`auth.registration.default_role_template_code`** is unset (see [`account-registration.md`](./account-registration.md)).
- **Employee** template (`code`: `EMPLOYEE`) — default baseline role template copy
- Additional templates may exist; all templates are copied into a newly created tenant.

When a tenant is created (registration or other flows per module docs), templates are copied into tenant roles:

- New rows in `role` (tenant-scoped) are created from templates, and their privilege grants are copied into `role_privilege`.
- The **self-service registering** user is assigned the tenant role copied from the configured default template via `membership` + `user_role` — **[`account-registration.md`](./account-registration.md)**.

#### Allowed columns (schema authority)

`role_template` (strict):

- `id` UUID PK
- `code` VARCHAR(32) unique, not null (example values: `ADMIN`, `EMPLOYEE`, `MANAGER`)
- `display_name` VARCHAR(128) not null (example values: `Admin`, `Employee`, `Manager`)
- `created_at`, `updated_at` timestamps not null

`role_template_privilege` (strict):

- `id` UUID PK
- `role_template_id` UUID FK → `role_template.id`
- `privilege_id` UUID FK → `privilege.id`

No PII in these tables.

---

## 4) Security & privileges

### New privilege codes (catalog)

This feature introduces two new global privilege codes (must be in Liquibase `privilege` table and in `DefinedPrivilege` enum):

- **`ROLE_VIEW`** — view roles and their privileges (tenant-scoped)
- **`ROLE_EDIT`** — edit roles and their privileges; create roles (tenant-scoped)

Platform superadmin access to template viewing is gated by existing platform-superadmin checks (same pattern as other `/api/v1/platform/**` endpoints).

**Notes**

- Any tenant-scoped role edits performed by a platform superadmin via elevation require **`X-Break-Glass-Reason`** per [`security.md`](./security.md) (mutations only).
- Superadmin elevation for a tenant without membership must still respect that tenant’s **effective pool ceiling** (see [`superadmin-tenant-lens.md`](./superadmin-tenant-lens.md)).

---

## 5) Business rules

1. **Assignable privileges** for a role are limited to the tenant’s **effective pool ceiling** (tenant allowances ∪ subscription-derived).
2. A role may not be assigned a privilege code that is not present in the global catalog.
3. **Role name**:
   - Required, trimmed
   - 1–128 chars
   - Unique per tenant (case-insensitive uniqueness recommended; exact rule enforced server-side)
4. **Self-lockout prevention (v1):** a caller with **ROLE_EDIT** must not be able to submit a change that results in the caller losing effective **ROLE_EDIT** privilege in the tenant after the update is applied (either by editing a role they currently hold, or by any edit that would remove their last source of ROLE_EDIT).
5. **Tenant creation from registration (v1):**
   - Registration creates a **new tenant**, a **membership** linking the new user to that tenant, copies the role templates into tenant roles, and assigns the new user the tenant **Admin** role.
   - Template roles are copied per-tenant; later tenant-admin changes affect **only** that tenant’s `role`/`role_privilege`.

---

## 6) API (tenant-scoped)

Base path: **`/api/v1/tenant/roles`** (requires tenant context via host or `X-Tenant-Id`).

### Endpoints

| Method | Path | Privilege | Description |
|--------|------|-----------|-------------|
| GET | `/api/v1/tenant/roles` | `ROLE_VIEW` | List roles. Query: `q` (optional contains search on name), `sort` = `NAME_ASC` \| `NAME_DESC` (default `NAME_ASC`). Response `data.items[]`: `{ id, name, privilegeCodes[], userCount }` (userCount optional for v1; if expensive, omit). |
| POST | `/api/v1/tenant/roles` | `ROLE_EDIT` | Create role. Body: `{ "name": "...", "privilegeCodes": ["USER_VIEW", ...] }` (`privilegeCodes` optional, default empty). Response `201` with `data.role`. Errors: `400` validation, `409` duplicate name. |
| GET | `/api/v1/tenant/roles/{roleId}` | `ROLE_VIEW` | Role detail. Response `data`: `{ role: { id, name, privilegeCodes[] }, assignablePrivilegeCodes[] }` where `assignablePrivilegeCodes` = effective pool ceiling (sorted). |
| PATCH | `/api/v1/tenant/roles/{roleId}` | `ROLE_EDIT` | Update role. Body: `{ "name": "...", "privilegeCodes": ["..."] }` (either or both; `privilegeCodes` is full replacement when present). Errors: `400` validation, `403` `CANNOT_LOCK_OUT_SELF` (or equivalent), `409` duplicate name, `404` unknown role. |

### Error shape

Use the existing API error conventions (problem+json / code) per global guides.

---

## 6b) API (platform role templates — platform superadmin)

Base path: **`/api/v1/platform/role-templates`**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/platform/role-templates` | platform superadmin | Returns `data.items[]`: `{ id, code, displayName, privilegeCodes[] }` (sorted by `code`). |
| POST | `/api/v1/platform/role-templates` | platform superadmin | Create template. Body: `{ "code": "MANAGER", "displayName": "Manager", "privilegeCodes": ["USER_VIEW", ...] }`. Response `201` with `data.item`. Errors: `400` validation, `409` duplicate code. |
| PATCH | `/api/v1/platform/role-templates/{id}` | platform superadmin | Update template. Body: `{ "displayName": "...", "privilegeCodes": ["..."] }` (either or both; `privilegeCodes` is full replacement when present). Response `200` with `data.item`. Errors: `400` validation, `404` unknown template. |

Notes:

- Template mutations affect **future tenant creation only** (Option A). Existing tenants are not modified.
- `ADMIN` template must exist for deployments that use default **self-service** registration (default role template code **`ADMIN`** when unset — [`account-registration.md`](./account-registration.md)).

---

## 7) Web UX (Next.js)

### Routes

- **`/app/roles`** — roles list view
- **`/app/roles/[roleId]`** — role detail/edit view (separate page)

Platform superadmin:

- **`/app/platform-role-templates`** (on `admin.{BASE_DOMAIN}`) — templates listing view.
- **`/app/platform-role-templates/new`** — create template view (separate route; code + display name + privileges).
- **`/app/platform-role-templates/[templateId]`** — template detail/edit view (separate route; edit display name + privileges).

### Listing view

- Shows table with:
  - **Role name**
  - **Privileges summary** (count + short list tooltip/expand)
  - Optional **Users** count (if available)
- Search: `q` query param (contains on role name)
- Sort: name asc/desc (URL-driven)
- If **ROLE_EDIT**: show “New role” CTA

### Edit view

- Header shows role name + back link to list
- If only **ROLE_VIEW**: read-only rendering (no inputs)
- If **ROLE_EDIT**:
  - Edit role name
  - Multi-select of assignable privilege codes
  - Privilege picker must make it obvious that only the tenant’s ceiling is assignable (e.g. “Assignable privileges (based on tenant plan + allowances)”)
  - Save button disabled while invalid / no changes
  - Surface server errors:
    - Duplicate name
    - Self-lockout prevention error

### Navigation visibility

Add a nav item (via `nav_menu_item` seeds) with:

- `path`: `/app/roles`
- `label_key`: `nav.roles`
- `required_privilege_code`: `ROLE_VIEW`

For platform operators, add a synthetic platform nav item (not tenant nav_menu_item) consistent with other platform pages:

- `path`: `/app/platform-role-templates`
- `label_key`: `nav.platform_role_templates`
- Visible only when `me.platformSuperadmin` (same pattern as `nav.platform_tenants`)

---

## 8) Edge cases

- Tenant has zero custom roles beyond seeds → list still works; creation allowed when ROLE_EDIT.
- Tenant effective pool ceiling changes (superadmin updates pool or subscription changes):
  - Role detail should reflect new `assignablePrivilegeCodes`.
  - Existing role privilege assignments that are no longer in ceiling should be treated as invalid for future edits; behavior must be explicit:
    - v1 recommendation: server rejects PATCH when requested `privilegeCodes` contains non-assignable codes; existing assignments outside ceiling should not be silently expanded.
- Superadmin operator without membership:
  - Can read roles when ROLE_VIEW is in tenant ceiling.
  - Can mutate only with break-glass header and only when ROLE_EDIT is in tenant ceiling.
- Template visibility:
  - Non-superadmin receives 403.
  - Templates never vary by tenant.

---

## 9) Acceptance criteria (testable)

1. Tenant user without `ROLE_VIEW` gets **403** for all roles routes and does not see `/app/roles` in nav.
2. With `ROLE_VIEW` only:
   - Can load `/app/roles` and `/app/roles/[roleId]` read-only.
   - No create/edit CTAs are shown.
3. With `ROLE_EDIT`:
   - Can create a role; duplicate name fails with **409**.
   - Can edit a role’s name and privileges; saved state reloads correctly.
4. Assigning privilege codes not in tenant ceiling fails with **400** (explicit error code).
5. Self-lockout prevention works: editing a role cannot remove the caller’s last source of `ROLE_EDIT` (request fails with **403** and a stable error code).
6. Platform superadmin via tenant lens:
   - Read without membership succeeds when the privilege is within tenant ceiling.
   - Mutations require `X-Break-Glass-Reason` and are audited per existing break-glass rules.
7. Platform superadmin can open `/app/platform-role-templates` and see two templates (Admin, Employee) with privilege lists.
8. Registration creating tenant (see [`account-registration.md`](./account-registration.md) for verification + API):
   - Registering a new account creates a new tenant; the user is assigned the tenant role copied from **`auth.registration.default_role_template_code`** (default **`ADMIN`**).
   - Tenant roles are independent: editing roles in tenant A does not affect tenant B.
9. Platform superadmin can create and edit templates; edits do not change existing tenants and are reflected for subsequent registrations.

---

## 10) Implementation notes (repo pointers)

### Backend

- **Controller**: `backend/src/main/java/com/wagepayroll/api/TenantRolesController.java`
- **Service** (business rules + self-lockout prevention): `backend/src/main/java/com/wagepayroll/tenant/TenantRoleAdminService.java`
- **Privilege enforcement + tenant lens break-glass**: `backend/src/main/java/com/wagepayroll/security/PrivilegeAuthorizationAspect.java`
  - Mutating superadmin-elevated calls require `X-Break-Glass-Reason` (403 with stable problem+json `code` when missing/invalid).
- **Privilege catalog**: `backend/src/main/java/com/wagepayroll/security/DefinedPrivilege.java` includes `ROLE_VIEW` / `ROLE_EDIT`
- **Platform role templates API**: `backend/src/main/java/com/wagepayroll/api/PlatformRoleTemplatesController.java`
- **Role templates persistence**:
  - Entities/repos: `backend/src/main/java/com/wagepayroll/domain/roletemplate/*`
  - Seed task: `backend/src/main/java/com/wagepayroll/liquibase/task/DataM6RoleTemplates1.java`
- **Liquibase (DML)**:
  - `backend/src/main/resources/db/changelog/dml/data-m6-role-admin-privileges-1.xml` → runs `DataM6RoleAdminPrivileges1`
  - `backend/src/main/resources/db/changelog/dml/data-m6-nav-roles-demo-1.xml` → demo nav row (`/app/roles`, `nav.roles`, requires `ROLE_VIEW`)
  - `backend/src/main/resources/db/changelog/dml/data-m6-role-templates-1.xml` → runs `DataM6RoleTemplates1`
- **Liquibase (DDL)**:
  - `backend/src/main/resources/db/changelog/ddl/schema-m6-role-templates-1.xml`
- **Registration tenant bootstrap**: `backend/src/main/java/com/wagepayroll/auth/RegistrationService.java`

### Web (Next.js)

- **Routes**:
  - List: `frontend/src/app/app/roles/page.tsx`
  - Detail/edit: `frontend/src/app/app/roles/[roleId]/page.tsx`
  - Platform templates list: `frontend/src/app/app/platform-role-templates/page.tsx`
  - Platform templates create: `frontend/src/app/app/platform-role-templates/new/page.tsx`
  - Platform templates edit: `frontend/src/app/app/platform-role-templates/[templateId]/page.tsx`
- **API client**: `frontend/src/lib/api.ts` (`fetchTenantRoles`, `fetchTenantRoleDetail`, `createTenantRole`, `patchTenantRole`)
- **API client (platform)**: `frontend/src/lib/api.ts` (`fetchPlatformRoleTemplates`)
- **Break-glass header forwarding (BFF)**: `frontend/src/app/api/bff/[...path]/route.ts` forwards `X-Break-Glass-Reason` for mutating requests.
- **Nav labels**: `frontend/src/messages/nav.ts` includes `nav.roles` and `nav.platform_role_templates`

### Web flows (v1)

- `/app/roles` lists roles; `ROLE_EDIT` users can create a role (name only) then continue editing in `/app/roles/[roleId]`.
- `/app/roles/[roleId]` shows assignable privilege codes from the server (tenant ceiling) and allows full replacement of role privileges.
- On `admin.{BASE_DOMAIN}` with tenant lens, the roles UI requires a break-glass reason for create/save and sends `X-Break-Glass-Reason`.

