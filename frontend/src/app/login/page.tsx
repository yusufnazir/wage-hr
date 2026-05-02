"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { AuthSplitLayout } from "@/components/shell/AuthSplitLayout";
import { authGlassCardClassName } from "@/components/shell/AuthShell";
import {
  EmailNotVerifiedError,
  fetchMeTenants,
  fetchPlatformPrivilegeCatalog,
  loginJson,
  redirectCheck,
  type TenantSummary,
} from "@/lib/api";
import { defaultTenantAppUrl, getAdminWebOrigin, isAdminWebOriginUrl, tenantWebAppUrlForHandle } from "@/lib/web-origins";

type SessionLandingContext = {
  isPlatformSuperadmin: boolean;
  tenants: TenantSummary[];
};

function tenantHandleFromAbsoluteUrl(absoluteUrl: string): string | null {
  try {
    const host = new URL(absoluteUrl).hostname.trim().toLowerCase();
    const labels = host.split(".");
    if (labels.length < 3) {
      return null;
    }
    const first = labels[0]?.trim();
    return first ? first : null;
  } catch {
    return null;
  }
}

async function resolvePostLoginNext(session: SessionLandingContext): Promise<string> {
  const memberships = session.tenants;

  const returnToParam = new URLSearchParams(window.location.search).get("returnTo");
  if (returnToParam && (await redirectCheck(returnToParam))) {
    if (!session.isPlatformSuperadmin && isAdminWebOriginUrl(returnToParam)) {
      return memberships.length > 0 ? tenantWebAppUrlForHandle(memberships[0]!.handle) : defaultTenantAppUrl();
    }

    // Avoid landing on a tenant host where this principal has no membership.
    if (!session.isPlatformSuperadmin) {
      const requestedHandle = tenantHandleFromAbsoluteUrl(returnToParam);
      if (requestedHandle) {
        const hasMembership = memberships.some((t) => t.handle.toLowerCase() === requestedHandle);
        if (!hasMembership) {
          if (memberships.length > 0) {
            return tenantWebAppUrlForHandle(memberships[0]!.handle);
          }
          return defaultTenantAppUrl();
        }
      }
    }

    return returnToParam;
  }
  if (session.isPlatformSuperadmin) {
    return `${getAdminWebOrigin()}/app`;
  }
  if (memberships.length > 0) {
    return tenantWebAppUrlForHandle(memberships[0]!.handle);
  }
  return defaultTenantAppUrl();
}

/** Prefer resolved tenant/admin (or returnTo); if not allow-listed, fall back to `/` (session router). */
async function goToPostAuthDestination(session: SessionLandingContext, navigation: "replace" | "assign"): Promise<void> {
  const next = await resolvePostLoginNext(session);
  const target = (await redirectCheck(next)) ? next : "/";
  if (navigation === "replace") {
    window.location.replace(target);
  } else {
    window.location.assign(target);
  }
}

async function detectSessionLandingContext(): Promise<SessionLandingContext | null> {
  const [catalogRes, tenantsRes] = await Promise.all([fetchPlatformPrivilegeCatalog(), fetchMeTenants()]);
  const isPlatformSuperadmin = catalogRes.ok;
  const tenants = tenantsRes.ok ? tenantsRes.tenants : [];
  if (!isPlatformSuperadmin && !tenantsRes.ok) {
    return null;
  }
  return { isPlatformSuperadmin, tenants };
}

export default function LoginPage() {
  const [email, setEmail] = useState("admin@demo.lvh.me");
  const [password, setPassword] = useState("ChangeMe!1");
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const session = await detectSessionLandingContext();
      if (cancelled || !session) {
        return;
      }
      await goToPostAuthDestination(session, "replace");
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      await loginJson(email, password);
      const session = await detectSessionLandingContext();
      if (!session) {
        setMsg("Signed in but could not resolve workspace.");
        return;
      }
      await goToPostAuthDestination(session, "assign");
    } catch (err) {
      if (err instanceof EmailNotVerifiedError) {
        setMsg("Confirm your email before signing in. Use Verify email / resend from the link below.");
      } else {
        setMsg(err instanceof Error ? err.message : "Login failed");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthSplitLayout title="Sign in" subtitle="Use your work email to access the tenant workspace.">
      <form onSubmit={onSubmit} className={`flex flex-col gap-4 p-6 ${authGlassCardClassName}`}>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-muted">Email</span>
          <input
            type="email"
            autoComplete="username"
            aria-label="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="rounded-md border border-border bg-background px-3 py-2 text-foreground"
            required
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-muted">Password</span>
          <input
            type="password"
            autoComplete="current-password"
            aria-label="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="rounded-md border border-border bg-background px-3 py-2 text-foreground"
            required
          />
        </label>
        <button
          type="submit"
          disabled={busy}
          className="rounded-md bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
        >
          {busy ? "Signing in…" : "Continue"}
        </button>
        {msg ? <p className="text-sm text-muted">{msg}</p> : null}
      </form>
      <p className="flex flex-wrap justify-center gap-x-4 gap-y-1 text-center text-sm text-muted">
        <Link href="/register" className="text-primary underline-offset-4 hover:underline">
          Create account
        </Link>
        <Link href="/verify-email" className="text-primary underline-offset-4 hover:underline">
          Verify email
        </Link>
        <Link href="/forgot-password" className="text-primary underline-offset-4 hover:underline">
          Forgot password
        </Link>
      </p>
    </AuthSplitLayout>
  );
}
