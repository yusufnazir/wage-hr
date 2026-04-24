"use client";

import { useState } from "react";
import Link from "next/link";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import { AuthShell, authGlassCardClassName } from "@/components/shell/AuthShell";
import { loginJson, redirectCheck } from "@/lib/api";
import { defaultTenantAppUrl } from "@/lib/web-origins";

export default function LoginPage() {
  const [email, setEmail] = useState("admin@demo.lvh.me");
  const [password, setPassword] = useState("ChangeMe!1");
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      await loginJson(email, password);
      const next = defaultTenantAppUrl();
      if (await redirectCheck(next)) {
        window.location.assign(next);
        return;
      }
      setMsg(`Signed in (session cookie set). Open demo tenant app: ${next}`);
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Login failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell>
      <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center gap-8 px-6 py-16">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-semibold text-foreground">Sign in</h1>
          <ThemeToggle />
        </div>
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
      </main>
    </AuthShell>
  );
}
