"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { postLogout } from "@/lib/api";
import { authLoginUrl } from "@/lib/web-origins";

function initialsFromEmail(email: string): string {
  const local = email.split("@")[0]?.trim() ?? "?";
  const parts = local.split(/[._-]+/).filter(Boolean);
  if (parts.length >= 2) {
    return (parts[0]![0]! + parts[1]![0]!).toUpperCase();
  }
  return local.slice(0, 2).toUpperCase() || "?";
}

const LOCALES = ["en", "nl", "nl-sr"] as const;

export function UserMenu() {
  const { me, patchLocale, localeBusy } = useTenantAppSession();
  const [open, setOpen] = useState(false);
  const [languageOpen, setLanguageOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    setLanguageOpen(false);
    function onDoc(e: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, [open]);

  async function onLogout() {
    setOpen(false);
    try {
      await postLogout();
    } catch {
      /* still navigate to clear client state */
    }
    window.location.href = authLoginUrl();
  }

  return (
    <div className="relative" ref={rootRef}>
      <button
        type="button"
        data-testid="user-menu-trigger"
        className="flex h-9 w-9 items-center justify-center rounded-full border border-border bg-surface text-xs font-semibold text-foreground shadow-sm ring-offset-background hover:bg-muted/40 focus-visible:outline focus-visible:ring-2 focus-visible:ring-primary"
        aria-expanded={open}
        aria-haspopup="menu"
        onClick={() => setOpen((v) => !v)}
      >
        <span className="sr-only">Account menu</span>
        {initialsFromEmail(me.email)}
      </button>
      {open ? (
        <div
          role="menu"
          className="absolute right-0 z-50 mt-2 w-56 rounded-xl border border-border/80 bg-surface py-1 shadow-md backdrop-blur-xl dark:border-border/60 dark:bg-surface/95"
        >
          <div className="border-b border-border px-3 py-2">
            <p className="truncate text-xs font-medium text-foreground">{me.email}</p>
            {me.tenantHandle ? (
              <p className="truncate text-xs text-muted" data-testid="user-menu-tenant">
                {me.tenantHandle}
              </p>
            ) : null}
          </div>
          <Link
            role="menuitem"
            href="/app/profile"
            className="block px-3 py-2 text-sm text-foreground hover:bg-muted/50"
            onClick={() => setOpen(false)}
          >
            Profile
          </Link>
          <Link
            role="menuitem"
            href="/forgot-password"
            className="block px-3 py-2 text-sm text-foreground hover:bg-muted/50"
            onClick={() => setOpen(false)}
          >
            Change password
          </Link>
          <div className="border-t border-border">
            <button
              type="button"
              role="menuitem"
              aria-expanded={languageOpen}
              data-testid="user-menu-locale-toggle"
              className="flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm text-foreground hover:bg-muted/50"
              onClick={() => setLanguageOpen((v) => !v)}
            >
              <span className="text-xs font-medium text-muted">Language</span>
              <span className="shrink-0 text-muted" aria-hidden>
                <svg className="h-4 w-4 transition-transform duration-150" style={{ transform: languageOpen ? "rotate(180deg)" : undefined }} viewBox="0 0 20 20" fill="currentColor">
                  <path
                    fillRule="evenodd"
                    d="M5.23 7.21a.75.75 0 011.06.02L10 11.17l3.71-3.94a.75.75 0 111.08 1.04l-4.24 4.5a.75.75 0 01-1.08 0L5.21 8.27a.75.75 0 01.02-1.06z"
                    clipRule="evenodd"
                  />
                </svg>
              </span>
            </button>
            {languageOpen ? (
              <div className="border-t border-border/60 bg-muted/15 px-2 py-1.5 dark:bg-muted/10" role="group" aria-label="Language options">
                {LOCALES.map((loc) => (
                  <button
                    key={loc}
                    type="button"
                    role="menuitem"
                    data-testid={`user-menu-locale-${loc}`}
                    disabled={localeBusy || me.locale === loc}
                    className="block w-full rounded-md px-2 py-1.5 text-left text-sm text-foreground hover:bg-muted/50 disabled:opacity-50"
                    onClick={() => {
                      void patchLocale(loc).then(() => setOpen(false));
                    }}
                  >
                    {loc}
                    {me.locale === loc ? " · current" : ""}
                  </button>
                ))}
              </div>
            ) : null}
          </div>
          <button
            type="button"
            role="menuitem"
            className="block w-full px-3 py-2 text-left text-sm text-foreground hover:bg-muted/50"
            onClick={() => void onLogout()}
          >
            Logout
          </button>
        </div>
      ) : null}
    </div>
  );
}
