"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { fetchPlatformRoleTemplates, type PlatformRoleTemplate } from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function PlatformRoleTemplatesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<PlatformRoleTemplate[]>([]);

  const reload = useCallback(async () => {
    setLoad("loading");
    const r = await fetchPlatformRoleTemplates();
    if (!r.ok) {
      setLoad(r.status === 403 ? "forbidden" : "error");
      return;
    }
    setItems(r.items);
    setLoad("ready");
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("nav.platform_role_templates")}</h1>
        <p className="text-sm text-muted">Only a platform operator (platform superadmin) can view role templates.</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("nav.platform_role_templates")}</h1>
        <p className="text-sm text-muted">Access denied (403). Your session may not be a platform operator.</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "error") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("nav.platform_role_templates")}</h1>
        <p className="text-sm text-muted">Could not load role templates.</p>
        <button
          type="button"
          className="text-sm font-medium text-primary underline-offset-4 hover:underline"
          onClick={() => void reload()}
        >
          Retry
        </button>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-4xl">
        <p className="text-sm text-muted">Loading role templates…</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6" data-testid="platform-role-templates-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("nav.platform_role_templates")}</h1>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>

      <p className="text-sm text-muted">
        These templates are used when creating a new tenant during registration. In v1 they are view-only.
      </p>

      <div className="space-y-4">
        {items.map((tpl) => (
          <section key={tpl.id} className="rounded-lg border border-border bg-surface p-6 shadow-sm">
            <div className="flex flex-wrap items-baseline justify-between gap-3">
              <h2 className="text-sm font-semibold text-foreground">{tpl.displayName}</h2>
              <span className="rounded-md border border-border bg-background px-2 py-1 font-mono text-xs text-muted">
                {tpl.code}
              </span>
            </div>
            <div className="mt-4">
              <div className="text-xs font-medium uppercase text-muted">Privileges</div>
              {tpl.privilegeCodes.length === 0 ? (
                <p className="mt-2 text-sm text-muted">—</p>
              ) : (
                <ul className="mt-2 flex flex-wrap gap-2">
                  {tpl.privilegeCodes.map((c) => (
                    <li
                      key={c}
                      className="rounded-md border border-border bg-background px-2 py-1 font-mono text-xs text-foreground"
                      title={c}
                    >
                      {c}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}

