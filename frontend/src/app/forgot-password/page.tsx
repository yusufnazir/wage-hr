"use client";

import { useState } from "react";
import Link from "next/link";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import { AuthShell, authGlassCardClassName } from "@/components/shell/AuthShell";
import { forgotPasswordJson } from "@/lib/api";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      await forgotPasswordJson(email);
      setMsg("If an account exists for this email, a reset link has been sent (check server logs in local dev).");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Request failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell>
      <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center gap-8 px-6 py-16">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-semibold text-foreground">Forgot password</h1>
          <ThemeToggle />
        </div>
        <form onSubmit={onSubmit} className={`flex flex-col gap-4 p-6 ${authGlassCardClassName}`}>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-muted">Email</span>
            <input
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="rounded-md border border-border bg-background px-3 py-2 text-foreground"
              required
            />
          </label>
          <button
            type="submit"
            disabled={busy}
            className="rounded-md bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
          >
            {busy ? "Sending…" : "Send reset link"}
          </button>
          {msg ? <p className="text-sm text-muted">{msg}</p> : null}
        </form>
        <p className="text-center text-sm text-muted">
          <Link href="/login" className="text-primary underline-offset-4 hover:underline">
            Back to sign in
          </Link>
        </p>
      </main>
    </AuthShell>
  );
}
