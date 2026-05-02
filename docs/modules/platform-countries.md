# Module: Platform Countries

**Feature slug:** `platform-countries`
**Milestone:** M5
**Related:** [`platform-settings.md`](./platform-settings.md) (operator role pattern), [`platform-tenant-admin.md`](./platform-tenant-admin.md) (platform-admin UI pattern), [`payroll-reference-data.md`](./payroll-reference-data.md) (countries stub → replaced by this doc), [`i18n.md`](./i18n.md) (locale resolution), [`security.md`](./security.md) (`platform_superadmin`)

---

## 1. Objective

Provide a **platform-managed global catalog of countries** (ISO 3166-1), seeded with all standard countries and their translations, so that downstream modules (employee records, payroll adapters, address fields) can reference a single authoritative list rather than hardcoding values.

SuperAdmin manages the catalog (activate, deactivate, edit). All authenticated users may read active countries for use in UI selectors.

---

## 2. Scope

**Included:**
- Full CRUD for `platform_country` records, restricted to platform superadmin
- Activate / deactivate flag (soft-disable; no hard delete once in use)
- Per-country translations in `en` and `nl` (stored in `platform_country_translation`)
- ISO 3166-1 fields: alpha-2, alpha-3, numeric code, phone dial code
- Liquibase DDL changesets (tables + indexes)
- Liquibase seed DML changeset: all ISO 3166-1 countries with `en` + `nl` translations
- Superadmin web UI: list (paginated, searchable, active/all toggle) + create/edit forms
- Tenant read-only API endpoint: active countries for selectors (authenticated, no special privilege)
- Audit logging (create / update / activate / deactivate)
- i18n: UI chrome uses message keys; country names served in caller's locale

**Excluded:**
- Mobile (web + backend only for this feature)
- Tenant-level country activation (a tenant marking which countries they operate in) — out of scope; natural extension for a later module
- Flag images or emoji rendering — out of scope
- Currency linkage — owned by `payroll-reference-data.md` (currency feature)
- Automatic ISO feed refresh / sync from external provider

---

## 3. Actors

| Actor | Access | Capability |
|---|---|---|
| Platform SuperAdmin | `platform_superadmin = true` | Full CRUD, activate/deactivate, view all (active + inactive) |
| Authenticated User (any tenant) | Any valid session | Read active countries list (for selectors) |
| Unauthenticated | — | No access |

---

## 4. User Flows

### 4.1 SuperAdmin — List Countries

1. SuperAdmin navigates to **Platform → Countries** in the platform admin sidebar.
2. System displays a paginated table of all countries (active and inactive), sorted alphabetically by name in the current locale (`en` or `nl`).
3. A filter toggle ("Active only / All") defaults to **All** for the superadmin view.
4. A search field filters by country name (in current locale) or ISO alpha-2 code.
5. Each row shows: flag column (active indicator), name (in current locale), alpha-2, alpha-3, numeric, dial code, active status badge, actions (Edit, Activate/Deactivate).

### 4.2 SuperAdmin — Create Country

1. SuperAdmin clicks **+ Add Country** on the list page.
2. A form opens (or modal) with fields: alpha-2 (unique), alpha-3 (unique), numeric, dial code, name (en), name (nl), active (default: true).
3. On submit, system validates uniqueness of alpha-2 and alpha-3.
4. On success: country appears in list; audit event recorded.
5. On error: inline field-level errors shown.

### 4.3 SuperAdmin — Edit Country

1. SuperAdmin clicks **Edit** on a country row.
2. Form pre-populates with current values. alpha-2 and alpha-3 are **editable** (only if not referenced by other records; see Business Rules).
3. SuperAdmin changes name translations, dial code, numeric code, or active flag and saves.
4. On success: list refreshes; audit event recorded.

### 4.4 SuperAdmin — Deactivate / Activate Country

1. SuperAdmin clicks **Deactivate** on an active country row (or **Activate** on an inactive row).
2. Confirmation dialog: "Deactivate [Country Name]? It will be hidden from tenant selectors." / "Activate [Country Name]?"
3. On confirm: `active` flag toggled; audit event recorded.
4. Deactivated countries remain visible in the superadmin list with an "Inactive" badge.
5. Deactivated countries are **hidden** from the tenant read-only endpoint.

### 4.5 Tenant User — Country Selector (read)

1. Any authenticated user calls `GET /api/v1/countries` (e.g., when filling an employee address form).
2. System returns paginated list of **active** countries only, in the locale specified by the query param (defaulting to `en`).
3. If the locale has no translation for a country, the `en` name is returned as fallback.

---

## 5. Data Model

