"use client";

import type { NavigationItem } from "@/lib/api";

function iconClass(className?: string) {
  return `h-5 w-5 shrink-0 text-muted ${className ?? ""}`.trim();
}

/** Map `label_key` (stable) to a simple inline SVG. */
export function NavMenuIcon({ labelKey }: { labelKey: string }) {
  switch (labelKey) {
    case "nav.dashboard":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <path d="M4 10.5 12 3l8 7.5V21a1 1 0 0 1-1 1h-5v-7H10v7H5a1 1 0 0 1-1-1v-10.5Z" strokeLinejoin="round" />
        </svg>
      );
    case "nav.documents":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6Z" strokeLinejoin="round" />
          <path d="M14 2v6h6" strokeLinejoin="round" />
        </svg>
      );
    case "nav.users":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" strokeLinecap="round" />
          <circle cx="9" cy="7" r="4" />
          <path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" strokeLinecap="round" />
        </svg>
      );
    case "nav.tenant_settings":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <circle cx="12" cy="12" r="3" />
          <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" strokeLinecap="round" />
        </svg>
      );
    case "nav.platform_mail_templates":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <path
            d="M4 6h16v12H4V6Zm0 0 8 5 8-5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      );
    case "nav.platform_settings":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <path d="M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z" strokeLinecap="round" />
          <path
            d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.6V4a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9c.26.604.852.997 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1Z"
            strokeLinecap="round"
          />
        </svg>
      );
    case "nav.platform_payroll_bases":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <path d="M4 7h16M4 12h10M4 17h6" strokeLinecap="round" />
          <path d="M16 12h4M18 10v4" strokeLinecap="round" />
        </svg>
      );
    case "nav.platform_country_tax_rules":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <path d="M4 19h16" strokeLinecap="round" />
          <path d="M7 16V8l3 5 3-8 3 11 3-6v6" strokeLinejoin="round" />
        </svg>
      );
    case "nav.platform_countries":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <circle cx="12" cy="12" r="9" />
          <path d="M3.5 12h17" strokeLinecap="round" />
          <path d="M12 3.5c2.8 2.3 4.5 5.2 4.5 8.5s-1.7 6.2-4.5 8.5" strokeLinecap="round" />
          <path d="M12 3.5c-2.8 2.3-4.5 5.2-4.5 8.5s1.7 6.2 4.5 8.5" strokeLinecap="round" />
        </svg>
      );
    case "nav.tenant_currencies":
    case "nav.platform_currencies":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <circle cx="12" cy="12" r="9" />
          <path d="M14.5 9a3 3 0 1 0 0 6H10a3 3 0 1 0 0-6h4.5Z" strokeLinejoin="round" />
          <path d="M12 7v2M12 15v2" strokeLinecap="round" />
        </svg>
      );
    case "nav.companies":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <path d="M3 21h18" strokeLinecap="round" />
          <path d="M5 21V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16" strokeLinejoin="round" />
          <path d="M9 21v-4h6v4" strokeLinejoin="round" />
          <path d="M9 9h1m5 0h-1M9 13h1m5 0h-1" strokeLinecap="round" />
        </svg>
      );
    case "nav.departments":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <rect x="9" y="2" width="6" height="4" rx="1" strokeLinejoin="round" />
          <rect x="2" y="17" width="6" height="4" rx="1" strokeLinejoin="round" />
          <rect x="16" y="17" width="6" height="4" rx="1" strokeLinejoin="round" />
          <path d="M12 6v4M5 17v-4h14v4" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case "nav.jobs":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <rect x="2" y="7" width="20" height="14" rx="2" strokeLinejoin="round" />
          <path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2" strokeLinejoin="round" />
          <path d="M12 12v4M10 14h4" strokeLinecap="round" />
        </svg>
      );
    case "nav.employee_groups":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <circle cx="9" cy="7" r="3" />
          <circle cx="17" cy="8" r="2.5" />
          <path d="M2 21v-1a7 7 0 0 1 14 0v1" strokeLinecap="round" />
          <path d="M17 21v-1a5 5 0 0 0-2.5-4.33" strokeLinecap="round" />
        </svg>
      );
    case "nav.employees":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <circle cx="12" cy="8" r="4" />
          <path d="M4 21v-1a8 8 0 0 1 16 0v1" strokeLinecap="round" />
        </svg>
      );
    case "nav.work_times":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <circle cx="12" cy="12" r="9" />
          <path d="M12 7v5l3 3" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case "nav.bank_templates":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <path d="M3 10h18M3 14h18" strokeLinecap="round" />
          <rect x="2" y="6" width="20" height="12" rx="2" strokeLinejoin="round" />
          <path d="M7 17v.01" strokeLinecap="round" />
          <path d="M11 17h2" strokeLinecap="round" />
        </svg>
      );
    case "nav.payment_locations":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <path d="M12 2a7 7 0 0 1 7 7c0 5.25-7 13-7 13S5 14.25 5 9a7 7 0 0 1 7-7Z" strokeLinejoin="round" />
          <circle cx="12" cy="9" r="2.5" />
        </svg>
      );
    case "nav.pay_periods":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <rect x="3" y="4" width="18" height="17" rx="2" strokeLinejoin="round" />
          <path d="M16 2v4M8 2v4M3 10h18" strokeLinecap="round" />
          <path d="M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01" strokeLinecap="round" />
        </svg>
      );
    case "nav.roles":
    case "nav.role_admin":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <path d="M12 2L3 7v5c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V7l-9-5Z" strokeLinejoin="round" />
          <path d="M9 12l2 2 4-4" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case "nav.platform_tenants":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <path d="M3 21h18" strokeLinecap="round" />
          <path d="M5 21V8l7-5 7 5v13" strokeLinejoin="round" />
          <path d="M9 21v-6h6v6" strokeLinejoin="round" />
        </svg>
      );
    case "nav.platform_role_templates":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <path d="M12 2L3 7v5c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V7l-9-5Z" strokeLinejoin="round" />
          <path d="M9 12h6M12 9v6" strokeLinecap="round" />
        </svg>
      );
    case "nav.platform_wage_component_templates":
    case "nav.platform_ledger_templates":
    case "nav.platform_component_group_templates":
    case "nav.component_groups":
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <path d="M4 6h16v12H4V6Z" strokeLinejoin="round" />
          <path d="M4 10h16M9 6V4h6v2" strokeLinecap="round" />
        </svg>
      );
    default:
      return (
        <svg className={iconClass()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <circle cx="12" cy="12" r="9" />
          <path d="M12 8v8M8 12h8" strokeLinecap="round" />
        </svg>
      );
  }
}

export function navItemActive(pathname: string, item: NavigationItem): boolean {
  if (!item.path) {
    return item.children?.some((child) => navItemActive(pathname, child)) ?? false;
  }
  if (pathname === item.path) {
    return true;
  }
  if (item.path !== "/app" && pathname.startsWith(`${item.path}/`)) {
    return true;
  }
  return false;
}
