/**
 * Same-origin BFF client: the browser only calls `/api/bff/...` on this Next.js app.
 * The Next server proxies to Spring using `API_BASE_URL` (server-only).
 */

import { authLoginUrlWithReturnTo, sessionExpiredReturnTo } from "@/lib/web-origins";

export function bffUrl(apiPath: string): string {
  if (!apiPath.startsWith("/api/v1")) {
    throw new Error(`Unexpected API path: ${apiPath}`);
  }
  return `/api/bff${apiPath.slice("/api".length)}`;
}

let bff401RedirectScheduled = false;

function bffRequestPath(url: string): string {
  try {
    const base =
      typeof window !== "undefined" ? window.location.origin : "http://localhost";
    return new URL(url, base).pathname;
  } catch {
    return "";
  }
}

function scheduleLoginRedirectOnExpiredSession(requestUrl: string): void {
  if (typeof window === "undefined") return;
  const path = bffRequestPath(requestUrl);
  // Failed sign-in/register should surface inline validation, not bounce to login.
  if (path.includes("/v1/auth/login") || path.includes("/v1/auth/register")) {
    return;
  }
  // Open-redirect probe may return 401 for anonymous callers on the login page.
  if (path.includes("/v1/auth/redirect-check")) {
    return;
  }
  const returnTo = sessionExpiredReturnTo(window.location);
  if (!returnTo) {
    return;
  }
  if (bff401RedirectScheduled) return;
  bff401RedirectScheduled = true;
  window.location.replace(authLoginUrlWithReturnTo(returnTo));
}

/**
 * Prefer this over raw {@code fetch} for `/api/bff/...`: Spring returns **401** when the session is
 * missing or expired (including idle timeout). We send the browser to login with {@code returnTo}
 * so they can resume. Real missing-privilege cases stay **403** and are handled per screen.
 */
export async function fetchBff(input: string | URL, init?: RequestInit): Promise<Response> {
  const urlString = typeof input === "string" ? input : input.href;
  const r = await fetch(input, { credentials: "same-origin", ...init });
  if (r.status === 401) {
    scheduleLoginRedirectOnExpiredSession(urlString);
  }
  return r;
}

async function readFailureMessage(r: Response): Promise<string> {
  const text = await r.text();
  if (!text) {
    return `Request failed: ${r.status}`;
  }
  try {
    const body = JSON.parse(text) as {
      detail?: string;
      title?: string;
      message?: string;
      error?: string;
    };
    const detail = body.detail ?? body.message ?? body.error ?? body.title;
    if (detail) {
      const requiredMatch = detail.match(/^([A-Za-z][A-Za-z0-9]*) is required$/);
      if (requiredMatch) {
        const field = requiredMatch[1]
          .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
          .replace(/\bId\b/g, "ID")
          .replace(/^./, (char) => char.toUpperCase());
        return `${field} is required.`;
      }
      return detail;
    }
  } catch {
    // Fall through to plain-text handling.
  }
  return `Request failed: ${r.status} ${text.slice(0, 200)}`;
}

