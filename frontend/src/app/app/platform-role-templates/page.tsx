"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchPlatformRoleTemplates,
  type PlatformRoleTemplate,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function PlatformRoleTemplatesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<PlatformRoleTemplate[]>([]);
  const [msg, setMsg] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoad("loading");
    setMsg(null);
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
        <div className="flex flex-wrap items-center gap-3">
          <Link
            href="/app/platform-role-templates/privileges"
            className="rounded-md border border-border bg-background px-3 py-1.5 text-sm font-medium text-foreground shadow-sm hover:bg-muted/30"
          >
            {t("roleTemplateMatrix.linkFromList")}
          </Link>
          <Link
            href="/app/platform-role-templates/new"
            className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90"
          >
            New template
          </Link>
          <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
            ← {t("nav.dashboard")}
          </Link>
        </div>
      </div>

      <p className="text-sm text-muted">
        These templates are used when creating a new tenant during registration. Changes affect future registrations only.
      </p>

      {msg ? (
        <p className={msg === "Saved." || msg === "Created." ? "text-sm text-foreground" : "text-sm text-destructive"}>
          {msg}
        </p>
      ) : null}

      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-foreground">Templates</h2>
        <div className="space-y-4">
          {items.map((tpl) => (
            <section key={tpl.id} className="rounded-lg border border-border bg-surface p-6 shadow-sm">
              <div className="flex flex-wrap items-baseline justify-between gap-3">
                <h3 className="text-sm font-semibold text-foreground">{tpl.displayName}</h3>
                <span className="rounded-md border border-border bg-background px-2 py-1 font-mono text-xs text-muted">
                  {tpl.code}
                </span>
              </div>
              <div className="mt-4">
                <div className="text-xs font-medium uppercase text-muted">Privileges</div>
                {tpl.privilegeCodes.length === 0 ? (
                  <p className="mt-2 text-sm text-muted">—</p>
                ) : (
                  <p className="mt-2 text-sm text-muted" title={tpl.privilegeCodes.join(", ")}>
                    {tpl.privilegeCodes.length} privileges
                  </p>
                )}
              </div>
              <div className="mt-4 flex flex-wrap gap-2">
                <Link
                  className="rounded-md border border-border bg-background px-3 py-1.5 text-sm font-medium text-foreground hover:bg-muted/30"
                  href={`/app/platform-role-templates/${tpl.id}`}
                >
                  Edit
                </Link>
              </div>
            </section>
          ))}
        </div>
      </section>
    </div>
  );
}

