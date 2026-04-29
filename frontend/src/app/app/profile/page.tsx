"use client";

import Link from "next/link";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";

export default function TenantProfilePage() {
  const { me } = useTenantAppSession();

  return (
    <div className="mx-auto flex max-w-lg flex-col gap-6">
      <div>
        <h1 className="text-lg font-semibold text-foreground">Profile</h1>
        <p className="mt-1 text-sm text-muted">Read-only summary from GET /api/v1/me. Use the header menu to change language or sign out.</p>
      </div>
      <dl className="rounded-lg border border-border bg-surface p-6 text-sm shadow-sm">
        <div className="grid gap-1 py-2">
          <dt className="text-muted">Email</dt>
          <dd className="font-mono text-foreground">{me.email}</dd>
        </div>
        <div className="grid gap-1 border-t border-border py-2">
          <dt className="text-muted">Locale</dt>
          <dd className="font-mono text-foreground">{me.locale}</dd>
        </div>
        <div className="grid gap-1 border-t border-border py-2">
          <dt className="text-muted">Tenant</dt>
          <dd className="font-mono text-foreground">{me.tenantHandle ?? "—"}</dd>
        </div>
      </dl>
      <p className="text-sm text-muted">
        <Link href="/forgot-password" className="text-primary underline-offset-4 hover:underline">
          Change password
        </Link>{" "}
        (email reset flow)
      </p>
      <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
        ← Back to dashboard
      </Link>
    </div>
  );
}
