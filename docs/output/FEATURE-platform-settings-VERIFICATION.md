# Verification — Platform settings (runtime + UX)

## Automated

- **Backend:** `./mvnw.cmd test` from `backend/` — includes `MeEndpointIT` (tenant `/me` branding fields), `PlatformPublicSurfaceIT` (unauthenticated GET), document/orphan tests updated for `MinioDocumentStorageGateway.isOperational()`.
- **Frontend:** `npm run build` from `frontend/` (typecheck + lint).
- **E2E (optional):** With API + DB up, set `PLAYWRIGHT_API_BASE_URL` and run Playwright; `m1-platform.spec.ts` includes **login page shows `applicationName` from public surface** (desktop marketing column).

## Manual smoke

1. **Public surface:** `GET http://127.0.0.1:8300/api/v1/platform/public-surface` (no cookie) → JSON `data` with `applicationName`, `publicBaseUrl`, `dateFormat`.
2. **Tenant `/me`:** Sign in, `GET /api/v1/me` with `Host: {tenant}.lvh.me` → same three fields populated; no MinIO/mail secrets.
3. **Auth shell:** Open `http://auth.lvh.me:3007/login` (or your port) — marketing copy should show seeded application name (e.g. **Wage Payroll**); mobile footer uses the same API-driven name.
4. **Password reset link:** Trigger forgot-password for a real user; link path should be `{resolved public base}/reset-password?token=…` (base from `platform.base_url` when set in DB, else `app.public.frontend-origin`).
5. **MinIO merge:** With empty `storage.minio.*` rows in DB but full `app.storage.minio.*` in env, document upload presign should still work; with all four blank after merge, upload returns **503**.
6. **Platform settings UI:** As platform superadmin, `/app/platform-settings` — tabs and helper copy switch with locale (`en` / `nl`); saves still audited server-side.

## Mobile

No Flutter app in this repository; when a mobile client exists, consume `applicationName`, `dateFormat`, and `publicBaseUrl` from authenticated `GET /api/v1/me` (tenant host), matching web.
