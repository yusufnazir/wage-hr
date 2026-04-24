"use client";

import { useEffect, useState } from "react";
import {
  fetchDemoUserView,
  fetchMe,
  fetchMeTenants,
  fetchNavigation,
  fetchPrivacyExport,
  patchMeLocale,
  postPrivacyErasureRequest,
  type MePayload,
  type NavigationItem,
  type TenantSummary,
} from "@/lib/api";
import { SetHtmlLang } from "@/components/i18n/SetHtmlLang";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import { navLabel } from "@/messages/nav";
import { authLoginUrl, tenantWebAppUrlForHandle } from "@/lib/web-origins";

type ViewState =
  | { kind: "loading" }
  | { kind: "unauthenticated" }
  | { kind: "tenant_not_found" }
  | { kind: "error"; status: number; detail?: string }
  | {
      kind: "ready";
      me: MePayload;
      demo?: { ok: true; message: string } | { ok: false; status: number };
      navigation?: NavigationItem[] | { ok: false; status: number };
      tenants?: TenantSummary[] | { ok: false; status: number };
    };

export default function TenantAppShellPage() {
  const [state, setState] = useState<ViewState>({ kind: "loading" });
  const [localeBusy, setLocaleBusy] = useState(false);
  const [privacyBusy, setPrivacyBusy] = useState(false);
  const [privacyMsg, setPrivacyMsg] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const meResult = await fetchMe();
        if (cancelled) return;

        if (!meResult.ok) {
          if (meResult.status === 401) {
            setState({ kind: "unauthenticated" });
            return;
          }
          if (meResult.status === 404) {
            setState({ kind: "tenant_not_found" });
            return;
          }
          setState({ kind: "error", status: meResult.status });
          return;
        }

        const [demo, nav, tenantList] = await Promise.all([
          fetchDemoUserView(),
          fetchNavigation(),
          fetchMeTenants(),
        ]);
        if (cancelled) return;

        const navigation = nav.ok ? nav.items : nav;
        const tenants = tenantList.ok ? tenantList.tenants : tenantList;
        setState({ kind: "ready", me: meResult.me, demo, navigation, tenants });
      } catch (e) {
        if (cancelled) return;
        const msg = e instanceof Error ? e.message : "Network error";
        setState({ kind: "error", status: 0, detail: msg });
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, []);

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

  async function onLocaleChange(next: string) {
    if (state.kind !== "ready" || next === state.me.locale) {
      return;
    }
    setLocaleBusy(true);
    try {
      await patchMeLocale(next);
      const meResult = await fetchMe();
      if (meResult.ok) {
        setState((s) => (s.kind === "ready" ? { ...s, me: meResult.me } : s));
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Locale update failed";
      window.alert(msg);
    } finally {
      setLocaleBusy(false);
    }
  }

  return (
    <div data-layout="app" className="min-h-screen bg-background text-foreground">
      <header className="sticky top-0 z-20 border-b border-border/80 bg-surface/95 shadow-sm backdrop-blur-md dark:bg-surface/90">
        <div className="mx-auto flex max-w-lg items-center justify-between gap-4 px-6 py-3">
          <h1 className="text-lg font-semibold tracking-tight text-foreground">Tenant app</h1>
          <ThemeToggle />
        </div>
      </header>
      <main className="mx-auto flex max-w-lg flex-col gap-6 px-6 py-8" data-testid="tenant-app-shell">
      {state.kind === "loading" ? (
        <p className="text-sm text-muted">Loading session…</p>
      ) : null}

      {state.kind === "unauthenticated" ? (
        <div className="flex flex-col gap-3 rounded-md border border-border bg-surface p-6 shadow-sm">
          <p className="text-sm text-foreground">You are not signed in, or the session expired.</p>
          <a
            href={authLoginUrl()}
            className="inline-flex w-fit items-center justify-center rounded-md bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground shadow-sm ring-offset-background hover:opacity-90 focus-visible:outline focus-visible:ring-2 focus-visible:ring-primary"
            data-testid="sign-in-link"
          >
            Sign in
          </a>
        </div>
      ) : null}

      {state.kind === "tenant_not_found" ? (
        <p className="text-sm text-muted">
          Unknown tenant for this host (404 from backend). Check the subdomain matches a tenant handle in the database.
        </p>
      ) : null}

      {state.kind === "error" ? (
        <div className="flex flex-col gap-3 rounded-md border border-border bg-surface p-6 shadow-sm">
          <p className="text-sm text-muted">
            {state.status === 0 ? (
              <>
                Could not load your session ({state.detail ?? "network"}). Is the Spring API running and{" "}
                <code className="rounded bg-background px-1">API_BASE_URL</code> set for the Next server?
              </>
            ) : (
              <>Request failed (HTTP {state.status}).</>
            )}
          </p>
          <a
            href={authLoginUrl()}
            className="inline-flex w-fit items-center justify-center rounded-md border border-border bg-background px-4 py-2.5 text-sm font-medium text-foreground shadow-sm hover:opacity-90 focus-visible:outline focus-visible:ring-2 focus-visible:ring-primary"
            data-testid="sign-in-link"
          >
            Sign in
          </a>
        </div>
      ) : null}

      {state.kind === "ready" ? (
        <div className="flex flex-col gap-6">
          <SetHtmlLang locale={state.me.locale} />
          {Array.isArray(state.tenants) && state.tenants.length > 1 ? (
            <section
              className="rounded-md border border-border bg-surface p-6 shadow-sm"
              data-testid="tenant-switcher"
            >
              <h2 className="text-sm font-medium text-foreground">Your tenants</h2>
              <p className="mt-1 text-xs text-muted">Open another tenant in the same browser session (same relay cookies).</p>
              <ul className="mt-3 space-y-2" data-testid="tenant-switcher-list">
                {state.tenants.map((t) => (
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
            <dl className="mt-3 space-y-2 text-sm">
              <div>
                <dt className="text-muted">Email</dt>
                <dd className="font-mono text-foreground" data-testid="me-email">
                  {state.me.email}
                </dd>
              </div>
              <div>
                <dt className="text-muted">Locale</dt>
                <dd className="mt-1">
                  <label className="sr-only" htmlFor="locale-select">
                    Interface language
                  </label>
                  <select
                    id="locale-select"
                    className="rounded-md border border-border bg-background px-2 py-1.5 font-mono text-sm text-foreground"
                    value={state.me.locale}
                    disabled={localeBusy}
                    onChange={(e) => void onLocaleChange(e.target.value)}
                    data-testid="locale-select"
                  >
                    <option value="en">en</option>
                    <option value="nl">nl</option>
                    <option value="nl-sr">nl-sr</option>
                  </select>
                </dd>
              </div>
              <div>
                <dt className="text-muted">Tenant handle</dt>
                <dd className="font-mono text-foreground" data-testid="me-tenant">
                  {state.me.tenantHandle ?? "—"}
                </dd>
              </div>
              <div>
                <dt className="text-muted">Privileges</dt>
                <dd>
                  {state.me.privileges.length === 0 ? (
                    <span className="text-muted">(none in this context)</span>
                  ) : (
                    <ul className="list-inside list-disc font-mono text-foreground" data-testid="me-privileges">
                      {state.me.privileges.map((p) => (
                        <li key={p}>{p}</li>
                      ))}
                    </ul>
                  )}
                </dd>
              </div>
              <div>
                <dt className="text-muted">Platform operator</dt>
                <dd className="font-mono text-foreground" data-testid="me-platform-operator">
                  {state.me.platformSuperadmin ? "yes" : "no"}
                </dd>
              </div>
            </dl>
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
            <p className="mt-2 text-sm text-muted">GET /api/bff/v1/me/navigation — filtered by privileges</p>
            {Array.isArray(state.navigation) ? (
              <NavTree items={state.navigation} locale={state.me.locale} />
            ) : state.navigation ? (
              <p className="mt-2 text-sm text-muted">Could not load menu (HTTP {state.navigation.status}).</p>
            ) : (
              <p className="mt-2 text-sm text-muted">No navigation payload.</p>
            )}
          </section>

          <section className="rounded-md border border-border bg-surface p-6 shadow-sm">
            <h2 className="text-sm font-medium text-foreground">Privilege check</h2>
            <p className="mt-2 text-sm text-muted">GET /api/bff/v1/demo/user-view (requires USER_VIEW)</p>
            {state.demo?.ok ? (
              <p className="mt-2 text-sm text-foreground" data-testid="demo-ok">
                {state.demo.message}
              </p>
            ) : state.demo ? (
              <p className="mt-2 text-sm text-muted">Denied or failed (HTTP {state.demo.status}).</p>
            ) : null}
          </section>
        </div>
      ) : null}
      </main>
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
