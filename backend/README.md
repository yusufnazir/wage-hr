# wage-payroll API (Spring Boot)

## Requirements

- **Java 21** (Temurin recommended). This repo pins the default JDK to **`D:\Tools\Eclipse Adoptium\jdk-21.0.3.9-hotspot`**: **`mvnw.cmd`** sets **`JAVA_HOME`** before running Maven; **`.vscode/settings.json`** sets the same for the integrated terminal and the Java language server. To use a different JDK, set **`JAVA_HOME`** in the shell *before* calling `mvnw.cmd`, or change those project files. Optional: unpack into `backend/.jdk/` — see `docs/guides/JAVA-BACKEND-TOOLING.md`.
- **Maven**: use the committed wrapper: `.\mvnw.cmd` (Windows) or `./mvnw` (Unix). No global Maven required once the wrapper has downloaded.
- **MariaDB** for local/prod-like runs (create database `wagepayroll` or set `DATABASE_URL`).

## Ports (see `docs/prompts/PROJECT-CONTEXT.md`)

| Service | Default |
|--------|---------|
| API | **8300** (`SERVER_PORT` / `server.port`) |
| Next.js dev | **3007** (separate process) |

## Run

```powershell
cd backend
# JDK 21 — use your install (JAVA_HOME = JDK root, not bin\java):
# $env:JAVA_HOME = 'D:\Tools\Eclipse Adoptium\jdk-21.0.3.9-hotspot'
# Or after fetch-local-jdk.ps1: $env:JAVA_HOME = (Resolve-Path .\.jdk).Path
.\mvnw.cmd spring-boot:run
```

Tests (H2 in-memory, Liquibase applied):

```bash
.\mvnw.cmd test
```

Copy `application-local.example.yml` patterns into `application-local.yml` (gitignored) for secrets-free local overrides.

## Troubleshooting

### `BeanCreationException` on `tenantRepository` / `Cannot resolve reference to bean 'jpaSharedEM_entityManagerFactory'`

That bean name is **internal to Spring Data JPA**. It almost always means the **`entityManagerFactory` bean did not finish starting** — the repository error is a follow-on.

1. **Scroll up** in the same stack trace for the **first** `Caused by` under `LocalContainerEntityManagerFactoryBean` / `SchemaManagementException` / `JDBCConnectionException` / `LiquibaseException`. That line is the real fix (e.g. DB unreachable, **`ddl-auto: validate`** mismatch with the DB, or **Liquibase** not completing).
2. **`spring.jpa.hibernate.ddl-auto: validate`** (see `application.yml`) requires the MariaDB schema to **match** the entities **after** Liquibase has run. If a migration failed part-way, repair the DB or drop/recreate the database and run again.
3. **MariaDB + reserved words:** raw SQL in `CustomTaskChange` must quote identifiers such as `` `key` `` on `platform_setting` / `tenant_setting` (see `DataM1MenuSettingsSeed1`). H2-based tests may not catch this.

### Liquibase / MariaDB SQL syntax near `key`

`KEY` is reserved in MySQL/MariaDB. Use **backticks** around the column name in hand-written INSERT/UPDATE strings.

### Tenant context wrong when calling the API from **Next.js only** (BFF)

The UI should call Spring **only from the Next.js server** (`API_BASE_URL`). The BFF forwards the browser `Host` as **`X-Forwarded-Host`** so `TenantContextFilter` can resolve `{tenant}.lvh.me` the same as a direct browser call. If you bypass the BFF, set **`SERVER_FORWARD_HEADERS_STRATEGY=native`** (or `framework`) and **`app.forwarding.trust-proxy=true`** when a trusted edge forwards `Forwarded` / `X-Forwarded-*`.

### `Schema-validation: wrong column type` on `password_reset_token.token_sha256` (CHAR vs VARCHAR)

Hibernate’s `ddl-auto: validate` maps `String` + `length=64` to **VARCHAR(64)**. MariaDB `CHAR(64)` from the original Liquibase DDL does not match. Changeset **`schema-password-reset-token-sha256-varchar-1`** alters the column to `VARCHAR(64)` — run migrations so it applies (new installs: runs right after the table is created).

## Scaffold notes

