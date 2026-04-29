"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { AuthSplitLayout } from "@/components/shell/AuthSplitLayout";
import { authGlassCardClassName } from "@/components/shell/AuthShell";
import { resetPasswordJson } from "@/lib/api";

function ResetPasswordForm() {
  const searchParams = useSearchParams();
  const tokenFromUrl = searchParams.get("token") ?? "";
  const [token, setToken] = useState(tokenFromUrl);
  const [password, setPassword] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      await resetPasswordJson(token, password);
      setMsg("Password updated. You can sign in with your new password.");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Reset failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className={`flex flex-col gap-4 p-6 ${authGlassCardClassName}`}>
      <label className="flex flex-col gap-1 text-sm">
        <span className="text-muted">Reset token</span>
        <input
          type="text"
          autoComplete="off"
          value={token}
          onChange={(e) => setToken(e.target.value)}
          className="rounded-md border border-border bg-background px-3 py-2 font-mono text-sm text-foreground"
          required
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        <span className="text-muted">New password (min 8 characters)</span>
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
        {busy ? "Saving…" : "Set password"}
      </button>
      {msg ? <p className="text-sm text-muted">{msg}</p> : null}
    </form>
  );
}

export default function ResetPasswordPage() {
  return (
    <AuthSplitLayout title="Reset password" subtitle="Paste the token from the reset email.">
      <Suspense fallback={<p className="text-sm text-muted">Loading…</p>}>
        <ResetPasswordForm />
      </Suspense>
      <p className="text-center text-sm text-muted">
        <Link href="/login" className="text-primary underline-offset-4 hover:underline">
          Back to sign in
        </Link>
      </p>
    </AuthSplitLayout>
  );
}
