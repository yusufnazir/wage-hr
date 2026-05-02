"use client";

import { useEffect } from "react";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import { AuthShell } from "@/components/shell/AuthShell";
import { fetchMeTenants, fetchPlatformPrivilegeCatalog, redirectCheck } from "@/lib/api";
import { authLoginUrlWithReturnTo, getAdminWebOrigin, tenantWebAppUrlForHandle } from "@/lib/web-origins";

/**
 * Root `/` is not a product surface: resolve session and send users to the app or auth login.
 * Treat **401**, **403** (e.g. signed-in viewer on `admin.*`), and other `/me` failures like “needs sign-in”
 * so we never leave a dead-end card here.
 */
export default function HomePage() {
  useEffect(() => {
    void (async () => {
      const returnTo = `${window.location.origin}${window.location.pathname}${window.location.search}${window.location.hash}`;
      const goLogin = () => window.location.replace(authLoginUrlWithReturnTo(returnTo));

      try {
        const [catalogRes, tenantsRes] = await Promise.all([fetchPlatformPrivilegeCatalog(), fetchMeTenants()]);
        let next: string | null = null;
        if (catalogRes.ok) {
          next = `${getAdminWebOrigin()}/app`;
        } else if (tenantsRes.ok && tenantsRes.tenants.length > 0) {
          next = tenantWebAppUrlForHandle(tenantsRes.tenants[0]!.handle);
        }
        if (!next) {
          goLogin();
          return;
        }
        if (await redirectCheck(next)) {
          window.location.replace(next);
          return;
        }
      } catch {
        goLogin();
        return;
      }
      goLogin();
    })();
  }, []);

  return (
    <AuthShell>
      <main className="mx-auto flex min-h-screen max-w-lg flex-col justify-center gap-6 px-6 py-16">
        <div className="flex items-center justify-between gap-4">
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">wage-payroll</h1>
          <ThemeToggle />
        </div>
        <p className="text-sm text-muted">Loading…</p>
      </main>
    </AuthShell>
  );
}