export async function postJson(
  path: string,
  body: unknown,
  okStatuses: number[],
  extraHeaders?: Record<string, string>,
): Promise<void> {
  const r = await fetchBff(bffUrl(path), {
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
  const r = await fetchBff(bffUrl(path), {
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
  const r = await fetchBff(bffUrl(path), {
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
  const r = await fetchBff(bffUrl("/api/v1/platform/settings"), {
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
  const r = await fetchBff(bffUrl("/api/v1/platform/mail-templates"), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl(`/api/v1/platform/mail-templates/${encodeURIComponent(templateId)}`), {
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
  const r = await fetchBff(bffUrl("/api/v1/platform/role-templates"), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl(`/api/v1/platform/role-templates/${encodeURIComponent(templateId)}`), {
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
  const r = await fetchBff(bffUrl("/api/v1/platform/role-templates"), {
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
  const r = await fetchBff(bffUrl(`/api/v1/platform/role-templates/${encodeURIComponent(args.id)}`), {
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
  const r = await fetchBff(bffUrl("/api/v1/platform/privileges/catalog"), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl(`/api/v1/platform/tenants?${q}`), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl(`/api/v1/platform/tenants/${tenantId}`), { credentials: "same-origin" });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ tenant: PlatformTenantRow }>;
  return { ok: true, tenant: body.data.tenant };
}

/** POST /api/v1/platform/tenants — returns created row. */
export async function postPlatformTenant(handle: string, name: string): Promise<PlatformTenantRow> {
  const r = await fetchBff(bffUrl("/api/v1/platform/tenants"), {
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
  const r = await fetchBff(bffUrl(`/api/v1/platform/tenants/${tenantId}`), {
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
  const r = await fetchBff(bffUrl("/api/v1/me/privacy/export"), {
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
  const r = await fetchBff(bffUrl("/api/v1/auth/login"), {
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
  const r = await fetchBff(bffUrl("/api/v1/auth/register"), {
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
  const r = await fetchBff(bffUrl(`/api/v1/platform/currencies?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ items: PlatformCurrencyRow[]; totalElements: number; page: number; size: number; totalPages: number }>;
  const d = body.data;
  return { ok: true, items: d.items, totalElements: d.totalElements, page: d.page, size: d.size, totalPages: d.totalPages };
}

/** GET /api/v1/platform/currencies/{id} — platform superadmin only. */
export async function fetchPlatformCurrency(
  id: string,
): Promise<{ ok: true; item: PlatformCurrencyRow } | { ok: false; status: number }> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/currencies/${encodeURIComponent(id)}`), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl("/api/v1/platform/currencies"), {
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
  const r = await fetchBff(bffUrl(`/api/v1/platform/currencies/${id}`), {
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
  payrollEnabled: boolean;
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
  payrollEnabled?: boolean | null;
  locale?: string;
}): Promise<PlatformCountriesResult> {
  const q = new URLSearchParams({
    page: String(args?.page ?? 0),
    size: String(args?.size ?? 50),
    locale: args?.locale ?? "en",
  });
  if (args?.search?.trim()) q.set("search", args.search.trim());
  if (typeof args?.active === "boolean") q.set("active", String(args.active));
  if (typeof args?.payrollEnabled === "boolean") q.set("payrollEnabled", String(args.payrollEnabled));
  const r = await fetchBff(bffUrl(`/api/v1/platform/countries?${q}`), { credentials: "same-origin" });
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
  const r = await fetchBff(
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
  payrollEnabled?: boolean;
  translations: PlatformCountryTranslation[];
};

/** POST /api/v1/platform/countries — platform superadmin only. */
export async function createPlatformCountry(payload: PlatformCountryUpsertRequest): Promise<PlatformCountryRow> {
  const r = await fetchBff(bffUrl("/api/v1/platform/countries"), {
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
  const r = await fetchBff(bffUrl(`/api/v1/platform/countries/${encodeURIComponent(id)}`), {
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
  const r = await fetchBff(bffUrl(`/api/v1/platform/countries/${encodeURIComponent(id)}/activate`), {
    method: "PATCH",
    credentials: "same-origin",
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformCountryRow }>;
  return body.data.item;
}

/** PATCH /api/v1/platform/countries/{id}/deactivate — platform superadmin only. */
export async function patchDeactivatePlatformCountry(id: string): Promise<PlatformCountryRow> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/countries/${encodeURIComponent(id)}/deactivate`), {
    method: "PATCH",
    credentials: "same-origin",
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformCountryRow }>;
  return body.data.item;
}

export type PlatformCountryTaxRuleRow = {
  id: string;
  countryCode: string;
  ruleCode: string;
  name: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  parametersJson: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type PlatformCountryTaxRulesResult =
  | {
      ok: true;
      items: PlatformCountryTaxRuleRow[];
      totalElements: number;
      page: number;
      size: number;
      totalPages: number;
    }
  | { ok: false; status: number };

/** GET /api/v1/platform/country-tax-rules — platform superadmin only. */
export async function fetchPlatformCountryTaxRules(args?: {
  page?: number;
  size?: number;
  country?: string;
  active?: boolean | null;
  search?: string;
}): Promise<PlatformCountryTaxRulesResult> {
  const q = new URLSearchParams({
    page: String(args?.page ?? 0),
    size: String(args?.size ?? 50),
  });
  if (args?.country?.trim()) q.set("country", args.country.trim().toUpperCase());
  if (typeof args?.active === "boolean") q.set("active", String(args.active));
  if (args?.search?.trim()) q.set("search", args.search.trim());
  const r = await fetchBff(bffUrl(`/api/v1/platform/country-tax-rules?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    items: PlatformCountryTaxRuleRow[];
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

/** GET /api/v1/platform/country-tax-rules/{id} — platform superadmin only. */
export async function fetchPlatformCountryTaxRule(
  id: string,
): Promise<{ ok: true; item: PlatformCountryTaxRuleRow } | { ok: false; status: number }> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/country-tax-rules/${encodeURIComponent(id)}`), {
    credentials: "same-origin",
  });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformCountryTaxRuleRow }>;
  return { ok: true, item: body.data.item };
}

export type PlatformCountryTaxRuleCreateRequest = {
  countryCode: string;
  ruleCode: string;
  name: string;
  effectiveFrom: string;
  effectiveTo?: string | null;
  parametersJson: string;
  active?: boolean;
};

/** POST /api/v1/platform/country-tax-rules — platform superadmin only. */
export async function createPlatformCountryTaxRule(
  payload: PlatformCountryTaxRuleCreateRequest,
): Promise<PlatformCountryTaxRuleRow> {
  const r = await fetchBff(bffUrl("/api/v1/platform/country-tax-rules"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformCountryTaxRuleRow }>;
  return body.data.item;
}

export type PlatformCountryTaxRulePutRequest = {
  name: string;
  parametersJson: string;
  effectiveTo?: string | null;
  active?: boolean;
};

/** PUT /api/v1/platform/country-tax-rules/{id} — platform superadmin only. */
export async function putPlatformCountryTaxRule(
  id: string,
  payload: PlatformCountryTaxRulePutRequest,
): Promise<PlatformCountryTaxRuleRow> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/country-tax-rules/${encodeURIComponent(id)}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformCountryTaxRuleRow }>;
  return body.data.item;
}

/** PATCH /api/v1/platform/country-tax-rules/{id}/activate — platform superadmin only. */
export async function patchActivatePlatformCountryTaxRule(id: string): Promise<PlatformCountryTaxRuleRow> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/country-tax-rules/${encodeURIComponent(id)}/activate`), {
    method: "PATCH",
    credentials: "same-origin",
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformCountryTaxRuleRow }>;
  return body.data.item;
}

/** PATCH /api/v1/platform/country-tax-rules/{id}/deactivate — platform superadmin only. */
export async function patchDeactivatePlatformCountryTaxRule(id: string): Promise<PlatformCountryTaxRuleRow> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/country-tax-rules/${encodeURIComponent(id)}/deactivate`), {
    method: "PATCH",
    credentials: "same-origin",
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformCountryTaxRuleRow }>;
  return body.data.item;
}

export type PlatformPayrollBaseRow = {
  id: string;
  code: string;
  name: string;
  category: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type PlatformPayrollBasesResult =
  | {
      ok: true;
      items: PlatformPayrollBaseRow[];
      totalElements: number;
      page: number;
      size: number;
      totalPages: number;
    }
  | { ok: false; status: number };

/** GET /api/v1/platform/payroll-bases — platform superadmin only. */
export async function fetchPlatformPayrollBases(args?: {
  page?: number;
  size?: number;
  category?: string;
  active?: boolean | null;
  search?: string;
}): Promise<PlatformPayrollBasesResult> {
  const q = new URLSearchParams({
    page: String(args?.page ?? 0),
    size: String(args?.size ?? 50),
  });
  if (args?.category?.trim()) q.set("category", args.category.trim().toUpperCase());
  if (typeof args?.active === "boolean") q.set("active", String(args.active));
  if (args?.search?.trim()) q.set("search", args.search.trim());
  const r = await fetchBff(bffUrl(`/api/v1/platform/payroll-bases?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    items: PlatformPayrollBaseRow[];
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

/** GET /api/v1/platform/payroll-bases/{id} — platform superadmin only. */
export async function fetchPlatformPayrollBase(
  id: string,
): Promise<{ ok: true; item: PlatformPayrollBaseRow } | { ok: false; status: number }> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/payroll-bases/${encodeURIComponent(id)}`), {
    credentials: "same-origin",
  });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformPayrollBaseRow }>;
  return { ok: true, item: body.data.item };
}

export type PlatformPayrollBaseCreateRequest = {
  code: string;
  name: string;
  category?: string | null;
  active?: boolean;
};

/** POST /api/v1/platform/payroll-bases — platform superadmin only. */
export async function createPlatformPayrollBase(
  payload: PlatformPayrollBaseCreateRequest,
): Promise<PlatformPayrollBaseRow> {
  const r = await fetchBff(bffUrl("/api/v1/platform/payroll-bases"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformPayrollBaseRow }>;
  return body.data.item;
}

export type PlatformPayrollBasePutRequest = {
  name: string;
  category?: string | null;
  active?: boolean;
};

/** PUT /api/v1/platform/payroll-bases/{id} — platform superadmin only. */
export async function putPlatformPayrollBase(
  id: string,
  payload: PlatformPayrollBasePutRequest,
): Promise<PlatformPayrollBaseRow> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/payroll-bases/${encodeURIComponent(id)}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformPayrollBaseRow }>;
  return body.data.item;
}

/** PATCH /api/v1/platform/payroll-bases/{id}/activate — platform superadmin only. */
export async function patchActivatePlatformPayrollBase(id: string): Promise<PlatformPayrollBaseRow> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/payroll-bases/${encodeURIComponent(id)}/activate`), {
    method: "PATCH",
    credentials: "same-origin",
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformPayrollBaseRow }>;
  return body.data.item;
}

/** PATCH /api/v1/platform/payroll-bases/{id}/deactivate — platform superadmin only. */
export async function patchDeactivatePlatformPayrollBase(id: string): Promise<PlatformPayrollBaseRow> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/payroll-bases/${encodeURIComponent(id)}/deactivate`), {
    method: "PATCH",
    credentials: "same-origin",
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformPayrollBaseRow }>;
  return body.data.item;
}

export type PlatformBankTemplateRow = {
  id: string;
  countryCode: string;
  name: string;
  bankName: string | null;
  swiftBic: string | null;
  bankCode: string | null;
  accountNumberFormat: string | null;
  currencyCode: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type PlatformBankTemplatesResult =
  | {
      ok: true;
      items: PlatformBankTemplateRow[];
      totalElements: number;
      page: number;
      size: number;
      totalPages: number;
    }
  | { ok: false; status: number };

/** GET /api/v1/platform/bank-templates — platform superadmin only. */
export async function fetchPlatformBankTemplates(args?: {
  page?: number;
  size?: number;
  country?: string | null;
  active?: boolean | null;
}): Promise<PlatformBankTemplatesResult> {
  const q = new URLSearchParams({
    page: String(args?.page ?? 0),
    size: String(args?.size ?? 20),
  });
  if (args?.country?.trim()) q.set("country", args.country.trim().toUpperCase());
  if (typeof args?.active === "boolean") q.set("active", String(args.active));
  const r = await fetchBff(bffUrl(`/api/v1/platform/bank-templates?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    items: PlatformBankTemplateRow[];
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

export async function fetchPlatformBankTemplate(id: string): Promise<
  { ok: true; template: PlatformBankTemplateRow } | { ok: false; status: number }
> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/bank-templates/${encodeURIComponent(id)}`), {
    credentials: "same-origin",
  });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ template: PlatformBankTemplateRow }>;
  return { ok: true, template: body.data.template };
}

export async function postPlatformBankTemplate(payload: {
  countryCode: string;
  name: string;
  bankName?: string | null;
  swiftBic?: string | null;
  bankCode?: string | null;
  accountNumberFormat?: string | null;
  currencyCode?: string | null;
  active?: boolean;
}): Promise<PlatformBankTemplateRow> {
  const r = await fetchBff(bffUrl("/api/v1/platform/bank-templates"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ template: PlatformBankTemplateRow }>;
  return body.data.template;
}

export async function putPlatformBankTemplate(
  id: string,
  payload: {
    name: string;
    bankName?: string | null;
    swiftBic?: string | null;
    bankCode?: string | null;
    accountNumberFormat?: string | null;
    currencyCode?: string | null;
    active: boolean;
  },
): Promise<PlatformBankTemplateRow> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/bank-templates/${encodeURIComponent(id)}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ template: PlatformBankTemplateRow }>;
  return body.data.template;
}

export async function patchActivatePlatformBankTemplate(id: string): Promise<PlatformBankTemplateRow> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/bank-templates/${encodeURIComponent(id)}/activate`), {
    method: "PATCH",
    credentials: "same-origin",
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ template: PlatformBankTemplateRow }>;
  return body.data.template;
}

export async function patchDeactivatePlatformBankTemplate(id: string): Promise<PlatformBankTemplateRow> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/bank-templates/${encodeURIComponent(id)}/deactivate`), {
    method: "PATCH",
    credentials: "same-origin",
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ template: PlatformBankTemplateRow }>;
  return body.data.template;
}

export type PlatformLedgerTemplateTranslation = {
  locale: string;
  description: string;
};

export type PlatformLedgerTemplateRow = {
  id: string;
  countryCode: string;
  code: string;
  description: string;
  translations: PlatformLedgerTemplateTranslation[];
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type PlatformLedgerTemplatesResult =
  | { ok: true; items: PlatformLedgerTemplateRow[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export async function fetchPlatformLedgerTemplates(args?: {
  page?: number;
  size?: number;
  country?: string | null;
  active?: boolean | null;
  locale?: string | null;
}): Promise<PlatformLedgerTemplatesResult> {
  const q = new URLSearchParams({ page: String(args?.page ?? 0), size: String(args?.size ?? 20) });
  if (args?.country) q.set("country", args.country);
  if (typeof args?.active === "boolean") q.set("active", String(args.active));
  const locale = (args?.locale ?? "en").trim() || "en";
  q.set("locale", locale);
  const r = await fetchBff(bffUrl(`/api/v1/platform/ledger-templates?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    items: PlatformLedgerTemplateRow[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  }>;
  return {
    ok: true,
    items: body.data.items,
    page: body.data.page,
    size: body.data.size,
    totalElements: body.data.totalElements,
    totalPages: body.data.totalPages,
  };
}

export async function fetchPlatformLedgerTemplate(
  id: string,
  args?: { locale?: string | null },
): Promise<{ ok: true; template: PlatformLedgerTemplateRow } | { ok: false; status: number }> {
  const q = new URLSearchParams();
  q.set("locale", (args?.locale ?? "en").trim() || "en");
  const qs = q.toString();
  const r = await fetchBff(
    bffUrl(`/api/v1/platform/ledger-templates/${encodeURIComponent(id)}${qs ? `?${qs}` : ""}`),
    {
      credentials: "same-origin",
    },
  );
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ template: PlatformLedgerTemplateRow }>;
  return { ok: true, template: body.data.template };
}

export async function postPlatformLedgerTemplate(
  payload: {
    countryCode: string;
    code: string;
    translations: PlatformLedgerTemplateTranslation[];
    active?: boolean | null;
  },
  args?: { locale?: string | null },
): Promise<PlatformLedgerTemplateRow> {
  const q = args?.locale ? `?locale=${encodeURIComponent(args.locale)}` : "";
  const r = await fetchBff(bffUrl(`/api/v1/platform/ledger-templates${q}`), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ template: PlatformLedgerTemplateRow }>;
  return body.data.template;
}

export async function putPlatformLedgerTemplate(
  id: string,
  payload: {
    countryCode: string;
    code: string;
    translations: PlatformLedgerTemplateTranslation[];
    active: boolean;
  },
  args?: { locale?: string | null },
): Promise<PlatformLedgerTemplateRow> {
  const q = args?.locale ? `?locale=${encodeURIComponent(args.locale)}` : "";
  const r = await fetchBff(bffUrl(`/api/v1/platform/ledger-templates/${encodeURIComponent(id)}${q}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ template: PlatformLedgerTemplateRow }>;
  return body.data.template;
}

export async function patchActivatePlatformLedgerTemplate(
  id: string,
  args?: { locale?: string | null },
): Promise<PlatformLedgerTemplateRow> {
  const q = args?.locale ? `?locale=${encodeURIComponent(args.locale)}` : "";
  const r = await fetchBff(bffUrl(`/api/v1/platform/ledger-templates/${encodeURIComponent(id)}/activate${q}`), {
    method: "PATCH",
    credentials: "same-origin",
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ template: PlatformLedgerTemplateRow }>;
  return body.data.template;
}

export async function patchDeactivatePlatformLedgerTemplate(
  id: string,
  args?: { locale?: string | null },
): Promise<PlatformLedgerTemplateRow> {
  const q = args?.locale ? `?locale=${encodeURIComponent(args.locale)}` : "";
  const r = await fetchBff(bffUrl(`/api/v1/platform/ledger-templates/${encodeURIComponent(id)}/deactivate${q}`), {
    method: "PATCH",
    credentials: "same-origin",
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ template: PlatformLedgerTemplateRow }>;
  return body.data.template;
}

export type PlatformComponentTranslation = {
  locale: string;
  name: string;
  description: string | null;
};

export type PlatformComponentGroupRow = {
  id: string;
  platformCountryId: string;
  countryCode: string;
  name: string;
  description: string | null;
  translations: PlatformComponentTranslation[];
  sortOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type PlatformComponentGroupsResult =
  | { ok: true; items: PlatformComponentGroupRow[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export async function fetchPlatformComponentGroups(args?: {
  page?: number;
  size?: number;
  country?: string | null;
  active?: boolean | null;
  locale?: string | null;
}): Promise<PlatformComponentGroupsResult> {
  const q = new URLSearchParams({ page: String(args?.page ?? 0), size: String(args?.size ?? 20) });
  if (args?.country) q.set("country", args.country);
  if (typeof args?.active === "boolean") q.set("active", String(args.active));
  q.set("locale", (args?.locale ?? "en").trim() || "en");
  const r = await fetchBff(bffUrl(`/api/v1/platform/component-group-templates?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    items: PlatformComponentGroupRow[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  }>;
  return {
    ok: true,
    items: body.data.items,
    page: body.data.page,
    size: body.data.size,
    totalElements: body.data.totalElements,
    totalPages: body.data.totalPages,
  };
}

export async function fetchPlatformComponentGroup(
  id: string,
  args?: { locale?: string | null },
): Promise<{ ok: true; group: PlatformComponentGroupRow } | { ok: false; status: number }> {
  const loc = (args?.locale ?? "en").trim() || "en";
  const r = await fetchBff(bffUrl(`/api/v1/platform/component-group-templates/${encodeURIComponent(id)}?locale=${encodeURIComponent(loc)}`), {
    credentials: "same-origin",
  });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ group: PlatformComponentGroupRow }>;
  return { ok: true, group: body.data.group };
}

export async function postPlatformComponentGroup(
  payload: {
    platformCountryId: string;
    sortOrder?: number | null;
    active?: boolean | null;
    translations: PlatformComponentTranslation[];
  },
  args?: { locale?: string | null },
): Promise<PlatformComponentGroupRow> {
  const q = args?.locale ? `?locale=${encodeURIComponent(args.locale)}` : "";
  const r = await fetchBff(bffUrl(`/api/v1/platform/component-group-templates${q}`), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ group: PlatformComponentGroupRow }>;
  return body.data.group;
}

export async function putPlatformComponentGroup(
  id: string,
  payload: { sortOrder: number; active: boolean; translations: PlatformComponentTranslation[] },
  args?: { locale?: string | null },
): Promise<PlatformComponentGroupRow> {
  const q = args?.locale ? `?locale=${encodeURIComponent(args.locale)}` : "";
  const r = await fetchBff(bffUrl(`/api/v1/platform/component-group-templates/${encodeURIComponent(id)}${q}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ group: PlatformComponentGroupRow }>;
  return body.data.group;
}

export async function deletePlatformComponentGroup(id: string): Promise<void> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/component-group-templates/${encodeURIComponent(id)}`), {
    method: "DELETE",
    credentials: "same-origin",
  });
  if (r.status !== 204) throw new Error(await readFailureMessage(r));
}

export type PlatformComponentHeaderRow = {
  id: string;
  platformComponentGroupId: string;
  name: string;
  description: string | null;
  translations: PlatformComponentTranslation[];
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
};

export type PlatformComponentHeadersResult =
  | { ok: true; items: PlatformComponentHeaderRow[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export async function fetchPlatformComponentHeaders(
  groupId: string,
  args?: { page?: number; size?: number; locale?: string | null },
): Promise<PlatformComponentHeadersResult> {
  const q = new URLSearchParams({ page: String(args?.page ?? 0), size: String(args?.size ?? 20) });
  q.set("locale", (args?.locale ?? "en").trim() || "en");
  const r = await fetchBff(
    bffUrl(`/api/v1/platform/component-group-templates/${encodeURIComponent(groupId)}/headers?${q}`),
    { credentials: "same-origin" },
  );
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    items: PlatformComponentHeaderRow[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  }>;
  return {
    ok: true,
    items: body.data.items,
    page: body.data.page,
    size: body.data.size,
    totalElements: body.data.totalElements,
    totalPages: body.data.totalPages,
  };
}

export async function fetchPlatformComponentHeader(
  groupId: string,
  headerId: string,
  args?: { locale?: string | null },
): Promise<{ ok: true; header: PlatformComponentHeaderRow } | { ok: false; status: number }> {
  const loc = (args?.locale ?? "en").trim() || "en";
  const r = await fetchBff(
    bffUrl(
      `/api/v1/platform/component-group-templates/${encodeURIComponent(groupId)}/headers/${encodeURIComponent(headerId)}?locale=${encodeURIComponent(loc)}`,
    ),
    { credentials: "same-origin" },
  );
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ header: PlatformComponentHeaderRow }>;
  return { ok: true, header: body.data.header };
}

export async function postPlatformComponentHeader(
  groupId: string,
  payload: { sortOrder?: number | null; translations: PlatformComponentTranslation[] },
  args?: { locale?: string | null },
): Promise<PlatformComponentHeaderRow> {
  const q = args?.locale ? `?locale=${encodeURIComponent(args.locale)}` : "";
  const r = await fetchBff(bffUrl(`/api/v1/platform/component-group-templates/${encodeURIComponent(groupId)}/headers${q}`), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ header: PlatformComponentHeaderRow }>;
  return body.data.header;
}

export async function putPlatformComponentHeader(
  groupId: string,
  headerId: string,
  payload: { sortOrder: number; translations: PlatformComponentTranslation[] },
  args?: { locale?: string | null },
): Promise<PlatformComponentHeaderRow> {
  const q = args?.locale ? `?locale=${encodeURIComponent(args.locale)}` : "";
  const r = await fetchBff(
    bffUrl(`/api/v1/platform/component-group-templates/${encodeURIComponent(groupId)}/headers/${encodeURIComponent(headerId)}${q}`),
    {
      method: "PUT",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
  );
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ header: PlatformComponentHeaderRow }>;
  return body.data.header;
}

export async function deletePlatformComponentHeader(groupId: string, headerId: string): Promise<void> {
  const r = await fetchBff(
    bffUrl(`/api/v1/platform/component-group-templates/${encodeURIComponent(groupId)}/headers/${encodeURIComponent(headerId)}`),
    { method: "DELETE", credentials: "same-origin" },
  );
  if (r.status !== 204) throw new Error(await readFailureMessage(r));
}

export type PlatformComponentItemRow = {
  id: string;
  platformComponentHeaderId: string;
  platformWageComponentTemplateId: string;
  wageComponentCode: string;
  wageComponentName: string;
  name: string;
  description: string | null;
  translations: PlatformComponentTranslation[];
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
};

export type PlatformComponentItemsResult =
  | { ok: true; items: PlatformComponentItemRow[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export async function fetchPlatformComponentItems(
  groupId: string,
  headerId: string,
  args?: { page?: number; size?: number; locale?: string | null },
): Promise<PlatformComponentItemsResult> {
  const q = new URLSearchParams({ page: String(args?.page ?? 0), size: String(args?.size ?? 20) });
  q.set("locale", (args?.locale ?? "en").trim() || "en");
  const r = await fetchBff(
    bffUrl(`/api/v1/platform/component-group-templates/${encodeURIComponent(groupId)}/headers/${encodeURIComponent(headerId)}/items?${q}`),
    { credentials: "same-origin" },
  );
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    items: PlatformComponentItemRow[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  }>;
  return {
    ok: true,
    items: body.data.items,
    page: body.data.page,
    size: body.data.size,
    totalElements: body.data.totalElements,
    totalPages: body.data.totalPages,
  };
}

export async function fetchPlatformComponentItem(
  groupId: string,
  headerId: string,
  itemId: string,
  args?: { locale?: string | null },
): Promise<{ ok: true; item: PlatformComponentItemRow } | { ok: false; status: number }> {
  const loc = (args?.locale ?? "en").trim() || "en";
  const r = await fetchBff(
    bffUrl(
      `/api/v1/platform/component-group-templates/${encodeURIComponent(groupId)}/headers/${encodeURIComponent(headerId)}/items/${encodeURIComponent(itemId)}?locale=${encodeURIComponent(loc)}`,
    ),
    { credentials: "same-origin" },
  );
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformComponentItemRow }>;
  return { ok: true, item: body.data.item };
}

export async function postPlatformComponentItem(
  groupId: string,
  headerId: string,
  payload: { platformWageComponentTemplateId: string; sortOrder?: number | null; translations: PlatformComponentTranslation[] },
  args?: { locale?: string | null },
): Promise<PlatformComponentItemRow> {
  const q = args?.locale ? `?locale=${encodeURIComponent(args.locale)}` : "";
  const r = await fetchBff(
    bffUrl(`/api/v1/platform/component-group-templates/${encodeURIComponent(groupId)}/headers/${encodeURIComponent(headerId)}/items${q}`),
    {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
  );
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformComponentItemRow }>;
  return body.data.item;
}

export async function putPlatformComponentItem(
  groupId: string,
  headerId: string,
  itemId: string,
  payload: { platformWageComponentTemplateId: string; sortOrder: number; translations: PlatformComponentTranslation[] },
  args?: { locale?: string | null },
): Promise<PlatformComponentItemRow> {
  const q = args?.locale ? `?locale=${encodeURIComponent(args.locale)}` : "";
  const r = await fetchBff(
    bffUrl(
      `/api/v1/platform/component-group-templates/${encodeURIComponent(groupId)}/headers/${encodeURIComponent(headerId)}/items/${encodeURIComponent(itemId)}${q}`,
    ),
    {
      method: "PUT",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
  );
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: PlatformComponentItemRow }>;
  return body.data.item;
}

export async function deletePlatformComponentItem(groupId: string, headerId: string, itemId: string): Promise<void> {
  const r = await fetchBff(
    bffUrl(
      `/api/v1/platform/component-group-templates/${encodeURIComponent(groupId)}/headers/${encodeURIComponent(headerId)}/items/${encodeURIComponent(itemId)}`,
    ),
    { method: "DELETE", credentials: "same-origin" },
  );
  if (r.status !== 204) throw new Error(await readFailureMessage(r));
}

function tenantComponentGroupsCompanyQuery(companyId: string): string {
  return `companyId=${encodeURIComponent(companyId)}`;
}

export type TenantComponentGroupRow = {
  id: string;
  companyId: string;
  platformComponentGroupTemplateId: string | null;
  countryCode: string;
  name: string;
  description: string | null;
  translations: PlatformComponentTranslation[];
  sortOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type TenantComponentGroupsResult =
  | { ok: true; items: TenantComponentGroupRow[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export async function fetchTenantComponentGroups(args: {
  companyId: string;
  page?: number;
  size?: number;
  active?: boolean | null;
  locale?: string | null;
}): Promise<TenantComponentGroupsResult> {
  const q = new URLSearchParams({ page: String(args.page ?? 0), size: String(args.size ?? 20) });
  q.set("companyId", args.companyId);
  if (typeof args.active === "boolean") q.set("active", String(args.active));
  q.set("locale", (args.locale ?? "en").trim() || "en");
  const r = await fetchBff(bffUrl(`/api/v1/component-groups?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    items: TenantComponentGroupRow[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  }>;
  return {
    ok: true,
    items: body.data.items,
    page: body.data.page,
    size: body.data.size,
    totalElements: body.data.totalElements,
    totalPages: body.data.totalPages,
  };
}

export async function fetchTenantComponentGroup(
  companyId: string,
  id: string,
  args?: { locale?: string | null },
): Promise<{ ok: true; group: TenantComponentGroupRow } | { ok: false; status: number }> {
  const loc = (args?.locale ?? "en").trim() || "en";
  const r = await fetchBff(
    bffUrl(`/api/v1/component-groups/${encodeURIComponent(id)}?${tenantComponentGroupsCompanyQuery(companyId)}&locale=${encodeURIComponent(loc)}`),
    { credentials: "same-origin" },
  );
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ group: TenantComponentGroupRow }>;
  return { ok: true, group: body.data.group };
}

export async function postTenantComponentGroup(
  payload: {
    companyId: string;
    platformComponentGroupTemplateId?: string | null;
    sortOrder?: number | null;
    active?: boolean | null;
    translations: PlatformComponentTranslation[];
  },
  args?: { locale?: string | null },
): Promise<TenantComponentGroupRow> {
  const q = args?.locale ? `?locale=${encodeURIComponent(args.locale)}` : "";
  const r = await fetchBff(bffUrl(`/api/v1/component-groups${q}`), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ group: TenantComponentGroupRow }>;
  return body.data.group;
}

export async function putTenantComponentGroup(
  companyId: string,
  id: string,
  payload: { sortOrder: number; active: boolean; translations: PlatformComponentTranslation[] },
  args?: { locale?: string | null },
): Promise<TenantComponentGroupRow> {
  const q = new URLSearchParams();
  q.set("companyId", companyId);
  if (args?.locale) q.set("locale", args.locale);
  const r = await fetchBff(bffUrl(`/api/v1/component-groups/${encodeURIComponent(id)}?${q}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ group: TenantComponentGroupRow }>;
  return body.data.group;
}

export async function deleteTenantComponentGroup(companyId: string, id: string): Promise<void> {
  const r = await fetchBff(
    bffUrl(`/api/v1/component-groups/${encodeURIComponent(id)}?${tenantComponentGroupsCompanyQuery(companyId)}`),
    { method: "DELETE", credentials: "same-origin" },
  );
  if (r.status !== 204) throw new Error(await readFailureMessage(r));
}

export type TenantComponentHeaderRow = {
  id: string;
  tenantComponentGroupId: string;
  name: string;
  description: string | null;
  translations: PlatformComponentTranslation[];
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
};

export type TenantComponentHeadersResult =
  | { ok: true; items: TenantComponentHeaderRow[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export async function fetchTenantComponentHeaders(
  companyId: string,
  groupId: string,
  args?: { page?: number; size?: number; locale?: string | null },
): Promise<TenantComponentHeadersResult> {
  const q = new URLSearchParams({ page: String(args?.page ?? 0), size: String(args?.size ?? 20) });
  q.set("companyId", companyId);
  q.set("locale", (args?.locale ?? "en").trim() || "en");
  const r = await fetchBff(bffUrl(`/api/v1/component-groups/${encodeURIComponent(groupId)}/headers?${q}`), {
    credentials: "same-origin",
  });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    items: TenantComponentHeaderRow[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  }>;
  return {
    ok: true,
    items: body.data.items,
    page: body.data.page,
    size: body.data.size,
    totalElements: body.data.totalElements,
    totalPages: body.data.totalPages,
  };
}

export async function fetchTenantComponentHeader(
  companyId: string,
  groupId: string,
  headerId: string,
  args?: { locale?: string | null },
): Promise<{ ok: true; header: TenantComponentHeaderRow } | { ok: false; status: number }> {
  const loc = (args?.locale ?? "en").trim() || "en";
  const r = await fetchBff(
    bffUrl(
      `/api/v1/component-groups/${encodeURIComponent(groupId)}/headers/${encodeURIComponent(headerId)}?companyId=${encodeURIComponent(companyId)}&locale=${encodeURIComponent(loc)}`,
    ),
    { credentials: "same-origin" },
  );
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ header: TenantComponentHeaderRow }>;
  return { ok: true, header: body.data.header };
}

export async function postTenantComponentHeader(
  companyId: string,
  groupId: string,
  payload: { sortOrder?: number | null; translations: PlatformComponentTranslation[] },
  args?: { locale?: string | null },
): Promise<TenantComponentHeaderRow> {
  const q = new URLSearchParams({ companyId });
  if (args?.locale) q.set("locale", args.locale);
  const r = await fetchBff(
    bffUrl(`/api/v1/component-groups/${encodeURIComponent(groupId)}/headers?${q}`),
    {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
  );
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ header: TenantComponentHeaderRow }>;
  return body.data.header;
}

export async function putTenantComponentHeader(
  companyId: string,
  groupId: string,
  headerId: string,
  payload: { sortOrder: number; translations: PlatformComponentTranslation[] },
  args?: { locale?: string | null },
): Promise<TenantComponentHeaderRow> {
  const q = new URLSearchParams({ companyId });
  if (args?.locale) q.set("locale", args.locale);
  const r = await fetchBff(
    bffUrl(`/api/v1/component-groups/${encodeURIComponent(groupId)}/headers/${encodeURIComponent(headerId)}?${q}`),
    {
      method: "PUT",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
  );
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ header: TenantComponentHeaderRow }>;
  return body.data.header;
}

export async function deleteTenantComponentHeader(companyId: string, groupId: string, headerId: string): Promise<void> {
  const r = await fetchBff(
    bffUrl(
      `/api/v1/component-groups/${encodeURIComponent(groupId)}/headers/${encodeURIComponent(headerId)}?${tenantComponentGroupsCompanyQuery(companyId)}`,
    ),
    { method: "DELETE", credentials: "same-origin" },
  );
  if (r.status !== 204) throw new Error(await readFailureMessage(r));
}

export type TenantComponentItemRow = {
  id: string;
  tenantComponentHeaderId: string;
  tenantWageComponentId: string;
  wageComponentCode: string;
  wageComponentName: string;
  name: string;
  description: string | null;
  translations: PlatformComponentTranslation[];
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
};

export type TenantComponentItemsResult =
  | { ok: true; items: TenantComponentItemRow[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export async function fetchTenantComponentItems(
  companyId: string,
  groupId: string,
  headerId: string,
  args?: { page?: number; size?: number; locale?: string | null },
): Promise<TenantComponentItemsResult> {
  const q = new URLSearchParams({ page: String(args?.page ?? 0), size: String(args?.size ?? 20) });
  q.set("companyId", companyId);
  q.set("locale", (args?.locale ?? "en").trim() || "en");
  const r = await fetchBff(
    bffUrl(`/api/v1/component-groups/${encodeURIComponent(groupId)}/headers/${encodeURIComponent(headerId)}/items?${q}`),
    { credentials: "same-origin" },
  );
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    items: TenantComponentItemRow[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  }>;
  return {
    ok: true,
    items: body.data.items,
    page: body.data.page,
    size: body.data.size,
    totalElements: body.data.totalElements,
    totalPages: body.data.totalPages,
  };
}

export async function fetchTenantComponentItem(
  companyId: string,
  groupId: string,
  headerId: string,
  itemId: string,
  args?: { locale?: string | null },
): Promise<{ ok: true; item: TenantComponentItemRow } | { ok: false; status: number }> {
  const loc = (args?.locale ?? "en").trim() || "en";
  const r = await fetchBff(
    bffUrl(
      `/api/v1/component-groups/${encodeURIComponent(groupId)}/headers/${encodeURIComponent(headerId)}/items/${encodeURIComponent(itemId)}?companyId=${encodeURIComponent(companyId)}&locale=${encodeURIComponent(loc)}`,
    ),
    { credentials: "same-origin" },
  );
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: TenantComponentItemRow }>;
  return { ok: true, item: body.data.item };
}

export async function postTenantComponentItem(
  companyId: string,
  groupId: string,
  headerId: string,
  payload: { tenantWageComponentId: string; sortOrder?: number | null; translations: PlatformComponentTranslation[] },
  args?: { locale?: string | null },
): Promise<TenantComponentItemRow> {
  const q = new URLSearchParams({ companyId });
  if (args?.locale) q.set("locale", args.locale);
  const r = await fetchBff(
    bffUrl(`/api/v1/component-groups/${encodeURIComponent(groupId)}/headers/${encodeURIComponent(headerId)}/items?${q}`),
    {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
  );
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantComponentItemRow }>;
  return body.data.item;
}

export async function putTenantComponentItem(
  companyId: string,
  groupId: string,
  headerId: string,
  itemId: string,
  payload: { tenantWageComponentId: string; sortOrder: number; translations: PlatformComponentTranslation[] },
  args?: { locale?: string | null },
): Promise<TenantComponentItemRow> {
  const q = new URLSearchParams({ companyId });
  if (args?.locale) q.set("locale", args.locale);
  const r = await fetchBff(
    bffUrl(
      `/api/v1/component-groups/${encodeURIComponent(groupId)}/headers/${encodeURIComponent(headerId)}/items/${encodeURIComponent(itemId)}?${q}`,
    ),
    {
      method: "PUT",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
  );
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantComponentItemRow }>;
  return body.data.item;
}

export async function deleteTenantComponentItem(
  companyId: string,
  groupId: string,
  headerId: string,
  itemId: string,
): Promise<void> {
  const r = await fetchBff(
    bffUrl(
      `/api/v1/component-groups/${encodeURIComponent(groupId)}/headers/${encodeURIComponent(headerId)}/items/${encodeURIComponent(itemId)}?${tenantComponentGroupsCompanyQuery(companyId)}`,
    ),
    { method: "DELETE", credentials: "same-origin" },
  );
  if (r.status !== 204) throw new Error(await readFailureMessage(r));
}

export type PlatformWageComponentCatalogRow = {
  id: string;
  countryCode: string;
  code: string;
  name: string;
};

export type PlatformWageComponentsCatalogResult =
  | { ok: true; items: PlatformWageComponentCatalogRow[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export async function fetchPlatformWageComponentsCatalog(args: {
  country: string;
  page?: number;
  size?: number;
}): Promise<PlatformWageComponentsCatalogResult> {
  const q = new URLSearchParams({
    page: String(args.page ?? 0),
    size: String(args.size ?? 50),
    country: args.country.trim().toUpperCase(),
  });
  const r = await fetchBff(bffUrl(`/api/v1/platform/wage-components?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    items: PlatformWageComponentCatalogRow[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  }>;
  return {
    ok: true,
    items: body.data.items,
    page: body.data.page,
    size: body.data.size,
    totalElements: body.data.totalElements,
    totalPages: body.data.totalPages,
  };
}

export type PlatformWageComponentTemplateBaseEffectRow = {
  id: string;
  payrollBaseId: string;
  payrollBaseCode: string;
  payrollBaseName: string;
  effectDirection: string;
  effectCalculationType: string;
  effectValue: number;
  priority: number;
  active: boolean;
};

export type PlatformWageComponentTemplateBaseEffectPutItem = {
  payrollBaseId: string;
  effectDirection: string;
  effectCalculationType: string;
  effectValue: number | null;
  priority?: number | null;
};

export type PlatformWageComponentTemplateDependencyRow = {
  id: string;
  dependsOnTemplateId: string;
  dependsOnTemplateCode: string;
  dependsOnTemplateName: string;
};

export type PlatformWageComponentTemplateDependencyPutItem = {
  dependsOnTemplateId: string;
};

export type PlatformWageComponentTemplateRow = {
  id: string;
  countryCode: string;
  templateCode: string;
  name: string;
  description: string | null;
  definitionDefaultsJson: string;
  processingOrderHint: number | null;
  phaseHint: string | null;
  debitPlatformLedgerTemplateId: string | null;
  creditPlatformLedgerTemplateId: string | null;
  duplicable: boolean;
  printOnPayslip: boolean;
  auxiliary: boolean;
  applyInPayroll: boolean;
  recurrence: string | null;
  countryRuleKey: string | null;
  platformCountryTaxRuleId: string | null;
  active: boolean;
  baseEffects?: PlatformWageComponentTemplateBaseEffectRow[];
  dependencies?: PlatformWageComponentTemplateDependencyRow[];
  createdAt: string;
  updatedAt: string;
};

export type PlatformWageComponentTemplatesResult =
  | { ok: true; items: PlatformWageComponentTemplateRow[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export async function fetchPlatformWageComponentTemplates(args?: {
  page?: number;
  size?: number;
  country?: string | null;
  active?: boolean | null;
}): Promise<PlatformWageComponentTemplatesResult> {
  const q = new URLSearchParams({ page: String(args?.page ?? 0), size: String(args?.size ?? 20) });
  if (args?.country) q.set("country", args.country);
  if (typeof args?.active === "boolean") q.set("active", String(args.active));
  const r = await fetchBff(bffUrl(`/api/v1/platform/wage-component-templates?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    items: PlatformWageComponentTemplateRow[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  }>;
  return {
    ok: true,
    items: body.data.items,
    page: body.data.page,
    size: body.data.size,
    totalElements: body.data.totalElements,
    totalPages: body.data.totalPages,
  };
}

export async function fetchPlatformWageComponentTemplate(
  id: string,
): Promise<{ ok: true; template: PlatformWageComponentTemplateRow } | { ok: false; status: number }> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/wage-component-templates/${encodeURIComponent(id)}`), {
    credentials: "same-origin",
  });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ template: PlatformWageComponentTemplateRow }>;
  return { ok: true, template: body.data.template };
}

export async function putPlatformWageComponentTemplateLedgerLinks(
  id: string,
  payload: { debitPlatformLedgerTemplateId: string | null; creditPlatformLedgerTemplateId: string | null },
): Promise<PlatformWageComponentTemplateRow> {
  const r = await fetchBff(
    bffUrl(`/api/v1/platform/wage-component-templates/${encodeURIComponent(id)}/ledger-links`),
    {
      method: "PUT",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
  );
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ template: PlatformWageComponentTemplateRow }>;
  return body.data.template;
}

export type PlatformWageComponentTemplateCreatePayload = {
  countryCode: string;
  templateCode: string;
  name: string;
  description?: string | null;
  definitionDefaultsJson: string;
  processingOrderHint?: number | null;
  phaseHint?: string | null;
  debitPlatformLedgerTemplateId?: string | null;
  creditPlatformLedgerTemplateId?: string | null;
  duplicable: boolean;
  printOnPayslip: boolean;
  auxiliary: boolean;
  applyInPayroll: boolean;
  recurrence?: string | null;
  countryRuleKey?: string | null;
  platformCountryTaxRuleId?: string | null;
  active: boolean;
};

export type PlatformWageComponentTemplatePutPayload = {
  name: string;
  description?: string | null;
  definitionDefaultsJson: string;
  processingOrderHint?: number | null;
  phaseHint?: string | null;
  debitPlatformLedgerTemplateId?: string | null;
  creditPlatformLedgerTemplateId?: string | null;
  duplicable: boolean;
  printOnPayslip: boolean;
  auxiliary: boolean;
  applyInPayroll: boolean;
  recurrence?: string | null;
  countryRuleKey?: string | null;
  platformCountryTaxRuleId?: string | null;
  active: boolean;
  baseEffects?: PlatformWageComponentTemplateBaseEffectPutItem[] | null;
  dependencies?: PlatformWageComponentTemplateDependencyPutItem[] | null;
};

export async function createPlatformWageComponentTemplate(
  payload: PlatformWageComponentTemplateCreatePayload,
): Promise<PlatformWageComponentTemplateRow> {
  const r = await fetchBff(bffUrl("/api/v1/platform/wage-component-templates"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ template: PlatformWageComponentTemplateRow }>;
  return body.data.template;
}

export async function putPlatformWageComponentTemplate(
  id: string,
  payload: PlatformWageComponentTemplatePutPayload,
): Promise<PlatformWageComponentTemplateRow> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/wage-component-templates/${encodeURIComponent(id)}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ template: PlatformWageComponentTemplateRow }>;
  return body.data.template;
}

export async function deletePlatformWageComponentTemplate(id: string): Promise<void> {
  const r = await fetchBff(bffUrl(`/api/v1/platform/wage-component-templates/${encodeURIComponent(id)}`), {
    method: "DELETE",
    credentials: "same-origin",
  });
  if (r.status !== 204) throw new Error(await readFailureMessage(r));
}

export type TenantLedgerRow = {
  id: string;
  companyId: string;
  platformLedgerTemplateId: string;
  code: string;
  description: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export async function fetchTenantLedgers(
  companyId: string,
): Promise<{ ok: true; items: TenantLedgerRow[] } | { ok: false; status: number }> {
  const q = new URLSearchParams({ companyId });
  const r = await fetchBff(bffUrl(`/api/v1/ledgers?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ items: TenantLedgerRow[] }>;
  return { ok: true, items: body.data.items };
}

export type TenantBankTemplateRow = {
  id: string;
  companyId: string;
  platformBankTemplateId: string | null;
  countryCode: string;
  platformTemplateName: string;
  bankName: string | null;
  swiftBic: string | null;
  accountNumber: string | null;
  currencyCode: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type TenantBankTemplateCatalogRow = {
  id: string;
  countryCode: string;
  name: string;
  bankName: string | null;
  swiftBic: string | null;
  currencyCode: string | null;
};

export type TenantBankTemplatesResult =
  | {
      ok: true;
      items: TenantBankTemplateRow[];
      totalElements: number;
      page: number;
      size: number;
      totalPages: number;
    }
  | { ok: false; status: number };

/** GET /api/v1/tenant/bank-templates — BANK_TEMPLATE_VIEW; companyId required. */
export async function fetchTenantBankTemplates(args: {
  companyId: string;
  page?: number;
  size?: number;
  active?: boolean | null;
}): Promise<TenantBankTemplatesResult> {
  const q = new URLSearchParams({
    companyId: args.companyId,
    page: String(args.page ?? 0),
    size: String(args.size ?? 20),
  });
  if (typeof args.active === "boolean") q.set("active", String(args.active));
  const r = await fetchBff(bffUrl(`/api/v1/tenant/bank-templates?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    items: TenantBankTemplateRow[];
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

export async function fetchTenantBankTemplateCatalog(
  companyId: string,
): Promise<{ ok: true; items: TenantBankTemplateCatalogRow[] } | { ok: false; status: number }> {
  const q = new URLSearchParams({ companyId });
  const r = await fetchBff(bffUrl(`/api/v1/tenant/bank-templates/catalog?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ items: TenantBankTemplateCatalogRow[] }>;
  return { ok: true, items: body.data.items };
}

export async function fetchTenantBankTemplate(id: string): Promise<
  { ok: true; template: TenantBankTemplateRow } | { ok: false; status: number }
> {
  const r = await fetchBff(bffUrl(`/api/v1/tenant/bank-templates/${encodeURIComponent(id)}`), {
    credentials: "same-origin",
  });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ template: TenantBankTemplateRow }>;
  return { ok: true, template: body.data.template };
}

export async function postTenantBankTemplate(payload: {
  companyId: string;
  platformBankTemplateId: string;
  accountNumber?: string | null;
  currencyCode?: string | null;
  active?: boolean;
}): Promise<TenantBankTemplateRow> {
  const r = await fetchBff(bffUrl("/api/v1/tenant/bank-templates"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ template: TenantBankTemplateRow }>;
  return body.data.template;
}

export async function putTenantBankTemplate(
  id: string,
  payload: {
    platformBankTemplateId: string;
    accountNumber?: string | null;
    currencyCode?: string | null;
    active: boolean;
  },
): Promise<TenantBankTemplateRow> {
  const r = await fetchBff(bffUrl(`/api/v1/tenant/bank-templates/${encodeURIComponent(id)}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ template: TenantBankTemplateRow }>;
  return body.data.template;
}

export async function patchActivateTenantBankTemplate(id: string): Promise<TenantBankTemplateRow> {
  const r = await fetchBff(bffUrl(`/api/v1/tenant/bank-templates/${encodeURIComponent(id)}/activate`), {
    method: "PATCH",
    credentials: "same-origin",
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ template: TenantBankTemplateRow }>;
  return body.data.template;
}

export async function patchDeactivateTenantBankTemplate(id: string): Promise<TenantBankTemplateRow> {
  const r = await fetchBff(bffUrl(`/api/v1/tenant/bank-templates/${encodeURIComponent(id)}/deactivate`), {
    method: "PATCH",
    credentials: "same-origin",
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ template: TenantBankTemplateRow }>;
  return body.data.template;
}

export async function deleteTenantBankTemplate(id: string): Promise<void> {
  const r = await fetchBff(bffUrl(`/api/v1/tenant/bank-templates/${encodeURIComponent(id)}`), {
    method: "DELETE",
    credentials: "same-origin",
  });
  if (r.status !== 204) throw new Error(await readFailureMessage(r));
}

// ─── Payment locations ───────────────────────────────────────────────────────

export type TenantPaymentLocationRow = {
  id: string;
  companyId: string;
  name: string;
  paymentType: "CASH" | "BANK_ACCOUNT";
  currency: string;
  bankTemplateId: string | null;
  bankTemplateName: string | null;
  bankName: string | null;
  swiftBic: string | null;
  accountNumberFormat: string | null;
  accountNumberMasked: string | null;
  accountNumberFull: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type TenantPaymentLocationsResult =
  | {
      ok: true;
      items: TenantPaymentLocationRow[];
      totalElements: number;
      page: number;
      size: number;
      totalPages: number;
    }
  | { ok: false; status: number };

export async function fetchTenantPaymentLocations(args: {
  companyId: string;
  page?: number;
  size?: number;
  active?: boolean;
}): Promise<TenantPaymentLocationsResult> {
  const params = new URLSearchParams({ companyId: args.companyId });
  if (args.page !== undefined) params.set("page", String(args.page));
  if (args.size !== undefined) params.set("size", String(args.size));
  if (args.active !== undefined) params.set("active", String(args.active));
  const r = await fetchBff(bffUrl(`/api/v1/tenant/payment-locations?${params}`), {
    credentials: "same-origin",
  });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    items: TenantPaymentLocationRow[];
    totalElements: number;
    page: number;
    size: number;
    totalPages: number;
  }>;
  return { ok: true, ...body.data };
}

export async function fetchTenantPaymentLocation(
  id: string,
): Promise<{ ok: true; item: TenantPaymentLocationRow } | { ok: false; status: number }> {
  const r = await fetchBff(bffUrl(`/api/v1/tenant/payment-locations/${encodeURIComponent(id)}`), {
    credentials: "same-origin",
  });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: TenantPaymentLocationRow }>;
  return { ok: true, item: body.data.item };
}

export async function postTenantPaymentLocation(payload: {
  companyId: string;
  name: string;
  paymentType: string;
  currency: string;
  bankTemplateId?: string | null;
  accountNumber?: string | null;
}): Promise<TenantPaymentLocationRow> {
  const r = await fetchBff(bffUrl("/api/v1/tenant/payment-locations"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantPaymentLocationRow }>;
  return body.data.item;
}

export async function putTenantPaymentLocation(
  id: string,
  payload: {
    name: string;
    currency: string;
    bankTemplateId?: string | null;
    accountNumber?: string | null;
  },
): Promise<TenantPaymentLocationRow> {
  const r = await fetchBff(bffUrl(`/api/v1/tenant/payment-locations/${encodeURIComponent(id)}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantPaymentLocationRow }>;
  return body.data.item;
}

export async function patchActivateTenantPaymentLocation(id: string): Promise<TenantPaymentLocationRow> {
  const r = await fetchBff(bffUrl(`/api/v1/tenant/payment-locations/${encodeURIComponent(id)}/activate`), {
    method: "PATCH",
    credentials: "same-origin",
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantPaymentLocationRow }>;
  return body.data.item;
}

export async function patchDeactivateTenantPaymentLocation(id: string): Promise<TenantPaymentLocationRow> {
  const r = await fetchBff(bffUrl(`/api/v1/tenant/payment-locations/${encodeURIComponent(id)}/deactivate`), {
    method: "PATCH",
    credentials: "same-origin",
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantPaymentLocationRow }>;
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
  payrollEnabled?: boolean | null;
  locale?: string;
}): Promise<CountriesResult> {
  const q = new URLSearchParams({
    page: String(args?.page ?? 0),
    size: String(args?.size ?? 50),
    locale: args?.locale ?? "en",
  });
  if (args?.search?.trim()) q.set("search", args.search.trim());
  if (typeof args?.payrollEnabled === "boolean") q.set("payrollEnabled", String(args.payrollEnabled));
  const r = await fetchBff(bffUrl(`/api/v1/countries?${q}`), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl("/api/v1/tenant/currencies"), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ items: TenantCurrencyItem[]; assignedCodes: string[] }>;
  return { ok: true, items: body.data.items, assignedCodes: body.data.assignedCodes };
}

/** PUT /api/v1/tenant/currencies — requires TENANT_CURRENCY_EDIT. */
export async function replaceTenantCurrencies(codes: string[]): Promise<void> {
  const r = await fetchBff(bffUrl("/api/v1/tenant/currencies"), {
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
  const r = await fetchBff(bffUrl(`/api/v1/tenant/exchange-rates?${q}`), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl("/api/v1/tenant/exchange-rates"), {
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
  const r = await fetchBff(bffUrl(`/api/v1/tenant/exchange-rates/${encodeURIComponent(id)}`), {
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
  const r = await fetchBff(bffUrl(`/api/v1/tenant/exchange-rates/${encodeURIComponent(id)}`), {
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
  const r = await fetchBff(bffUrl("/api/v1/platform/public-surface"), {
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
  const r = await fetchBff(bffUrl("/api/v1/tenant/billing/commercial-plans"), {
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
  const r = await fetchBff(bffUrl("/api/v1/tenant/billing/summary"), {
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
  const r = await fetchBff(bffUrl("/api/v1/tenant/billing/stripe/checkout-session"), {
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
  const r = await fetchBff(bffUrl("/api/v1/tenant/billing/paypal/subscription"), {
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
  const r = await fetchBff(bffUrl("/api/v1/tenant/billing/stripe/billing-portal-session"), {
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
  const r = await fetchBff(bffUrl("/api/v1/me"), {
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
  const r = await fetchBff("/api/bff/lens-tenant", {
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
  const r = await fetchBff("/api/bff/lens-tenant", {
    method: "DELETE",
    credentials: "same-origin",
  });
  if (!r.ok) {
    throw new Error(await readFailureMessage(r));
  }
}

/** All tenant memberships for the signed-in user (no tenant host required). */
export async function fetchMeTenants(): Promise<MeTenantsFetchResult> {
  const r = await fetchBff(bffUrl("/api/v1/me/tenants"), {
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
  const r = await fetchBff(bffUrl("/api/v1/me/navigation"), {
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
  const r = await fetchBff(bffUrl("/api/v1/tenant/users?page=0&size=1"), {
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
  const r = await fetchBff(bffUrl(`/api/v1/tenant/users?${q}`), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl("/api/v1/tenant/users/role-options"), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl(`/api/v1/tenant/users/${encodeURIComponent(userId)}`), {
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
  const r = await fetchBff(bffUrl(`/api/v1/tenant/roles${qs ? `?${qs}` : ""}`), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl(`/api/v1/tenant/roles/${encodeURIComponent(roleId)}`), {
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
  const r = await fetchBff(bffUrl("/api/v1/tenant/roles"), {
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
  const r = await fetchBff(bffUrl(`/api/v1/tenant/roles/${encodeURIComponent(args.roleId)}`), {
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
  const r = await fetchBff(`${path}?${q}`, { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl("/api/v1/tenant/documents"), {
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  const body = (await r.json()) as ApiEnvelope<{ items: DocumentHubItem[] }>;
  return { ok: true, items: body.data.items };
}

/** GET /api/v1/tenant/documents/by-entity — requires DOCUMENT_VIEW. */
export async function fetchTenantDocumentsByEntity(
  entityType: string,
  entityId: string,
): Promise<DocumentsHubFetchResult> {
  const q = new URLSearchParams({ entityType, entityId });
  const r = await fetchBff(bffUrl(`/api/v1/tenant/documents/by-entity?${q}`), {
    credentials: "same-origin",
  });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ items: DocumentHubItem[] }>;
  return { ok: true, items: body.data.items };
}

export type DocumentDownloadUrlResult =
  | { ok: true; downloadUrl: string; expiresAt: string }
  | { ok: false; status: number };

/** GET /api/v1/tenant/documents/{id}/download-url — DOCUMENT_VIEW + readable document. */
export async function fetchDocumentDownloadUrl(documentId: string): Promise<DocumentDownloadUrlResult> {
  const r = await fetchBff(bffUrl(`/api/v1/tenant/documents/${encodeURIComponent(documentId)}/download-url`), {
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
  const r = await fetchBff(bffUrl("/api/v1/tenant/documents/upload-sessions"), {
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
  payPeriodEndDate: string | null;
  timesheetEndDate: string | null;
  currentYear: number | null;
  currentPeriod: number | null;
  active: boolean;
  logoUrl: string | null;
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
  const r = await fetchBff(bffUrl(`/api/v1/companies?${q}`), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl(`/api/v1/companies/${encodeURIComponent(id)}`), { credentials: "same-origin" });
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
  payPeriodEndDate?: string | null;
  timesheetEndDate?: string | null;
  currentYear?: number | null;
  currentPeriod?: number | null;
  active?: boolean;
};

export async function createTenantCompany(payload: TenantCompanyUpsertPayload): Promise<TenantCompanyItem> {
  const r = await fetchBff(bffUrl("/api/v1/companies"), {
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
  const r = await fetchBff(bffUrl(`/api/v1/companies/${encodeURIComponent(id)}`), {
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
  const r = await fetchBff(bffUrl(`/api/v1/companies/${encodeURIComponent(id)}/active`), {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ active }),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantCompanyItem }>;
  return body.data.item;
}

export async function uploadCompanyLogo(
  id: string,
  file: File,
): Promise<{ ok: true; item: TenantCompanyItem } | { ok: false; status: number; message?: string }> {
  const form = new FormData();
  form.append("file", file);
  const r = await fetchBff(bffUrl(`/api/v1/companies/${encodeURIComponent(id)}/logo`), {
    method: "POST",
    credentials: "same-origin",
    body: form,
  });
  if (!r.ok) return { ok: false, status: r.status, message: await readFailureMessage(r) };
  const body = (await r.json()) as ApiEnvelope<{ item: TenantCompanyItem }>;
  return { ok: true, item: body.data.item };
}

export async function deleteCompanyLogo(
  id: string,
): Promise<{ ok: true; item: TenantCompanyItem } | { ok: false; status: number }> {
  const r = await fetchBff(bffUrl(`/api/v1/companies/${encodeURIComponent(id)}/logo`), {
    method: "DELETE",
    credentials: "same-origin",
  });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: TenantCompanyItem }>;
  return { ok: true, item: body.data.item };
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
  const r = await fetchBff(bffUrl(`/api/v1/departments?${q}`), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl(`/api/v1/departments/${encodeURIComponent(id)}`), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl("/api/v1/departments"), {
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
  const r = await fetchBff(bffUrl(`/api/v1/departments/${encodeURIComponent(id)}`), {
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
  const r = await fetchBff(bffUrl(`/api/v1/departments/${encodeURIComponent(id)}/active`), {
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
  const r = await fetchBff(bffUrl(`/api/v1/jobs?${q}`), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl(`/api/v1/jobs/${encodeURIComponent(id)}`), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl("/api/v1/jobs"), {
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
  const r = await fetchBff(bffUrl(`/api/v1/jobs/${encodeURIComponent(id)}`), {
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
  const r = await fetchBff(bffUrl(`/api/v1/jobs/${encodeURIComponent(id)}/active`), {
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
  const r = await fetchBff(bffUrl(`/api/v1/employee-groups?${q}`), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl(`/api/v1/employee-groups/${encodeURIComponent(id)}`), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl("/api/v1/employee-groups"), {
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
  const r = await fetchBff(bffUrl(`/api/v1/employee-groups/${encodeURIComponent(id)}`), {
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
  const r = await fetchBff(bffUrl(`/api/v1/employee-groups/${encodeURIComponent(id)}/active`), {
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
  badgeNumber: string | null;
  idNumber: string | null;
  gender: string | null;
  nationality: string | null;
  placeOfBirth: string | null;
  civilState: string | null;
  resignationDate: string | null;
  addressStreet: string | null;
  addressNumber: string | null;
  addressCity: string | null;
  addressCountry: string | null;
  addressPostalCode: string | null;
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
  companyIds?: string[];
  departmentId?: string;
  jobId?: string;
  employeeGroupId?: string;
  status?: string;
  firstName?: string;
  lastName?: string;
  active?: boolean | null;
}): Promise<TenantEmployeePageResult> {
  const q = new URLSearchParams({ page: String(args?.page ?? 0), size: String(args?.size ?? 20) });
  if (args?.companyId) q.set("companyId", args.companyId);
  if (args?.companyIds && args.companyIds.length > 0) {
    for (const cid of args.companyIds) q.append("companyId", cid);
  }
  if (args?.departmentId) q.set("departmentId", args.departmentId);
  if (args?.jobId) q.set("jobId", args.jobId);
  if (args?.employeeGroupId) q.set("employeeGroupId", args.employeeGroupId);
  if (args?.status) q.set("status", args.status);
  if (args?.firstName?.trim()) q.set("firstName", args.firstName.trim());
  if (args?.lastName?.trim()) q.set("lastName", args.lastName.trim());
  if (typeof args?.active === "boolean") q.set("active", String(args.active));
  const r = await fetchBff(bffUrl(`/api/v1/employees?${q}`), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl(`/api/v1/employees/${encodeURIComponent(id)}`), { credentials: "same-origin" });
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
  badgeNumber?: string | null;
  idNumber?: string | null;
  gender?: string | null;
  nationality?: string | null;
  placeOfBirth?: string | null;
  civilState?: string | null;
  resignationDate?: string | null;
  addressStreet?: string | null;
  addressNumber?: string | null;
  addressCity?: string | null;
  addressCountry?: string | null;
  addressPostalCode?: string | null;
};

export async function createTenantEmployee(payload: TenantEmployeeUpsertPayload): Promise<TenantEmployeeItem> {
  const r = await fetchBff(bffUrl("/api/v1/employees"), {
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
  const r = await fetchBff(bffUrl(`/api/v1/employees/${encodeURIComponent(id)}`), {
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
  const r = await fetchBff(bffUrl(`/api/v1/employees/${encodeURIComponent(id)}/status`), {
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
  const r = await fetchBff(bffUrl(`/api/v1/employees/${encodeURIComponent(id)}/active`), {
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
// Employee Compensation
// ---------------------------------------------------------------------------

export type TenantEmployeeCompensationItem = {
  id: string;
  employeeId: string;
  companyId: string;
  currencyCode: string;
  wageType: "PER_HOUR" | "PER_PERIOD" | "PER_MONTH" | "PER_YEAR";
  wageAmount: number;
  workTimeId: string | null;
  workTimeName: string | null;
  workTimeHoursPerDay: number | null;
  workTimeDaysPerWeek: number | null;
  applyTaxes: boolean;
  applyTaxExempt: boolean;
  applyAov: boolean;
  notes: string | null;
  derivedYearlyAmount: number | null;
  derivedPeriodAmount: number | null;
  derivedMonthlyAmount: number | null;
  derivedHourlyAmount: number | null;
  createdAt: string;
  updatedAt: string;
};

export type TenantEmployeeCompensationPayload = {
  currencyCode: string;
  wageType: "PER_HOUR" | "PER_PERIOD";
  wageAmount: number;
  workTimeId?: string | null;
  applyTaxes?: boolean;
  applyTaxExempt?: boolean;
  applyAov?: boolean;
  notes?: string | null;
};

export async function fetchTenantEmployeeCompensation(
  employeeId: string,
): Promise<{ ok: true; item: TenantEmployeeCompensationItem } | { ok: false; status: number }> {
  const r = await fetchBff(bffUrl(`/api/v1/employees/${encodeURIComponent(employeeId)}/compensation`), {
    credentials: "same-origin",
  });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: TenantEmployeeCompensationItem }>;
  return { ok: true, item: body.data.item };
}

export async function putTenantEmployeeCompensation(
  employeeId: string,
  payload: TenantEmployeeCompensationPayload,
): Promise<TenantEmployeeCompensationItem> {
  const r = await fetchBff(bffUrl(`/api/v1/employees/${encodeURIComponent(employeeId)}/compensation`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantEmployeeCompensationItem }>;
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
  const r = await fetchBff(bffUrl(`/api/v1/work-times?${q}`), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl(`/api/v1/work-times/${encodeURIComponent(id)}`), { credentials: "same-origin" });
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
  const r = await fetchBff(bffUrl("/api/v1/work-times"), {
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
  const r = await fetchBff(bffUrl(`/api/v1/work-times/${encodeURIComponent(id)}`), {
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
  const r = await fetchBff(bffUrl(`/api/v1/work-times/${encodeURIComponent(id)}/active`), {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ active }),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantWorkTimeItem }>;
  return body.data.item;
}

// ── Wage components (tenant catalog) ───────────────────────────────────────────

export type TenantWageComponentItem = {
  id: string;
  companyId: string;
  platformTemplateId: string | null;
  /** Platform template_code when the row was created from a template. */
  templateCode: string | null;
  code: string;
  name: string;
  description: string | null;
  componentType: string;
  category: string;
  netEffect: string;
  taxableWageTax: boolean;
  taxableSocialSecurity: boolean;
  taxablePension: boolean;
  taxableVacationReserve: boolean;
  calculationMethod: string;
  percentageBase: string | null;
  formulaExpression: string | null;
  defaultAmount: string | null;
  roundingStrategy: string;
  processingOrder: number;
  phase: string;
  maintainsBalance: boolean;
  balanceType: string | null;
  balanceDirection: string | null;
  counterComponentId: string | null;
  debitTenantLedgerId: string | null;
  creditTenantLedgerId: string | null;
  postingStrategy: string | null;
  printOnPayslip: boolean;
  auxiliary: boolean;
  applyInPayroll: boolean;
  recurrence: string | null;
  countryRuleKey: string | null;
  platformCountryTaxRuleId: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type TenantWageComponentPageResult =
  | { ok: true; items: TenantWageComponentItem[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export async function fetchTenantWageComponents(args?: {
  page?: number;
  size?: number;
  companyId?: string;
  active?: boolean | null;
}): Promise<TenantWageComponentPageResult> {
  const q = new URLSearchParams({ page: String(args?.page ?? 0), size: String(args?.size ?? 20) });
  if (args?.companyId) q.set("companyId", args.companyId);
  if (typeof args?.active === "boolean") q.set("active", String(args.active));
  const r = await fetchBff(bffUrl(`/api/v1/wage-components?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    data: TenantWageComponentItem[];
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

export async function fetchTenantWageComponent(
  id: string,
): Promise<{ ok: true; item: TenantWageComponentItem } | { ok: false; status: number }> {
  const r = await fetchBff(bffUrl(`/api/v1/wage-components/${encodeURIComponent(id)}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: TenantWageComponentItem }>;
  return { ok: true, item: body.data.item };
}

export type TenantWageComponentTemplateCatalogItem = {
  id: string;
  countryCode: string;
  templateCode: string;
  name: string;
  description: string | null;
  processingOrderHint: number | null;
  phaseHint: string | null;
  debitPlatformLedgerTemplateId: string | null;
  creditPlatformLedgerTemplateId: string | null;
  duplicable: boolean;
  printOnPayslip: boolean;
  auxiliary: boolean;
  applyInPayroll: boolean;
  recurrence: string | null;
  countryRuleKey: string | null;
  platformCountryTaxRuleId: string | null;
};

export type PlatformStatutoryWageComponentItem = {
  id: string;
  countryCode: string;
  code: string;
  name: string;
  description: string | null;
  statutory: boolean;
  componentType: string;
  category: string;
  netEffect: string;
  calculationMethod: string;
  processingOrder: number;
  phase: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export async function fetchTenantWageComponentTemplates(
  companyId: string,
): Promise<{ ok: true; items: TenantWageComponentTemplateCatalogItem[] } | { ok: false; status: number }> {
  const q = new URLSearchParams({ companyId });
  const r = await fetchBff(bffUrl(`/api/v1/wage-components/catalog/templates?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ items: TenantWageComponentTemplateCatalogItem[] }>;
  return { ok: true, items: body.data.items };
}

export async function fetchTenantWageComponentStatutory(
  companyId: string,
): Promise<{ ok: true; items: PlatformStatutoryWageComponentItem[] } | { ok: false; status: number }> {
  const q = new URLSearchParams({ companyId });
  const r = await fetchBff(bffUrl(`/api/v1/wage-components/catalog/statutory?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ items: PlatformStatutoryWageComponentItem[] }>;
  return { ok: true, items: body.data.items };
}

export type TenantWageComponentCreatePayload = {
  companyId: string;
  platformTemplateId: string;
  /** Appended after template code with an underscore; omit or empty for template code only. */
  codeSuffix?: string | null;
  name?: string | null;
};

export async function createTenantWageComponent(payload: TenantWageComponentCreatePayload): Promise<TenantWageComponentItem> {
  const r = await fetchBff(bffUrl("/api/v1/wage-components"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantWageComponentItem }>;
  return body.data.item;
}

export type TenantWageComponentPutPayload = {
  companyId: string;
  name: string;
  codeSuffix: string | null;
  debitTenantLedgerId: string | null;
  creditTenantLedgerId: string | null;
  printOnPayslip: boolean;
  active: boolean;
  formulaExpression?: string | null;
};

export async function putTenantWageComponent(
  id: string,
  payload: TenantWageComponentPutPayload,
): Promise<TenantWageComponentItem> {
  const r = await fetchBff(bffUrl(`/api/v1/wage-components/${encodeURIComponent(id)}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantWageComponentItem }>;
  return body.data.item;
}

export async function patchTenantWageComponentActive(id: string, active: boolean): Promise<TenantWageComponentItem> {
  const r = await fetchBff(bffUrl(`/api/v1/wage-components/${encodeURIComponent(id)}/active`), {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ active }),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantWageComponentItem }>;
  return body.data.item;
}

// ── Pay Periods ──────────────────────────────────────────────────────────────

export type TenantPayPeriodItem = {
  id: string;
  companyId: string;
  year: number;
  startDate: string;
  endDate: string;
  status: string;
  supervisorApprovedAt: string | null;
  supervisorApprovedByUserId: string | null;
  createdAt: string;
  updatedAt: string;
};

export type TenantPayPeriodPageResult =
  | { ok: true; items: TenantPayPeriodItem[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export type TenantPayPeriodUpsertPayload = {
  companyId: string;
  year: number;
  startDate: string;
  endDate: string;
  status: string;
};

export async function fetchTenantPayPeriods(args?: {
  page?: number;
  size?: number;
  companyId?: string;
  year?: number | null;
  status?: string | null;
}): Promise<TenantPayPeriodPageResult> {
  const q = new URLSearchParams({ page: String(args?.page ?? 0), size: String(args?.size ?? 20) });
  if (args?.companyId) q.set("companyId", args.companyId);
  if (args?.year != null) q.set("year", String(args.year));
  if (args?.status) q.set("status", args.status);
  const r = await fetchBff(bffUrl(`/api/v1/pay-periods?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    data: TenantPayPeriodItem[];
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

export async function fetchTenantPayPeriod(id: string): Promise<{ ok: true; item: TenantPayPeriodItem } | { ok: false; status: number }> {
  const r = await fetchBff(bffUrl(`/api/v1/pay-periods/${encodeURIComponent(id)}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: TenantPayPeriodItem }>;
  return { ok: true, item: body.data.item };
}

export async function createTenantPayPeriod(payload: TenantPayPeriodUpsertPayload): Promise<TenantPayPeriodItem> {
  const r = await fetchBff(bffUrl("/api/v1/pay-periods"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantPayPeriodItem }>;
  return body.data.item;
}

export async function putTenantPayPeriod(id: string, payload: TenantPayPeriodUpsertPayload): Promise<TenantPayPeriodItem> {
  const r = await fetchBff(bffUrl(`/api/v1/pay-periods/${encodeURIComponent(id)}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantPayPeriodItem }>;
  return body.data.item;
}

export type TenantFormulaPreviewLine = {
  employeeId: string;
  tenantWageComponentId: string | null;
  tenantWageComponentCode: string;
  calculationMethod: string;
  evaluatedAmount: number;
  formulaExpression: string | null;
  componentSource?: string | null;
  platformWageComponentId?: string | null;
};

export type TenantFormulaPreviewResult = {
  items: TenantFormulaPreviewLine[];
  employeeBaseTotals: Record<string, Record<string, number>>;
  employeeNetPay: Record<string, number>;
  /** Art. 17 N (aantal loontijdvakken) per employee — Suriname only. */
  employeeArt17AttributionPeriods?: Record<string, number>;
  employeeCalculationTraceLines?: Record<string, PayrollCalculationTraceLine[]>;
  employeeCalculationTraceText?: Record<string, string>;
};

export type PayrollCalculationTraceLine = {
  sequence: number;
  enginePhase: string;
  employeeId: string;
  componentCode: string;
  componentName: string;
  componentSource: string;
  componentType: string;
  category: string;
  netEffect: string;
  payEffect: string;
  taxationSummary: string;
  calculationMethod: string;
  countryRuleKey: string | null;
  processingOrder: number | null;
  factorQuantity: number | null;
  factorRate: number | null;
  factorExplanation: string | null;
  amount: number | null;
  amountExplanation: string | null;
  formulaExpression: string | null;
  includedInResult: boolean;
  skipReason: string | null;
};

export async function postTenantPayPeriodFormulaPreview(
  payPeriodId: string,
  payload: { employeeIds: string[]; persistToPeriodInputs?: boolean },
): Promise<
  { ok: true; result: TenantFormulaPreviewResult } | { ok: false; status: number; message: string }
> {
  const r = await fetchBff(bffUrl(`/api/v1/pay-periods/${encodeURIComponent(payPeriodId)}/formula-preview`), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      employeeIds: payload.employeeIds,
      ...(payload.persistToPeriodInputs ? { persistToPeriodInputs: true } : {}),
    }),
  });
  if (!r.ok) return { ok: false, status: r.status, message: await readFailureMessage(r) };
  const body = (await r.json()) as ApiEnvelope<{
    items: TenantFormulaPreviewLine[];
    employeeBaseTotals: Record<string, Record<string, number>>;
    employeeNetPay: Record<string, number>;
    employeeArt17AttributionPeriods?: Record<string, number>;
    employeeCalculationTraceLines?: Record<string, PayrollCalculationTraceLine[]>;
    employeeCalculationTraceText?: Record<string, string>;
  }>;
  return {
    ok: true,
    result: {
      items: body.data.items,
      employeeBaseTotals: body.data.employeeBaseTotals ?? {},
      employeeNetPay: body.data.employeeNetPay ?? {},
      employeeArt17AttributionPeriods: body.data.employeeArt17AttributionPeriods ?? {},
      employeeCalculationTraceLines: body.data.employeeCalculationTraceLines ?? {},
      employeeCalculationTraceText: body.data.employeeCalculationTraceText ?? {},
    },
  };
}

export async function fetchTenantPayPeriodCalculationTraceDownload(
  payPeriodId: string,
  employeeId: string,
): Promise<{ ok: true; blob: Blob; filename: string } | { ok: false; status: number; message: string }> {
  const r = await fetchBff(
    bffUrl(
      `/api/v1/pay-periods/${encodeURIComponent(payPeriodId)}/employees/${encodeURIComponent(employeeId)}/calculation-trace.txt`,
    ),
    { credentials: "same-origin" },
  );
  if (!r.ok) return { ok: false, status: r.status, message: await readFailureMessage(r) };
  const blob = await r.blob();
  const disposition = r.headers.get("Content-Disposition") ?? "";
  const match = /filename="([^"]+)"/.exec(disposition);
  return { ok: true, blob, filename: match?.[1] ?? `payroll-calculation-${employeeId}.txt` };
}

export type FormulaMockContext = {
  compensationPeriodicRate?: string | number | null;
  /** 1 = PER_HOUR compensation, 0 = periodic (month/year/period). */
  compensationIsHourly?: string | number | null;
  /** Derived hourly rate for overtime etc. (periodic ÷ contract hours per period). */
  compensationHourlyRate?: string | number | null;
  transactionQuantity?: string | number | null;
  transactionRate?: string | number | null;
  transactionAmount?: string | number | null;
  definitionDefaultAmount?: string | number | null;
  componentAmounts?: Record<string, string | number | null>;
};

export type FormulaValidateRequest = {
  calculationMethod: string;
  formulaExpression?: string | null;
  percentageBase?: string | null;
  roundingStrategy?: string | null;
  mockContext: FormulaMockContext;
};

export type FormulaValidateResult = { ok: boolean; amount: number | string };

export async function postTenantWageComponentValidateFormula(
  body: FormulaValidateRequest,
): Promise<{ ok: true; amount: number | string } | { ok: false; status: number; message?: string }> {
  const r = await fetchBff(bffUrl("/api/v1/wage-components/validate-formula"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!r.ok) {
    return { ok: false, status: r.status, message: await readFailureMessage(r) };
  }
  const res = (await r.json()) as ApiEnvelope<{ item: FormulaValidateResult }>;
  return { ok: true, amount: res.data.item.amount };
}

export async function postPlatformWageComponentTemplateValidateFormula(
  body: FormulaValidateRequest,
): Promise<{ ok: true; amount: number | string } | { ok: false; status: number; message?: string }> {
  const r = await fetchBff(bffUrl("/api/v1/platform/wage-component-templates/validate-formula"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!r.ok) {
    return { ok: false, status: r.status, message: await readFailureMessage(r) };
  }
  const res = (await r.json()) as ApiEnvelope<{ item: FormulaValidateResult }>;
  return { ok: true, amount: res.data.item.amount };
}

export async function patchTenantPayPeriodStatus(id: string, status: string): Promise<TenantPayPeriodItem> {
  const r = await fetchBff(bffUrl(`/api/v1/pay-periods/${encodeURIComponent(id)}/status`), {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ status }),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantPayPeriodItem }>;
  return body.data.item;
}

export async function supervisorApproveTenantPayPeriod(id: string): Promise<TenantPayPeriodItem> {
  const r = await fetchBff(bffUrl(`/api/v1/pay-periods/${encodeURIComponent(id)}/supervisor-approve`), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: "{}",
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantPayPeriodItem }>;
  return body.data.item;
}

// ── Pay Period Runs ───────────────────────────────────────────────────────────

export type TenantPayPeriodRunItem = {
  id: string;
  payPeriodId: string;
  tenantId: string;
  runType: string;
  runNumber: number;
  createdAt: string;
  updatedAt: string;
};

export type TenantPayPeriodRunPageResult =
  | { ok: true; items: TenantPayPeriodRunItem[]; page: number; size: number; totalElements: number; totalPages: number }
  | { ok: false; status: number };

export async function fetchTenantPayPeriodRuns(
  payPeriodId: string,
  args?: { page?: number; size?: number },
): Promise<TenantPayPeriodRunPageResult> {
  const q = new URLSearchParams({ page: String(args?.page ?? 0), size: String(args?.size ?? 20) });
  const r = await fetchBff(bffUrl(`/api/v1/pay-periods/${encodeURIComponent(payPeriodId)}/runs?${q}`), {
    credentials: "same-origin",
  });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    data: TenantPayPeriodRunItem[];
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

export async function createTenantPayPeriodRun(payload: {
  payPeriodId: string;
  runType: string;
}): Promise<TenantPayPeriodRunItem> {
  const r = await fetchBff(bffUrl("/api/v1/pay-period-runs"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantPayPeriodRunItem }>;
  return body.data.item;
}

export async function generateTenantCompanyPayPeriods(
  companyId: string,
  args: { fromDate?: string; yearsAhead?: number }
): Promise<{ created: number }> {
  const r = await fetchBff(bffUrl(`/api/v1/companies/${companyId}/pay-periods/generate`), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(args),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ created: number }>;
  return { created: body.data.created };
}

// ── Employee payroll standing instructions & wage component transactions ─────

export type TenantPayrollStandingInstructionItem = {
  id: string;
  companyId: string;
  employeeId: string;
  tenantWageComponentId: string;
  wageComponentCode: string;
  wageComponentName: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  amount: number | null;
  quantity: number | null;
  rate: number | null;
  recurrence: string;
  active: boolean;
  amountOverride: boolean;
  factorOverride: boolean;
  remarks: string | null;
  createdAt: string;
  updatedAt: string;
};

export async function fetchTenantPayrollStandingInstructions(args: {
  companyId: string;
  employeeId: string;
}): Promise<{ ok: true; items: TenantPayrollStandingInstructionItem[] } | { ok: false; status: number }> {
  const q = new URLSearchParams({ companyId: args.companyId, employeeId: args.employeeId });
  const r = await fetchBff(bffUrl(`/api/v1/payroll-standing-instructions?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ data: TenantPayrollStandingInstructionItem[] }>;
  return { ok: true, items: body.data.data };
}

export type TenantPayrollStandingInstructionCreatePayload = {
  companyId: string;
  employeeId: string;
  tenantWageComponentId: string;
  effectiveFrom: string;
  effectiveTo?: string | null;
  amount?: number | null;
  quantity?: number | null;
  rate?: number | null;
  recurrence?: string | null;
  amountOverride?: boolean | null;
  factorOverride?: boolean | null;
  remarks?: string | null;
};

export type TenantPayrollStandingInstructionPutPayload = {
  companyId: string;
  employeeId: string;
  tenantWageComponentId: string;
  effectiveFrom: string;
  effectiveTo?: string | null;
  amount?: number | null;
  quantity?: number | null;
  rate?: number | null;
  recurrence?: string | null;
  active?: boolean | null;
  amountOverride?: boolean | null;
  factorOverride?: boolean | null;
  remarks?: string | null;
};

export async function createTenantPayrollStandingInstruction(
  payload: TenantPayrollStandingInstructionCreatePayload,
): Promise<TenantPayrollStandingInstructionItem> {
  const r = await fetchBff(bffUrl("/api/v1/payroll-standing-instructions"), {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (r.status !== 201) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantPayrollStandingInstructionItem }>;
  return body.data.item;
}

export async function putTenantPayrollStandingInstruction(
  id: string,
  payload: TenantPayrollStandingInstructionPutPayload,
): Promise<TenantPayrollStandingInstructionItem> {
  const r = await fetchBff(bffUrl(`/api/v1/payroll-standing-instructions/${encodeURIComponent(id)}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantPayrollStandingInstructionItem }>;
  return body.data.item;
}

export type TenantMaterializePayrollInputsResult = {
  created: number;
  updated: number;
  skippedManualOverride: number;
  skippedInactiveEmployee: number;
  skippedInactiveInstruction: number;
  skippedInactiveWageComponent: number;
};

export async function materializeTenantPayrollInputs(
  payPeriodId: string,
  companyId: string,
  options?: { employeeIds?: string[] },
): Promise<TenantMaterializePayrollInputsResult> {
  const r = await fetchBff(
    bffUrl(`/api/v1/pay-periods/${encodeURIComponent(payPeriodId)}/materialize-payroll-inputs`),
    {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        companyId,
        ...(options?.employeeIds?.length ? { employeeIds: options.employeeIds } : {}),
      }),
    },
  );
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantMaterializePayrollInputsResult }>;
  return body.data.item;
}

export type CompanyCalendarAdvanceResult = {
  advanced: boolean;
  previousYear: number | null;
  previousPeriod: number | null;
  currentYear: number | null;
  currentPeriod: number | null;
  payPeriodEndDate: string | null;
};

export type TenantPayPeriodFinalizeResult = {
  runId: string;
  linesCreated: number;
  employeeCount: number;
  employeeNetPay: Record<string, number>;
  balancesUpdated: number;
  postingsCreated: number;
  calendarAdvance: CompanyCalendarAdvanceResult | null;
};

export async function finalizeTenantPayPeriodRun(
  payPeriodId: string,
  runId: string,
  payload?: { employeeIds?: string[]; materializeInputs?: boolean },
): Promise<TenantPayPeriodFinalizeResult> {
  const r = await fetchBff(
    bffUrl(`/api/v1/pay-periods/${encodeURIComponent(payPeriodId)}/runs/${encodeURIComponent(runId)}/finalize`),
    {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload ?? {}),
    },
  );
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantPayPeriodFinalizeResult }>;
  return body.data.item;
}

export type TenantPayrollResultLineItem = {
  id: string;
  payPeriodRunId: string;
  employeeId: string;
  componentSource: string;
  componentRefId: string;
  phase: string;
  processingOrderSnapshot: number;
  quantity: number | null;
  rate: number | null;
  amount: number;
  roundedAmount: number;
  createdAt: string;
};

// ── Employee payment destinations & period disbursement ─────────────────────

export type TenantEmployeePaymentDestinationItem = {
  id: string;
  companyId: string;
  employeeId: string;
  channelType: string;
  paymentLocationId: string | null;
  paymentLocationName: string | null;
  bankTemplateId: string | null;
  bankName: string | null;
  accountNumber: string | null;
  currency: string;
  splitType: string;
  splitValue: number;
  sortOrder: number;
  active: boolean;
};

export type TenantEmployeePayPeriodPaymentItem = {
  id: string;
  payPeriodId: string;
  payPeriodRunId: string;
  payPeriodYear: number;
  payPeriodStartDate: string;
  payPeriodEndDate: string;
  payPeriodStatus: string;
  channelType: string;
  paymentLocationId: string | null;
  paymentLocationName: string | null;
  bankTemplateId: string | null;
  bankName: string | null;
  accountNumber: string | null;
  currency: string;
  splitType: string;
  splitValue: number;
  allocatedAmount: number;
};

export type TenantEmployeePaymentPeriodGroup = {
  payPeriodId: string | null;
  year: number;
  startDate: string;
  endDate: string;
  status: string;
  periodNumber: number | null;
  payments: TenantEmployeePayPeriodPaymentItem[];
};

export type TenantEmployeePaymentOverview = {
  destinations: TenantEmployeePaymentDestinationItem[];
  activePeriod: TenantEmployeePaymentPeriodGroup;
  closedPeriods: TenantEmployeePaymentPeriodGroup[];
};

export async function fetchTenantEmployeePaymentOverview(
  employeeId: string,
): Promise<{ ok: true; item: TenantEmployeePaymentOverview } | { ok: false; status: number }> {
  const r = await fetchBff(bffUrl(`/api/v1/employees/${encodeURIComponent(employeeId)}/payment-overview`), {
    credentials: "same-origin",
  });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ item: TenantEmployeePaymentOverview }>;
  return { ok: true, item: body.data.item };
}

export type TenantEmployeePaymentHistoryPageResult =
  | {
      ok: true;
      items: TenantEmployeePayPeriodPaymentItem[];
      page: number;
      size: number;
      totalElements: number;
      totalPages: number;
    }
  | { ok: false; status: number };

export async function fetchTenantEmployeePaymentHistory(
  employeeId: string,
  args?: { year?: number | null; payPeriodId?: string | null; page?: number; size?: number },
): Promise<TenantEmployeePaymentHistoryPageResult> {
  const q = new URLSearchParams({
    page: String(args?.page ?? 0),
    size: String(args?.size ?? 50),
  });
  if (args?.year != null) q.set("year", String(args.year));
  if (args?.payPeriodId) q.set("payPeriodId", args.payPeriodId);
  const r = await fetchBff(
    bffUrl(`/api/v1/employees/${encodeURIComponent(employeeId)}/payment-history?${q}`),
    { credentials: "same-origin" },
  );
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    data: TenantEmployeePayPeriodPaymentItem[];
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

export type TenantEmployeePaymentDestinationPutItem = {
  channelType: string;
  paymentLocationId?: string | null;
  bankTemplateId?: string | null;
  accountNumber?: string | null;
  currency: string;
  splitType: string;
  splitValue: number;
  sortOrder?: number | null;
  active?: boolean | null;
};

export async function putTenantEmployeePaymentDestinations(
  employeeId: string,
  items: TenantEmployeePaymentDestinationPutItem[],
): Promise<TenantEmployeePaymentDestinationItem[]> {
  const r = await fetchBff(bffUrl(`/api/v1/employees/${encodeURIComponent(employeeId)}/payment-destinations`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ items }),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ data: TenantEmployeePaymentDestinationItem[] }>;
  return body.data.data;
}

export async function fetchTenantPayrollResultLines(
  runId: string,
  args?: { employeeId?: string },
): Promise<{ ok: true; items: TenantPayrollResultLineItem[] } | { ok: false; status: number }> {
  const q = new URLSearchParams();
  if (args?.employeeId) q.set("employeeId", args.employeeId);
  const suffix = q.size ? `?${q}` : "";
  const r = await fetchBff(bffUrl(`/api/v1/pay-period-runs/${encodeURIComponent(runId)}/result-lines${suffix}`), {
    credentials: "same-origin",
  });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{ data: TenantPayrollResultLineItem[] }>;
  return { ok: true, items: body.data.data };
}

export type TenantWageComponentTransactionItem = {
  id: string;
  companyId: string;
  employeeId: string;
  payPeriodId: string;
  payPeriodRunId: string | null;
  tenantWageComponentId: string;
  wageComponentCode: string;
  wageComponentName: string;
  quantity: number | null;
  rate: number | null;
  amount: number;
  manualOverride: boolean;
  remarks: string | null;
  createdAt: string;
  updatedAt: string;
};

export async function fetchTenantWageComponentTransactions(args: {
  companyId: string;
  payPeriodId: string;
  employeeId?: string | null;
  page?: number;
  size?: number;
}): Promise<
  | {
      ok: true;
      items: TenantWageComponentTransactionItem[];
      page: number;
      size: number;
      totalElements: number;
      totalPages: number;
    }
  | { ok: false; status: number }
> {
  const q = new URLSearchParams({
    page: String(args.page ?? 0),
    size: String(args.size ?? 20),
    companyId: args.companyId,
    payPeriodId: args.payPeriodId,
  });
  if (args.employeeId) q.set("employeeId", args.employeeId);
  const r = await fetchBff(bffUrl(`/api/v1/wage-component-transactions?${q}`), { credentials: "same-origin" });
  if (!r.ok) return { ok: false, status: r.status };
  const body = (await r.json()) as ApiEnvelope<{
    data: TenantWageComponentTransactionItem[];
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

export async function putTenantWageComponentTransaction(
  id: string,
  payload: {
    amount?: number | null;
    quantity?: number | null;
    rate?: number | null;
    manualOverride?: boolean | null;
    remarks?: string | null;
  },
): Promise<TenantWageComponentTransactionItem> {
  const r = await fetchBff(bffUrl(`/api/v1/wage-component-transactions/${encodeURIComponent(id)}`), {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!r.ok) throw new Error(await readFailureMessage(r));
  const body = (await r.json()) as ApiEnvelope<{ item: TenantWageComponentTransactionItem }>;
  return body.data.item;
}

export async function completeDocumentUpload(args: {
  documentId: string;
  storageKey: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
}): Promise<{ ok: true } | { ok: false; status: number }> {
  const r = await fetchBff(bffUrl("/api/v1/tenant/documents/complete"), {
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
  const r = await fetchBff(bffUrl(`/api/v1/tenant/documents/${encodeURIComponent(documentId)}/shares`), {
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
  const r = await fetchBff(bffUrl(`/api/v1/tenant/documents/${encodeURIComponent(documentId)}/shares`), {
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
  const r = await fetchBff(
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
  const r = await fetchBff(bffUrl(`/api/v1/tenant/documents/${encodeURIComponent(documentId)}/attachments`), {
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
  const r = await fetchBff(bffUrl(`/api/v1/tenant/documents/${encodeURIComponent(documentId)}/attachments`), {
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
  const r = await fetchBff(
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
  const r = await fetchBff(bffUrl(`/api/v1/tenant/documents/${encodeURIComponent(documentId)}`), {
    method: "DELETE",
    credentials: "same-origin",
  });
  if (!r.ok) {
    return { ok: false, status: r.status };
  }
  return { ok: true };
}
