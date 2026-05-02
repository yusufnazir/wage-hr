# Verification — Platform countries (backend + web)

## Automated

- Backend: `./mvnw.cmd -Dtest=PlatformCountriesIT test` from `backend/`.
  - Result: PASS (`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`).
  - Covers superadmin authorization, CRUD, activate/deactivate flow, tenant active-only read behavior, and locale handling.
- Frontend: `npm run build` from `frontend/`.
  - Result: PASS (Next.js compile + lint + typecheck + static generation).
  - Confirms new routes compile:
    - `/app/platform-countries`
    - `/app/platform-countries/new`
    - `/app/platform-countries/[id]/edit`

## Manual smoke

1. Sign in as platform superadmin on `admin.{host}` and open `/app/platform-countries`.
2. Confirm list renders with ISO fields (alpha-2, alpha-3, numeric, dial code), search, and active/all filtering.
3. Create a country (with `en` + `nl` names) and verify it appears in list.
4. Edit the country and verify values update.
5. Deactivate the country and confirm it disappears from tenant `GET /api/v1/countries` results.
6. Reactivate and confirm it appears again in tenant read endpoint.
7. Switch locale (`en`/`nl`) and verify localized country name resolution in web list.

## Seed and schema checks

- New schema and seed changelog entries are included in `backend/src/main/resources/db/changelog/db.changelog-master.yaml`.
- Country seed task is implemented via `DataM7PlatformCountriesSeed1` and inserts ISO catalog + translations.

## E2E notes

- Added Playwright route-level coverage in `frontend/e2e/m1-platform.spec.ts`:
  - superadmin can access platform countries page
  - tenant viewer is denied management access
- Playwright execution was not run in this session (requires local API/DB runtime and `PLAYWRIGHT_API_BASE_URL`).
