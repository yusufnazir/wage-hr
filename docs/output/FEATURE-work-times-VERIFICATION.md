# FEATURE work-times Verification

## Scope

Full-stack verification (backend + web frontend) for work times:
- `tenant_work_time` → `/app/work-times`
- API: `/api/v1/work-times`

## Phase 0 — Schema Preflight

- [x] `docs/modules/work-times.md` reviewed as sole behavioral and schema authority
- [x] `backend/src/main/resources/db/changelog/ddl/create-table-work-time.xml` — DDL exists
- [x] `backend/src/main/resources/db/changelog/ddl/schema-work-time.xml` — DDL wrapper exists
- [x] `backend/src/main/resources/db/changelog/dml/data-m8-work-time-privileges-1.xml` — privileges + nav item exists

## Phase 1 — Backend

### Controller
- `GET /api/v1/work-times` — list (`WORK_TIME_VIEW`)
- `GET /api/v1/work-times/{id}` — get (`WORK_TIME_VIEW`)
- `POST /api/v1/work-times` — create (`WORK_TIME_MANAGE`) → 201
- `PUT /api/v1/work-times/{id}` — update (`WORK_TIME_MANAGE`)
- `PATCH /api/v1/work-times/{id}/active` — activate/deactivate (`WORK_TIME_MANAGE`)

### Automated tests
- `TenantWorkTimesIT` covers:
  - Forbidden without `WORK_TIME_VIEW`
  - Viewer can list but cannot mutate
  - Create + update + deactivate + reactivate + active filter

## Phase 2 — Web Frontend

- `/app/work-times` list page exists and is navigable via `nav.work_times`

## Smoke Test Steps (manual)

1. Create a company
2. Navigate to `/app/work-times`
3. Create a work time: code `STD`, hours/day `8.00`, days/week `5`
4. Deactivate it and verify it disappears from Active-only view
5. Reactivate and verify it returns

