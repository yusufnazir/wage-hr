/**
 * Same-origin BFF client: the browser only calls `/api/bff/...` on this Next.js app.
 * The Next server proxies to Spring using `API_BASE_URL` (server-only).
 */

export function bffUrl(apiPath: string): string {
  if (!apiPath.startsWith("/api/v1")) {
    throw new Error(`Unexpected API path: ${apiPath}`);
  }
  return `/api/bff${apiPath.slice("/api".length)}`;
}

async function readFailureMessage(r: Response): Promise<string> {
  const text = await r.text();
  return `Request failed: ${r.status} ${text.slice(0, 200)}`;
}

export async function postJson(path: string, body: unknown, okStatuses: number[]): Promise<void> {
  const r = await fetch(bffUrl(path), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!okStatuses.includes(r.status)) {
    throw new Error(await readFailureMessage(r));
  }
}

export async function patchJson(path: string, body: unknown, okStatuses: number[]): Promise<void> {
  const r = await fetch(bffUrl(path), {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!okStatuses.includes(r.status)) {
    throw new Error(await readFailureMessage(r));
  }
}

export async function patchMeLocale(locale: string): Promise<void> {
  await patchJson("/api/v1/me/locale", { locale }, [204]);
}

export type PrivacyExport = {
  exportSchemaVersion: number;
  generatedAt: string;
  account: Record<string, unknown>;
  tenantMemberships: TenantSummary[];
};

/** Subject access export — GET; logs `SUBJECT_DATA_EXPORTED` on the server. */
export async function fetchPrivacyExport(): Promise<PrivacyExport> {
  const r = await fetch(bffUrl("/api/v1/me/privacy/export"), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    throw new Error(`Privacy export failed: ${r.status}`);
  }
  const body = (await r.json()) as ApiEnvelope<{ export: PrivacyExport }>;
  return body.data.export;
}

/** Erasure request stub — 202; logs `SUBJECT_ERASURE_REQUESTED`; optional `note` ≤ 500 chars. */
export async function postPrivacyErasureRequest(note?: string): Promise<void> {
  await postJson("/api/v1/me/privacy/erasure-request", note ? { note } : {}, [202]);
}

export async function loginJson(email: string, password: string): Promise<void> {
  const r = await fetch(bffUrl("/api/v1/auth/login"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!r.ok) {
    throw new Error(`Login failed: ${r.status}`);
  }
}

export async function registerJson(email: string, password: string): Promise<void> {
  await postJson("/api/v1/auth/register", { email, password }, [201]);
}

export async function forgotPasswordJson(email: string): Promise<void> {
  await postJson("/api/v1/auth/forgot-password", { email }, [202]);
}

export async function resetPasswordJson(token: string, newPassword: string): Promise<void> {
  await postJson("/api/v1/auth/reset-password", { token, newPassword }, [204]);
}

export type ApiEnvelope<T> = { data: T; meta: { requestId: string } };

export type MePayload = {
  email: string;
  locale: string;
  privileges: string[];
  tenantHandle: string | null;
  platformSuperadmin: boolean;
};

export type NavigationItem = {
  id: string;
  path: string;
  labelKey: string;
  sortOrder: number;
  children: NavigationItem[];
};

export type NavigationFetchResult =
  | { ok: true; items: NavigationItem[] }
  | { ok: false; status: number };

export type MeFetchResult = { ok: true; me: MePayload } | { ok: false; status: number };

export type TenantSummary = {
  id: string;
  handle: string;
  name: string;
  roles: string[];
};

export type MeTenantsFetchResult =
  | { ok: true; tenants: TenantSummary[] }
  | { ok: false; status: number };

/**
 * Authenticated session + optional tenant context from {@code Host} (forwarded by the BFF as
 * {@code X-Forwarded-Host}).
 */
export async function fetchMe(): Promise<MeFetchResult> {
  const r = await fetch(bffUrl("/api/v1/me"), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<
    MePayload & { platformSuperadmin?: boolean; locale?: string }
  >;
  const raw = body.data;
  const me: MePayload = {
    email: raw.email,
    locale: raw.locale ?? "en",
    privileges: raw.privileges,
    tenantHandle: raw.tenantHandle,
    platformSuperadmin: raw.platformSuperadmin ?? false,
  };
  return { ok: true, me };
}

/** All tenant memberships for the signed-in user (no tenant host required). */
export async function fetchMeTenants(): Promise<MeTenantsFetchResult> {
  const r = await fetch(bffUrl("/api/v1/me/tenants"), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ tenants: TenantSummary[] }>;
  return { ok: true, tenants: body.data.tenants };
}

/** Effective navigation tree for the current session + tenant host (or {@code X-Tenant-Id}). */
export async function fetchNavigation(): Promise<NavigationFetchResult> {
  const r = await fetch(bffUrl("/api/v1/me/navigation"), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ items: NavigationItem[] }>;
  return { ok: true, items: body.data.items };
}

export type DemoUserViewResult =
  | { ok: true; message: string }
  | { ok: false; status: number };

/** Requires {@code USER_VIEW} in current tenant context. */
export async function fetchDemoUserView(): Promise<DemoUserViewResult> {
  const r = await fetch(bffUrl("/api/v1/demo/user-view"), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ message: string }>;
  return { ok: true, message: body.data.message };
}

/**
 * Validates {@code returnTo} for post-login navigation (open-redirect guard).
 * Backend: {@code GET /api/v1/auth/redirect-check} → 204 when allowed, 400 when not.
 */
export async function redirectCheck(returnTo: string): Promise<boolean> {
  const path = bffUrl("/api/v1/auth/redirect-check");
  const q = new URLSearchParams({ returnTo });
  const r = await fetch(`${path}?${q}`, { credentials: "same-origin" });
  return r.status === 204;
}
