# API conventions

Public HTTP API shape for multi-tenant SaaS. Aligns with architecture output: single canonical base, stable errors, safe links.

## Base URL and versioning

- **One canonical public API base** (scheme + host + path prefix + version), e.g. `https://api.{BASE_DOMAIN}/v1/`.
- Avoid ambiguous multiple public bases unless architecture explicitly defines separate surfaces.

## Endpoint naming

- Consistent resource naming and HTTP verbs; document patterns in OpenAPI.

## Success and error responses

- **One standard success shape** (e.g. wrapped `data` + optional `meta`).
- **One standard error shape** — stable **`code`**, human message, optional `details`; prefer **RFC 7807 Problem Details** (`application/problem+json`) where applicable.
- **No stack traces** in production error bodies.

## Canonical URL generation

- How absolute URLs are built for **emails**, **invites**, **password reset**, etc.
- Which **subdomain** is used for which link type (auth vs tenant vs app).

## Multi-tenancy in API

- Tenant context from host, header, or path — match architecture; document precedence.

## Related

- Local **API vs Next.js dev ports** and CORS origins: [LOCAL-DEV-PORTS.md](./LOCAL-DEV-PORTS.md).
- Proxy and forwarded headers: [CROSS-CUTTING-SECURITY.md](./CROSS-CUTTING-SECURITY.md).
- Database and migrations: [LIQUIBASE-RULES.md](./LIQUIBASE-RULES.md).
