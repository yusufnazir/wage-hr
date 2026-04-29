# Module: Superadmin tenant lens (platform operator)

**Feature slug:** `superadmin-tenant-lens`  
**Related:** [`tenancy-routing.md`](./tenancy-routing.md), [`platform-tenant-admin.md`](./platform-tenant-admin.md), [`security.md`](./security.md), [`audit.md`](./audit.md)

Platform **superadmins** are not tied to a single tenant. In the browser they **use the reserved `admin.{baseDomain}` host** (not tenant subdomains) for the operator workspace. They open a **tenant lens** (`X-Tenant-Id` via BFF cookie) so normal tenant-scoped APIs and UI run **without** a `membership` row for that tenant. Elevation follows the same **privilege catalog + break-glass** rules as today; **non-member** elevation additionally respects the tenant **privilege pool ceiling** (allowances + subscription-derived privileges) so behavior matches what the tenant could ever assign.

---

## Host and `X-Tenant-Id`

| Host mode | `X-Tenant-Id` behavior |
|-----------|-------------------------|
| **API** (`api.{base}`) | Optional header resolves tenant when valid UUID and tenant row exists. |
| **ADMIN** (`admin.{base}`) | **Primary web origin** for operators: no tenant from host; optional **`X-Tenant-Id`** (BFF lens cookie) sets tenant context. |
| **APP** (`app.{base}`) | Same optional-header behavior as API (tools); product UI should send operators to **ADMIN** host. |
| **TENANT** (`{handle}.{base}`) | Tenant from handle first; **`X-Tenant-Id`** when present and valid **overrides** host (API / edge cases). |
| **AUTH** | No tenant from header in v1. |

Invalid UUID / unknown id: same problem+json codes as [`tenancy-routing.md`](./tenancy-routing.md) (`INVALID_TENANT_ID_HEADER`, `UNKNOWN_TENANT_ID`).

---

## Authorization (`PermissionService`)

1. **Membership present:** Unchanged: role pool + `effectivePoolContains`; if not granted and user is platform superadmin and privilege exists in global catalog → `SUPERADMIN_ELEVATED` (no pool check on this path — existing product behavior).
2. **No membership:** If user is **not** platform superadmin → `AccessDeniedException("No membership for tenant")`. If **platform superadmin** → grant `SUPERADMIN_ELEVATED` only when privilege exists in catalog **and** `effectivePoolContains(tenant, privilege)`; else deny (`PrivilegeGrant.DENIED` → aspect → missing privilege).
3. **`effectivePrivilegeCodes` (e.g. `GET /me`, navigation):** With membership → unchanged. Without membership, if platform superadmin → return `tenantPoolPrivilegeCodes(tenantId)` (pool + subscription ceiling, sorted). Otherwise throw `No membership for tenant`.

`@RequiresPrivilege` / `PrivilegeAuthorizationAspect`: unchanged break-glass + `SUPERADMIN_TENANT_ELEVATED_ACCESS` audit.

---

## Cross-tenant header safety

A signed-in user who is **not** a platform superadmin must **not** use `X-Tenant-Id` to access a tenant they do not belong to: `PermissionService` throws `No membership for tenant` before any tenant data is returned. A member sending a header for a **different** tenant than their resolved context gets the same denial when hitting `@RequiresPrivilege` tenant routes (context tenant ≠ membership).

---

## `GET /me/navigation` without tenant context

If there is **no** `tenant_id` in `TenantContext` but the principal is a **platform superadmin**, return **only** synthetic platform nav entries (`nav.platform_tenants`, `nav.platform_settings`) so the app shell loads on `app.{base}` before a lens is chosen.

---

## Web BFF: lens cookie

- **Cookie name:** `wp_lens_tenant` (HttpOnly, `SameSite=Lax`, `Path=/`).
- **Value:** UUID string of the selected tenant, or cleared when lens cleared.
- **Forwarding:** Next BFF (`/api/bff/...`) reads the cookie and sets **`X-Tenant-Id`** on upstream Spring requests **except** paths under `/api/v1/platform/` and `/api/v1/auth/` (avoid coupling platform ops to an accidental tenant context).

**Set / clear:** `POST /api/bff/lens-tenant` with JSON `{ "tenantId": "<uuid>" }` or `{ "clear": true }` — validates UUID shape when setting; does not prove membership (Spring enforces).

---

## Web UX

- **Host:** Tenant lens UI is shown only on **`admin.{base}`**; post-login and in-app redirect send `platformSuperadmin` users there. **Tenant filter:** Shown for `me.platformSuperadmin` on tenant-scoped app routes under that host (hide on `/app/platform-tenants`, `/app/platform-settings`).
- **Banner:** When `platformSuperadmin` and `me.tenantHandle` is set (lens active), show “Viewing **{name/handle}** as platform operator” with **Clear lens** (calls clear endpoint + reload shell).
- **Data source for picker:** `GET /api/v1/platform/tenants` (first page / size as needed) or existing detail flows.

---

## Mobile

Out of scope for v1 (mirror later: stored tenant id + `X-Tenant-Id` on API client if product adds operator mobile).

---

## Acceptance criteria

1. Platform superadmin **without** membership in tenant **T** may `GET` a tenant route requiring privilege **P** when `P` is in **T**’s effective pool and `TenantContext` is **T** (e.g. `admin.{base}` + `X-Tenant-Id`), and receives **200**; read elevation is audited when the aspect applies.
2. Same user on **P** **not** in **T**’s pool receives **403** (missing privilege), not membership error.
3. Mutating calls still require break-glass headers when elevation applies; audit metadata unchanged.
4. Non–platform-superadmin cannot use `X-Tenant-Id` to access a tenant they are not a member of → **403** `No membership for tenant` on tenant-privileged routes.
5. `GET /me` with lens returns `privileges` / `planFeatureCodes` consistent with pool ceiling for non-member superadmin.
6. BFF does not attach `X-Tenant-Id` for `/api/v1/platform/**` or `/api/v1/auth/**` upstream calls.
7. `GET /me/navigation` on **admin** host **without** tenant returns **200** for platform superadmin with only platform items.

---

## Implementation pointers

- Backend: `SubdomainParser` / `HostMode.ADMIN`, `TenantContextFilter`, `AdminHostPlatformOperatorFilter`, `PermissionService`, `NavigationController`, `MeController` (`tenantId` in `GET /me` when tenant context is present), tests under `SuperadminTenantLensIT`, `AdminHostIT`.
- Frontend: `frontend/src/app/api/bff/[...path]/route.ts`, `frontend/src/app/api/bff/lens-tenant/route.ts`, `frontend/src/lib/server/lens-tenant-cookie.ts`, `SuperadminTenantLensBar` + `TenantAppShell` wiring, `api.ts` helpers.
