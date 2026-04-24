# Security infrastructure summary (Phase 3)

**Scope:** Privilege-based authorization, tenant-aware checks, session/CSRF baseline, rate limiting, security headers, redirect validation, Liquibase-backed catalog + seed — per `docs/templates/3. SECURITY-INFRASTRUCTURE-PROMPT.md` and `docs/guides/CROSS-CUTTING-SECURITY.md`.

## Implementation map

| Concern | Location |
|--------|----------|
| Privilege resolution (no role-only checks in business code) | `backend/src/main/java/com/wagepayroll/security/PermissionService.java` |
| Method-level enforcement | `@RequiresPrivilege`, `backend/.../security/PrivilegeAuthorizationAspect.java` |
| Authentication | Spring Security + `AccountUserDetailsService` (principal name = user UUID string) |
| Session + CSRF | `backend/.../security/SecurityConfiguration.java` — `CookieCsrfTokenRepository.withHttpOnlyFalse()`, CORS enabled |
| Login rate limiting | `backend/.../security/LoginAttemptService.java` + `app.security.rate-limit.*` |
| Security headers | `backend/.../security/SecurityHeadersFilter.java` + `app.security.headers.*` |
| Redirect / `returnTo` safety | `backend/.../security/RedirectUrlValidator.java`, `GET /api/v1/auth/redirect-check` |
| Tenant context | `backend/.../tenant/TenantContextFilter.java`, `TenantContext`, `SubdomainParser` |
| Global API envelope + Problem Details | `backend/.../common/api/ApiResponse.java`, `ProblemDetailControllerAdvice.java` |
| Request ID | `backend/.../common/api/RequestIdFilter.java` |

## Data & migrations

| Artifact | Purpose |
|----------|---------|
| `schema-*-1` in `backend/src/main/resources/db/changelog/ddl/schema-bootstrap-1.xml` | `tenant`, `privilege`, `user_account`, `tenant_privilege_allowance`, `role`, `role_privilege`, `membership`, `user_role` |
| `data-scaffold-1` in `backend/src/main/resources/db/changelog/dml/data-scaffold-1.xml` | Delegates to `com.wagepayroll.liquibase.task.DataScaffoldSeed1` (Java DML only — no raw SQL DML in XML) |

**Privileges seeded:** `USER_VIEW`, `USER_EDIT`, `TENANT_SETTINGS_EDIT`. Demo tenant handle: `demo`.

## Endpoint → privilege (illustrative)

| Endpoint | Effective requirement |
|----------|------------------------|
| `GET /api/v1/demo/user-view` | `USER_VIEW` + membership in tenant + grant via role + tenant allowance |
| `GET /api/v1/me` | Authenticated; privileges listed only when `tenant_id` is resolved |

All other `/api/v1/**` routes require authentication unless listed as `permitAll` in `SecurityConfiguration`.

## SuperAdmin non-bypass

There is **no** `if (superAdmin) return true` path. Access is derived from **membership + tenant allowance + role → privilege** only (`PermissionService`).

## Tests

- `backend/src/test/java/com/wagepayroll/api/DemoPrivilegedEndpointIT.java` — tenant host vs localhost; user with vs without role grants.

## Build

- Same as scaffold summary: run `backend\mvnw.cmd test` with **JDK 21** available to confirm in your environment.
