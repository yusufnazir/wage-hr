"use client";

import { useCallback, useEffect, useMemo, useState } from "react";

import { clearLensTenant, fetchPlatformTenants, postLensTenant, type MePayload, type PlatformTenantRow } from "@/lib/api";
import { navLabel } from "@/messages/nav";
import { isAdminWorkspaceHostname } from "@/lib/web-origins";

function hideLensChrome(pathname: string): boolean {
  const p = pathname.replace(/\/$/, "") || "/";
  return (
    p.startsWith("/app/platform-tenants") ||
    p.startsWith("/app/platform-settings") ||
    p.startsWith("/app/platform-mail-templates")
  );
}

export function SuperadminTenantLens(props: {
  locale: string;
  me: MePayload;
  pathname: string;
  onLensChanged: () => void;
}) {
  const { locale, me, pathname, onLensChanged } = props;
  const [rows, setRows] = useState<PlatformTenantRow[]>([]);
  const [loadError, setLoadError] = useState(false);
  const [busy, setBusy] = useState(false);

  const onPlatformOnly = useMemo(() => hideLensChrome(pathname), [pathname]);

  useEffect(() => {
    if (!me.platformSuperadmin || onPlatformOnly) {
      return;
    }
    let cancelled = false;
    (async () => {
      const r = await fetchPlatformTenants(0, 100);
      if (cancelled) {
        return;
      }
      if (r.ok) {
        setRows(r.items);
        setLoadError(false);
      } else {
        setRows([]);
        setLoadError(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [me.platformSuperadmin, onPlatformOnly]);

  const applyTenant = useCallback(
    async (tenantId: string) => {
      setBusy(true);
      try {
        await postLensTenant(tenantId);
        onLensChanged();
      } finally {
        setBusy(false);
      }
    },
    [onLensChanged],
  );

  const clear = useCallback(async () => {
    setBusy(true);
    try {
      await clearLensTenant();
      onLensChanged();
    } finally {
      setBusy(false);
    }
  }, [onLensChanged]);

  if (!me.platformSuperadmin) {
    return null;
  }
  if (typeof window !== "undefined" && !isAdminWorkspaceHostname(window.location.hostname)) {
    return null;
  }

  if (onPlatformOnly && !me.tenantHandle) {
    return null;
  }

  const selectedId = me.tenantId ?? "";
  const displayName =
    rows.find((r) => r.id === selectedId)?.name?.trim() ||
    rows.find((r) => r.id === selectedId)?.handle ||
    me.tenantHandle ||
    "";

  if (onPlatformOnly && me.tenantHandle) {
    return (
      <div className="flex min-w-0 items-center justify-between gap-2 border-b border-border/60 bg-muted/20 px-3 py-2 sm:px-4">
        <p className="min-w-0 text-xs text-muted sm:text-sm">
          <span className="font-medium text-foreground">
            {navLabel(locale, "superadminLens.banner").replace("{name}", displayName || me.tenantHandle)}
          </span>
        </p>
        <button
          type="button"
          className="shrink-0 rounded-md border border-border bg-background px-2 py-1 text-xs font-medium text-foreground hover:bg-muted/50 disabled:opacity-50"
          disabled={busy}
          onClick={() => void clear()}
        >
          {navLabel(locale, "superadminLens.action.clear")}
        </button>
      </div>
    );
  }

  return (
    <div className="flex min-w-0 flex-col gap-2 border-b border-border/60 bg-muted/20 px-3 py-2 sm:flex-row sm:items-center sm:justify-between sm:px-4">
      {me.tenantHandle ? (
        <p className="min-w-0 text-xs text-muted sm:text-sm">
          <span className="font-medium text-foreground">
            {navLabel(locale, "superadminLens.banner").replace("{name}", displayName || me.tenantHandle)}
          </span>
          <button
            type="button"
            className="ml-3 rounded-md border border-border bg-background px-2 py-1 text-xs font-medium text-foreground hover:bg-muted/50 disabled:opacity-50"
            disabled={busy}
            onClick={() => void clear()}
          >
            {navLabel(locale, "superadminLens.action.clear")}
          </button>
        </p>
      ) : (
        <p className="text-xs text-muted sm:text-sm">{navLabel(locale, "superadminLens.hint")}</p>
      )}
      <label className="flex min-w-0 flex-col gap-1 sm:max-w-xs">
        <span className="sr-only">{navLabel(locale, "superadminLens.label")}</span>
        <select
          className="h-9 max-w-full rounded-md border border-border bg-background px-2 text-sm text-foreground disabled:opacity-50"
          disabled={busy || loadError}
          value={selectedId}
          onChange={(e) => {
            const v = e.target.value;
            if (!v) {
              void clear();
              return;
            }
            void applyTenant(v);
          }}
          data-testid="superadmin-lens-select"
        >
          <option value="">{navLabel(locale, "superadminLens.placeholder")}</option>
          {rows.map((t) => (
            <option key={t.id} value={t.id}>
              {t.handle} — {t.name}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}
