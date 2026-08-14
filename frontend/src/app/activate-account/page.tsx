"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { AuthSplitLayout } from "@/components/shell/AuthSplitLayout";
import { authGlassCardClassName } from "@/components/shell/AuthShell";
import { activateEmployeeAccountJson } from "@/lib/api";

function ActivateAccountForm() {
  const searchParams = useSearchParams();
  const tokenFromUrl = searchParams.get("token") ?? "";
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (password !== confirm) {
      setMsg("Passwords do not match.");
      return;
    }
    setBusy(true);
    setMsg(null);
    try {
      await activateEmployeeAccountJson(tokenFromUrl, password);
      setMsg("Account activated. You can sign in with your new password.");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Activation failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className={`flex flex-col gap-4 p-6 ${authGlassCardClassName}`}>
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
      <label className="flex flex-col gap-1 text-sm">
        <span className="text-muted">Confirm password</span>
        <input
          type="password"
          autoComplete="new-password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          className="rounded-md border border-border bg-background px-3 py-2 text-foreground"
          required
          minLength={8}
        />
      </label>
      <button
        type="submit"
        disabled={busy || !tokenFromUrl}
        className="rounded-md bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
      >
        {busy ? "Saving…" : "Activate account"}
      </button>
      {!tokenFromUrl ? (
        <p className="text-sm text-destructive">Missing activation token. Open the link from your email.</p>
      ) : null}
      {msg ? <p className="text-sm text-muted">{msg}</p> : null}
    </form>
  );
}

export default function ActivateAccountPage() {
  return (
    <AuthSplitLayout title="Activate your account" subtitle="Set a password to finish creating your login.">
      <Suspense fallback={<p className="text-sm text-muted">Loading…</p>}>
        <ActivateAccountForm />
      </Suspense>
      <p className="text-center text-sm text-muted">
        <Link href="/login" className="text-primary underline-offset-4 hover:underline">
          Back to sign in
        </Link>
      </p>
    </AuthSplitLayout>
  );
}
