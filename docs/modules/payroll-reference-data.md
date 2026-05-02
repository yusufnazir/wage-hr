# Module: Payroll Reference Data

Tenant-scoped reference tables that underpin payroll calculations. This module owns the **exchange rates** sub-feature (M5). Other sub-features (countries, business units, BU-scoped roles) are stubs — expand them when their sprint begins.

---

## Sub-feature: Exchange Rates

### Objective

Allow tenant admins to define and maintain dated exchange rates between their activated currencies, and expose a resolve endpoint so payroll-engine modules can retrieve the correct rate for any given calculation date.

### Scope

**Included:**
- Full CRUD for exchange rate records scoped to the tenant
- Rate resolution endpoint (latest rate ≤ a given date) for internal payroll engine use
- Web UI under Tenant App → Settings → Currencies → **Exchange Rates tab**
- Privilege enforcement (`EXCHANGE_RATE_VIEW`, `EXCHANGE_RATE_MANAGE`)
- Audit logging (create / update / delete)
- Liquibase DDL changeset + privilege seed DML changeset

**Excluded:**
- Mobile (web-only for this milestone)
- Commercial plan gating — privilege-only access control
- Automatic rate fetching from external providers
- Derived/inverse rate calculation
- Cross-tenant rate sharing

---

### Actors

| Actor | Privilege | Capability |
|---|---|---|
| Tenant Admin | `EXCHANGE_RATE_MANAGE` | Create, edit, delete exchange rates |
| Tenant Staff | `EXCHANGE_RATE_VIEW` | View the exchange rate list and single records |
| Payroll Engine (internal service call) | `EXCHANGE_RATE_VIEW` | Call the resolve endpoint |
| SuperAdmin | All (via same enforcement path — no bypass) | Full access |

---

### Data — `tenant_exchange_rate` (strict)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Generated |
| `tenant_id` | UUID | NOT NULL, FK → `tenant.id` | Set from `TenantContext` |
| `from_currency_id` | UUID | NOT NULL, FK → `platform_currency.id` | Must appear in tenant's `tenant_currency` list |
| `to_currency_id` | UUID | NOT NULL, FK → `platform_currency.id` | Must appear in tenant's `tenant_currency` list |
| `rate` | DECIMAL(18,8) | NOT NULL | Conversion multiplier: 1 `from` unit = `rate` `to` units; must be > 0 |
| `effective_date` | DATE | NOT NULL | Date this rate becomes effective; past and future dates are both valid |
| `created_at` | TIMESTAMP | NOT NULL | Set on insert |
| `updated_at` | TIMESTAMP | NOT NULL | Set on insert and update |

**Unique constraint:** `(tenant_id, from_currency_id, to_currency_id, effective_date)`

`from_currency_id` ≠ `to_currency_id` is enforced at the application layer (400 if violated).

> **No PII** — this table contains only financial reference data.

---

### Privileges

| Code | Purpose |
|---|---|
| `EXCHANGE_RATE_VIEW` | Read access: list, single record, and resolve endpoints |
| `EXCHANGE_RATE_MANAGE` | Write access: create, update, delete (logically implies VIEW) |

Both privileges must be registered in the **global privilege catalog** via a Liquibase DML task so SuperAdmin inherits them automatically. Tenant admins may then expose either or both to their tenant's roles.

---

### API

All routes are tenant-scoped. `TenantContext` is resolved from the subdomain before the request reaches the service layer (see `tenancy-routing.md`). All responses follow `ApiResponse` / `ProblemDetail` conventions (see `docs/guides/API-CONVENTIONS.md`).

#### List exchange rates

```
GET /api/v1/tenant/exchange-rates
@RequiresPrivilege("EXCHANGE_RATE_VIEW")
Query params (all optional):
  page     integer  default 0
  size     integer  default 20, max 100
  sort     string   default "effectiveDate,desc"

Response 200:
{
  "data": [
    {
      "id": "uuid",
      "fromCurrencyId": "uuid",
      "fromCurrencyCode": "USD",
      "fromCurrencyDisplayName": "US Dollar",
      "toCurrencyId": "uuid",
      "toCurrencyCode": "EUR",
      "toCurrencyDisplayName": "Euro",
      "rate": "0.92500000",
      "effectiveDate": "2026-05-01",
      "createdAt": "2026-04-30T10:30:00Z",
      "updatedAt": "2026-04-30T10:30:00Z"
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 5, "totalPages": 1 }
}
```

