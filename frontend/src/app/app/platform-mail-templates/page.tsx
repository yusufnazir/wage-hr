"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { fetchPlatformMailTemplates, type MailTemplateListItem } from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function PlatformMailTemplatesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<MailTemplateListItem[]>([]);

  const reload = useCallback(async () => {
    setLoad("loading");
    const r = await fetchPlatformMailTemplates();
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
        <h1 className="text-lg font-semibold text-foreground">{t("mailTemplates.list.title")}</h1>
        <p className="text-sm text-muted">{t("mailTemplates.list.error.notOperator")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("mailTemplates.list.title")}</h1>
        <p className="text-sm text-muted">{t("mailTemplates.list.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "error") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("mailTemplates.list.title")}</h1>
        <p className="text-sm text-muted">{t("mailTemplates.list.error.load")}</p>
        <button
          type="button"
          className="text-sm font-medium text-primary underline-offset-4 hover:underline"
          onClick={() => void reload()}
        >
          {t("mailTemplates.list.action.retry")}
        </button>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-4xl">
        <p className="text-sm text-muted">{t("mailTemplates.list.state.loading")}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6" data-testid="platform-mail-templates-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("mailTemplates.list.title")}</h1>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>
      <p className="text-sm text-muted">{t("mailTemplates.list.helper")}</p>

      <div className="overflow-x-auto rounded-lg border border-border bg-surface shadow-sm">
        <table className="min-w-full divide-y divide-border text-sm">
          <thead className="bg-muted/30">
            <tr>
              <th className="px-4 py-2 text-left font-medium text-foreground">{t("mailTemplates.list.col.code")}</th>
              <th className="px-4 py-2 text-left font-medium text-foreground">{t("mailTemplates.list.col.version")}</th>
              <th className="px-4 py-2 text-left font-medium text-foreground">{t("mailTemplates.list.col.active")}</th>
              <th className="px-4 py-2 text-left font-medium text-foreground">{t("mailTemplates.list.col.updated")}</th>
              <th className="px-4 py-2 text-left font-medium text-foreground">{t("mailTemplates.list.col.actions")}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {items.map((row) => (
              <tr key={row.id}>
                <td className="px-4 py-2 font-mono text-xs text-foreground">{row.code}</td>
                <td className="px-4 py-2 font-mono text-xs text-muted">{row.contentVersion}</td>
                <td className="px-4 py-2 text-foreground">{row.active ? "yes" : "no"}</td>
                <td className="px-4 py-2 text-muted">{row.updatedAt}</td>
                <td className="px-4 py-2">
                  <Link
                    href={`/app/platform-mail-templates/${row.id}`}
                    className="font-medium text-primary underline-offset-4 hover:underline"
                    data-testid={`mail-template-edit-${row.code}`}
                  >
                    {t("mailTemplates.list.action.edit")}
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
