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

export async function postJson(
  path: string,
  body: unknown,
  okStatuses: number[],
  extraHeaders?: Record<string, string>,
): Promise<void> {
  const r = await fetch(bffUrl(path), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json", ...(extraHeaders ?? {}) },
    body: JSON.stringify(body),
  });
  if (!okStatuses.includes(r.status)) {
    throw new Error(await readFailureMessage(r));
  }
}

export async function patchJson(
  path: string,
  body: unknown,
  okStatuses: number[],
  extraHeaders?: Record<string, string>,
): Promise<void> {
  const r = await fetch(bffUrl(path), {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json", ...(extraHeaders ?? {}) },
    body: JSON.stringify(body),
  });
  if (!okStatuses.includes(r.status)) {
    throw new Error(await readFailureMessage(r));
  }
}

export async function putJson(
  path: string,
  body: unknown,
  okStatuses: number[],
  extraHeaders?: Record<string, string>,
): Promise<void> {
  const r = await fetch(bffUrl(path), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json", ...(extraHeaders ?? {}) },
    body: JSON.stringify(body),
  });
  if (!okStatuses.includes(r.status)) {
    throw new Error(await readFailureMessage(r));
  }
}

export async function patchMeLocale(locale: string): Promise<void> {
  await patchJson("/api/v1/me/locale", { locale }, [204]);
}

export type PlatformSettingEntry = { key: string; value: string };

export type PlatformSettingsFetchResult =
  | { ok: true; entries: PlatformSettingEntry[] }
  | { ok: false; status: number };

/** GET /api/v1/platform/settings — platform superadmin only. */
export async function fetchPlatformSettings(): Promise<PlatformSettingsFetchResult> {
  const r = await fetch(bffUrl("/api/v1/platform/settings"), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ entries: PlatformSettingEntry[] }>;
  return { ok: true, entries: body.data.entries };
}

/** PATCH /api/v1/platform/settings — platform superadmin only; CSRF via BFF. */
export async function patchPlatformSettings(entries: PlatformSettingEntry[]): Promise<void> {
  await patchJson("/api/v1/platform/settings", { entries }, [204]);
}

/** POST /api/v1/platform/settings/mail/test — platform superadmin only; CSRF via BFF. */
export async function postPlatformMailTest(to: string): Promise<void> {
  await postJson("/api/v1/platform/settings/mail/test", { to }, [204]);
}

export type MailTemplateListItem = {
  id: string;
  code: string;
  contentVersion: string;
  active: boolean;
  updatedAt: string;
};

export type MailTemplateLocalePayload = {
  locale: string;
  subject: string;
  bodyHtml: string;
};

export type MailTemplateDetail = MailTemplateListItem & {
  locales: MailTemplateLocalePayload[];
};

export type MailTemplatesListResult =
  | { ok: true; items: MailTemplateListItem[] }
  | { ok: false; status: number };

/** GET /api/v1/platform/mail-templates — platform superadmin only. */
export async function fetchPlatformMailTemplates(): Promise<MailTemplatesListResult> {
  const r = await fetch(bffUrl("/api/v1/platform/mail-templates"), { credentials: "same-origin" });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ items: MailTemplateListItem[] }>;
  return { ok: true, items: body.data.items };
}

export type MailTemplateOneResult =
  | { ok: true; item: MailTemplateDetail }
  | { ok: false; status: number };

export async function fetchPlatformMailTemplate(templateId: string): Promise<MailTemplateOneResult> {
  const r = await fetch(bffUrl(`/api/v1/platform/mail-templates/${encodeURIComponent(templateId)}`), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ item: MailTemplateDetail }>;
  return { ok: true, item: body.data.item };
}

/** PUT /api/v1/platform/mail-templates/{id} — platform superadmin; CSRF via BFF. */
export async function putPlatformMailTemplate(
  templateId: string,
  body: { ifUpdatedAt: string; active: boolean; locales: MailTemplateLocalePayload[] },
): Promise<void> {
  await putJson(`/api/v1/platform/mail-templates/${encodeURIComponent(templateId)}`, body, [204]);
}

export type PlatformRoleTemplate = {
  id: string;
  code: string;
  displayName: string;
  privilegeCodes: string[];
};

export type PlatformRoleTemplatesFetchResult =
  | { ok: true; items: PlatformRoleTemplate[] }
  | { ok: false; status: number };

/** GET /api/v1/platform/role-templates — platform superadmin only. */
export async function fetchPlatformRoleTemplates(): Promise<PlatformRoleTemplatesFetchResult> {
  const r = await fetch(bffUrl("/api/v1/platform/role-templates"), { credentials: "same-origin" });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ items: PlatformRoleTemplate[] }>;
  return { ok: true, items: body.data.items };
}

export type PlatformRoleTemplateOneResult =
  | { ok: true; item: PlatformRoleTemplate }
  | { ok: false; status: number };

export async function fetchPlatformRoleTemplate(templateId: string): Promise<PlatformRoleTemplateOneResult> {
  const r = await fetch(bffUrl(`/api/v1/platform/role-templates/${encodeURIComponent(templateId)}`), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformRoleTemplate }>;
  return { ok: true, item: body.data.item };
}

export async function createPlatformRoleTemplate(args: {
  code: string;
  displayName: string;
  privilegeCodes: string[];
}): Promise<PlatformRoleTemplate> {
  const r = await fetch(bffUrl("/api/v1/platform/role-templates"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(args),
  });
  if (r.status !== 201) {
    throw new Error(await readFailureMessage(r));
  }
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformRoleTemplate }>;
  return body.data.item;
}

export async function patchPlatformRoleTemplate(args: {
  id: string;
  displayName?: string;
  privilegeCodes?: string[];
}): Promise<PlatformRoleTemplate> {
  const r = await fetch(bffUrl(`/api/v1/platform/role-templates/${encodeURIComponent(args.id)}`), {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ displayName: args.displayName, privilegeCodes: args.privilegeCodes }),
  });
  if (r.status !== 200) {
    throw new Error(await readFailureMessage(r));
  }
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformRoleTemplate }>;
  return body.data.item;
}

export type PlatformPrivilegeCatalogEntry = {
  code: string;
  action: string | null;
  resource: string | null;
  description: string | null;
};

export type PlatformPrivilegeCatalogFetchResult =
  | { ok: true; entries: PlatformPrivilegeCatalogEntry[] }
  | { ok: false; status: number };

/** GET /api/v1/platform/privileges/catalog — platform superadmin only. */
export async function fetchPlatformPrivilegeCatalog(): Promise<PlatformPrivilegeCatalogFetchResult> {
  const r = await fetch(bffUrl("/api/v1/platform/privileges/catalog"), { credentials: "same-origin" });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ entries: PlatformPrivilegeCatalogEntry[] }>;
  return { ok: true, entries: body.data.entries };
}

export type PlatformTenantRow = {
  id: string;
  handle: string;
  name: string;
  createdAt: string;
  updatedAt: string;
};

export type PlatformTenantsPageResult =
  | {
      ok: true;
      items: PlatformTenantRow[];
      totalElements: number;
      page: number;
      size: number;
      totalPages: number;
    }
  | { ok: false; status: number };

