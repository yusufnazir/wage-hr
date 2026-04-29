# Verification — platform tenant admin

**Module:** `docs/modules/platform-tenant-admin.md`  
**Feature slug:** `platform-tenant-admin`

## Automated

| Area | Command | Notes |
|------|---------|--------|
| Backend | `cd backend && ./mvnw.cmd test -Dtest=PlatformTenantRegistryIT,NavigationAndSettingsIT,MeTenantsIT` | Registry + navigation counts after synthetic nav item. |
| Full backend | `cd backend && ./mvnw.cmd test` | Regression suite. |
| Frontend | `cd frontend && npm run lint` && `npm run build` | |

## Manual smoke

Prerequisites: API **8300**, Next **3007**, BFF env; user with **`platform_superadmin`** (e.g. seeded admin).

1. Open tenant host (e.g. `http://demo.lvh.me:3007/app`), sign in as platform operator.
2. Sidebar shows **Tenants** (before Platform settings); open **`/app/platform-tenants`** — table lists **acme** and **demo** (sorted by handle).
3. **Create:** handle `newco`, name `New Co` → redirects to editor; list shows **newco** after **Back to list**.
4. **Edit:** change display name, **Save** → success message; reload confirms persistence.
5. **403:** sign in as non-operator viewer — **Tenants** not in nav (or direct URL shows operator-only copy).
6. **API:** `POST` duplicate handle **409**; reserved handle **auth** → **400**.
