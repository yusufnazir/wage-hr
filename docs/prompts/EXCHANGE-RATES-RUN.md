# Run prompt — Exchange Rates feature

Copy the block below into Cursor (or your agent) exactly as-is, replacing nothing — all token values are already filled.

---

```
Run feature (Phases 0–4): @docs/prompts/MASTER-FEATURE-END-TO-END.md — filled values: @docs/prompts/PROJECT-CONTEXT.md — module (sole behavioral + schema authority): @docs/modules/payroll-reference-data.md — @docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md — @docs/guides/DATA-MODEL-STANDARDS.md — docs: @docs — repo root: @wage-payroll

Existing codebase: foundation (scaffold + security) is already in place. Execute MASTER-FEATURE-END-TO-END for the **Exchange Rates** sub-feature defined in docs/modules/payroll-reference-data.md.

Use docs/modules/payroll-reference-data.md as the sole behavioral and schema contract.

Implement:
- Phase 1 — Backend (depth of docs/templates/4. BACKEND-FEATURE-PROMPT.md)
- Phase 2 — Web (depth of docs/templates/5. WEB-FRONTEND-PROMPT.md)
- Phase 3 — Mobile: **SKIP** (out of scope per module doc)
- Phase 4 — Verification

Key implementation notes:

BACKEND
- Entity: extend TenantScopedEntity; table name `tenant_exchange_rate`; UUID PKs (see AbstractUuidEntity pattern)
- Both `from_currency_id` and `to_currency_id` are FKs to `platform_currency.id`; validate at service layer that both appear in the tenant's `tenant_currency` list (use TenantCurrencyRepository / TenantCurrencyService as reference)
- Unique DB constraint: (tenant_id, from_currency_id, to_currency_id, effective_date)
- Rate stored as BigDecimal; precision 18, scale 8; must be > 0
- PATCH endpoint: `fromCurrencyId` and `toCurrencyId` are immutable — return 400 if client sends them
- Resolve endpoint: `GET /api/v1/tenant/exchange-rates/resolve?from=&to=&date=` — returns record with MAX(effectiveDate) WHERE effectiveDate ≤ :date for the given pair; 404 if none; uses currency ISO codes (not IDs) as query params
- Privileges: `EXCHANGE_RATE_VIEW` (read), `EXCHANGE_RATE_MANAGE` (write) — annotate with @RequiresPrivilege
- Liquibase: DDL task `DdlExchangeRateTable1` + DML task `DataExchangeRatePrivileges1` (seeds both privileges into global pool, category `payroll_reference`)
- Audit: log EXCHANGE_RATE_CREATED / EXCHANGE_RATE_UPDATED / EXCHANGE_RATE_DELETED via AuditService
- No commercial plan gating
- Reference pattern for tenant-scoped CRUD: TenantCurrenciesController / TenantCurrencyService / TenantCurrencyEntity / TenantCurrencyRepository

WEB
- Location: Tenant App → Settings → Currencies page → new **Exchange Rates tab** (add alongside existing tabs)
- List table: From | To | Rate | Effective Date | Actions; default sort effectiveDate DESC; paginated
- Create modal: From/To currency dropdowns (tenant-activated currencies only; selected `from` removed from `to` options), Rate input (8 dp), Effective Date picker
- Edit modal: From/To as read-only display labels (not inputs); Rate and Effective Date editable
- Delete: confirmation dialog before DELETE call
- Permission gates: hide Create/Edit/Delete controls for users without EXCHANGE_RATE_MANAGE; backend enforces regardless
- Error mapping: 409 → "A rate for this currency pair on this date already exists."; 422 → "One or more selected currencies are not active for this tenant."; 404 on edit/delete → "This record no longer exists — refresh the page."
- BFF route: Next.js Route Handler at /api/bff/tenant/exchange-rates proxying to Spring

Read docs/guides/README.md and docs/output/ARCHITECTURE-DEFINITION.md before writing any code. If architecture conflicts with PROJECT-CONTEXT.md or the module doc, follow PROJECT-CONTEXT.md + the module doc.

Do not merge schema assumptions from other module docs. Do not use MASTER-FOUNDATION-TO-FEATURES or greenfield scaffold Phases 2–3 — foundation is already in place.

When done:
- Update docs/modules/payroll-reference-data.md with API notes and file pointers
- Add docs/output/FEATURE-payroll-reference-data-VERIFICATION.md with manual smoke steps
- Confirm builds and tests pass for all touched areas (backend + frontend)
```
