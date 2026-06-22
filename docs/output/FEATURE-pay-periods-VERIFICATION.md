# FEATURE pay-periods Verification

## Scope

Full-stack verification (backend + web frontend) for pay periods + runs:
- `tenant_pay_period`, `tenant_pay_period_run` → `/app/pay-periods`
- API: `/api/v1/pay-periods`, `/api/v1/pay-period-runs`, generation endpoint

## Phase 0 — Schema Preflight

- [x] `docs/modules/pay-periods.md` reviewed as sole behavioral and schema authority
- [x] `backend/src/main/resources/db/changelog/ddl/create-table-pay-period.xml` — DDL exists
- [x] `backend/src/main/resources/db/changelog/ddl/schema-pay-period.xml` — DDL wrapper exists
- [x] `backend/src/main/resources/db/changelog/dml/data-m9-pay-period-privileges-1.xml` — privileges + nav item exists

## Phase 1 — Backend

### Controller
- `GET /api/v1/pay-periods` — list (`PAY_PERIOD_VIEW`)
- `GET /api/v1/pay-periods/{id}` — get (`PAY_PERIOD_VIEW`)
- `POST /api/v1/pay-periods` — create (`PAY_PERIOD_MANAGE`) → 201
- `PUT /api/v1/pay-periods/{id}` — update (`PAY_PERIOD_MANAGE`)
- `PATCH /api/v1/pay-periods/{id}/status` — lifecycle (`PAY_PERIOD_MANAGE`)
- `GET /api/v1/pay-periods/{id}/runs` — list runs (`PAY_PERIOD_RUN_VIEW`)
- `POST /api/v1/pay-period-runs` — create run (`PAY_PERIOD_RUN_MANAGE`)
- `POST /api/v1/companies/{companyId}/pay-periods/generate` — generate (`PAY_PERIOD_MANAGE`)
- `POST /api/v1/pay-periods/{id}/supervisor-approve` — supervisor sign-off (`PAY_PERIOD_SUPERVISOR_APPROVE`)

### Automated tests
- `TenantPayPeriodsIT` covers:
  - Forbidden without `PAY_PERIOD_VIEW`
  - Viewer can list but cannot mutate
  - Create pay period + patch status + create run + list runs
  - Generate endpoint returns created count
- `TenantPayPeriodFinalizeApiIT` covers supervisor approval (AC-PP-S1 … S4):
  - Close without approval → **409** `SUPERVISOR_APPROVAL_REQUIRED`
  - Approve without finalized FINAL run → **409** `FINAL_RUN_REQUIRED`
  - Approve without privilege → **403**
  - Approve then close → **200**

## Phase 2 — Web Frontend

- `/app/pay-periods` list page exists and is navigable via `nav.pay_periods`

## Smoke Test Steps (manual)

1. Create a company
2. Navigate to `/app/pay-periods`
3. Create a pay period for the current year (or use Generate)
4. Change status `READY` → `OPEN`
5. Create an interim run; verify it appears in the runs list