#### Get single exchange rate

```
GET /api/v1/tenant/exchange-rates/{id}
@RequiresPrivilege("EXCHANGE_RATE_VIEW")

Response 200: single item (same shape as list item)
Response 404: id not found or belongs to a different tenant
```

#### Create exchange rate

```
POST /api/v1/tenant/exchange-rates
@RequiresPrivilege("EXCHANGE_RATE_MANAGE")
Content-Type: application/json

Body:
{
  "fromCurrencyId": "uuid",
  "toCurrencyId": "uuid",
  "rate": "0.92500000",
  "effectiveDate": "2026-05-01"
}

Response 201: created item (full shape)
Response 400: from = to, rate ≤ 0, or required field missing
Response 409: duplicate (fromCurrencyId, toCurrencyId, effectiveDate) for this tenant
Response 422: one or both currencies not in tenant's activated currency list
```

#### Update exchange rate (PATCH)

```
PATCH /api/v1/tenant/exchange-rates/{id}
@RequiresPrivilege("EXCHANGE_RATE_MANAGE")
Content-Type: application/json

Mutable fields: rate, effectiveDate
Immutable fields: fromCurrencyId, toCurrencyId (return 400 if client sends these)

Body (example — one or both fields):
{
  "rate": "0.93000000",
  "effectiveDate": "2026-06-01"
}

Response 200: updated item (full shape)
Response 400: rate ≤ 0, or client attempted to change immutable currency fields
Response 404: id not found
Response 409: the new effectiveDate creates a duplicate for the same currency pair
```

#### Delete exchange rate

```
DELETE /api/v1/tenant/exchange-rates/{id}
@RequiresPrivilege("EXCHANGE_RATE_MANAGE")

Response 204: deleted
Response 404: id not found
```

#### Resolve (payroll engine / internal)

```
GET /api/v1/tenant/exchange-rates/resolve?from={ISO-3}&to={ISO-3}&date={YYYY-MM-DD}
@RequiresPrivilege("EXCHANGE_RATE_VIEW")

Semantics: returns the record with MAX(effectiveDate) WHERE effectiveDate ≤ :date
           for the given (from, to) currency pair within the tenant.

Query params:
  from   string  ISO 4217 3-letter code (e.g. "USD"); must be in tenant's activated list
  to     string  ISO 4217 3-letter code (e.g. "EUR"); must be in tenant's activated list
  date   string  ISO 8601 date "YYYY-MM-DD"

Response 200:
{
  "fromCurrencyCode": "USD",
  "toCurrencyCode": "EUR",
  "rate": "0.92500000",
  "effectiveDate": "2026-05-01"
}

Response 400: from = to, or currency code invalid / not in tenant's list
Response 404: no rate exists for this pair on or before the requested date
```

---

### Business Rules

1. `fromCurrencyId` ≠ `toCurrencyId` — enforced on create; return **400**.
2. Both `fromCurrencyId` and `toCurrencyId` must appear in the tenant's `tenant_currency` list (i.e., linked to an active `platform_currency`) — return **422** if violated.
3. No duplicate `(tenant_id, fromCurrencyId, toCurrencyId, effectiveDate)` — return **409** if duplicate.
4. `rate` must be > 0 (BigDecimal comparison) — return **400** for zero or negative values.
5. `effectiveDate` is required on create. Past and future dates are equally valid — no restriction.
6. On PATCH, `fromCurrencyId` and `toCurrencyId` are immutable. If the client sends either field, return **400** with a clear error message.
7. Resolve query uses `MAX(effectiveDate) WHERE effectiveDate ≤ :date` — if no such record exists, return **404**.
8. Deletion is unrestricted for now. Payroll-engine reference protection (block delete if used by a payroll run) is deferred until the payroll engine module is scoped.

---

### States & Transitions

Exchange rates have no lifecycle state. A record either exists or has been deleted. Historical records (past `effectiveDate`) are visible in the list and editable, subject to the business rules above.

---

### Edge Cases

