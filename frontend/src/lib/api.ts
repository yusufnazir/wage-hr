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
  /** Active commercial plan feature codes for the current tenant host; empty when none or not subscribed. */
  planFeatureCodes: string[];
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
    email: raw.email,
    locale: raw.locale ?? "en",
    privileges: raw.privileges,
    planFeatureCodes: raw.planFeatureCodes ?? [],
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
