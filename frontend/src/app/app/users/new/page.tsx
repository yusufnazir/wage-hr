"use client";

import Link from "next/link";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { navLabel } from "@/messages/nav";

export default function TenantUserNewPlaceholderPage() {
  const { me } = useTenantAppSession();
  return (
    <div className="mx-auto max-w-lg space-y-4 p-6">
      <h1 className="text-lg font-semibold text-foreground">Add user</h1>
      <p className="text-sm text-muted">
        Inviting or creating users from the tenant directory is not available in this version. This route is reserved
        for a future release.
      </p>
      <Link href="/app/users" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
        ← {navLabel(me.locale, "nav.users")}
      </Link>
    </div>
  );
}
