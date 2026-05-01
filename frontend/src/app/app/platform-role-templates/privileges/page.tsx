"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchPlatformPrivilegeCatalog,
  fetchPlatformRoleTemplates,
  type PlatformPrivilegeCatalogEntry,
  type PlatformRoleTemplate,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

function catalogByCode(entries: PlatformPrivilegeCatalogEntry[]): Map<string, PlatformPrivilegeCatalogEntry> {
  return new Map(entries.map((e) => [e.code, e]));
}

export default function PlatformRoleTemplatePrivilegesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [templates, setTemplates] = useState<PlatformRoleTemplate[]>([]);
  const [catalog, setCatalog] = useState<PlatformPrivilegeCatalogEntry[]>([]);

  const catMap = useMemo(() => catalogByCode(catalog), [catalog]);

  const reload = useCallback(async () => {
    setLoad("loading");
    const [tplRes, catRes] = await Promise.all([fetchPlatformRoleTemplates(), fetchPlatformPrivilegeCatalog()]);
    if (!tplRes.ok) {
      setLoad(tplRes.status === 403 ? "forbidden" : "error");
      return;
    }
    if (!catRes.ok) {
      setLoad(catRes.status === 403 ? "forbidden" : "error");
      return;
    }
    setTemplates([...tplRes.items].sort((a, b) => a.code.localeCompare(b.code)));
    setCatalog(catRes.entries);
    setLoad("ready");
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("roleTemplateMatrix.title")}</h1>
        <p className="text-sm text-muted">{t("roleTemplateMatrix.error.notOperator")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("roleTemplateMatrix.title")}</h1>
        <p className="text-sm text-muted">{t("roleTemplateMatrix.error.forbidden")}</p>
        <Link href="/app/platform-role-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.platform_role_templates")}
        </Link>
      </div>
    );
  }

  if (load === "error") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("roleTemplateMatrix.title")}</h1>
        <p className="text-sm text-muted">{t("roleTemplateMatrix.error.load")}</p>
        <button
          type="button"
          className="text-sm font-medium text-primary underline-offset-4 hover:underline"
          onClick={() => void reload()}
        >
          {t("roleTemplateMatrix.action.retry")}
        </button>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-5xl">
        <p className="text-sm text-muted">{t("roleTemplateMatrix.state.loading")}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6" data-testid="platform-role-template-privileges-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <div className="space-y-1">
          <h1 className="text-lg font-semibold text-foreground">{t("roleTemplateMatrix.title")}</h1>
          <p className="text-sm text-muted">{t("roleTemplateMatrix.helper.intro")}</p>
        </div>
        <Link
          href="/app/platform-role-templates"
          className="text-sm font-medium text-primary underline-offset-4 hover:underline"
        >
          ← {t("nav.platform_role_templates")}
        </Link>
      </div>

      <p className="rounded-lg border border-border bg-muted/20 p-3 text-xs text-muted">{t("roleTemplateMatrix.helper.semantics")}</p>

      {templates.map((tpl) => {
        const codes = [...tpl.privilegeCodes].sort((a, b) => a.localeCompare(b));
        return (
          <section key={tpl.id} className="rounded-lg border border-border bg-surface shadow-sm">
            <div className="flex flex-wrap items-baseline justify-between gap-3 border-b border-border px-4 py-3">
              <div>
                <h2 className="text-sm font-semibold text-foreground">{tpl.displayName}</h2>
                <p className="mt-0.5 font-mono text-xs text-muted">{tpl.code}</p>
              </div>
              <Link
                href={`/app/platform-role-templates/${tpl.id}`}
                className="text-xs font-medium text-primary underline-offset-4 hover:underline"
              >
                {t("roleTemplateMatrix.action.editTemplate")}
              </Link>
            </div>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-border text-sm">
                <thead className="bg-muted/20 text-xs font-medium uppercase tracking-wide text-muted">
                  <tr>
                    <th className="px-4 py-2 text-left">{t("roleTemplateMatrix.table.code")}</th>
                    <th className="px-4 py-2 text-left">{t("roleTemplateMatrix.table.action")}</th>
                    <th className="px-4 py-2 text-left">{t("roleTemplateMatrix.table.resource")}</th>
                    <th className="px-4 py-2 text-left">{t("roleTemplateMatrix.table.description")}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {codes.length === 0 ? (
                    <tr>
                      <td colSpan={4} className="px-4 py-3 text-sm text-muted">
                        —
                      </td>
                    </tr>
                  ) : (
                    codes.map((code) => {
                      const row = catMap.get(code);
                      return (
                        <tr key={code}>
                          <td className="whitespace-nowrap px-4 py-2 font-mono text-xs text-foreground">{code}</td>
                          <td className="whitespace-nowrap px-4 py-2 text-muted">{row?.action ?? "—"}</td>
                          <td className="whitespace-nowrap px-4 py-2 text-muted">{row?.resource ?? "—"}</td>
                          <td className="min-w-[12rem] px-4 py-2 text-muted">{row?.description ?? "—"}</td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </section>
        );
      })}
    </div>
  );
}