| Scenario | Expected behaviour |
|---|---|
| `from` = `to` on create | 400 Bad Request |
| Currency not in tenant's activated list | 422 Unprocessable Entity |
| Duplicate `(from, to, effectiveDate)` on create | 409 Conflict |
| Duplicate `(from, to, effectiveDate)` on PATCH (new date collides) | 409 Conflict |
| `rate` = 0 or negative | 400 Bad Request |
| Attempt to PATCH `fromCurrencyId` or `toCurrencyId` | 400 Bad Request |
| Resolve: no rate on or before requested date | 404 Not Found |
| Resolve: exact date match exists | Return that record |
| Resolve: multiple past rates exist | Return the one with the latest `effectiveDate` ≤ date |
| Tenant has only one activated currency | Natural state — no valid pair exists; no error, empty list |
| Very large rates (e.g., VND/USD ≈ 25 000) | DECIMAL(18,8) handles this; no application-level upper cap |
| SuperAdmin accessing the endpoints | Same privilege check as every other user — no bypass |
| Concurrent create of the same (from, to, date) | Unique constraint on DB catches the race; 409 returned |

---

### Audit

All write operations append a record to `audit_event` via `AuditService`.

| Operation | Audit action code | Metadata fields |
|---|---|---|
| Create | `EXCHANGE_RATE_CREATED` | `fromCurrencyCode`, `toCurrencyCode`, `rate`, `effectiveDate` |
| Update (PATCH) | `EXCHANGE_RATE_UPDATED` | `id`, changed fields only (`rate` and/or `effectiveDate`, old + new values) |
| Delete | `EXCHANGE_RATE_DELETED` | `fromCurrencyCode`, `toCurrencyCode`, `effectiveDate` |

No PII is stored in audit metadata — all fields are financial reference data.

---

### Web Flows

**Location:** Tenant App → Settings → Currencies (existing page) → **Exchange Rates tab** (new tab alongside existing currency tabs)

#### List view
- Table columns: **From** | **To** | **Rate** | **Effective Date** | **Actions**
- Default sort: `effectiveDate DESC`
- Pagination controls (page size 20)
- **"New Exchange Rate"** button — visible and enabled only for users with `EXCHANGE_RATE_MANAGE`
- Empty state: "No exchange rates configured yet." + call-to-action button (if MANAGE)

#### Create modal
- **From Currency** — dropdown populated from tenant's activated currencies
- **To Currency** — same source; the selected `from` value is removed from `to` options
- **Rate** — decimal input; placeholder "e.g. 0.92500000"; up to 8 decimal places
- **Effective Date** — date picker; defaults to today
- Submit → `POST /api/v1/tenant/exchange-rates`
  - 201 → close modal, show success toast, refresh list
  - 409 → inline error: "A rate for this currency pair on this date already exists."
  - 422 → inline error: "One or more selected currencies are not active for this tenant."
  - 400 → inline error per field

#### Edit modal
- Opens pre-filled with existing values
- **From Currency** and **To Currency** are displayed as read-only labels (not inputs)
- **Rate** and **Effective Date** are editable (same validation as create)
- Submit → `PATCH /api/v1/tenant/exchange-rates/{id}`
  - 200 → close modal, success toast, refresh list
  - 409 → inline error: "A rate for this currency pair on this date already exists."
  - 404 → "This record no longer exists — refresh the page."

#### Delete
- **Delete** action per row (visible only with `EXCHANGE_RATE_MANAGE`)
- Confirmation dialog: *"Delete exchange rate USD → EUR effective 2026-05-01? This cannot be undone."*
- On confirm → `DELETE /api/v1/tenant/exchange-rates/{id}`
  - 204 → success toast, remove row from list
  - 404 → "This record no longer exists — refresh the page."

#### Permission-based UI behaviour
| Privilege | Visible controls |
|---|---|
| `EXCHANGE_RATE_VIEW` only | Table + read-only list; no Create / Edit / Delete controls |
| `EXCHANGE_RATE_MANAGE` | Full controls visible and enabled |

The backend enforces privileges regardless of UI state — the frontend gates are presentation only.

---

### Liquibase

Follow `docs/guides/LIQUIBASE-RULES.md` for all changesets.