### Table: `platform_country` (strict)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Generated |
| `iso_alpha2` | VARCHAR(2) | NOT NULL, UNIQUE | ISO 3166-1 alpha-2 (e.g. `NL`). Stored uppercase. |
| `iso_alpha3` | VARCHAR(3) | NOT NULL, UNIQUE | ISO 3166-1 alpha-3 (e.g. `NLD`). Stored uppercase. |
| `iso_numeric` | VARCHAR(3) | NOT NULL | ISO 3166-1 numeric (e.g. `528`). Zero-padded string. |
| `dial_code` | VARCHAR(15) | NULLABLE | E.164 dial code prefix (e.g. `+31`). NULL if unknown. |
| `active` | BOOLEAN | NOT NULL, DEFAULT true | When false: hidden from tenant selectors; visible in superadmin list only. |
| `created_at` | TIMESTAMP | NOT NULL | Set on insert. |
| `updated_at` | TIMESTAMP | NOT NULL | Set on insert and update. |

**Indexes:** unique on `iso_alpha2`, unique on `iso_alpha3`; index on `active`.

> **No PII** — this table contains only ISO reference data.

---

### Table: `platform_country_translation` (strict)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Generated |
| `country_id` | UUID | NOT NULL, FK → `platform_country.id` ON DELETE CASCADE | |
| `locale` | VARCHAR(35) | NOT NULL | BCP 47 tag — e.g. `en`, `nl` |
| `name` | VARCHAR(100) | NOT NULL | Country name in this locale |

**Unique constraint:** `(country_id, locale)`

> **No PII.**

**Supported locales in seed:** `en`, `nl`.

**Fallback rule:** if a translation for the requested locale does not exist, fall back to `en`.

---

## 6. States & Transitions

```
active = true  ──[deactivate]──►  active = false
active = false ──[activate]────►  active = true
```

- Countries **cannot be hard-deleted** once created (referential integrity with future employee/payroll tables). The only removal mechanism is deactivation.
- The seed countries are all created with `active = true`.

---

## 7. Business Rules

| # | Rule |
|---|------|
| BR-1 | `iso_alpha2` must be unique across all countries (case-insensitive; stored uppercase). Duplicate → **409 COUNTRY_ALPHA2_EXISTS**. |
| BR-2 | `iso_alpha3` must be unique across all countries (case-insensitive; stored uppercase). Duplicate → **409 COUNTRY_ALPHA3_EXISTS**. |
| BR-3 | `iso_alpha2` must be exactly 2 uppercase letters `[A-Z]{2}`. |
| BR-4 | `iso_alpha3` must be exactly 3 uppercase letters `[A-Z]{3}`. |
| BR-5 | `iso_numeric` must be a 1–3 digit string (zero-padded to 3 is preferred but not enforced). |
| BR-6 | `dial_code` if provided must match pattern `^\+[1-9]\d{0,14}$`. |
| BR-7 | Translations for `en` and `nl` are **required** on create and edit (both locales must be provided; neither name may be blank). |
| BR-8 | `name` per locale must be between 1 and 100 characters (trimmed). |
| BR-9 | Deactivate/Activate are **atomic** state changes — no partial update. |
| BR-10 | If editing `iso_alpha2` or `iso_alpha3`, system must ensure no other table already references the old code as a string FK (if applicable). For M5, no downstream FK exists yet — editing is allowed. When downstream FKs are introduced, codes become immutable (document at that time). |
| BR-11 | The tenant-facing `GET /api/v1/countries` endpoint returns **only active** countries regardless of caller's role. The superadmin platform endpoint returns all (active + inactive). |
| BR-12 | `locale` param on `GET /api/v1/countries` must be one of the supported locales (`en`, `nl`). Unknown locale → **400 UNSUPPORTED_LOCALE**. Default: `en`. |
| BR-13 | Platform superadmin check: `platform_superadmin = true` on caller's `user_account`. **403** if not (matching pattern from `platform-settings.md`). |

---

## 8. Edge Cases

| Case | Handling |
|---|---|
| Duplicate alpha-2 on create | 409 `COUNTRY_ALPHA2_EXISTS` with field detail |
| Duplicate alpha-2 on edit (changed to conflict) | 409 `COUNTRY_ALPHA2_EXISTS` |
| Missing `en` or `nl` translation on create/edit | 400 — both locales required |
| Deactivating an already-inactive country | 409 `COUNTRY_ALREADY_INACTIVE` (idempotent alternative: return 200 no-op — choose one; recommended: 409 for clarity) |
| Activating an already-active country | 409 `COUNTRY_ALREADY_ACTIVE` |
| Unknown `id` on GET/PUT/PATCH | 404 `COUNTRY_NOT_FOUND` |
| `locale` fallback: `nl-sr` requested but only `en`/`nl` exist | Return `nl` translation (closest match) if available; else `en`. For M5, only `en` and `nl` are seeded — `nl-sr` requests fall back to `nl`. |
| Seed already applied (re-run Liquibase) | Changeset is idempotent; author includes `onFail="MARK_RAN"` or uses `preconditions` to skip if rows exist. |
| Very large list (250 countries) | Pagination enforced: default `size=50`, max 100; total returned via `totalElements`. |
| Search with empty string | Returns full paginated list (no filtering applied). |