/** GET /api/v1/platform/tenants — platform superadmin only. */
export async function fetchPlatformTenants(page = 0, size = 20): Promise<PlatformTenantsPageResult> {
  const q = new URLSearchParams({ page: String(page), size: String(size) });
  const r = await fetch(bffUrl(`/api/v1/platform/tenants?${q}`), { credentials: "same-origin" });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{
    items: PlatformTenantRow[];
    totalElements: number;
    page: number;
    size: number;
    totalPages: number;
  }>;
  const d = body.data;
  return {
    ok: true,
    items: d.items,
    totalElements: d.totalElements,
    page: d.page,
    size: d.size,
    totalPages: d.totalPages,
  };
}

export type PlatformTenantOneResult = { ok: true; tenant: PlatformTenantRow } | { ok: false; status: number };

export async function fetchPlatformTenant(tenantId: string): Promise<PlatformTenantOneResult> {
  const r = await fetch(bffUrl(`/api/v1/platform/tenants/${tenantId}`), { credentials: "same-origin" });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ tenant: PlatformTenantRow }>;
  return { ok: true, tenant: body.data.tenant };
}

/** POST /api/v1/platform/tenants — returns created row. */
export async function postPlatformTenant(handle: string, name: string): Promise<PlatformTenantRow> {
  const r = await fetch(bffUrl("/api/v1/platform/tenants"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ handle, name }),
  });
  if (r.status !== 201) {
    throw new Error(await readFailureMessage(r));
  }
  const body = (await r.json()) as ApiEnvelope<{ tenant: PlatformTenantRow }>;
  return body.data.tenant;
}

