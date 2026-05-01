"use client";

import { useState } from "react";
import Link from "next/link";
import { AuthSplitLayout } from "@/components/shell/AuthSplitLayout";
import { authGlassCardClassName } from "@/components/shell/AuthShell";
import { registerJson } from "@/lib/api";
import { tenantWebAppUrlForHandle } from "@/lib/web-origins";

export default function RegisterPage() {
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [tenantHandle, setTenantHandle] = useState("");
  const [agreeTerms, setAgreeTerms] = useState(false);
  const [agreePrivacy, setAgreePrivacy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      const res = await registerJson({
        firstName,
        lastName,
        email,
        password,
        tenantHandle: tenantHandle.trim(),
        agreeToTermsOfService: agreeTerms,
        agreeToPrivacyPolicy: agreePrivacy,
      });
      const hint = res.tenantHandle
        ? `Your workspace: ${tenantWebAppUrlForHandle(res.tenantHandle)}`
        : "";
      setMsg(`Check your email to verify your account before signing in. ${hint}`);
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Registration failed");
    } finally {
      setBusy(false);
    }
  }

  const canSubmit = agreeTerms && agreePrivacy && !busy;

  return (
    <AuthSplitLayout title="Create account" subtitle="Your name, email, password, and organization handle.">
      <form onSubmit={onSubmit} className={`flex flex-col gap-4 p-6 ${authGlassCardClassName}`}>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-muted">First name</span>
            <input
              type="text"
              autoComplete="given-name"
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              className="rounded-md border border-border bg-background px-3 py-2 text-foreground"
              required
              maxLength={100}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-muted">Last name</span>
            <input
              type="text"
              autoComplete="family-name"
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
              className="rounded-md border border-border bg-background px-3 py-2 text-foreground"
              required
              maxLength={100}
            />
          </label>
        </div>
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
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-muted">Organization handle (subdomain)</span>
          <input
            type="text"
            autoComplete="organization"
            value={tenantHandle}
            onChange={(e) => setTenantHandle(e.target.value.toLowerCase())}
            className="rounded-md border border-border bg-background px-3 py-2 text-foreground"
            required
            minLength={3}
            maxLength={64}
            pattern="[a-z0-9]([a-z0-9-]*[a-z0-9])?"
            title="Lowercase letters, digits, hyphens; 3–64 characters."
          />
        </label>
        <label className="flex cursor-pointer items-start gap-2 text-sm">
          <input
            type="checkbox"
            checked={agreeTerms}
            onChange={(e) => setAgreeTerms(e.target.checked)}
            className="mt-1 h-4 w-4 shrink-0 rounded border-border"
          />
          <span className="text-muted">
            I agree to the{" "}
            <Link href="/terms-of-service" className="text-primary underline-offset-4 hover:underline" target="_blank" rel="noreferrer">
              terms of service
            </Link>
            .
          </span>
        </label>
        <label className="flex cursor-pointer items-start gap-2 text-sm">
          <input
            type="checkbox"
            checked={agreePrivacy}
            onChange={(e) => setAgreePrivacy(e.target.checked)}
            className="mt-1 h-4 w-4 shrink-0 rounded border-border"
          />
          <span className="text-muted">
            I agree to the{" "}
            <Link href="/privacy-policy" className="text-primary underline-offset-4 hover:underline" target="_blank" rel="noreferrer">
              privacy policy
            </Link>
            .
          </span>
        </label>
        <button
          type="submit"
          disabled={!canSubmit}
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
        {" · "}
        <Link href="/verify-email" className="text-primary underline-offset-4 hover:underline">
          Verify email / resend
        </Link>
      </p>
    </AuthSplitLayout>
  );
}
