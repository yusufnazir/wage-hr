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

## Pagination

All list endpoints that may return more than one screen of data **must** be paginated. There is no unpaginated list endpoint for user-facing resources.

### Query parameters

| Param  | Type | Default | Notes                             |
|--------|------|---------|-----------------------------------|
| `page` | int  | `0`     | Zero-based page index.            |
| `size` | int  | varies  | Items per page. Max 100 enforced server-side. |

### Response envelope

List endpoints return a `data` object with the following keys inside the standard `ApiResponse` wrapper:

```json
{
  "data": {
    "items": [ /* array of resource objects */ ],
    "page": 0,
    "size": 50,
    "totalElements": 153,
    "totalPages": 4
  }
}
```

### Backend implementation pattern

```java
private static final int MAX_PAGE_SIZE = 100;

public Map<String, Object> list(int page, int size) {
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    int safePage = Math.max(page, 0);
    Page<MyEntity> p = repository.findAllByOrderBy...(PageRequest.of(safePage, safeSize));
    Map<String, Object> out = new HashMap<>();
    out.put("items", p.getContent().stream().map(this::toDto).toList());
    out.put("totalElements", p.getTotalElements());
    out.put("page", p.getNumber());
    out.put("size", p.getSize());
    out.put("totalPages", p.getTotalPages());
    return out;
}
```

Controller `@GetMapping` accepts `@RequestParam(name = "page", defaultValue = "0") int page` and `@RequestParam(name = "size", defaultValue = "50") int size`.

### Frontend implementation pattern

```ts
export async function fetchMyResources(page = 0, size = 50): Promise<MyPageResult> {
  const q = new URLSearchParams({ page: String(page), size: String(size) });
  const r = await fetch(bffUrl(`/api/v1/.../resources?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const d = (await r.json()).data;
  return { ok: true, items: d.items, page: d.page, size: d.size, totalElements: d.totalElements, totalPages: d.totalPages };
}
```

The page component holds `const [page, setPage] = useState(0)` and `const [totalPages, setTotalPages] = useState(1)`, passes `page` to the fetch, and renders prev/next buttons when `totalPages > 1`. After a create action the page resets to `0`.

## Related

- Local **API vs Next.js dev ports** and CORS origins: [LOCAL-DEV-PORTS.md](./LOCAL-DEV-PORTS.md).
- Proxy and forwarded headers: [CROSS-CUTTING-SECURITY.md](./CROSS-CUTTING-SECURITY.md).
- Database and migrations: [LIQUIBASE-RULES.md](./LIQUIBASE-RULES.md).
- Filter/sort URL conventions: [FILTER-FIELD-STANDARDS.md](./FILTER-FIELD-STANDARDS.md).