**DDL — Java task `DdlExchangeRateTable1`:**
- Create `tenant_exchange_rate` with all columns listed in the Data section
- Add unique index: `uidx_tenant_exchange_rate_pair_date` on `(tenant_id, from_currency_id, to_currency_id, effective_date)`
- FK constraint from `from_currency_id` → `platform_currency.id`
- FK constraint from `to_currency_id` → `platform_currency.id`
- Index on `(tenant_id)` for tenant-scoped queries

**DML — Java task `DataExchangeRatePrivileges1`:**
- Insert `EXCHANGE_RATE_VIEW` into `privilege` (global pool, category `payroll_reference`)
- Insert `EXCHANGE_RATE_MANAGE` into `privilege` (global pool, category `payroll_reference`)

---

### Acceptance Criteria

1. **Create — happy path:** Tenant admin with `EXCHANGE_RATE_MANAGE` creates a valid rate → 201 returned, record persisted, `EXCHANGE_RATE_CREATED` audit event recorded.
2. **Create — same currency:** `fromCurrencyId` = `toCurrencyId` → 400.
3. **Create — inactive currency:** Either currency not in tenant's `tenant_currency` list → 422.
4. **Create — duplicate:** Same `(from, to, effectiveDate)` for the same tenant → 409.
5. **Create — invalid rate:** `rate` = 0 or negative → 400.
6. **List:** Tenant admin with `EXCHANGE_RATE_VIEW` retrieves a paginated, `effectiveDate DESC`-sorted list.
7. **Update — happy path:** `rate` and `effectiveDate` updated → 200, `EXCHANGE_RATE_UPDATED` audit event with changed fields.
8. **Update — immutable currencies:** PATCH body contains `fromCurrencyId` or `toCurrencyId` → 400.
9. **Update — collision:** New `effectiveDate` creates a duplicate for the same pair → 409.
10. **Delete:** Record deleted → 204, `EXCHANGE_RATE_DELETED` audit event.
11. **Resolve — match found:** `GET /resolve?from=USD&to=EUR&date=2026-05-15` returns the rate with the latest `effectiveDate` ≤ 2026-05-15.
12. **Resolve — no match:** No rate exists on or before the date → 404.
13. **Authorisation — read denied:** User without `EXCHANGE_RATE_VIEW` → 403 on all endpoints.
14. **Authorisation — write denied:** User with `EXCHANGE_RATE_VIEW` but not `EXCHANGE_RATE_MANAGE` → 403 on POST / PATCH / DELETE.
15. **SuperAdmin:** Resolves via the same privilege enforcement path as every other user — no bypass code path.
16. **Web — permission guard:** User with `EXCHANGE_RATE_VIEW` only sees the table; Create / Edit / Delete controls are hidden.
17. **Web — currency dropdown:** Only tenant-activated currencies appear in the From / To dropdowns.

### Implementation Notes (M5 Exchange Rates)

- Backend controller: `backend/src/main/java/com/wagepayroll/api/TenantExchangeRatesController.java`
- Backend service: `backend/src/main/java/com/wagepayroll/currency/TenantExchangeRateService.java`
- Backend entity/repository:
  - `backend/src/main/java/com/wagepayroll/domain/currency/TenantExchangeRateEntity.java`
  - `backend/src/main/java/com/wagepayroll/domain/currency/TenantExchangeRateRepository.java`
- Liquibase DDL/DML:
  - `backend/src/main/java/com/wagepayroll/liquibase/task/DdlExchangeRateTable1.java`
  - `backend/src/main/java/com/wagepayroll/liquibase/task/DataExchangeRatePrivileges1.java`
  - `backend/src/main/resources/db/changelog/ddl/schema-exchange-rate-1.xml`
  - `backend/src/main/resources/db/changelog/dml/data-exchange-rate-privileges-1.xml`
- Frontend tab and API client:
  - `frontend/src/app/app/tenant-currencies/page.tsx`
  - `frontend/src/lib/api.ts`
  - `frontend/src/messages/nav.ts`

---

## Sub-feature stubs (expand when milestone begins)

### Countries
Planned M5. Covers ISO 3166 country reference data used for employee records and legal calculations. TBD.

### Business Units
Planned M5. Organizational hierarchy within a tenant. TBD.

### BU-scoped Roles
Planned M5. Roles that apply within a specific business unit rather than the whole tenant. TBD.