---

## 9. UX Considerations

- **List page:** table columns — Name (in current locale), Alpha-2, Alpha-3, Numeric, Dial Code, Status badge (Active / Inactive), Actions.
- **Search:** client-side debounce (300 ms) → server-side `?search=` param filtering by name (locale-aware) OR alpha-2.
- **Active/All toggle:** filter chip above table; default "All" for superadmin (they manage both states).
- **Deactivate/Activate confirmation:** modal with country name to prevent accidental bulk actions.
- **Create form:** all fields on one form. Alpha-2, Alpha-3, Numeric, Dial Code in one row; Name EN and Name NL below. Active toggle defaults to true.
- **Edit form:** same layout as create. Alpha-2 and Alpha-3 are editable for M5 (note future lock when downstream FKs exist).
- **Error feedback:** field-level inline errors for validation failures; toast/banner for server errors.
- **i18n (UI chrome):** all labels, buttons, page title, column headers, and helper text use message keys (e.g. `platformCountries.title`, `platformCountries.search`, `platformCountries.column.name`, etc.) — no hardcoded English strings in components.
- **Country name display:** rendered in the **user's current locale** (`preferred_locale`); falls back to `en`.
- **Navigation:** synthetic nav item `nav.platform_countries` appears in platform sidebar when `me.platformSuperadmin === true`, between Platform Tenants and Platform Settings (sort order **30**).

---

## 10. Open Questions

All resolved before finalization:

| # | Question | Resolution |
|---|---|---|
| OQ-1 | Mobile scope? | **Excluded** — web + backend only. |
| OQ-2 | Tenant-level country activation? | **Out of scope** — natural extension for a later sub-feature. |
| OQ-3 | Hard delete? | **Disallowed** — deactivation only (referential safety). |
| OQ-4 | Tenant read needs privilege? | **No** — authenticated only; countries are non-sensitive reference data. |
| OQ-5 | Locale fallback for `nl-sr`? | Fall back to `nl` if available, else `en`. |
| OQ-6 | Are alpha-2 / alpha-3 editable? | **Yes for M5** (no downstream FKs yet). When downstream FKs exist, codes become immutable. |

---

## 11. Acceptance Criteria

| ID | Criterion |
|---|---|
| AC-1 | Liquibase DDL creates `platform_country` and `platform_country_translation` tables with all specified columns, constraints, and indexes. |
| AC-2 | Liquibase seed DML inserts all ISO 3166-1 countries with correct alpha-2, alpha-3, numeric, and dial code, plus `en` and `nl` name translations. |
| AC-3 | `GET /api/v1/platform/countries` returns paginated list of all countries (active + inactive) to superadmin; 403 for non-superadmin. |
| AC-4 | `POST /api/v1/platform/countries` creates a country; returns 201; validates uniqueness (409 on duplicate alpha-2 or alpha-3); 403 for non-superadmin. |
| AC-5 | `GET /api/v1/platform/countries/{id}` returns single country with translations; 404 on unknown id; 403 for non-superadmin. |
| AC-6 | `PUT /api/v1/platform/countries/{id}` updates country and translations; requires both `en` and `nl` names; 400 on validation failure; 403 for non-superadmin. |
| AC-7 | `PATCH /api/v1/platform/countries/{id}/activate` and `.../deactivate` toggle the `active` flag; 409 on no-op state; audit event recorded; 403 for non-superadmin. |
| AC-8 | `GET /api/v1/countries` returns **only active** countries; requires authentication (401 if unauthenticated); no special privilege required. |
| AC-9 | `GET /api/v1/countries?locale=nl` returns names in Dutch; `?locale=en` returns English; unsupported locale → 400. |
| AC-10 | Deactivated countries are absent from `GET /api/v1/countries` but present in `GET /api/v1/platform/countries`. |
| AC-11 | Superadmin web list page renders at `/app/platform-countries`; shows paginated table with search and active/all toggle; visible only to superadmin. |
| AC-12 | Create and Edit forms validate all BR rules client-side and display server-side errors inline. |
| AC-13 | Deactivate/Activate triggers a confirmation modal before calling the API. |
| AC-14 | All UI chrome strings use message keys — no hardcoded English in components. |
| AC-15 | Country names in the list render in the user's current locale (`preferred_locale`), falling back to `en`. |
| AC-16 | Audit events are recorded for: country created, country updated, country activated, country deactivated. |
| AC-17 | Navigation item `nav.platform_countries` appears in platform sidebar iff `me.platformSuperadmin === true`. |
| AC-18 | Backend unit tests cover: create, update, activate/deactivate, list (active only vs all), locale fallback, all validation rules. |
| AC-19 | Seed is idempotent — re-running Liquibase does not insert duplicate rows. |

