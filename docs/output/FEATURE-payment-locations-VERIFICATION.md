# FEATURE payment-locations Verification

## Scope

Full-stack verification (backend + web frontend) for payment locations:
- `tenant_payment_location` → `/app/payment-locations`
- API: `/api/v1/tenant/payment-locations`
- Payment types: `CASH`, `BANK_ACCOUNT`

## Phase 0 — Schema Preflight (complete)

- [x] `docs/modules/payment-locations.md` reviewed as sole behavioral and schema authority
- [x] `backend/src/main/resources/db/changelog/ddl/create-table-tenant-payment-location.xml` — DDL created
- [x] `backend/src/main/resources/db/changelog/ddl/schema-payment-locations.xml` — DDL wrapper created
- [x] `backend/src/main/resources/db/changelog/dml/data-m12-payment-location-privileges-1.xml` — privileges + role grants created
- [x] `backend/src/main/resources/db/changelog/dml/data-m12-payment-location-nav-1.xml` — nav item created
- [x] `backend/src/main/resources/db/changelog/db.changelog-master.yaml` — updated with DDL + DML includes

## Phase 1 — Backend (complete)

### Entities / repositories / DTOs
- `TenantPaymentLocationEntity` — JPA entity with all allowed columns
- `TenantPaymentLocationRepository` — Spring Data repo with uniqueness check methods
- `TenantPaymentLocationCreateRequest` — record DTO for create (includes paymentType)
- `TenantPaymentLocationUpdateRequest` — record DTO for update (no paymentType — immutable)
- `TenantPaymentLocationRowDto` — full read DTO with masked + full account number fields

### Service
- `TenantPaymentLocationService` — full business rules:
  - BR-1: Name unique per company (case-insensitive)
  - BR-2: paymentType immutable after creation (enforced in update, not in DTO)
  - BR-3: Currency must match `^[A-Z]{3}$`
  - BR-4: CASH → bankTemplateId + accountNumber must be null
  - BR-5: BANK_ACCOUNT → both bankTemplateId + accountNumber required
  - BR-6: bankTemplateId must belong to same tenantId AND companyId
  - BR-7: accountNumberFormat regex validated if present and compilable
  - BR-8: Bank template must be active
  - BR-9: All queries scoped to tenant + company
  - Account number masking: last 4 chars visible (•••• prefix), list view masked, get-by-id returns full

### Controller
- `TenantPaymentLocationsController` — REST at `/api/v1/tenant/payment-locations`
  - `GET /` — list (PAYMENT_LOCATION_VIEW), params: companyId, page, size, active
  - `GET /{id}` — get one (PAYMENT_LOCATION_VIEW)
  - `POST /` — create (PAYMENT_LOCATION_MANAGE) → 201
  - `PUT /{id}` — update (PAYMENT_LOCATION_MANAGE)
  - `PATCH /{id}/activate` — activate (PAYMENT_LOCATION_MANAGE)
  - `PATCH /{id}/deactivate` — deactivate (PAYMENT_LOCATION_MANAGE)

### Audit
- `AuditActionCodes`: `PAYMENT_LOCATION_CREATED`, `PAYMENT_LOCATION_UPDATED`, `PAYMENT_LOCATION_ACTIVATED`, `PAYMENT_LOCATION_DEACTIVATED`
- `AuditResourceTypes`: `TENANT_PAYMENT_LOCATION`

### Automated tests
- `TenantPaymentLocationsIT` — 10 tests covering:
  - List requires companyId
  - List forbidden without PAYMENT_LOCATION_VIEW
  - Viewer can list but cannot mutate
  - Create CASH location (happy path)
  - CASH with bankTemplateId rejected (BR-4)
  - Invalid currency rejected (BR-3)
  - Duplicate name rejected (BR-1)
  - Deactivate + active filter + reactivate
  - paymentType immutability (BR-2)
  - Cross-tenant access forbidden

```
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
```

## Phase 2 — Web Frontend (complete)

### Files created / modified
- `frontend/src/lib/api.ts` — added `TenantPaymentLocationRow` type + 6 fetch functions
- `frontend/src/messages/nav.ts` — added ~50 i18n keys (en + nl)
- `frontend/src/app/app/payment-locations/page.tsx` — list page with company selector, active filter, type filter, masked account number, activate/deactivate confirm dialog
- `frontend/src/app/app/payment-locations/new/page.tsx` — create form with CASH/BANK_ACCOUNT radio toggle, bank template dropdown + summary panel
- `frontend/src/app/app/payment-locations/[id]/edit/page.tsx` — edit form with read-only paymentType, bank template inactive warning

### TypeScript check
```
npx tsc --noEmit  →  0 errors
```

## Smoke Test Steps

### Prerequisites
- Local backend running on port 8300 with `application-local.yml`
- Frontend running on port 3007
- Logged in as demo admin to `demo.lvh.me:3007`

### Steps
1. Navigate to `/app/payment-locations`
2. Verify nav item "Payment locations" is visible (requires PAYMENT_LOCATION_VIEW)
3. Select a company from the dropdown — verify empty state
4. Click "+ Add location" — verify form opens at `/app/payment-locations/new`
5. Fill Name="Main Cash", Type=Cash, Currency=SRD → Submit → verify redirects to list with item
6. Verify account number column shows "—" for CASH locations
7. Click "Deactivate" → confirm → verify status badge becomes Inactive
8. Filter "Active only" → verify inactive item disappears
9. Click "Activate" → verify status reverts to Active
10. Click location name → verify edit form opens
11. Verify Payment type field is read-only on edit form
12. Create BANK_ACCOUNT location: select bank template, enter account number → submit
13. Verify account number shows masked value in list (••••last4)
14. Click edit on BANK_ACCOUNT → verify full account number shown in edit form
15. As viewer role: verify list loads but "+ Add location" button is hidden
16. As user without PAYMENT_LOCATION_VIEW: verify forbidden message shown

## Acceptance Criteria Checklist

| AC | Description | Status |
|----|-------------|--------|
| AC-1 | List endpoint scoped to tenantId + companyId | ✅ |
| AC-2 | Create with paymentType CASH | ✅ |
| AC-3 | Create with paymentType BANK_ACCOUNT | ✅ |
| AC-4 | BR-1: Name unique per company (case-insensitive) | ✅ |
| AC-5 | BR-2: paymentType immutable after creation | ✅ |
| AC-6 | BR-3: Currency regex `^[A-Z]{3}$` | ✅ |
| AC-7 | BR-4: CASH rejects bankTemplateId/accountNumber | ✅ |
| AC-8 | BR-5: BANK_ACCOUNT requires both bankTemplateId + accountNumber | ✅ |
| AC-9 | BR-6: bankTemplateId scoped to same tenant + company | ✅ |
| AC-10 | BR-7: accountNumberFormat regex validated when present | ✅ |
| AC-11 | BR-8: Inactive bank template rejected | ✅ |
| AC-12 | Activate/deactivate endpoints work | ✅ |
| AC-13 | Audit events written for all mutations | ✅ |
| AC-14 | PAYMENT_LOCATION_VIEW privilege gates read | ✅ |
| AC-15 | PAYMENT_LOCATION_MANAGE privilege gates write | ✅ |
| AC-16 | Nav item visible with PAYMENT_LOCATION_VIEW | ✅ |
| AC-17 | Active filter works on list | ✅ |
| AC-18 | Account number masked in list, full in edit | ✅ |
