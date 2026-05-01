"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

/**
 * Backward compatibility: old route redirects to the role template privileges matrix.
 */
export default function PlatformTenantPrivilegesRedirectPage() {
  const router = useRouter();
  useEffect(() => {
    router.replace("/app/platform-role-templates/privileges");
  }, [router]);
  return (
    <div className="mx-auto max-w-md py-8">
      <p className="text-sm text-muted">Redirecting…</p>
    </div>
  );
}