---

## API

All platform routes: **authenticated**; **no** `TenantContext` required. `PlatformOperatorService.requirePlatformSuperadmin()`.

Responses follow `ApiResponse` / `ProblemDetail` conventions (`docs/guides/API-CONVENTIONS.md`).

### Superadmin — Platform endpoints

| Method | Path | Query / Body | Success | Errors |
|---|---|---|---|---|
| `GET` | `/api/v1/platform/countries` | `page` (0-based, default 0), `size` (default 50, max 100), `search` (optional name or alpha-2), `active` (optional boolean filter) | **200** `data`: `{ items: PlatformCountryRow[], totalElements, page, size, totalPages }` | **403** |
| `POST` | `/api/v1/platform/countries` | `{ iso_alpha2, iso_alpha3, iso_numeric, dial_code?, translations: [{locale, name}] }` | **201** `data.country` | **400** validation; **409** duplicate code; **403** |
| `GET` | `/api/v1/platform/countries/{id}` | — | **200** `data.country` (with `translations[]`) | **404**; **403** |
| `PUT` | `/api/v1/platform/countries/{id}` | Same shape as POST body | **200** `data.country` | **400**; **409**; **404**; **403** |
| `PATCH` | `/api/v1/platform/countries/{id}/activate` | — | **200** `data.country` | **409 COUNTRY_ALREADY_ACTIVE**; **404**; **403** |
| `PATCH` | `/api/v1/platform/countries/{id}/deactivate` | — | **200** `data.country` | **409 COUNTRY_ALREADY_INACTIVE**; **404**; **403** |

### Tenant / Authenticated — Read-only endpoint

| Method | Path | Query | Success | Errors |
|---|---|---|---|---|
| `GET` | `/api/v1/countries` | `page` (default 0), `size` (default 50, max 100), `search` (optional), `locale` (default `en`) | **200** `data`: `{ items: CountryRow[], totalElements, page, size, totalPages }` | **400 UNSUPPORTED_LOCALE**; **401** |

**`CountryRow`:** `{ id, iso_alpha2, iso_alpha3, iso_numeric, dial_code, name }` (name resolved per `locale`).

**`PlatformCountryRow`:** same as `CountryRow` plus `active` flag and full `translations[]` array.

---

## Audit

| `action_code` | When |
|---|---|
| `PLATFORM_COUNTRY_CREATED` | After successful POST (metadata: `iso_alpha2`) |
| `PLATFORM_COUNTRY_UPDATED` | After successful PUT (metadata: `iso_alpha2`, `fields` changed) |
| `PLATFORM_COUNTRY_ACTIVATED` | After successful PATCH .../activate |
| `PLATFORM_COUNTRY_DEACTIVATED` | After successful PATCH .../deactivate |

`resource_type`: `PLATFORM_COUNTRY`, `resource_id`: country UUID, `tenant_id`: null (platform-level event).

---

## Web (Next.js)

- **`/app/platform-countries`** — list page: paginated table, search bar, active/all filter chip, "+ Add Country" button. Superadmin only.
- **`/app/platform-countries/new`** — create form.
- **`/app/platform-countries/[id]`** — edit form (pre-populated); activate/deactivate action button.
- **API client functions:** `fetchPlatformCountries`, `fetchPlatformCountry`, `postPlatformCountry`, `putPlatformCountry`, `patchActivateCountry`, `patchDeactivateCountry`, `fetchCountries` (tenant read).
- **Nav:** synthetic item `nav.platform_countries` when `me.platformSuperadmin` (sort order **30**, between Platform Tenants and Platform Settings).
- **i18n message keys:** `frontend/src/messages/platformCountries.ts` — keys cover `title`, `search`, `addButton`, `column.*`, `form.*`, `confirmDeactivate`, `confirmActivate`, etc.

## Mobile (Flutter)

Out of scope for this feature.

---

## Proposed Schema Extension (requires PII review)

- **Table / entity:** `platform_country`
- **Proposed column(s):** `region` (VARCHAR, e.g. "Europe"), `sub_region` (VARCHAR, e.g. "Western Europe") — UN M.49 classification
- **Justification:** Useful for grouped selectors (e.g., "Select region first, then country"). Not needed for M5.
- **PII classification:** none
- **Retention / deletion / anonymization impact:** none
- **Liquibase changeset id (when approved):** TBD
