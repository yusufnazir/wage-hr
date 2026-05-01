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
  if (pathname === item.path) {
    return true;
  }
  if (item.path !== "/app" && pathname.startsWith(`${item.path}/`)) {
    return true;
  }
  return false;
}
