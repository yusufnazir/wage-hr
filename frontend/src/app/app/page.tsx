"use client";

import { useEffect, useState } from "react";
import {
  createPaypalSubscriptionSession,
  createStripeBillingPortalSession,
  createStripeCheckoutSession,
  fetchBillingSummary,
  fetchTenantUserListProbe,
  fetchPrivacyExport,
  fetchTenantCommercialPlans,
  postPrivacyErasureRequest,
  type BillingSummaryFetchResult,
  type NavigationItem,
  type TenantCommercialPlansFetchResult,
} from "@/lib/api";
import { DemoUserViewBody } from "@/components/demo/DemoUserViewBody";
import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { navLabel } from "@/messages/nav";
import { tenantWebAppUrlForHandle } from "@/lib/web-origins";

type ViewState =
  | { kind: "loading" }
  | {
      kind: "ready";
      demo?: { ok: true; totalElements: number } | { ok: false; status: number };
      billingSummary?: BillingSummaryFetchResult;
      commercialPlans?: TenantCommercialPlansFetchResult;
    };

export default function TenantAppShellPage() {
  const { me, navigation, navigationLoadError, tenants, tenantsLoadError } = useTenantAppSession();
  const [state, setState] = useState<ViewState>({ kind: "loading" });
  const [privacyBusy, setPrivacyBusy] = useState(false);
  const [privacyMsg, setPrivacyMsg] = useState<string | null>(null);
  const [stripePortalBusy, setStripePortalBusy] = useState(false);
  const [stripePortalMsg, setStripePortalMsg] = useState<string | null>(null);
  const [selectedCommercialPlanId, setSelectedCommercialPlanId] = useState<string | null>(null);
  const [stripeCheckoutBusy, setStripeCheckoutBusy] = useState(false);
  const [paypalSubscribeBusy, setPaypalSubscribeBusy] = useState(false);
  const [billingActionMsg, setBillingActionMsg] = useState<string | null>(null);
  const [billingReturnFlash, setBillingReturnFlash] = useState<string | null>(null);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    const q = new URLSearchParams(window.location.search).get("billing");
    if (!q) {
      return;
    }
    if (q === "stripe_success") {
      setBillingReturnFlash(
        "Returned from Stripe checkout. Subscription and plan features update after the provider webhook is processed.",
      );
    } else if (q === "stripe_cancel") {
      setBillingReturnFlash("Stripe checkout was cancelled.");
    } else if (q === "paypal_return") {
      setBillingReturnFlash(
        "Returned from PayPal. Subscription and plan features update after the provider webhook is processed.",
      );
    } else if (q === "paypal_cancel") {
      setBillingReturnFlash("PayPal approval was cancelled.");
    }
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const [demo, billingSummary, commercialPlans] = await Promise.all([
          fetchTenantUserListProbe(),
          fetchBillingSummary(),
          fetchTenantCommercialPlans(),
        ]);
        if (cancelled) return;

        setState({ kind: "ready", demo, billingSummary, commercialPlans });
      } catch {
        if (cancelled) return;
        setState({ kind: "ready" });
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, [me.email]);

  useEffect(() => {
    if (state.kind !== "ready" || !state.commercialPlans?.ok) {
      return;
    }
    const plans = state.commercialPlans.plans;
    if (plans.length === 0) {
      return;
    }
    setSelectedCommercialPlanId((prev) => {
      if (prev && plans.some((p) => p.id === prev)) {
        return prev;
      }
      return plans[0].id;
    });
  }, [state]);

  async function onPrivacyExport() {
    setPrivacyBusy(true);
    setPrivacyMsg(null);
    try {
      const exp = await fetchPrivacyExport();
      const blob = new Blob([JSON.stringify(exp, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "wage-payroll-privacy-export.json";
      a.click();
      URL.revokeObjectURL(url);
      setPrivacyMsg("Export downloaded (server logged SUBJECT_DATA_EXPORTED).");
    } catch (e) {
      setPrivacyMsg(e instanceof Error ? e.message : "Export failed");
    } finally {
      setPrivacyBusy(false);
    }
  }

  async function onErasureRequest() {
    if (!window.confirm("Submit an erasure request to operators? Fulfillment is not automated in M1.")) {
      return;
    }
    setPrivacyBusy(true);
    setPrivacyMsg(null);
    try {
      await postPrivacyErasureRequest();
      setPrivacyMsg("Erasure request accepted (202). Logged for operators.");
    } catch (e) {
      setPrivacyMsg(e instanceof Error ? e.message : "Request failed");
    } finally {
      setPrivacyBusy(false);
    }
  }

  function billingRedirectBasePath(): string {
    if (typeof window === "undefined") {
      return "";
    }
    const u = new URL(window.location.href);
    return `${u.origin}${u.pathname}`;
  }

  function billingRedirectHint(): string | null {
    if (typeof window === "undefined") {
      return null;
    }
    const u = new URL(window.location.href);
    const host = u.hostname;
    const devHttp =
      host === "localhost" || host === "127.0.0.1" || host === "lvh.me" || host.endsWith(".lvh.me");
    if (u.protocol === "http:" && !devHttp) {
      return "Checkout and PayPal redirects require HTTPS in production. For local HTTP, use localhost/127.0.0.1, *.lvh.me (with STRIPE_ALLOW_INSECURE_CHECKOUT_URLS on the API), or HTTPS.";
    }
    return null;
  }

  async function onStripeCheckout() {
    if (state.kind !== "ready" || !selectedCommercialPlanId || !state.commercialPlans?.ok) {
      return;
    }
    const plan = state.commercialPlans.plans.find((p) => p.id === selectedCommercialPlanId);
    const priceId = plan?.stripeSubscriptionPriceId?.trim();
    if (!plan || !priceId) {
      setBillingActionMsg("Pick a plan that has a Stripe price id configured.");
      return;
    }
    setStripeCheckoutBusy(true);
    setBillingActionMsg(null);
    try {
      const base = billingRedirectBasePath();
      const res = await createStripeCheckoutSession({
        commercialPlanId: plan.id,
        priceId,
        successUrl: `${base}?billing=stripe_success`,
        cancelUrl: `${base}?billing=stripe_cancel`,
      });
      if (!res.ok) {
        setBillingActionMsg(`Stripe checkout request failed (HTTP ${res.status}).`);
        return;
      }
      window.location.assign(res.url);
    } catch (e) {
      setBillingActionMsg(e instanceof Error ? e.message : "Request failed");
    } finally {
      setStripeCheckoutBusy(false);
    }
  }

  async function onPaypalSubscribe() {
    if (state.kind !== "ready" || !selectedCommercialPlanId || !state.commercialPlans?.ok) {
      return;
    }
    const plan = state.commercialPlans.plans.find((p) => p.id === selectedCommercialPlanId);
    if (!plan) {
      return;
    }
    const planId = plan.paypalBillingPlanId?.trim();
    if (!planId) {
      setBillingActionMsg("Pick a plan that has a PayPal billing plan id (P-…) configured, or ask the operator to bind one.");
      return;
    }
    setPaypalSubscribeBusy(true);
    setBillingActionMsg(null);
    try {
      const base = billingRedirectBasePath();
      const res = await createPaypalSubscriptionSession({
        commercialPlanId: plan.id,
        planId,
        returnUrl: `${base}?billing=paypal_return`,
        cancelUrl: `${base}?billing=paypal_cancel`,
      });
      if (!res.ok) {
        setBillingActionMsg(`PayPal subscribe request failed (HTTP ${res.status}).`);
        return;
      }
      window.location.assign(res.approvalUrl);
    } catch (e) {
      setBillingActionMsg(e instanceof Error ? e.message : "Request failed");
    } finally {
      setPaypalSubscribeBusy(false);
    }
  }

  async function onStripeBillingPortal() {
    if (state.kind !== "ready") {
      return;
    }
    setStripePortalBusy(true);
    setStripePortalMsg(null);
    try {
      const returnUrl = typeof window !== "undefined" ? window.location.href : "";
      const res = await createStripeBillingPortalSession(returnUrl);
      if (!res.ok) {
        setStripePortalMsg(`Stripe portal request failed (HTTP ${res.status}).`);
        return;
      }
      window.location.assign(res.url);
    } catch (e) {
      setStripePortalMsg(e instanceof Error ? e.message : "Request failed");
    } finally {
      setStripePortalBusy(false);
    }
  }

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6" data-testid="tenant-app-shell">
      {state.kind === "loading" ? <p className="text-sm text-muted">Loading dashboard…</p> : null}

      {state.kind === "ready" ? (
        <div className="flex flex-col gap-6">
          {tenantsLoadError ? (
            <p className="text-xs text-muted">Could not load tenant list (HTTP {tenantsLoadError}).</p>
          ) : null}
          {tenants.length > 1 ? (
            <section
              className="rounded-md border border-border bg-surface p-6 shadow-sm"
              data-testid="tenant-switcher"
            >
              <h2 className="text-sm font-medium text-foreground">Your tenants</h2>
              <p className="mt-1 text-xs text-muted">Open another tenant in the same browser session (same relay cookies).</p>
              <ul className="mt-3 space-y-2" data-testid="tenant-switcher-list">
                {tenants.map((t) => (
                  <li key={t.id}>
                    <a
                      href={tenantWebAppUrlForHandle(t.handle)}
                      className="text-sm font-medium text-primary underline-offset-4 hover:underline"
                      data-testid={`tenant-link-${t.handle}`}
                    >
                      {t.name}
                    </a>
                    <span className="text-muted"> · </span>
                    <span className="font-mono text-xs text-muted">{t.handle}</span>
                    <span className="text-muted"> · </span>
                    <span className="text-xs text-muted">{t.roles.join(", ")}</span>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
          <section className="rounded-md border border-border bg-surface p-6 shadow-sm">
            <h2 className="text-sm font-medium text-foreground">Current user</h2>
            <p className="mt-1 text-xs text-muted">Change language from the account menu (header).</p>
            <dl className="mt-3 space-y-2 text-sm">
              <div>
                <dt className="text-muted">Email</dt>
                <dd className="font-mono text-foreground" data-testid="me-email">
                  {me.email}
                </dd>
              </div>
              <div>
                <dt className="text-muted">Locale</dt>
                <dd className="font-mono text-foreground" data-testid="me-locale-display">
                  {me.locale}
                </dd>
              </div>
              <div>
                <dt className="text-muted">Tenant handle</dt>
                <dd className="font-mono text-foreground" data-testid="me-tenant">
                  {me.tenantHandle ?? "—"}
                </dd>
              </div>
              <div>
                <dt className="text-muted">Privileges</dt>
                <dd>
                  {me.privileges.length === 0 ? (
                    <span className="text-muted">(none in this context)</span>
                  ) : (
                    <ul className="list-inside list-disc font-mono text-foreground" data-testid="me-privileges">
                      {me.privileges.map((p) => (
                        <li key={p}>{p}</li>
                      ))}
                    </ul>
                  )}
                </dd>
              </div>
              <div>
                <dt className="text-muted">Platform operator</dt>
                <dd className="font-mono text-foreground" data-testid="me-platform-operator">
                  {me.platformSuperadmin ? "yes" : "no"}
                </dd>
              </div>
              <div>
                <dt className="text-muted">Plan features (subscription)</dt>
                <dd>
                  {me.planFeatureCodes.length === 0 ? (
                    <span className="text-muted">(none)</span>
                  ) : (
                    <ul className="list-inside list-disc font-mono text-foreground" data-testid="me-plan-features">
                      {me.planFeatureCodes.map((c) => (
                        <li key={c}>{c}</li>
                      ))}
                    </ul>
                  )}
                </dd>
              </div>
            </dl>
          </section>

          <section
            className="rounded-md border border-border bg-surface p-6 shadow-sm"
            data-testid="billing-summary-section"
          >
            <h2 className="text-sm font-medium text-foreground">Billing integration</h2>
            {billingReturnFlash ? (
              <p className="mt-2 text-xs text-foreground" data-testid="billing-return-flash">
                {billingReturnFlash}
              </p>
            ) : null}
            <p className="mt-2 text-xs text-muted">
              GET /api/bff/v1/tenant/billing/summary (**USER_VIEW**). Catalog: GET …/commercial-plans (**TENANT_SETTINGS_EDIT**).
              Read-only; no provider customer ids in summary.
            </p>
            {state.billingSummary?.ok ? (
              <dl className="mt-3 space-y-2 text-sm">
                <div>
                  <dt className="text-muted">Stripe billing (platform)</dt>
                  <dd className="font-mono text-foreground" data-testid="billing-stripe-enabled">
                    {state.billingSummary.summary.stripeBillingEnabled ? "on" : "off"}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted">PayPal billing (platform)</dt>
                  <dd className="font-mono text-foreground" data-testid="billing-paypal-enabled">
                    {state.billingSummary.summary.paypalBillingEnabled ? "on" : "off"}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted">Stripe customer linked</dt>
                  <dd className="font-mono text-foreground" data-testid="billing-stripe-linked">
                    {state.billingSummary.summary.stripeCustomerLinked ? "yes" : "no"}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted">PayPal payer linked</dt>
                  <dd className="font-mono text-foreground" data-testid="billing-paypal-linked">
                    {state.billingSummary.summary.paypalCustomerLinked ? "yes" : "no"}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted">Commercial subscription</dt>
                  <dd className="mt-1 text-foreground" data-testid="billing-subscription">
                    {state.billingSummary.summary.subscription ? (
                      <span className="font-mono text-xs">
                        {state.billingSummary.summary.subscription.status} ·{" "}
                        {state.billingSummary.summary.subscription.commercialPlanCode ?? "(plan)"} ·{" "}
                        {state.billingSummary.summary.subscription.commercialPlanId}
                      </span>
                    ) : (
                      <span className="text-muted">(none)</span>
                    )}
                  </dd>
                </div>
              </dl>
            ) : state.billingSummary ? (
              state.billingSummary.status === 403 ? (
                <p className="mt-2 text-xs text-muted" data-testid="billing-summary-forbidden">
                  Billing summary requires USER_VIEW in this tenant.
                </p>
              ) : (
                <p className="mt-2 text-sm text-muted" data-testid="billing-summary-error">
                  Could not load billing summary (HTTP {state.billingSummary.status}).
                </p>
              )
            ) : null}

            {state.commercialPlans?.ok ? (
              state.commercialPlans.plans.length === 0 ? (
                <p className="mt-3 text-xs text-muted" data-testid="billing-plans-empty">
                  No active commercial plans are published for this deployment.
                </p>
              ) : (
                <div className="mt-4 space-y-3 border-t border-border/80 pt-4">
                  <div>
                    <label htmlFor="billing-plan-picker" className="text-xs font-medium text-muted">
                      Commercial plan (checkout)
                    </label>
                    <select
                      id="billing-plan-picker"
                      className="mt-1 block w-full rounded-md border border-border bg-background px-2 py-2 font-mono text-xs text-foreground"
                      data-testid="billing-plan-picker"
                      value={selectedCommercialPlanId ?? ""}
                      onChange={(e) => setSelectedCommercialPlanId(e.target.value || null)}
                    >
                      {state.commercialPlans.plans.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.code}
                          {p.stripeSubscriptionPriceId ? " · Stripe" : ""}
                          {p.paypalBillingPlanId ? " · PayPal" : ""}
                        </option>
                      ))}
                    </select>
                  </div>
                  {billingRedirectHint() ? (
                    <p className="text-xs text-muted" data-testid="billing-redirect-hint">
                      {billingRedirectHint()}
                    </p>
                  ) : null}
                  {me.privileges.includes("TENANT_SETTINGS_EDIT") && state.billingSummary?.ok ? (
                    <div className="flex flex-col gap-2">
                      {state.billingSummary.summary.stripeBillingEnabled &&
                      state.billingSummary.summary.stripeCustomerLinked &&
                      selectedCommercialPlanId &&
                      state.commercialPlans.plans.find((p) => p.id === selectedCommercialPlanId)?.stripeSubscriptionPriceId ? (
                        <button
                          type="button"
                          disabled={stripeCheckoutBusy}
                          onClick={() => void onStripeCheckout()}
                          className="inline-flex w-fit items-center justify-center rounded-md border border-border bg-background px-3 py-2 text-sm font-medium text-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
                          data-testid="stripe-checkout-btn"
                        >
                          {stripeCheckoutBusy ? "Redirecting…" : "Subscribe with Stripe Checkout"}
                        </button>
                      ) : null}
                      {state.billingSummary.summary.paypalBillingEnabled &&
                      selectedCommercialPlanId &&
                      state.commercialPlans.plans.find((p) => p.id === selectedCommercialPlanId)?.paypalBillingPlanId ? (
                        <button
                          type="button"
                          disabled={paypalSubscribeBusy}
                          onClick={() => void onPaypalSubscribe()}
                          className="inline-flex w-fit items-center justify-center rounded-md border border-border bg-background px-3 py-2 text-sm font-medium text-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
                          data-testid="paypal-subscribe-btn"
                        >
                          {paypalSubscribeBusy ? "Redirecting…" : "Subscribe with PayPal"}
                        </button>
                      ) : null}
                    </div>
                  ) : null}
                  {billingActionMsg ? (
                    <p className="text-xs text-muted" data-testid="billing-action-msg">
                      {billingActionMsg}
                    </p>
                  ) : null}
                </div>
              )
            ) : state.commercialPlans ? (
              state.commercialPlans.status === 403 ? (
                <p className="mt-3 text-xs text-muted" data-testid="billing-plans-forbidden">
                  Commercial plan catalog (Stripe/PayPal ids) is limited to users with TENANT_SETTINGS_EDIT.
                </p>
              ) : (
                <p className="mt-3 text-sm text-muted" data-testid="billing-plans-error">
                  Could not load commercial plans (HTTP {state.commercialPlans.status}).
                </p>
              )
            ) : null}

            {state.billingSummary?.ok &&
            me.privileges.includes("TENANT_SETTINGS_EDIT") &&
            state.billingSummary.summary.stripeBillingEnabled &&
            state.billingSummary.summary.stripeCustomerLinked ? (
              <div className="mt-4 flex flex-col gap-2 border-t border-border/80 pt-4">
                <p className="text-xs text-muted">
                  Opens Stripe Customer Portal (payment method, invoices). Requires platform Stripe secret and Billing Portal
                  configuration in Stripe.
                </p>
                <button
                  type="button"
                  disabled={stripePortalBusy}
                  onClick={() => void onStripeBillingPortal()}
                  className="inline-flex w-fit items-center justify-center rounded-md border border-border bg-background px-3 py-2 text-sm font-medium text-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
                  data-testid="stripe-billing-portal-btn"
                >
                  {stripePortalBusy ? "Opening…" : "Open Stripe billing portal"}
                </button>
                {stripePortalMsg ? (
                  <p className="text-xs text-muted" data-testid="stripe-portal-msg">
                    {stripePortalMsg}
                  </p>
                ) : null}
              </div>
            ) : null}
          </section>

          <section className="rounded-md border border-border bg-surface p-6 shadow-sm">
            <h2 className="text-sm font-medium text-foreground">Privacy & data lifecycle</h2>
            <p className="mt-2 text-xs text-muted">
              Subject export and erasure request (M1). See <code className="rounded bg-background px-1">docs/modules/data-lifecycle.md</code>.
            </p>
            <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:flex-wrap">
              <button
                type="button"
                disabled={privacyBusy}
                onClick={() => void onPrivacyExport()}
                className="inline-flex w-fit items-center justify-center rounded-md border border-border bg-background px-3 py-2 text-sm font-medium text-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
                data-testid="privacy-export-btn"
              >
                Download JSON export
              </button>
              <button
                type="button"
                disabled={privacyBusy}
                onClick={() => void onErasureRequest()}
                className="inline-flex w-fit items-center justify-center rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
                data-testid="privacy-erasure-btn"
              >
                Request account erasure
              </button>
            </div>
            {privacyMsg ? (
              <p className="mt-2 text-xs text-muted" data-testid="privacy-msg">
                {privacyMsg}
              </p>
            ) : null}
          </section>

          <section className="rounded-md border border-border bg-surface p-6 shadow-sm">
            <h2 className="text-sm font-medium text-foreground">Navigation (API)</h2>
            <p className="mt-2 text-sm text-muted">GET /api/bff/v1/me/navigation — filtered by privileges (sidebar mirrors this tree).</p>
            {navigationLoadError ? (
              <p className="mt-2 text-sm text-muted">Could not load menu (HTTP {navigationLoadError}).</p>
            ) : (
              <NavTree items={navigation} locale={me.locale} />
            )}
          </section>

          <section className="rounded-md border border-border bg-surface p-6 shadow-sm">
            <h2 className="text-sm font-medium text-foreground">Privilege check</h2>
            <DemoUserViewBody demo={state.demo} />
          </section>
        </div>
      ) : null}
    </div>
  );
}

function NavTree({
  items,
  locale,
  depth = 0,
}: {
  items: NavigationItem[];
  locale: string;
  depth?: number;
}) {
  if (items.length === 0) {
    return <p className="mt-2 text-sm text-muted">(empty)</p>;
  }
  return (
    <ul
      className={depth === 0 ? "mt-3 space-y-2" : "ml-4 mt-2 list-inside list-disc space-y-1"}
      data-testid={depth === 0 ? "nav-root" : undefined}
    >
      {items.map((n) => (
        <li key={n.id} className="text-sm">
          <span className="text-foreground">{navLabel(locale, n.labelKey)}</span>
          <span className="text-muted"> · </span>
          <span className="font-mono text-xs text-muted">{n.labelKey}</span>
          <span className="text-muted"> · </span>
          <span className="font-mono text-muted">{n.path}</span>
          {n.children?.length ? <NavTree items={n.children} locale={locale} depth={depth + 1} /> : null}
        </li>
      ))}
    </ul>
  );
}
