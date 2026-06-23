"use client";

import Image from "next/image";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";

import { AppSidebar } from "@/components/shell/AppSidebar";
import { SuperadminTenantLens } from "@/components/shell/SuperadminTenantLens";
import { TenantAppSessionProvider, type TenantAppSessionValue } from "@/components/shell/TenantAppSessionContext";
import { UserMenu } from "@/components/shell/UserMenu";
import { SetHtmlLang } from "@/components/i18n/SetHtmlLang";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import {
  clearLensTenant,
  fetchMe,
  fetchMeTenants,
  fetchNavigation,
  fetchPublicSurface,
  fetchTenantCompanies,
  patchMeLocale,
  type MePayload,
  type NavigationItem,
  type PublicSurfacePayload,
  type TenantSummary,
} from "@/lib/api";
import { brandFaviconSrc } from "@/lib/brand-assets";
import {
  authLoginUrlWithReason,
  getAdminWebOrigin,
  isAdminWorkspaceHostname,
  meBootstrapFailureReason,
  sessionExpiredReturnTo,
  type AuthGateReason,
} from "@/lib/web-origins";

import { ToastContainer } from "@/components/ui/Toast";

const SIDEBAR_COLLAPSED_KEY = "wp_app_sidebar_collapsed";

type GatePhase = "loading" | "redirecting" | "ready";

function sortNavigation(items: NavigationItem[], parentLabelKey?: string): NavigationItem[] {
  const workspaceOrder: Record<string, number> = {
    "nav.dashboard": 10,
    // Org structure
    "nav.companies": 20,
    "nav.departments": 30,
    "nav.jobs": 40,
    "nav.employee_groups": 50,
    "nav.employees": 60,
    // Time / payroll
    "nav.work_times": 70,
    "nav.wage_components": 75,
    "nav.employee_payroll_inputs": 76,
    "nav.pay_periods": 80,
    // Payments / finance
    "nav.tenant_currencies": 90,
    "nav.bank_templates": 100,
    "nav.payment_locations": 110,
    // Admin-ish
    "nav.documents": 120,
    "nav.tenant_settings": 130,
  };

  const securityOrder: Record<string, number> = {
    "nav.users": 10,
    "nav.roles": 20,
    "nav.role_admin": 20,
  };

  const adminOrder: Record<string, number> = {
    "nav.platform_tenants": 10,
    "nav.platform_settings": 20,
    "nav.platform_countries": 30,
    "nav.platform_country_tax_rules": 31,
    "nav.platform_currencies": 40,
    "nav.platform_wage_component_templates": 41,
    "nav.platform_ledger_templates": 42,
    "nav.component_groups": 48,
    "nav.platform_component_group_templates": 43,
    "nav.platform_bank_templates": 50,
    "nav.platform_role_templates": 60,
    "nav.platform_mail_templates": 70,
  };

  const orderMap =
    parentLabelKey === "nav.group.workspace"
      ? workspaceOrder
      : parentLabelKey === "nav.group.security"
        ? securityOrder
        : parentLabelKey === "nav.group.administration"
          ? adminOrder
          : null;

  const normalized = items.map((i) => ({
    ...i,
    children: i.children?.length ? sortNavigation(i.children, i.labelKey) : [],
  }));

  return normalized.sort((a, b) => {
    const ao = orderMap?.[a.labelKey];
    const bo = orderMap?.[b.labelKey];
    if (ao != null || bo != null) {
      return (ao ?? 9_999) - (bo ?? 9_999);
    }
    if (a.sortOrder !== b.sortOrder) {
      return a.sortOrder - b.sortOrder;
    }
    return a.labelKey.localeCompare(b.labelKey);
  });
}

