import type { TenantUserListProbeResult } from "@/lib/api";

type DemoUserViewBodyProps = {
  /** Result from {@link fetchTenantUserListProbe}; omit when not yet fetched or unavailable. */
  demo?: TenantUserListProbeResult;
  /**
   * When true, missing {@code demo} shows a loading line (standalone page).
   * When false (default), missing {@code demo} shows nothing after the hint (dashboard may skip the probe on error).
   */
  pending?: boolean;
};

/** Shared copy for GET /api/v1/tenant/users (USER_VIEW privilege probe). */
export function DemoUserViewBody({ demo, pending = false }: DemoUserViewBodyProps) {
  return (
    <>
      <p className="mt-2 text-sm text-muted">GET /api/bff/v1/tenant/users (requires USER_VIEW)</p>
      {demo === undefined ? (
        pending ? (
          <p className="mt-2 text-sm text-muted">Loading…</p>
        ) : null
      ) : demo.ok ? (
        <p className="mt-2 text-sm text-foreground" data-testid="demo-ok">
          Tenant user directory reachable — {demo.totalElements} member{demo.totalElements === 1 ? "" : "s"} (USER_VIEW).
        </p>
      ) : (
        <p className="mt-2 text-sm text-muted">Denied or failed (HTTP {demo.status}).</p>
      )}
    </>
  );
}
