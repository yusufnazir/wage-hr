# FEATURE payroll-reference-data Verification

## Scope
- Sub-feature: Exchange Rates
- Platforms covered: Backend + Web
- Mobile: skipped by requirement

## Manual Smoke Checklist
1. Open Tenant App -> Settings -> Currencies -> Exchange Rates.
2. Confirm list loads with default effective date descending order.
3. As a user with EXCHANGE_RATE_VIEW only, verify table is visible and Create/Edit/Delete controls are hidden.
4. As a user with EXCHANGE_RATE_MANAGE, create a rate with valid from/to/rate/effective date and confirm success.
5. Try create duplicate (same from/to/effective date) and confirm duplicate message.
6. Try create with a currency not active for tenant and confirm inactive currency message.
7. Edit an existing rate (rate and/or effective date), save, and confirm list refresh.
8. Attempt patch payload with immutable currency ids (API-level check) and confirm 400.
9. Delete an existing rate and confirm removal from list.
10. Call resolve endpoint for known pair/date and confirm latest effectiveDate <= requested date is returned.
11. Call resolve where no prior rate exists and confirm 404.

## Backend Automated Checks
- Integration tests added in `backend/src/test/java/com/wagepayroll/api/TenantExchangeRatesIT.java`.

## Expected Audit Events
- EXCHANGE_RATE_CREATED
- EXCHANGE_RATE_UPDATED
- EXCHANGE_RATE_DELETED
