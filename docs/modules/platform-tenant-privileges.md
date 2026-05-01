# Module: Platform role template privileges matrix (operator)

**Feature slug:** `platform-role-template-privileges`  
**Doc file note:** Kept path `docs/modules/platform-tenant-privileges.md` for continuity; the feature is **not** tenant-lens or `GET /me` driven.

**Primary host:** `admin.{BASE_DOMAIN}`  
**Related:** [`role-admin.md`](./role-admin.md) (role templates CRUD + schema), [`navigation-menu.md`](./navigation-menu.md) (synthetic nav under role templates area only — no separate top-level “Tenant privileges” item).

---

## Superseded behavior (do not implement)

The earlier “tenant privileges inspector” compared **platform superadmin effective privileges** (`GET /api/v1/me`) to **tenant pool ceiling** in lens context. That was misleading for operators: superadmin lens semantics are not a substitute for “what privileges do **role templates** grant new tenants?” That UI and **`nav.platform_tenant_privileges`** are **removed**.

---

## Product semantics (read this before UI copy)

| Concept | Meaning |
|--------|--------|
| **`role_template` + `role_template_privilege`** | Bootstrap blueprint: privilege codes **copied** into new tenant `role` / `role_privilege` when a tenant is created (e.g. registration). |
| **`tenant_privilege_allowance` + subscription-derived ceiling** | **Assignable ceiling** inside a tenant (`PermissionService`). It can **differ** from the union of template privileges unless operations keep them aligned. |

This module’s screen is **templates only** — it does **not** require a tenant lens and does **not** use `GET /api/v1/me` `privileges` as primary content.

---

## Web routes (Next.js App Router)

- **Read-only matrix:** `/app/platform-role-templates/privileges`
- **Backward compatibility:** `/app/platform-tenant-privileges` **redirects** (HTTP or client) to `/app/platform-role-templates/privileges` so old bookmarks still work.

### Navigation

- **No** dedicated synthetic item `nav.platform_tenant_privileges`.
- Operators reach the matrix from **Role templates** (`/app/platform-role-templates`) via an explicit in-page link (“Template privileges” / equivalent i18n key).

---

## UX requirements (web)

1. **Platform superadmin only** (same as other `/app/platform-*` operator pages): non-superadmin sees a short denial message.
2. **No tenant lens** required; page loads without `X-Tenant-Id`.
3. **Data shown:**
   - **All role templates:** `GET /api/v1/platform/role-templates` → each item’s `code`, `displayName`, `privilegeCodes` (sorted for display).
   - **Global privilege catalog** (labels): `GET /api/v1/platform/privileges/catalog` → `data.entries[]` (`code`, `action`, `resource`, `description`).
4. **Presentation:** For each template, list privileges with catalog columns where the code exists in the catalog; codes on the template that are missing from the catalog row set still show the code (edge case).
5. **Optional:** client-side filter by privilege code or template code (nice-to-have; not required for v1 if time-constrained).

---

## Backend API surface (reuse only)

- `GET /api/v1/platform/role-templates` — list templates with `privilegeCodes` per [`PlatformRoleTemplateDto`](../../backend/src/main/java/com/wagepayroll/api/dto/PlatformRoleTemplateDto.java).
- `GET /api/v1/platform/privileges/catalog` — platform superadmin only.

**Related but not part of this screen:** `GET /api/v1/platform/tenants/{tenantId}/privilege-pool` remains available for tooling / other features; this matrix does not call it.

---

## Acceptance criteria

1. On `admin.{BASE_DOMAIN}`, a platform superadmin can open `/app/platform-role-templates/privileges` and see **every** role template with its privilege list enriched from the catalog.
2. `/app/platform-tenants` navigation does **not** include a separate “Tenant privileges” item.
3. `/app/platform-role-templates` includes a visible link to the matrix route.
4. Visiting `/app/platform-tenant-privileges` ends up on the matrix route (redirect).
5. No schema changes for this revision.

---

## Implementation pointers

- Web:
  - [`frontend/src/app/app/platform-role-templates/privileges/page.tsx`](../../frontend/src/app/app/platform-role-templates/privileges/page.tsx) — matrix page
  - [`frontend/src/app/app/platform-role-templates/page.tsx`](../../frontend/src/app/app/platform-role-templates/page.tsx) — link to matrix
  - [`frontend/src/app/app/platform-tenant-privileges/page.tsx`](../../frontend/src/app/app/platform-tenant-privileges/page.tsx) — client redirect to matrix
  - [`frontend/src/lib/api.ts`](../../frontend/src/lib/api.ts) — `fetchPlatformRoleTemplates`, `fetchPlatformPrivilegeCatalog` (existing)
  - [`frontend/src/messages/nav.ts`](../../frontend/src/messages/nav.ts) — `roleTemplateMatrix.*` keys
- Backend:
  - [`backend/src/main/java/com/wagepayroll/api/NavigationController.java`](../../backend/src/main/java/com/wagepayroll/api/NavigationController.java) — remove synthetic `nav.platform_tenant_privileges` item
- Tests: [`NavigationAndSettingsIT`](../../backend/src/test/java/com/wagepayroll/api/NavigationAndSettingsIT.java), [`MeTenantsIT`](../../backend/src/test/java/com/wagepayroll/api/MeTenantsIT.java) — navigation item counts decrease by 1.

---

## Verification (manual smoke)

1. Sign in as platform superadmin on `admin.{BASE_DOMAIN}`.
2. Open **Role templates** → follow link to **Template privileges** (matrix).
3. Confirm each template section lists privileges with catalog metadata.
4. Open `/app/platform-tenant-privileges` in the address bar → lands on matrix route.

**Automated:** `mvn test` (backend), `npm run build` (frontend).
