# Feature verification — mail templates (platform)

## Preconditions

- API and Next.js dev (or production build) per `docs/guides/LOCAL-DEV-PORTS.md`.
- Platform superadmin session (seed: `admin@demo.lvh.me` in dev).

## Manual smoke

1. Sign in as platform superadmin; open operator workspace (`admin.{BASE_DOMAIN}` / `app` per product routing).
2. From the sidebar, open **Mail templates** (`/app/platform-mail-templates`).
3. Confirm the table lists `TENANT_INVITATION`, `EMAIL_VERIFICATION`, and `PASSWORD_RESET_REQUEST` with content version and **Active** yes.
4. Open `EMAIL_VERIFICATION`; edit and save `en` + `nl` locales (subject + HTML); verify `contentVersion` bumps.
5. Open `PASSWORD_RESET_REQUEST`; edit and save `en` + `nl` locales; verify `contentVersion` bumps.
6. Optional optimistic lock check: open one template in two tabs, save in tab A, then save stale content in tab B — expect conflict and no silent overwrite.
7. Register a new user in dev (`/api/v1/auth/register` flow) and verify mail log / provider payload includes an HTML body containing the rendered verify link.
8. Trigger forgot-password (`/api/v1/auth/forgot-password`) and verify mail log / provider payload includes an HTML body containing rendered reset link and expiry minutes.

## Automated

- Backend: `MailTemplateCatalogServiceTest`, `OutboundMailServiceIT`, `MailTemplateSeedLiquibaseIT`, `PlatformMailTemplatesIT`, `NavigationAndSettingsIT`.
- Frontend E2E: `frontend/e2e/m1-platform.spec.ts` — “Mail templates — platform list” (requires `PLAYWRIGHT_API_BASE_URL`).

## Outbound mail paths

- With mail API **not** configured, invitation create still succeeds (log-only path).
- With templates active and mail API configured, outbound JSON includes `html` for `TENANT_INVITATION`, `EMAIL_VERIFICATION`, and `PASSWORD_RESET_REQUEST` when catalog render succeeds.
- If template row is absent/inactive or locale fallback fails, outbound send falls back to existing text-only behavior (no regression in invitation, verification, or password-reset flows).