- **Liquibase**: `src/main/resources/db/changelog/` — DDL in XML; DML via `DataScaffoldSeed1`, `DataM1MenuSettingsSeed1`, `DataM1SecondTenantAcmeSeed1` (CustomTaskChange).
- **Security**: session + cookie CSRF; tenant routes use `@RequiresPrivilege` + `PermissionService` (platform superadmin elevation + `X-Break-Glass-Reason` on mutating routes — see `docs/modules/security.md`). Platform routes: `/api/v1/platform/settings`, `/api/v1/platform/privileges/catalog`, `PUT .../platform/tenants/{id}/privilege-pool` via `platform_superadmin` (`docs/modules/platform-settings.md`).
- **Tenancy**: `TenantContextFilter` resolves tenant from **`X-Forwarded-Host`** (when present) else `Host` / server name, or `X-Tenant-Id` on `api.*` host; unknown tenant on `/api/**` → **404** / **400** `application/problem+json` (see `docs/modules/tenancy-routing.md`).
- **i18n (M1):** `user_account.preferred_locale`; `GET/PATCH /api/v1/me` locale field and `PATCH /api/v1/me/locale` — `docs/modules/i18n.md`.
- **Audit (M1):** append-only `audit_event` + `AuditService`; locale and settings PATCH handlers emit events — `docs/modules/audit.md`.
- **Commercial plans (M3 v1):** `plan_feature` catalog (`GET /api/v1/platform/plan-features`) + **`commercial_plan` / `commercial_plan_feature`** (`GET|POST|PUT|DELETE /api/v1/platform/commercial-plans`, superadmin; delete when plan unused) — `docs/modules/commercial-plans.md`.
- **Navigation (M3 on M1 base):** `nav_menu_item.required_plan_feature_code` + `GET /api/v1/me/navigation` — `docs/modules/navigation-menu.md`.
- **Subscriptions + gating (M3 v1):** `tenant_subscription`; `GET|PUT .../platform/tenants/{tenantId}/subscription`; `GET /api/v1/me/subscription`; **`GET /api/v1/me`** includes **`planFeatureCodes`** for an **`ACTIVE`** subscription; effective privilege pool = allowances **∪** `PlanFeaturePrivilegeWiring` (e.g. `HR_ESSENTIALS` → `USER_INVITE`) — `docs/modules/commercial-subscriptions.md`.
- **Commercial billing (M3 v1 — code-complete):** Ops runbook [`docs/operations/STRIPE-FIRST-CUSTOMER-RUNBOOK.md`](../docs/operations/STRIPE-FIRST-CUSTOMER-RUNBOOK.md). Liquibase seeds **`DEMO_STARTER`** (`commercial_plan` + `TENANT_CORE`) with placeholder Stripe/PayPal price/plan ids for local catalog + E2E. `billing_webhook_receipt` + Stripe webhook **minimal `tenant_subscription` reconcile** (`checkout.session.completed` / `customer.subscription.updated` / `customer.subscription.deleted`; updated uses subscription **metadata** or **price** id → `commercial_plan`; Checkout sets **`subscription_data.metadata.commercial_plan_id`**) + PayPal **minimal reconcile** (`BILLING.SUBSCRIPTION.ACTIVATED` / `RE-ACTIVATED` with `custom_id` `WAGE|<tenant>|<commercialPlan>`; `CANCELLED` / `EXPIRED` / `SUSPENDED` → cancelled row) + `billing_usage_event` + **`billing_usage_aggregate`** (deterministic daily recompute via **`BillingUsageAggregationService`**, optional UTC **`BillingUsageAggregationScheduler`** for the previous day, read **`GET .../tenant/billing/usage-aggregates`**) + webhooks + **`GET .../tenant/billing/summary`** (**`USER_VIEW`** — provider flags, links, **`tenant_subscription`** snapshot) + **`GET .../tenant/billing/commercial-plans`** (**`TENANT_SETTINGS_EDIT`** — active catalog with Stripe/PayPal ids) + Stripe/PayPal session flows + **`POST .../tenant/billing/usage-events`** (metering persistence, idempotent); **`billing_provider_link`**; **`commercial_plan.stripe_subscription_price_id`** + tenant Checkout body **`commercialPlanId`**; PayPal subscribe body **`commercialPlanId`** + `planId` (must match **`commercial_plan.paypal_billing_plan_id`** when set); env **`STRIPE_SECRET_KEY`**, **`PAYPAL_*`**, **`billing.*.enabled`**. Provider meter push + full reconciliation — `docs/modules/commercial-billing.md`.
- **Invitations + inbox (M2):** `USER_INVITE` on demo Admin; `POST/GET /api/v1/tenant/invitations` (create is **idempotent** per pending email; response includes `idempotentReplay`); `POST /api/v1/auth/invitations/accept` (CSRF-disabled for this path); `GET /api/v1/me/notifications?limit=&offset=` (defaults **50** / **0**, max limit **100**) returns `items`, `total`, `limit`, `offset`; `PATCH …/read`. **`APP_INVITATION_EXPOSE_PLAIN_TOKEN`** / `app.invitation.expose-plain-token`: default **false**; `devPlainToken` is returned **only** when the flag is true **and** Spring profiles include **`dev`**, **`test`**, or **`local`** — `docs/modules/invitations.md`, `docs/modules/notifications-inbox.md`.

## Scripts

- `scripts/fetch-local-jdk.ps1` / `scripts/fetch-local-jdk.sh` — download Temurin into `.jdk/` (gitignored).
