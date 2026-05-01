"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { AuthSplitLayout } from "@/components/shell/AuthSplitLayout";
import { authGlassCardClassName } from "@/components/shell/AuthShell";
import { resendVerificationJson, verifyEmailJson } from "@/lib/api";

function VerifyEmailInner() {
  const searchParams = useSearchParams();
  const [token, setToken] = useState("");
  const [resendEmail, setResendEmail] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const q = searchParams.get("token");
    if (!q || !q.trim()) {
      return;
    }
    setToken(q);
    let cancelled = false;
    void (async () => {
      setBusy(true);
      setMsg(null);
      try {
        await verifyEmailJson(q.trim());
        if (!cancelled) {
          setMsg("Email verified. You can sign in.");
        }
      } catch (err) {
        if (!cancelled) {
          setMsg(err instanceof Error ? err.message : "Verification failed");
        }
      } finally {
        if (!cancelled) {
          setBusy(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [searchParams]);

  async function onVerify(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      await verifyEmailJson(token.trim());
      setMsg("Email verified. You can sign in.");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Verification failed");
    } finally {
      setBusy(false);
    }
  }

  async function onResend(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      await resendVerificationJson(resendEmail.trim());
      setMsg("If an unverified account exists for that email, we sent a new link.");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Request failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthSplitLayout title="Verify email" subtitle="Open the link from your email, or paste the token below.">
      <form onSubmit={onVerify} className={`flex flex-col gap-4 p-6 ${authGlassCardClassName}`}>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-muted">Token from email</span>
          <input
            type="text"
            autoComplete="one-time-code"
            value={token}
            onChange={(e) => setToken(e.target.value)}
            className="rounded-md border border-border bg-background px-3 py-2 font-mono text-sm text-foreground"
            required
          />
        </label>
        <button
          type="submit"
          disabled={busy}
          className="rounded-md bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
        >
          {busy ? "Verifying…" : "Verify"}
        </button>
        {msg ? <p className="text-sm text-muted">{msg}</p> : null}
      </form>
      <form onSubmit={onResend} className={`mt-6 flex flex-col gap-4 p-6 ${authGlassCardClassName}`}>
        <p className="text-sm text-muted">Didn&apos;t get the email? Request a new link (same response whether we find an account).</p>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-muted">Email</span>
          <input
            type="email"
            autoComplete="email"
            value={resendEmail}
            onChange={(e) => setResendEmail(e.target.value)}
            className="rounded-md border border-border bg-background px-3 py-2 text-foreground"
            required
          />
        </label>
        <button
          type="submit"
          disabled={busy}
          className="rounded-md border border-border bg-background px-4 py-2.5 text-sm font-medium text-foreground hover:bg-muted/40 disabled:opacity-50"
        >
          Resend verification
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-muted">
        <Link href="/login" className="text-primary underline-offset-4 hover:underline">
          Sign in
        </Link>
      </p>
    </AuthSplitLayout>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={<div className="p-8 text-center text-muted">Loading…</div>}>
      <VerifyEmailInner />
    </Suspense>
  );
}
