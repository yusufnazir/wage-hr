"use client";

import Image from "next/image";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { usePathname } from "next/navigation";

import { AppSidebar } from "@/components/shell/AppSidebar";
import { SuperadminTenantLens } from "@/components/shell/SuperadminTenantLens";
import { TenantAppSessionProvider, type TenantAppSessionValue } from "@/components/shell/TenantAppSessionContext";
import { UserMenu } from "@/components/shell/UserMenu";
import { SetHtmlLang } from "@/components/i18n/SetHtmlLang";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import {
  fetchMe,
  fetchMeTenants,
  fetchNavigation,
  fetchPublicSurface,
  patchMeLocale,
  type MePayload,
  type NavigationItem,
  type PublicSurfacePayload,
  type TenantSummary,
} from "@/lib/api";
import { brandFaviconSrc, brandLogoWordmarkSmallSrc } from "@/lib/brand-assets";
import { authLoginUrl, authLoginUrlWithReturnTo, getAdminWebOrigin, isAdminWorkspaceHostname } from "@/lib/web-origins";

const SIDEBAR_COLLAPSED_KEY = "wp_app_sidebar_collapsed";

type GatePhase = "loading" | "unauthenticated" | "tenant_not_found" | "error" | "ready";

function GateChrome({ children, title, productName }: { children: ReactNode; title: string; productName: string }) {
  return (
    <div data-layout="app" className="flex min-h-screen flex-col bg-background text-foreground">
      <header className="sticky top-0 z-10 flex items-center justify-between border-b border-border/80 bg-surface/95 px-4 py-3 shadow-sm backdrop-blur-md dark:bg-surface/90 sm:px-6">
        <Link href="/" className="flex items-center gap-2 text-sm font-semibold tracking-tight text-foreground">
          <Image
            src={brandLogoWordmarkSmallSrc}
            alt={productName}
            width={160}
            height={36}
            className="h-7 w-auto max-w-[9rem] object-contain object-left"
          />
        </Link>
        <ThemeToggle />
      </header>
      <main className="flex flex-1 flex-col items-center justify-center px-4 py-10 sm:px-6">
        <div className="w-full max-w-md space-y-4">
          <h1 className="text-lg font-semibold text-foreground" data-testid="auth-gate-heading">
            {title}
          </h1>
          {children}
        </div>
      </main>
    </div>
  );
}

