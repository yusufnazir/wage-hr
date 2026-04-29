"use client";

import { useState } from "react";
import Link from "next/link";
import { AuthSplitLayout } from "@/components/shell/AuthSplitLayout";
import { authGlassCardClassName } from "@/components/shell/AuthShell";
import { registerJson } from "@/lib/api";

export default function RegisterPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      await registerJson(email, password);
      setMsg("Account created. You can sign in now.");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Registration failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthSplitLayout title="Create account" subtitle="Start with email and a strong password.">
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
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-muted">Password (min 8 characters)</span>
          <input
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="rounded-md border border-border bg-background px-3 py-2 text-foreground"
            required
            minLength={8}
          />
        </label>
        <button
          type="submit"
          disabled={busy}
          className="rounded-md bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
        >
          {busy ? "Creating…" : "Register"}
        </button>
        {msg ? <p className="text-sm text-muted">{msg}</p> : null}
      </form>
      <p className="text-center text-sm text-muted">
        <Link href="/login" className="text-primary underline-offset-4 hover:underline">
          Sign in
        </Link>
      </p>
    </AuthSplitLayout>
  );
}
