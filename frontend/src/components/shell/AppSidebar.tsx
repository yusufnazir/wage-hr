"use client";

import Image from "next/image";
import Link from "next/link";

import type { NavigationItem } from "@/lib/api";
import { brandFaviconSrc, brandLogoWordmarkSmallSrc } from "@/lib/brand-assets";
import { navLabel } from "@/messages/nav";
import { NavMenuIcon, navItemActive } from "@/components/shell/nav-icons";

function NavBranch({
  items,
  pathname,
  locale,
  collapsed,
  depth,
  onPick,
}: {
  items: NavigationItem[];
  pathname: string;
  locale: string;
  collapsed: boolean;
  depth: number;
  onPick?: () => void;
}) {
  return (
    <ul className={depth === 0 ? "space-y-0.5" : "ml-2 mt-1 space-y-0.5 border-l border-border/60 pl-2"} data-testid={depth === 0 ? "app-sidebar-nav" : undefined}>
      {items.map((item) => {
        const active = navItemActive(pathname, item);
        const showChildren = !collapsed && item.children && item.children.length > 0;
        return (
          <li key={item.id}>
            <Link
              href={item.path}
              data-testid={`nav-link-${item.labelKey}`}
              onClick={onPick}
              className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all duration-150 ${
                active
                  ? "bg-primary/12 text-primary shadow-sm ring-1 ring-primary/20 dark:bg-primary/15 dark:ring-primary/25"
                  : "text-foreground/90 hover:bg-foreground/[0.04] hover:text-foreground"
              }`}
            >
              <NavMenuIcon labelKey={item.labelKey} />
              <span className={collapsed ? "sr-only" : "truncate"}>{navLabel(locale, item.labelKey)}</span>
            </Link>
            {showChildren ? (
              <NavBranch items={item.children!} pathname={pathname} locale={locale} collapsed={collapsed} depth={depth + 1} onPick={onPick} />
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}

export type AppSidebarProps = {
  navigation: NavigationItem[];
  locale: string;
  pathname: string;
  collapsed: boolean;
  onToggleCollapsed: () => void;
  mobileOpen: boolean;
  onCloseMobile: () => void;
  tenantLine: string;
  tenantSub?: string | null;
};

export function AppSidebar({
  navigation,
  locale,
  pathname,
  collapsed,
  onToggleCollapsed,
  mobileOpen,
  onCloseMobile,
  tenantLine,
  tenantSub,
}: AppSidebarProps) {
  return (
    <>
      {mobileOpen ? (
        <button
          type="button"
          className="fixed inset-0 z-40 bg-background/60 backdrop-blur-sm lg:hidden"
          aria-label="Close navigation"
          onClick={onCloseMobile}
        />
      ) : null}
      <aside
        data-testid="app-sidebar"
        className={`fixed inset-y-0 left-0 z-50 flex flex-col border-r border-border bg-surface shadow-lg transition-transform duration-200 lg:static lg:z-0 lg:translate-x-0 lg:shadow-none ${
          collapsed ? "w-[4.5rem]" : "w-64"
        } ${mobileOpen ? "translate-x-0" : "-translate-x-full lg:translate-x-0"}`}
      >
        <div className={`border-b border-border px-3 py-4 ${collapsed ? "px-2" : ""}`}>
          <Link href="/app" className="block rounded-lg outline-none ring-offset-background focus-visible:ring-2 focus-visible:ring-primary" onClick={onCloseMobile}>
            <div className={`flex flex-col gap-2 ${collapsed ? "items-center" : ""}`}>
              {collapsed ? (
                <Image
                  src={brandFaviconSrc}
                  alt="Wage Payroll"
                  width={40}
                  height={40}
                  className="h-10 w-10 shrink-0 rounded-xl object-contain shadow-sm ring-1 ring-border/60"
                />
              ) : (
                <Image
                  src={brandLogoWordmarkSmallSrc}
                  alt="Wage Payroll"
                  width={220}
                  height={48}
                  className="h-8 w-auto max-w-[11.5rem] object-contain object-left"
                  priority
                />
              )}
              {!collapsed ? (
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold text-foreground" data-testid="app-sidebar-brand">
                    {tenantLine}
                  </p>
                  {tenantSub ? <p className="truncate text-xs text-muted">{tenantSub}</p> : null}
                </div>
              ) : (
                <span className="sr-only">{tenantLine}</span>
              )}
            </div>
          </Link>
        </div>
        <nav className="flex-1 overflow-y-auto px-2 py-3">
          <NavBranch items={navigation} pathname={pathname} locale={locale} collapsed={collapsed} depth={0} onPick={onCloseMobile} />
        </nav>
        <div className="border-t border-border p-2">
          <button
            type="button"
            data-testid="sidebar-collapse-toggle"
            className="flex w-full items-center justify-center gap-2 rounded-lg px-2 py-2 text-xs font-medium text-muted hover:bg-muted/40"
            onClick={() => {
              if (mobileOpen) {
                onCloseMobile();
              } else {
                onToggleCollapsed();
              }
            }}
          >
            <span className="lg:hidden">Close menu</span>
            <span className="hidden lg:inline">{collapsed ? "Expand sidebar" : "Collapse sidebar"}</span>
          </button>
        </div>
      </aside>
    </>
  );
}
