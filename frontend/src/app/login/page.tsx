"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { AuthSplitLayout } from "@/components/shell/AuthSplitLayout";
import { authGlassCardClassName } from "@/components/shell/AuthShell";
import { fetchMe, loginJson, redirectCheck } from "@/lib/api";
import { defaultTenantAppUrl, getAdminWebOrigin, isAdminWebOriginUrl } from "@/lib/web-origins";
import type { MePayload } from "@/lib/api";

async function resolvePostLoginNext(me: MePayload): Promise<string> {
  const returnToParam = new URLSearchParams(window.location.search).get("returnTo");
  if (returnToParam && (await redirectCheck(returnToParam))) {
    if (!me.platformSuperadmin && isAdminWebOriginUrl(returnToParam)) {
      return defaultTenantAppUrl();
    }
    return returnToParam;
  }
  if (me.platformSuperadmin) {
    return `${getAdminWebOrigin()}/app`;
  }
  return defaultTenantAppUrl();
}

export default function LoginPage() {
  const [email, setEmail] = useState("admin@demo.lvh.me");
  const [password, setPassword] = useState("ChangeMe!1");
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const me = await fetchMe();
      if (cancelled || !me.ok) {
        return;
      }
      const next = await resolvePostLoginNext(me.me);
      if (await redirectCheck(next)) {
        window.location.replace(next);
      }
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
      const meRes = await fetchMe();
      if (!meRes.ok) {
        setMsg("Signed in but could not load profile.");
        return;
      }
      const next = await resolvePostLoginNext(meRes.me);
      if (await redirectCheck(next)) {
        window.location.assign(next);
        return;
      }
      setMsg(`Signed in (session cookie set). Open app: ${next}`);
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Login failed");
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
        <Link href="/forgot-password" className="text-primary underline-offset-4 hover:underline">
          Forgot password
        </Link>
        <Link href="/" className="text-primary underline-offset-4 hover:underline">
          Back
        </Link>
      </p>
    </AuthSplitLayout>
  );
}