export function TenantAppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [phase, setPhase] = useState<GatePhase>("loading");
  const [publicSurface, setPublicSurface] = useState<PublicSurfacePayload | null>(null);
  const [me, setMe] = useState<MePayload | null>(null);
  const [navigation, setNavigation] = useState<NavigationItem[]>([]);
  const [navigationLoadError, setNavigationLoadError] = useState<number | null>(null);
  const [tenants, setTenants] = useState<TenantSummary[]>([]);
  const [tenantsLoadError, setTenantsLoadError] = useState<number | null>(null);
  const [hasCompany, setHasCompany] = useState<boolean | null>(null);
  const [hasCompanyLoadError, setHasCompanyLoadError] = useState<number | null>(null);
  const [primaryCompanyId, setPrimaryCompanyId] = useState<string | null>(null);
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  const markCompanyCreated = useCallback((companyId?: string) => {
    setHasCompany(true);
    setHasCompanyLoadError(null);
    if (companyId?.trim()) {
      setPrimaryCompanyId(companyId.trim());
    }
  }, []);

  useEffect(() => {
    try {
      if (localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === "1") {
        setCollapsed(true);
      }
    } catch {
      /* ignore */
    }
  }, []);

  const redirectToLogin = useCallback((reason: AuthGateReason) => {
    setMe(null);
    setPhase("redirecting");
    if (typeof window === "undefined") {
      return;
    }
    const returnTo = sessionExpiredReturnTo(window.location);
    window.location.replace(authLoginUrlWithReason(reason, returnTo));
  }, []);

  const loadShell = useCallback(async () => {
    setPhase("loading");
    try {
      const [psRes, initialMeResult] = await Promise.all([fetchPublicSurface(), fetchMe()]);
      let meResult = initialMeResult;
      if (!meResult.ok && meResult.status === 404) {
        // Recover from a stale superadmin lens cookie that points to a removed tenant id.
        try {
          await clearLensTenant();
          meResult = await fetchMe();
        } catch {
          // Keep original 404 behavior if recovery fails.
        }
      }
      if (psRes.ok) {
        setPublicSurface(psRes.surface);
      }
      if (!meResult.ok) {
        redirectToLogin(meBootstrapFailureReason(meResult.status));
        return;
      }
      setMe(meResult.me);
      setHasCompany(null);
      setHasCompanyLoadError(null);
      setPrimaryCompanyId(null);
      const [nav, tenantList, companiesProbe] = await Promise.all([
        fetchNavigation(),
        fetchMeTenants(),
        fetchTenantCompanies({ page: 0, size: 1 }),
      ]);
      if (nav.ok) {
        setNavigation(sortNavigation(nav.items));
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
      if (companiesProbe.ok) {
        setHasCompany(companiesProbe.totalElements > 0);
        setHasCompanyLoadError(null);
        setPrimaryCompanyId(companiesProbe.items[0]?.id ?? null);
      } else {
        setHasCompanyLoadError(companiesProbe.status);
        // Some deployments restrict the companies list behind COMPANY_VIEW.
        // For onboarding, treat 403 as "no company" so the user sees the create-company CTA.
        if (companiesProbe.status === 403) {
          setHasCompany(false);
          setPrimaryCompanyId(null);
        } else {
          setHasCompany(null);
          setPrimaryCompanyId(null);
        }
      }
      setPhase("ready");
    } catch {
      redirectToLogin("load_failed");
    }
  }, [redirectToLogin]);

  useEffect(() => {
    void loadShell();
  }, [loadShell]);

  useEffect(() => {
    if (phase !== "ready") return;
    if (hasCompany !== false) return;
    if (typeof window === "undefined") return;

    const current = `${window.location.pathname}${window.location.search}${window.location.hash}`;
    const companyDependentPrefixes = [
      "/app/departments",
      "/app/jobs",
      "/app/employee-groups",
      "/app/employees",
      "/app/work-times",
      "/app/wage-components",
      "/app/pay-periods",
      "/app/run-payroll",
      "/app/bank-templates",
      "/app/payment-locations",
    ];

    const isCompanyDependentPath = (path: string) => companyDependentPrefixes.some((p) => path === p || path.startsWith(`${p}/`));

    const allowed =
      pathname === "/app" ||
      pathname === "/app/profile" ||
      pathname === "/app/companies" ||
      pathname === "/app/companies/new" ||
      pathname.startsWith("/app/platform-") ||
      !isCompanyDependentPath(pathname);

    const companyDependent = isCompanyDependentPath(pathname);

    if (!allowed && companyDependent) {
      router.replace(`/app?returnTo=${encodeURIComponent(current)}`);
    }
  }, [hasCompany, pathname, phase, router]);

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

  const filteredNavigation = (() => {
    if (hasCompany !== false) return navigation;

    // When the tenant has no company yet, we still want to show tenant-level tooling
    // (security, documents, tenant settings, platform admin for superadmins, etc.).
    // Only company-dependent sections should be hidden until a company exists.
    const companyDependentPrefixes = [
      "/app/departments",
      "/app/jobs",
      "/app/employee-groups",
      "/app/employees",
      "/app/work-times",
      "/app/wage-components",
      "/app/pay-periods",
      "/app/run-payroll",
      "/app/bank-templates",
      "/app/payment-locations",
    ];

    const isCompanyDependentPath = (path: string) => companyDependentPrefixes.some((p) => path === p || path.startsWith(`${p}/`));

    const filter = (items: NavigationItem[]): NavigationItem[] => {
      const out: NavigationItem[] = [];
      for (const item of items) {
        if (!item.path) {
          const children = filter(item.children ?? []);
          if (children.length) out.push({ ...item, children });
          continue;
        }
        // Always keep the company onboarding entry points + platform admin screens.
        if (item.path === "/app" || item.path === "/app/companies" || item.path === "/app/companies/new" || item.path.startsWith("/app/platform-")) {
          out.push(item);
          continue;
        }
        if (!isCompanyDependentPath(item.path)) {
          out.push(item);
        }
      }
      return out;
    };

    return filter(navigation);
  })();

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

  if (phase === "redirecting") {
    return (
      <div data-layout="app" className="flex min-h-screen items-center justify-center bg-background text-foreground">
        <p className="text-sm text-muted">Redirecting to sign in…</p>
      </div>
    );
  }

  if (!me) {
    return null;
  }

  const session: TenantAppSessionValue = {
    me,
    hasCompany,
    hasCompanyLoadError,
    primaryCompanyId,
    markCompanyCreated,
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
          navigation={filteredNavigation}
          locale={me.locale}
          pathname={pathname}
          collapsed={collapsed}
          onToggleCollapsed={toggleCollapsed}
          mobileOpen={mobileOpen}
          onCloseMobile={() => setMobileOpen(false)}
          tenantLine={tenantBranding.line}
          tenantSub={tenantBranding.sub}
        />
        <div className="flex min-w-0 flex-1 flex-col lg:ml-64" style={collapsed ? { marginLeft: '4.5rem' } : {}}>
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
          <main className="flex-1 overflow-auto px-4 py-6 sm:px-6 lg:px-8">
            {hasCompany === false ? (
              <div className="mb-4 rounded-lg border border-border bg-surface px-4 py-3 shadow-sm">
                <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-foreground">No company yet</p>
                    <p className="text-xs text-muted">Create a company to unlock payroll setup, pay periods, banks, and payment locations.</p>
                  </div>
                  {me.privileges.includes("COMPANY_MANAGE") ? (
                    <Link
                      href={`/app/companies/new?returnTo=${encodeURIComponent(
                        typeof window !== "undefined"
                          ? `${window.location.pathname}${window.location.search}${window.location.hash}`
                          : "/app",
                      )}`}
                      className="inline-flex w-fit items-center justify-center rounded-md bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90"
                      data-testid="no-company-banner-cta"
                    >
                      Create company
                    </Link>
                  ) : (
                    <span className="text-xs text-muted" data-testid="no-company-banner-no-access">
                      Ask an admin for <span className="font-mono">COMPANY_MANAGE</span> to create a company.
                    </span>
                  )}
                </div>
              </div>
            ) : null}
            {children}
          </main>
        </div>
      </div>
      <ToastContainer />
    </TenantAppSessionProvider>
  );
}