export async function patchPlatformTenantName(tenantId: string, name: string): Promise<PlatformTenantRow> {
  const r = await fetch(bffUrl(`/api/v1/platform/tenants/${tenantId}`), {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
  if (r.status !== 200) {
    throw new Error(await readFailureMessage(r));
  }
  const body = (await r.json()) as ApiEnvelope<{ tenant: PlatformTenantRow }>;
  return body.data.tenant;
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

/** Ends server session and clears relay cookies (BFF mirrors Set-Cookie). */
export async function postLogout(): Promise<void> {
  await postJson("/api/v1/auth/logout", {}, [200]);
}

export class EmailNotVerifiedError extends Error {
  constructor() {
    super("EMAIL_NOT_VERIFIED");
    this.name = "EmailNotVerifiedError";
  }
}

export async function loginJson(email: string, password: string): Promise<void> {
  const r = await fetch(bffUrl("/api/v1/auth/login"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (r.status === 403) {
    const text = await r.text();
    if (text.includes("EMAIL_NOT_VERIFIED")) {
      throw new EmailNotVerifiedError();
    }
    throw new Error(`Login failed: 403 ${text.slice(0, 200)}`);
  }
  if (!r.ok) {
    throw new Error(await readFailureMessage(r));
  }
}

export type AccountRegisterResult = {
  status: string;
  tenantHandle: string;
};

export type AccountRegisterPayload = {
  email: string;
  password: string;
  tenantHandle: string;
  firstName: string;
  lastName: string;
  agreeToTermsOfService: boolean;
  agreeToPrivacyPolicy: boolean;
};

export async function registerJson(payload: AccountRegisterPayload): Promise<AccountRegisterResult> {
  const r = await fetch(bffUrl("/api/v1/auth/register"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) {
    throw new Error(await readFailureMessage(r));
  }
  const body = (await r.json()) as { data: AccountRegisterResult; meta: { requestId: string } };
  return body.data;
}

export async function verifyEmailJson(token: string): Promise<void> {
  await postJson("/api/v1/auth/verify-email", { token }, [204]);
}

export async function resendVerificationJson(email: string): Promise<void> {
  await postJson("/api/v1/auth/resend-verification", { email }, [202]);
}

export async function forgotPasswordJson(email: string): Promise<void> {
  await postJson("/api/v1/auth/forgot-password", { email }, [202]);
}

export async function resetPasswordJson(token: string, newPassword: string): Promise<void> {
  await postJson("/api/v1/auth/reset-password", { token, newPassword }, [204]);
}

export type ApiEnvelope<T> = { data: T; meta: { requestId: string } };

export type MePayload = {
  /** Authenticated principal {@code user_account.id}. */
  userId: string;
  email: string;
  locale: string;
  privileges: string[];
  /** Active commercial plan feature codes for the current tenant host; empty when none or not subscribed. */
  planFeatureCodes: string[];
  tenantHandle: string | null;
  /** Resolved tenant id when host or BFF lens supplies tenant context; null otherwise. */
  tenantId: string | null;
  platformSuperadmin: boolean;
  /** From platform settings when tenant context is present; defaults applied client-side when absent. */
  applicationName: string;
  dateFormat: string;
  publicBaseUrl: string;
};

export type PublicSurfacePayload = {
  applicationName: string;
  publicBaseUrl: string;
  dateFormat: string;
};

export type PlatformCurrencyRow = {
  id: string;
  code: string;
  displayName: string;
  sortOrder: number;
  active: boolean;
  updatedAt: string;
};

export type PlatformCurrenciesResult =
  | { ok: true; items: PlatformCurrencyRow[]; totalElements: number; page: number; size: number; totalPages: number }
  | { ok: false; status: number };

/** GET /api/v1/platform/currencies — platform superadmin only. */
export async function fetchPlatformCurrencies(page = 0, size = 50): Promise<PlatformCurrenciesResult> {
  const q = new URLSearchParams({ page: String(page), size: String(size) });
  const r = await fetch(bffUrl(`/api/v1/platform/currencies?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ items: PlatformCurrencyRow[]; totalElements: number; page: number; size: number; totalPages: number }>;
  const d = body.data;
  return { ok: true, items: d.items, totalElements: d.totalElements, page: d.page, size: d.size, totalPages: d.totalPages };
}

/** GET /api/v1/platform/currencies/{id} — platform superadmin only. */
export async function fetchPlatformCurrency(
  id: string,
): Promise<{ ok: true; item: PlatformCurrencyRow } | { ok: false; status: number }> {
  const r = await fetch(bffUrl(`/api/v1/platform/currencies/${encodeURIComponent(id)}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformCurrencyRow }>;
  return { ok: true, item: body.data.item };
}

/** POST /api/v1/platform/currencies — platform superadmin only. */
export async function createPlatformCurrency(args: {
  code: string;
  displayName: string;
  sortOrder?: number;
  active?: boolean;
}): Promise<PlatformCurrencyRow> {
  const r = await fetch(bffUrl("/api/v1/platform/currencies"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(args),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformCurrencyRow }>;
  return body.data.item;
}

/** PATCH /api/v1/platform/currencies/{id} — platform superadmin only. */
export async function patchPlatformCurrency(
  id: string,
  patch: { displayName?: string; sortOrder?: number; active?: boolean },
): Promise<PlatformCurrencyRow> {
  const r = await fetch(bffUrl(`/api/v1/platform/currencies/${id}`), {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(patch),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformCurrencyRow }>;
  return body.data.item;
}

export type PlatformCountryTranslation = {
  locale: string;
  name: string;
};

export type PlatformCountryRow = {
  id: string;
  isoAlpha2: string;
  isoAlpha3: string;
  isoNumeric: string;
  dialCode: string | null;
  active: boolean;
  name: string;
  translations: PlatformCountryTranslation[];
  updatedAt: string;
};

export type PlatformCountriesResult =
  | {
      ok: true;
      items: PlatformCountryRow[];
      totalElements: number;
      page: number;
      size: number;
      totalPages: number;
    }
  | { ok: false; status: number };

/** GET /api/v1/platform/countries — platform superadmin only. */
export async function fetchPlatformCountries(args?: {
  page?: number;
  size?: number;
  search?: string;
  active?: boolean | null;
  locale?: string;
}): Promise<PlatformCountriesResult> {
  const q = new URLSearchParams({
    page: String(args?.page ?? 0),
    size: String(args?.size ?? 50),
    locale: args?.locale ?? "en",
  });
  if (args?.search?.trim()) q.set("search", args.search.trim());
  if (typeof args?.active === "boolean") q.set("active", String(args.active));
  const r = await fetch(bffUrl(`/api/v1/platform/countries?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    items: PlatformCountryRow[];
    totalElements: number;
    page: number;
    size: number;
    totalPages: number;
  }>;
  const d = body.data;
  return {
    ok: true,
    items: d.items,
    totalElements: d.totalElements,
    page: d.page,
    size: d.size,
    totalPages: d.totalPages,
  };
}

/** GET /api/v1/platform/countries/{id} — platform superadmin only. */
export async function fetchPlatformCountry(
  id: string,
  locale = "en",
): Promise<{ ok: true; item: PlatformCountryRow } | { ok: false; status: number }> {
  const r = await fetch(
    bffUrl(`/api/v1/platform/countries/${encodeURIComponent(id)}?locale=${encodeURIComponent(locale)}`),
    { credentials: "same-origin" },
  );
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformCountryRow }>;
  return { ok: true, item: body.data.item };
}

export type PlatformCountryUpsertRequest = {
  isoAlpha2: string;
  isoAlpha3: string;
  isoNumeric: string;
  dialCode?: string | null;
  active?: boolean;
  translations: PlatformCountryTranslation[];
};

/** POST /api/v1/platform/countries — platform superadmin only. */
export async function createPlatformCountry(payload: PlatformCountryUpsertRequest): Promise<PlatformCountryRow> {
  const r = await fetch(bffUrl("/api/v1/platform/countries"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformCountryRow }>;
  return body.data.item;
}

/** PUT /api/v1/platform/countries/{id} — platform superadmin only. */
export async function putPlatformCountry(id: string, payload: PlatformCountryUpsertRequest): Promise<PlatformCountryRow> {
  const r = await fetch(bffUrl(`/api/v1/platform/countries/${encodeURIComponent(id)}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformCountryRow }>;
  return body.data.item;
}

/** PATCH /api/v1/platform/countries/{id}/activate — platform superadmin only. */
export async function patchActivatePlatformCountry(id: string): Promise<PlatformCountryRow> {
  const r = await fetch(bffUrl(`/api/v1/platform/countries/${encodeURIComponent(id)}/activate`), {
    method: "PATCH",
    credentials: "same-origin",
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformCountryRow }>;
  return body.data.item;
}

/** PATCH /api/v1/platform/countries/{id}/deactivate — platform superadmin only. */
export async function patchDeactivatePlatformCountry(id: string): Promise<PlatformCountryRow> {
  const r = await fetch(bffUrl(`/api/v1/platform/countries/${encodeURIComponent(id)}/deactivate`), {
    method: "PATCH",
    credentials: "same-origin",
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformCountryRow }>;
  return body.data.item;
}

export type CountriesResult =
  | {
      ok: true;
      items: PlatformCountryRow[];
      totalElements: number;
      page: number;
      size: number;
      totalPages: number;
    }
  | { ok: false; status: number };

/** GET /api/v1/countries — authenticated users; active-only. */
export async function fetchCountries(args?: {
  page?: number;
  size?: number;
  search?: string;
  locale?: string;
}): Promise<CountriesResult> {
  const q = new URLSearchParams({
    page: String(args?.page ?? 0),
    size: String(args?.size ?? 50),
    locale: args?.locale ?? "en",
  });
  if (args?.search?.trim()) q.set("search", args.search.trim());
  const r = await fetch(bffUrl(`/api/v1/countries?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    items: PlatformCountryRow[];
    totalElements: number;
    page: number;
    size: number;
    totalPages: number;
  }>;
  const d = body.data;
  return {
    ok: true,
    items: d.items,
    totalElements: d.totalElements,
    page: d.page,
    size: d.size,
    totalPages: d.totalPages,
  };
}

export type TenantCurrencyItem = {
  id: string;
  code: string;
  displayName: string;
  sortOrder: number;
  assigned: boolean;
};

export type TenantCurrenciesResult =
  | { ok: true; items: TenantCurrencyItem[]; assignedCodes: string[] }
  | { ok: false; status: number };

/** GET /api/v1/tenant/currencies — requires TENANT_CURRENCY_VIEW. */
export async function fetchTenantCurrencies(): Promise<TenantCurrenciesResult> {
  const r = await fetch(bffUrl("/api/v1/tenant/currencies"), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ items: TenantCurrencyItem[]; assignedCodes: string[] }>;
  return { ok: true, items: body.data.items, assignedCodes: body.data.assignedCodes };
}

/** PUT /api/v1/tenant/currencies — requires TENANT_CURRENCY_EDIT. */
export async function replaceTenantCurrencies(codes: string[]): Promise<void> {
  const r = await fetch(bffUrl("/api/v1/tenant/currencies"), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ codes }),
  });
  if (!r.ok && r.status !== 204) throw new Error(await readFailureMessage(r));
}

export type TenantExchangeRateItem = {
  id: string;
  fromCurrencyId: string;
  fromCurrencyCode: string;
  fromCurrencyDisplayName: string;
  toCurrencyId: string;
  toCurrencyCode: string;
  toCurrencyDisplayName: string;
  rate: number;
  effectiveDate: string;
  createdAt: string;
  updatedAt: string;
};

export type TenantExchangeRatesPageResult =
  | {
      ok: true;
      items: TenantExchangeRateItem[];
      page: number;
      size: number;
      totalElements: number;
      totalPages: number;
    }
  | { ok: false; status: number };

/** GET /api/v1/tenant/exchange-rates — requires EXCHANGE_RATE_VIEW. */
export async function fetchTenantExchangeRates(
  page = 0,
  size = 20,
  sort = "effectiveDate,desc",
): Promise<TenantExchangeRatesPageResult> {
  const q = new URLSearchParams({ page: String(page), size: String(size), sort });
  const r = await fetch(bffUrl(`/api/v1/tenant/exchange-rates?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    data: TenantExchangeRateItem[];
    page: { number: number; size: number; totalElements: number; totalPages: number };
  }>;
  return {
    ok: true,
    items: body.data.data,
    page: body.data.page.number,
    size: body.data.page.size,
    totalElements: body.data.page.totalElements,
    totalPages: body.data.page.totalPages,
  };
}

export type TenantExchangeRateMutationResult =
  | { ok: true; item: TenantExchangeRateItem }
  | { ok: false; status: number };

/** POST /api/v1/tenant/exchange-rates — requires EXCHANGE_RATE_MANAGE. */
export async function createTenantExchangeRate(payload: {
  fromCurrencyId: string;
  toCurrencyId: string;
  rate: string;
  effectiveDate: string;
}): Promise<TenantExchangeRateMutationResult> {
  const r = await fetch(bffUrl("/api/v1/tenant/exchange-rates"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: TenantExchangeRateItem }>;
  return { ok: true, item: body.data.item };
}

/** PATCH /api/v1/tenant/exchange-rates/{id} — requires EXCHANGE_RATE_MANAGE. */
export async function patchTenantExchangeRate(
  id: string,
  payload: { rate?: string; effectiveDate?: string },
): Promise<TenantExchangeRateMutationResult> {
  const r = await fetch(bffUrl(`/api/v1/tenant/exchange-rates/${encodeURIComponent(id)}`), {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: TenantExchangeRateItem }>;
  return { ok: true, item: body.data.item };
}

export type TenantExchangeRateDeleteResult = { ok: true } | { ok: false; status: number };

/** DELETE /api/v1/tenant/exchange-rates/{id} — requires EXCHANGE_RATE_MANAGE. */
export async function deleteTenantExchangeRate(id: string): Promise<TenantExchangeRateDeleteResult> {
  const r = await fetch(bffUrl(`/api/v1/tenant/exchange-rates/${encodeURIComponent(id)}`), {
    method: "DELETE",
    credentials: "same-origin",
  });
  if (r.status === 204) return { ok: true };
  return { ok: false, status: r.status };
}

export type PublicSurfaceFetchResult =
  | { ok: true; surface: PublicSurfacePayload }
  | { ok: false; status: number };

/** GET /api/v1/platform/public-surface — permitAll; no session. */
export async function fetchPublicSurface(): Promise<PublicSurfaceFetchResult> {
  const r = await fetch(bffUrl("/api/v1/platform/public-surface"), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<PublicSurfacePayload>;
  return { ok: true, surface: body.data };
}

export type NavigationItem = {
  id: string;
  path: string | null;
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

export type TenantBillingSubscriptionSnapshot = {
  status: string;
  commercialPlanId: string;
  commercialPlanCode: string | null;
};

export type TenantBillingSummary = {
  stripeBillingEnabled: boolean;
  paypalBillingEnabled: boolean;
  stripeCustomerLinked: boolean;
  paypalCustomerLinked: boolean;
  subscription: TenantBillingSubscriptionSnapshot | null;
};

export type BillingSummaryFetchResult =
  | { ok: true; summary: TenantBillingSummary }
  | { ok: false; status: number };

export type CommercialPlanListItem = {
  id: string;
  code: string;
  sortOrder: number;
  active: boolean;
  featureCount: number;
  stripeSubscriptionPriceId: string | null;
  paypalBillingPlanId: string | null;
};

export type TenantCommercialPlansFetchResult =
  | { ok: true; plans: CommercialPlanListItem[] }
  | { ok: false; status: number };

/** GET /api/v1/tenant/billing/commercial-plans — requires TENANT_SETTINGS_EDIT; active plans only (for checkout / subscribe UI). */
export async function fetchTenantCommercialPlans(): Promise<TenantCommercialPlansFetchResult> {
  const r = await fetch(bffUrl("/api/v1/tenant/billing/commercial-plans"), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ plans: CommercialPlanListItem[] }>;
  return { ok: true, plans: body.data.plans };
}

/** GET /api/v1/tenant/billing/summary — requires USER_VIEW; provider flags, link presence, optional subscription snapshot. */
export async function fetchBillingSummary(): Promise<BillingSummaryFetchResult> {
  const r = await fetch(bffUrl("/api/v1/tenant/billing/summary"), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ summary: TenantBillingSummary }>;
  return { ok: true, summary: body.data.summary };
}

export type StripePortalSessionResult = { ok: true; url: string } | { ok: false; status: number };

export type StripeCheckoutSessionResult = { ok: true; url: string } | { ok: false; status: number };

/** POST /api/v1/tenant/billing/stripe/checkout-session — requires TENANT_SETTINGS_EDIT + linked Stripe customer + active plan with matching price. */
export async function createStripeCheckoutSession(args: {
  commercialPlanId: string;
  priceId: string;
  successUrl: string;
  cancelUrl: string;
}): Promise<StripeCheckoutSessionResult> {
  const r = await fetch(bffUrl("/api/v1/tenant/billing/stripe/checkout-session"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      commercialPlanId: args.commercialPlanId,
      priceId: args.priceId,
      successUrl: args.successUrl,
      cancelUrl: args.cancelUrl,
    }),
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ url: string }>;
  return { ok: true, url: body.data.url };
}

export type PaypalSubscriptionSessionResult = { ok: true; approvalUrl: string } | { ok: false; status: number };

/** POST /api/v1/tenant/billing/paypal/subscription — requires TENANT_SETTINGS_EDIT; returns PayPal approval URL. */
export async function createPaypalSubscriptionSession(args: {
  commercialPlanId: string;
  planId: string;
  returnUrl: string;
  cancelUrl: string;
}): Promise<PaypalSubscriptionSessionResult> {
  const r = await fetch(bffUrl("/api/v1/tenant/billing/paypal/subscription"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      commercialPlanId: args.commercialPlanId,
      planId: args.planId,
      returnUrl: args.returnUrl,
      cancelUrl: args.cancelUrl,
    }),
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ approvalUrl: string }>;
  return { ok: true, approvalUrl: body.data.approvalUrl };
}

/** POST /api/v1/tenant/billing/stripe/billing-portal-session — requires TENANT_SETTINGS_EDIT + linked Stripe customer. */
export async function createStripeBillingPortalSession(returnUrl: string): Promise<StripePortalSessionResult> {
  const r = await fetch(bffUrl("/api/v1/tenant/billing/stripe/billing-portal-session"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ returnUrl }),
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ url: string }>;
  return { ok: true, url: body.data.url };
}

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
    userId: (raw as { userId?: string }).userId ?? "",
    email: raw.email,
    locale: raw.locale ?? "en",
    privileges: raw.privileges,
    planFeatureCodes: raw.planFeatureCodes ?? [],
    tenantHandle: raw.tenantHandle,
    tenantId: (raw as { tenantId?: string | null }).tenantId ?? null,
    platformSuperadmin: raw.platformSuperadmin ?? false,
    applicationName: (raw as { applicationName?: string }).applicationName ?? "Wage Payroll",
    dateFormat: (raw as { dateFormat?: string }).dateFormat ?? "yyyy-MM-dd",
    publicBaseUrl: (raw as { publicBaseUrl?: string }).publicBaseUrl ?? "",
  };
  return { ok: true, me };
}

/** Set HttpOnly lens cookie; next BFF calls send {@code X-Tenant-Id} to Spring (superadmin tenant lens). */
export async function postLensTenant(tenantId: string): Promise<void> {
  const r = await fetch("/api/bff/lens-tenant", {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ tenantId }),
  });
  if (!r.ok) {
    throw new Error(await readFailureMessage(r));
  }
}

/** Clear lens cookie. */
export async function clearLensTenant(): Promise<void> {
  const r = await fetch("/api/bff/lens-tenant", {
    method: "DELETE",
    credentials: "same-origin",
  });
  if (!r.ok) {
    throw new Error(await readFailureMessage(r));
  }
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

export type TenantUserListProbeResult =
  | { ok: true; totalElements: number }
  | { ok: false; status: number };

/** GET /api/v1/tenant/users — requires {@code USER_VIEW} in current tenant context (first page probe). */
export async function fetchTenantUserListProbe(): Promise<TenantUserListProbeResult> {
  const r = await fetch(bffUrl("/api/v1/tenant/users?page=0&size=1"), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{
    totalElements: number;
  }>;
  return { ok: true, totalElements: body.data.totalElements };
}

export type TenantUserListItem = {
  userId: string;
  email: string;
  status: string;
  lastActiveAt: string | null;
  roleNames: string[];
};

export type TenantUsersPageResult =
  | {
      ok: true;
      items: TenantUserListItem[];
      totalElements: number;
      page: number;
      size: number;
      totalPages: number;
    }
  | { ok: false; status: number };

export async function fetchTenantUsersPage(params: {
  page?: number;
  size?: number;
  sort?: string;
  email?: string;
  status?: string;
  role?: string;
}): Promise<TenantUsersPageResult> {
  const q = new URLSearchParams();
  q.set("page", String(params.page ?? 0));
  q.set("size", String(params.size ?? 20));
  if (params.sort) q.set("sort", params.sort);
  if (params.email?.trim()) q.set("email", params.email.trim());
  if (params.status?.trim()) q.set("status", params.status.trim());
  if (params.role?.trim()) q.set("role", params.role.trim());
  const r = await fetch(bffUrl(`/api/v1/tenant/users?${q}`), { credentials: "same-origin" });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{
    items: TenantUserListItem[];
    totalElements: number;
    page: number;
    size: number;
    totalPages: number;
  }>;
  const d = body.data;
  return {
    ok: true,
    items: d.items,
    totalElements: d.totalElements,
    page: d.page,
    size: d.size,
    totalPages: d.totalPages,
  };
}

export type TenantRoleOption = { id: string; name: string };
export type TenantRoleOptionsResult = { ok: true; roles: TenantRoleOption[] } | { ok: false; status: number };

/** GET /api/v1/tenant/users/role-options — requires USER_VIEW. */
export async function fetchTenantUserRoleOptions(): Promise<TenantRoleOptionsResult> {
  const r = await fetch(bffUrl("/api/v1/tenant/users/role-options"), { credentials: "same-origin" });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ roles: TenantRoleOption[] }>;
  return { ok: true, roles: body.data.roles };
}

export type TenantUserDetail = {
  userId: string;
  email: string;
  status: string;
  lastActiveAt: string | null;
  roleNames: string[];
  roleAssignments: { roleId: string; roleName: string }[];
  assignableRoles: { id: string; name: string }[];
};

export type TenantUserDetailResult = { ok: true; user: TenantUserDetail } | { ok: false; status: number };

export async function fetchTenantUserDetail(userId: string): Promise<TenantUserDetailResult> {
  const r = await fetch(bffUrl(`/api/v1/tenant/users/${encodeURIComponent(userId)}`), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ user: TenantUserDetail }>;
  return { ok: true, user: body.data.user };
}

export async function patchTenantUser(
  userId: string,
  body: { email?: string | null; roleIds?: string[] | null },
): Promise<void> {
  await patchJson(`/api/v1/tenant/users/${encodeURIComponent(userId)}`, body, [204]);
}

export type TenantRoleListItem = {
  id: string;
  name: string;
  privilegeCodes: string[];
};

export type TenantRolesListResult = { ok: true; items: TenantRoleListItem[] } | { ok: false; status: number };

/** GET /api/v1/tenant/roles — requires ROLE_VIEW. */
export async function fetchTenantRoles(params?: {
  q?: string;
  sort?: string;
}): Promise<TenantRolesListResult> {
  const q = new URLSearchParams();
  if (params?.q?.trim()) q.set("q", params.q.trim());
  if (params?.sort?.trim()) q.set("sort", params.sort.trim());
  const qs = q.toString();
  const r = await fetch(bffUrl(`/api/v1/tenant/roles${qs ? `?${qs}` : ""}`), { credentials: "same-origin" });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ items: TenantRoleListItem[] }>;
  return { ok: true, items: body.data.items };
}

export type TenantRoleDetail = {
  role: TenantRoleListItem;
  assignablePrivilegeCodes: string[];
};

export type TenantRoleDetailResult = { ok: true; data: TenantRoleDetail } | { ok: false; status: number };

/** GET /api/v1/tenant/roles/{roleId} — requires ROLE_VIEW. */
export async function fetchTenantRoleDetail(roleId: string): Promise<TenantRoleDetailResult> {
  const r = await fetch(bffUrl(`/api/v1/tenant/roles/${encodeURIComponent(roleId)}`), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<TenantRoleDetail>;
  return { ok: true, data: body.data };
}

export async function createTenantRole(args: {
  name: string;
  privilegeCodes?: string[];
  breakGlassReason?: string;
}): Promise<TenantRoleListItem> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (args.breakGlassReason?.trim()) {
    headers["X-Break-Glass-Reason"] = args.breakGlassReason.trim();
  }
  const r = await fetch(bffUrl("/api/v1/tenant/roles"), {
    method: "POST",
    credentials: "same-origin",
    headers,
    body: JSON.stringify({ name: args.name, privilegeCodes: args.privilegeCodes ?? [] }),
  });
  if (r.status !== 201) {
    throw new Error(await readFailureMessage(r));
  }
  const body = (await r.json()) as ApiEnvelope<{ role: TenantRoleListItem }>;
  return body.data.role;
}

export async function patchTenantRole(args: {
  roleId: string;
  name?: string;
  privilegeCodes?: string[];
  breakGlassReason?: string;
}): Promise<TenantRoleListItem> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (args.breakGlassReason?.trim()) {
    headers["X-Break-Glass-Reason"] = args.breakGlassReason.trim();
  }
  const r = await fetch(bffUrl(`/api/v1/tenant/roles/${encodeURIComponent(args.roleId)}`), {
    method: "PATCH",
    credentials: "same-origin",
    headers,
    body: JSON.stringify({ name: args.name, privilegeCodes: args.privilegeCodes }),
  });
  if (r.status !== 200) {
    throw new Error(await readFailureMessage(r));
  }
  const body = (await r.json()) as ApiEnvelope<{ role: TenantRoleListItem }>;
  return body.data.role;
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

export type DocumentHubItem = {
  id: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
  hubSource: string;
};

export type DocumentsHubFetchResult = { ok: true; items: DocumentHubItem[] } | { ok: false; status: number };

/** GET /api/v1/tenant/documents — requires DOCUMENT_VIEW. */
export async function fetchTenantDocumentsHub(): Promise<DocumentsHubFetchResult> {
  const r = await fetch(bffUrl("/api/v1/tenant/documents"), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ items: DocumentHubItem[] }>;
  return { ok: true, items: body.data.items };
}

export type DocumentDownloadUrlResult =
  | { ok: true; downloadUrl: string; expiresAt: string }
  | { ok: false; status: number };

/** GET /api/v1/tenant/documents/{id}/download-url — DOCUMENT_VIEW + readable document. */
export async function fetchDocumentDownloadUrl(documentId: string): Promise<DocumentDownloadUrlResult> {
  const r = await fetch(bffUrl(`/api/v1/tenant/documents/${encodeURIComponent(documentId)}/download-url`), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ downloadUrl: string; expiresAt: string }>;
  return { ok: true, downloadUrl: body.data.downloadUrl, expiresAt: body.data.expiresAt };
}

export type DocumentUploadSession = {
  documentId: string;
  storageKey: string;
  uploadUrl: string;
  uploadMethod: string;
  expiresAt: string;
  requiredHeaders: Record<string, string>;
};

export type DocumentUploadSessionResult =
  | { ok: true; session: DocumentUploadSession }
  | { ok: false; status: number };

/** POST /api/v1/tenant/documents/upload-sessions — DOCUMENT_EDIT; 503 when MinIO not configured. */
export async function createDocumentUploadSession(args: {
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
}): Promise<DocumentUploadSessionResult> {
  const r = await fetch(bffUrl("/api/v1/tenant/documents/upload-sessions"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      originalFilename: args.originalFilename,
      contentType: args.contentType,
      sizeBytes: args.sizeBytes,
    }),
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<DocumentUploadSession>;
  return { ok: true, session: body.data };
}

// ---------------------------------------------------------------------------
// Payroll Org Structure — Companies
// ---------------------------------------------------------------------------

export type TenantCompanyItem = {
  id: string;
  name: string;
  legalName: string | null;
  registrationNumber: string | null;
  taxId: string | null;
  payrollCountry: string;
  currency: string;
  payrollFrequency: string;
  timezone: string;
  dateFormat: string;
  contactEmail: string | null;
  contactPhone: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  stateRegion: string | null;
  postalCode: string | null;
  country: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type TenantCompanyPageResult =
  | { ok: true; items: TenantCompanyItem[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export async function fetchTenantCompanies(args?: {
  page?: number;
  size?: number;
  q?: string;
  active?: boolean | null;
}): Promise<TenantCompanyPageResult> {
  const q = new URLSearchParams({ page: String(args?.page ?? 0), size: String(args?.size ?? 20) });
  if (args?.q?.trim()) q.set("q", args.q.trim());
  if (typeof args?.active === "boolean") q.set("active", String(args.active));
  const r = await fetch(bffUrl(`/api/v1/companies?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    data: TenantCompanyItem[];
    page: { number: number; size: number; totalElements: number; totalPages: number };
  }>;
  return {
    ok: true,
    items: body.data.data,
    page: body.data.page.number,
    size: body.data.page.size,
    totalElements: body.data.page.totalElements,
    totalPages: body.data.page.totalPages,
  };
}

export async function fetchTenantCompany(id: string): Promise<{ ok: true; item: TenantCompanyItem } | { ok: false; status: number }> {
  const r = await fetch(bffUrl(`/api/v1/companies/${encodeURIComponent(id)}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: TenantCompanyItem }>;
  return { ok: true, item: body.data.item };
}

export type TenantCompanyUpsertPayload = {
  name: string;
  legalName?: string | null;
  registrationNumber?: string | null;
  taxId?: string | null;
  payrollCountry: string;
  currency: string;
  payrollFrequency: string;
  timezone: string;
  dateFormat: string;
  contactEmail?: string | null;
  contactPhone?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  stateRegion?: string | null;
  postalCode?: string | null;
  country?: string | null;
  active?: boolean;
};

export async function createTenantCompany(payload: TenantCompanyUpsertPayload): Promise<TenantCompanyItem> {
  const r = await fetch(bffUrl("/api/v1/companies"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantCompanyItem }>;
  return body.data.item;
}

export async function putTenantCompany(id: string, payload: TenantCompanyUpsertPayload): Promise<TenantCompanyItem> {
  const r = await fetch(bffUrl(`/api/v1/companies/${encodeURIComponent(id)}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantCompanyItem }>;
  return body.data.item;
}

export async function patchTenantCompanyActive(id: string, active: boolean): Promise<TenantCompanyItem> {
  const r = await fetch(bffUrl(`/api/v1/companies/${encodeURIComponent(id)}/active`), {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ active }),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantCompanyItem }>;
  return body.data.item;
}

// ---------------------------------------------------------------------------
// Payroll Org Structure — Departments
// ---------------------------------------------------------------------------

export type TenantDepartmentItem = {
  id: string;
  companyId: string;
  name: string;
  code: string;
  description: string | null;
  parentDepartmentId: string | null;
  managerEmployeeId: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type TenantDepartmentPageResult =
  | { ok: true; items: TenantDepartmentItem[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export async function fetchTenantDepartments(args?: {
  page?: number;
  size?: number;
  companyId?: string;
  q?: string;
  active?: boolean | null;
}): Promise<TenantDepartmentPageResult> {
  const q = new URLSearchParams({ page: String(args?.page ?? 0), size: String(args?.size ?? 20) });
  if (args?.companyId) q.set("companyId", args.companyId);
  if (args?.q?.trim()) q.set("q", args.q.trim());
  if (typeof args?.active === "boolean") q.set("active", String(args.active));
  const r = await fetch(bffUrl(`/api/v1/departments?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    data: TenantDepartmentItem[];
    page: { number: number; size: number; totalElements: number; totalPages: number };
  }>;
  return {
    ok: true,
    items: body.data.data,
    page: body.data.page.number,
    size: body.data.page.size,
    totalElements: body.data.page.totalElements,
    totalPages: body.data.page.totalPages,
  };
}

export async function fetchTenantDepartment(id: string): Promise<{ ok: true; item: TenantDepartmentItem } | { ok: false; status: number }> {
  const r = await fetch(bffUrl(`/api/v1/departments/${encodeURIComponent(id)}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: TenantDepartmentItem }>;
  return { ok: true, item: body.data.item };
}

export type TenantDepartmentUpsertPayload = {
  companyId: string;
  name: string;
  code: string;
  description?: string | null;
  parentDepartmentId?: string | null;
  managerEmployeeId?: string | null;
  active?: boolean;
};

export async function createTenantDepartment(payload: TenantDepartmentUpsertPayload): Promise<TenantDepartmentItem> {
  const r = await fetch(bffUrl("/api/v1/departments"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantDepartmentItem }>;
  return body.data.item;
}

export async function putTenantDepartment(id: string, payload: TenantDepartmentUpsertPayload): Promise<TenantDepartmentItem> {
  const r = await fetch(bffUrl(`/api/v1/departments/${encodeURIComponent(id)}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantDepartmentItem }>;
  return body.data.item;
}

export async function patchTenantDepartmentActive(id: string, active: boolean): Promise<TenantDepartmentItem> {
  const r = await fetch(bffUrl(`/api/v1/departments/${encodeURIComponent(id)}/active`), {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ active }),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantDepartmentItem }>;
  return body.data.item;
}

// ---------------------------------------------------------------------------
// Payroll Org Structure — Jobs
// ---------------------------------------------------------------------------

export type TenantJobItem = {
  id: string;
  companyId: string;
  departmentId: string;
  title: string;
  code: string;
  description: string | null;
  salaryType: "HOURLY" | "MONTHLY";
  defaultSalary: number | null;
  defaultHourlyRate: number | null;
  standardHoursPerWeek: number | null;
  jobLevel: string | null;
  jobCategory: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type TenantJobPageResult =
  | { ok: true; items: TenantJobItem[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export async function fetchTenantJobs(args?: {
  page?: number;
  size?: number;
  companyId?: string;
  departmentId?: string;
  q?: string;
  active?: boolean | null;
}): Promise<TenantJobPageResult> {
  const q = new URLSearchParams({ page: String(args?.page ?? 0), size: String(args?.size ?? 20) });
  if (args?.companyId) q.set("companyId", args.companyId);
  if (args?.departmentId) q.set("departmentId", args.departmentId);
  if (args?.q?.trim()) q.set("q", args.q.trim());
  if (typeof args?.active === "boolean") q.set("active", String(args.active));
  const r = await fetch(bffUrl(`/api/v1/jobs?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    data: TenantJobItem[];
    page: { number: number; size: number; totalElements: number; totalPages: number };
  }>;
  return {
    ok: true,
    items: body.data.data,
    page: body.data.page.number,
    size: body.data.page.size,
    totalElements: body.data.page.totalElements,
    totalPages: body.data.page.totalPages,
  };
}

export async function fetchTenantJob(id: string): Promise<{ ok: true; item: TenantJobItem } | { ok: false; status: number }> {
  const r = await fetch(bffUrl(`/api/v1/jobs/${encodeURIComponent(id)}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: TenantJobItem }>;
  return { ok: true, item: body.data.item };
}

export type TenantJobUpsertPayload = {
  companyId: string;
  departmentId: string;
  title: string;
  code: string;
  description?: string | null;
  salaryType: "HOURLY" | "MONTHLY";
  defaultSalary?: number | null;
  defaultHourlyRate?: number | null;
  standardHoursPerWeek?: number | null;
  jobLevel?: string | null;
  jobCategory?: string | null;
  active?: boolean;
};

export async function createTenantJob(payload: TenantJobUpsertPayload): Promise<TenantJobItem> {
  const r = await fetch(bffUrl("/api/v1/jobs"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantJobItem }>;
  return body.data.item;
}

export async function putTenantJob(id: string, payload: TenantJobUpsertPayload): Promise<TenantJobItem> {
  const r = await fetch(bffUrl(`/api/v1/jobs/${encodeURIComponent(id)}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantJobItem }>;
  return body.data.item;
}

export async function patchTenantJobActive(id: string, active: boolean): Promise<TenantJobItem> {
  const r = await fetch(bffUrl(`/api/v1/jobs/${encodeURIComponent(id)}/active`), {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ active }),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantJobItem }>;
  return body.data.item;
}

// ---------------------------------------------------------------------------
// Payroll Org Structure — Employee Groups
// ---------------------------------------------------------------------------

export type TenantEmployeeGroupItem = {
  id: string;
  companyId: string;
  name: string;
  code: string;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type TenantEmployeeGroupPageResult =
  | { ok: true; items: TenantEmployeeGroupItem[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export async function fetchTenantEmployeeGroups(args?: {
  page?: number;
  size?: number;
  companyId?: string;
  q?: string;
  active?: boolean | null;
}): Promise<TenantEmployeeGroupPageResult> {
  const q = new URLSearchParams({ page: String(args?.page ?? 0), size: String(args?.size ?? 20) });
  if (args?.companyId) q.set("companyId", args.companyId);
  if (args?.q?.trim()) q.set("q", args.q.trim());
  if (typeof args?.active === "boolean") q.set("active", String(args.active));
  const r = await fetch(bffUrl(`/api/v1/employee-groups?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    data: TenantEmployeeGroupItem[];
    page: { number: number; size: number; totalElements: number; totalPages: number };
  }>;
  return {
    ok: true,
    items: body.data.data,
    page: body.data.page.number,
    size: body.data.page.size,
    totalElements: body.data.page.totalElements,
    totalPages: body.data.page.totalPages,
  };
}

export async function fetchTenantEmployeeGroup(id: string): Promise<{ ok: true; item: TenantEmployeeGroupItem } | { ok: false; status: number }> {
  const r = await fetch(bffUrl(`/api/v1/employee-groups/${encodeURIComponent(id)}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: TenantEmployeeGroupItem }>;
  return { ok: true, item: body.data.item };
}

export type TenantEmployeeGroupUpsertPayload = {
  companyId: string;
  name: string;
  code: string;
  description?: string | null;
  active?: boolean;
};

export async function createTenantEmployeeGroup(payload: TenantEmployeeGroupUpsertPayload): Promise<TenantEmployeeGroupItem> {
  const r = await fetch(bffUrl("/api/v1/employee-groups"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantEmployeeGroupItem }>;
  return body.data.item;
}

export async function putTenantEmployeeGroup(id: string, payload: TenantEmployeeGroupUpsertPayload): Promise<TenantEmployeeGroupItem> {
  const r = await fetch(bffUrl(`/api/v1/employee-groups/${encodeURIComponent(id)}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantEmployeeGroupItem }>;
  return body.data.item;
}

export async function patchTenantEmployeeGroupActive(id: string, active: boolean): Promise<TenantEmployeeGroupItem> {
  const r = await fetch(bffUrl(`/api/v1/employee-groups/${encodeURIComponent(id)}/active`), {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ active }),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantEmployeeGroupItem }>;
  return body.data.item;
}

// ---------------------------------------------------------------------------
// Payroll Org Structure — Employees
// ---------------------------------------------------------------------------

export type TenantEmployeeItem = {
  id: string;
  companyId: string;
  departmentId: string;
  jobId: string;
  employeeGroupId: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string | null;
  hireDate: string;
  email: string | null;
  phone: string | null;
  status: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type TenantEmployeePageResult =
  | { ok: true; items: TenantEmployeeItem[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export async function fetchTenantEmployees(args?: {
  page?: number;
  size?: number;
  companyId?: string;
  departmentId?: string;
  jobId?: string;
  employeeGroupId?: string;
  status?: string;
  q?: string;
  active?: boolean | null;
}): Promise<TenantEmployeePageResult> {
  const q = new URLSearchParams({ page: String(args?.page ?? 0), size: String(args?.size ?? 20) });
  if (args?.companyId) q.set("companyId", args.companyId);
  if (args?.departmentId) q.set("departmentId", args.departmentId);
  if (args?.jobId) q.set("jobId", args.jobId);
  if (args?.employeeGroupId) q.set("employeeGroupId", args.employeeGroupId);
  if (args?.status) q.set("status", args.status);
  if (args?.q?.trim()) q.set("q", args.q.trim());
  if (typeof args?.active === "boolean") q.set("active", String(args.active));
  const r = await fetch(bffUrl(`/api/v1/employees?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    data: TenantEmployeeItem[];
    page: { number: number; size: number; totalElements: number; totalPages: number };
  }>;
  return {
    ok: true,
    items: body.data.data,
    page: body.data.page.number,
    size: body.data.page.size,
    totalElements: body.data.page.totalElements,
    totalPages: body.data.page.totalPages,
  };
}

export async function fetchTenantEmployee(id: string): Promise<{ ok: true; item: TenantEmployeeItem } | { ok: false; status: number }> {
  const r = await fetch(bffUrl(`/api/v1/employees/${encodeURIComponent(id)}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: TenantEmployeeItem }>;
  return { ok: true, item: body.data.item };
}

export type TenantEmployeeUpsertPayload = {
  companyId: string;
  departmentId: string;
  jobId: string;
  employeeGroupId: string;
  firstName: string;
  lastName: string;
  dateOfBirth?: string | null;
  hireDate: string;
  email?: string | null;
  phone?: string | null;
  status: string;
  active?: boolean;
};

export async function createTenantEmployee(payload: TenantEmployeeUpsertPayload): Promise<TenantEmployeeItem> {
  const r = await fetch(bffUrl("/api/v1/employees"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantEmployeeItem }>;
  return body.data.item;
}

export async function putTenantEmployee(id: string, payload: TenantEmployeeUpsertPayload): Promise<TenantEmployeeItem> {
  const r = await fetch(bffUrl(`/api/v1/employees/${encodeURIComponent(id)}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantEmployeeItem }>;
  return body.data.item;
}

export async function patchTenantEmployeeStatus(id: string, status: string): Promise<TenantEmployeeItem> {
  const r = await fetch(bffUrl(`/api/v1/employees/${encodeURIComponent(id)}/status`), {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ status }),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantEmployeeItem }>;
  return body.data.item;
}

export async function patchTenantEmployeeActive(id: string, active: boolean): Promise<TenantEmployeeItem> {
  const r = await fetch(bffUrl(`/api/v1/employees/${encodeURIComponent(id)}/active`), {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ active }),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantEmployeeItem }>;
  return body.data.item;
}

// ---------------------------------------------------------------------------
// Payroll Org Structure — Work Times
// ---------------------------------------------------------------------------

export type TenantWorkTimeItem = {
  id: string;
  companyId: string;
  name: string;
  code: string;
  hoursPerDay: number;
  workDaysPerWeek: number;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type TenantWorkTimePageResult =
  | { ok: true; items: TenantWorkTimeItem[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export async function fetchTenantWorkTimes(args?: {
  page?: number;
  size?: number;
  companyId?: string;
  active?: boolean | null;
}): Promise<TenantWorkTimePageResult> {
  const q = new URLSearchParams({ page: String(args?.page ?? 0), size: String(args?.size ?? 20) });
  if (args?.companyId) q.set("companyId", args.companyId);
  if (typeof args?.active === "boolean") q.set("active", String(args.active));
  const r = await fetch(bffUrl(`/api/v1/work-times?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    data: TenantWorkTimeItem[];
    page: { number: number; size: number; totalElements: number; totalPages: number };
  }>;
  return {
    ok: true,
    items: body.data.data,
    page: body.data.page.number,
    size: body.data.page.size,
    totalElements: body.data.page.totalElements,
    totalPages: body.data.page.totalPages,
  };
}

export async function fetchTenantWorkTime(id: string): Promise<{ ok: true; item: TenantWorkTimeItem } | { ok: false; status: number }> {
  const r = await fetch(bffUrl(`/api/v1/work-times/${encodeURIComponent(id)}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: TenantWorkTimeItem }>;
  return { ok: true, item: body.data.item };
}

export type TenantWorkTimeUpsertPayload = {
  companyId: string;
  name: string;
  code: string;
  hoursPerDay: number;
  workDaysPerWeek: number;
  description?: string | null;
  active?: boolean;
};

export async function createTenantWorkTime(payload: TenantWorkTimeUpsertPayload): Promise<TenantWorkTimeItem> {
  const r = await fetch(bffUrl("/api/v1/work-times"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantWorkTimeItem }>;
  return body.data.item;
}

export async function putTenantWorkTime(id: string, payload: TenantWorkTimeUpsertPayload): Promise<TenantWorkTimeItem> {
  const r = await fetch(bffUrl(`/api/v1/work-times/${encodeURIComponent(id)}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantWorkTimeItem }>;
  return body.data.item;
}

export async function patchTenantWorkTimeActive(id: string, active: boolean): Promise<TenantWorkTimeItem> {
  const r = await fetch(bffUrl(`/api/v1/work-times/${encodeURIComponent(id)}/active`), {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ active }),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantWorkTimeItem }>;
  return body.data.item;
}
export async function completeDocumentUpload(args: {
  documentId: string;
  storageKey: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
}): Promise<{ ok: true } | { ok: false; status: number }> {
  const r = await fetch(bffUrl("/api/v1/tenant/documents/complete"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(args),
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  return { ok: true };
}

/** Browser PUT to MinIO/S3 presigned URL (requires bucket CORS for your app origin). */
export async function putToDocumentUploadUrl(
  uploadUrl: string,
  file: Blob,
  requiredHeaders: Record<string, string>,
): Promise<void> {
  const r = await fetch(uploadUrl, { method: "PUT", body: file, headers: requiredHeaders, mode: "cors" });
  if (!r.ok) {
    throw new Error(`PUT to storage failed: ${r.status}`);
  }
}

export type DocumentShareListItem = {
  id: string;
  granteeUserId: string | null;
  granteeRoleId: string | null;
  createdByUserId: string;
  createdAt: string;
};

export type DocumentSharesFetchResult = { ok: true; items: DocumentShareListItem[] } | { ok: false; status: number };

/** GET …/documents/{id}/shares — DOCUMENT_EDIT + uploader. */
export async function fetchDocumentShares(documentId: string): Promise<DocumentSharesFetchResult> {
  const r = await fetch(bffUrl(`/api/v1/tenant/documents/${encodeURIComponent(documentId)}/shares`), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ items: DocumentShareListItem[] }>;
  return { ok: true, items: body.data.items };
}

export async function createDocumentShare(
  documentId: string,
  body: { granteeUserId: string | null; granteeRoleId: string | null },
): Promise<{ ok: true } | { ok: false; status: number }> {
  const r = await fetch(bffUrl(`/api/v1/tenant/documents/${encodeURIComponent(documentId)}/shares`), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  return { ok: true };
}

export async function deleteDocumentShare(
  documentId: string,
  shareId: string,
): Promise<{ ok: true } | { ok: false; status: number }> {
  const r = await fetch(
    bffUrl(`/api/v1/tenant/documents/${encodeURIComponent(documentId)}/shares/${encodeURIComponent(shareId)}`),
    { method: "DELETE", credentials: "same-origin" },
  );
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  return { ok: true };
}

export type DocumentAttachmentListItem = {
  id: string;
  entityType: string;
  entityId: string;
  createdByUserId: string;
  createdAt: string;
};

export type DocumentAttachmentsFetchResult =
  | { ok: true; items: DocumentAttachmentListItem[] }
  | { ok: false; status: number };

/** GET …/documents/{id}/attachments — DOCUMENT_VIEW + readable doc. */
export async function fetchDocumentAttachments(documentId: string): Promise<DocumentAttachmentsFetchResult> {
  const r = await fetch(bffUrl(`/api/v1/tenant/documents/${encodeURIComponent(documentId)}/attachments`), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ items: DocumentAttachmentListItem[] }>;
  return { ok: true, items: body.data.items };
}

export async function createDocumentAttachment(
  documentId: string,
  body: { entityType: string; entityId: string },
): Promise<{ ok: true } | { ok: false; status: number }> {
  const r = await fetch(bffUrl(`/api/v1/tenant/documents/${encodeURIComponent(documentId)}/attachments`), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  return { ok: true };
}

export async function deleteDocumentAttachment(
  documentId: string,
  attachmentId: string,
): Promise<{ ok: true } | { ok: false; status: number }> {
  const r = await fetch(
    bffUrl(`/api/v1/tenant/documents/${encodeURIComponent(documentId)}/attachments/${encodeURIComponent(attachmentId)}`),
    { method: "DELETE", credentials: "same-origin" },
  );
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  return { ok: true };
}

/** DELETE …/documents/{id} — DOCUMENT_EDIT + soft-delete (uploader only). */
export async function softDeleteTenantDocument(documentId: string): Promise<{ ok: true } | { ok: false; status: number }> {
  const r = await fetch(bffUrl(`/api/v1/tenant/documents/${encodeURIComponent(documentId)}`), {
    method: "DELETE",
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  return { ok: true };
}