export function TenantAppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const [phase, setPhase] = useState<GatePhase>("loading");
  const [publicSurface, setPublicSurface] = useState<PublicSurfacePayload | null>(null);
  const [me, setMe] = useState<MePayload | null>(null);
  const [navigation, setNavigation] = useState<NavigationItem[]>([]);
  const [navigationLoadError, setNavigationLoadError] = useState<number | null>(null);
  const [tenants, setTenants] = useState<TenantSummary[]>([]);
  const [tenantsLoadError, setTenantsLoadError] = useState<number | null>(null);
  const [errorDetail, setErrorDetail] = useState<string | null>(null);
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    try {
      if (localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === "1") {
        setCollapsed(true);
      }
    } catch {
      /* ignore */
    }
  }, []);

  const loadShell = useCallback(async () => {
    setPhase("loading");
    setErrorDetail(null);
    try {
      const [psRes, meResult] = await Promise.all([fetchPublicSurface(), fetchMe()]);
      if (psRes.ok) {
        setPublicSurface(psRes.surface);
      }
      if (!meResult.ok) {
        if (meResult.status === 401 || meResult.status === 403) {
          setMe(null);
          if (typeof window !== "undefined") {
            // Avoid redirect loops when the session is valid but access is still forbidden.
            const loopGuardKey = "wp_forbidden_redirected";
            const alreadyRedirected = (() => {
              try {
                return window.sessionStorage.getItem(loopGuardKey) === "1";
              } catch {
                return false;
              }
            })();
            if (alreadyRedirected) {
              setPhase("error");
              setErrorDetail(`HTTP ${meResult.status}`);
              return;
            }
            try {
              window.sessionStorage.setItem(loopGuardKey, "1");
            } catch {
              /* ignore */
            }
            const returnTo = `${window.location.origin}${window.location.pathname}${window.location.search}${window.location.hash}`;
            window.location.replace(authLoginUrlWithReturnTo(returnTo));
            return;
          }
          setPhase("unauthenticated");
          return;
        }
        if (meResult.status === 404) {
          setMe(null);
          setPhase("tenant_not_found");
          return;
        }
        setMe(null);
        setPhase("error");
        setErrorDetail(`HTTP ${meResult.status}`);
        return;
      }
      setMe(meResult.me);
      if (typeof window !== "undefined") {
        try {
          window.sessionStorage.removeItem("wp_forbidden_redirected");
        } catch {
          /* ignore */
        }
      }
      const [nav, tenantList] = await Promise.all([fetchNavigation(), fetchMeTenants()]);
      if (nav.ok) {
        setNavigation(nav.items);
        setNavigationLoadError(null);
      } else {
        setNavigation([]);
        setNavigationLoadError(nav.status);
      }
      if (tenantList.ok) {
        setTenants(tenantList.tenants);
        setTenantsLoadError(null);
      } else {
        setTenants([]);
        setTenantsLoadError(tenantList.status);
      }
      setPhase("ready");
    } catch (e) {
      setMe(null);
      setPhase("error");
      setErrorDetail(e instanceof Error ? e.message : "Network error");
    }
  }, []);

  useEffect(() => {
    void loadShell();
  }, [loadShell]);

  useEffect(() => {
    if (phase !== "unauthenticated" || typeof window === "undefined") {
      return;
    }
    const returnTo = `${window.location.origin}${pathname}${window.location.search}${window.location.hash}`;
    window.location.replace(authLoginUrlWithReturnTo(returnTo));
  }, [phase, pathname]);

  useEffect(() => {
    if (phase !== "ready" || !me?.platformSuperadmin) {
      return;
    }
    if (typeof window === "undefined") {
      return;
    }
    if (isAdminWorkspaceHostname(window.location.hostname)) {
      return;
    }
    const target = `${getAdminWebOrigin()}${pathname}${window.location.search}${window.location.hash}`;
    window.location.replace(target);
  }, [phase, me?.platformSuperadmin, pathname]);

  const refreshMe = useCallback(async () => {
    const r = await fetchMe();
    if (r.ok) {
      setMe(r.me);
    }
  }, []);

  const [localeBusy, setLocaleBusy] = useState(false);
  const patchLocale = useCallback(
    async (locale: string) => {
      if (!me || locale === me.locale) {
        return;
      }
      setLocaleBusy(true);
      try {
        await patchMeLocale(locale);
        await refreshMe();
      } finally {
        setLocaleBusy(false);
      }
    },
    [me, refreshMe],
  );

  const toggleCollapsed = useCallback(() => {
    setCollapsed((c) => {
      const next = !c;
      try {
        localStorage.setItem(SIDEBAR_COLLAPSED_KEY, next ? "1" : "0");
      } catch {
        /* ignore */
      }
      return next;
    });
  }, []);

  const tenantBranding = useMemo(() => {
    if (!me?.tenantHandle) {
      return { line: me?.applicationName ?? publicSurface?.applicationName ?? "Wage Payroll", sub: "Open a tenant subdomain" };
    }
    const t = tenants.find((x) => x.handle === me.tenantHandle);
    const name = t?.name?.trim();
    if (name && name !== me.tenantHandle) {
      return { line: name, sub: me.tenantHandle };
    }
    return { line: me.tenantHandle, sub: null as string | null };
  }, [me, tenants, publicSurface?.applicationName]);

  useEffect(() => {
    if (!me?.applicationName || !pathname) {
      return;
    }
    const parts = pathname.replace(/\/$/, "").split("/").filter(Boolean);
    const seg = parts.length ? parts[parts.length - 1]! : "app";
    document.title = `${seg} — ${me.applicationName}`;
  }, [pathname, me?.applicationName]);

  if (phase === "loading") {
    return (
      <div data-layout="app" className="flex min-h-screen items-center justify-center bg-background text-foreground">
        <p className="text-sm text-muted">Loading workspace…</p>
      </div>
    );
  }

  const gateProductName = publicSurface?.applicationName ?? "Wage Payroll";

  if (phase === "unauthenticated") {
    return (
      <div data-layout="app" className="flex min-h-screen items-center justify-center bg-background text-foreground">
        <p className="text-sm text-muted">Redirecting to sign in…</p>
      </div>
    );
  }

  if (phase === "tenant_not_found") {
    return (
      <GateChrome title="Unknown tenant" productName={gateProductName}>
        <p className="text-sm text-muted">
          Unknown tenant for this host (404 from backend). Check the subdomain matches a tenant handle in the database.
        </p>
      </GateChrome>
    );
  }

  if (phase === "error") {
    return (
      <GateChrome title="Could not load" productName={gateProductName}>
        <div className="rounded-lg border border-border bg-surface p-6 shadow-sm">
          <p className="text-sm text-muted">
            {errorDetail?.startsWith("HTTP") ? (
              <>Request failed ({errorDetail}).</>
            ) : (
              <>
                Could not load your session ({errorDetail ?? "network"}). Is the Spring API running and{" "}
                <code className="rounded bg-background px-1">API_BASE_URL</code> set for the Next server?
              </>
            )}
          </p>
          <a
            href={authLoginUrl()}
            className="mt-4 inline-flex w-fit items-center justify-center rounded-md border border-border bg-background px-4 py-2.5 text-sm font-medium text-foreground shadow-sm hover:opacity-90"
            data-testid="sign-in-link"
          >
            Sign in
          </a>
        </div>
      </GateChrome>
    );
  }

  if (!me) {
    return null;
  }

  const session: TenantAppSessionValue = {
    me,
    navigation,
    navigationLoadError,
    tenants,
    tenantsLoadError,
    refreshMe,
    patchLocale,
    localeBusy,
  };

  return (
    <TenantAppSessionProvider value={session}>
      <SetHtmlLang locale={me.locale} />
      <div data-layout="app" className="flex min-h-screen bg-background text-foreground">
        <AppSidebar
          navigation={navigation}
          locale={me.locale}
          pathname={pathname}
          collapsed={collapsed}
          onToggleCollapsed={toggleCollapsed}
          mobileOpen={mobileOpen}
          onCloseMobile={() => setMobileOpen(false)}
          tenantLine={tenantBranding.line}
          tenantSub={tenantBranding.sub}
        />
        <div className="flex min-w-0 flex-1 flex-col">
          <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-border/60 bg-surface/90 px-3 shadow-sm backdrop-blur-xl dark:bg-surface/95 sm:px-4">
            <button
              type="button"
              className="inline-flex h-10 w-10 items-center justify-center rounded-lg border border-border bg-background text-foreground hover:bg-muted/40 lg:hidden"
              aria-label="Open navigation menu"
              data-testid="app-mobile-nav-open"
              onClick={() => setMobileOpen(true)}
            >
              <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
                <path d="M4 6h16M4 12h16M4 18h16" strokeLinecap="round" />
              </svg>
            </button>
            <div className="flex min-w-0 flex-1 items-center gap-2">
              <Image
                src={brandFaviconSrc}
                alt=""
                width={32}
                height={32}
                className="hidden h-8 w-8 shrink-0 rounded-lg object-contain ring-1 ring-border/50 sm:block"
                aria-hidden
              />
              <div className="flex min-w-0 flex-1 flex-col">
                <span className="truncate text-sm font-semibold text-foreground" data-testid="app-header-title">
                  {me.applicationName}
                </span>
                <span className="truncate text-xs text-muted">{me.tenantHandle ?? "—"}</span>
              </div>
            </div>
            <div className="flex shrink-0 items-center gap-2">
              <ThemeToggle />
              <UserMenu />
            </div>
          </header>
          <SuperadminTenantLens locale={me.locale} me={me} pathname={pathname} onLensChanged={() => void loadShell()} />
          <main className="flex-1 overflow-auto px-4 py-6 sm:px-6 lg:px-8">{children}</main>
        </div>
      </div>
    </TenantAppSessionProvider>
  );
}
