"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { fetchPlatformCurrencies, type PlatformCurrencyRow } from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function PlatformCurrenciesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<PlatformCurrencyRow[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  const reload = useCallback(async (p = 0) => {
    setLoad("loading");
    const r = await fetchPlatformCurrencies(p);
    if (!r.ok) {
      setLoad(r.status === 403 ? "forbidden" : "error");
      return;
    }
    setItems(r.items);
    setTotalPages(r.totalPages);
    setLoad("ready");
  }, []);

  useEffect(() => {
    void reload(page);
  }, [reload, page]);

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCurrencies.title")}</h1>
        <p className="text-sm text-muted">{t("platformCurrencies.error.notOperator")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"\u2190 "}{t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCurrencies.title")}</h1>
        <p className="text-sm text-muted">{t("platformCurrencies.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"\u2190 "}{t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-2xl">
        <p className="text-sm text-muted">{t("platformCurrencies.state.loading")}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCurrencies.title")}</h1>
        <div className="flex items-center gap-4">
          <Link
            href="/app/platform-currencies/new"
            className="rounded bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground"
          >
            + {t("platformCurrencies.title.new")}
          </Link>
          <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
            {"\u2190 "}{t("nav.dashboard")}
          </Link>
        </div>
      </div>

      <p className="text-sm text-muted">{t("platformCurrencies.helper.intro")}</p>

      {load === "error" ? (
        <p className="text-sm text-muted">{t("platformCurrencies.error.load")}</p>
      ) : (
        <div className="overflow-x-auto rounded-md border border-border">
          <table className="w-full text-sm">
            <thead className="bg-surface text-left text-xs font-medium uppercase text-muted">
              <tr>
                <th className="px-3 py-2">{t("platformCurrencies.col.code")}</th>
                <th className="px-3 py-2">{t("platformCurrencies.col.name")}</th>
                <th className="px-3 py-2">{t("platformCurrencies.col.sortOrder")}</th>
                <th className="px-3 py-2">{t("platformCurrencies.col.active")}</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {items.map((row) => (
                <tr key={row.id} className="border-t border-border">
                  <td className="px-3 py-2 font-mono font-semibold text-foreground">{row.code}</td>
                  <td className="px-3 py-2 text-foreground">{row.displayName}</td>
                  <td className="px-3 py-2 text-muted">{row.sortOrder}</td>
                  <td className="px-3 py-2 text-muted">{row.active ? "\u2713" : "\u2014"}</td>
                  <td className="px-3 py-2">
                    <Link
                      href={`/app/platform-currencies/${row.id}/edit`}
                      className="text-sm font-medium text-primary underline-offset-4 hover:underline"
                    >
                      {t("platformCurrencies.action.edit")}
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 ? (
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <button
            type="button"
            className="rounded border border-border px-3 py-1 text-sm disabled:opacity-40"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            {"\u2190 "}{t("platformCurrencies.action.prev")}
          </button>
          <span className="text-muted">
            {t("platformCurrencies.pageIndicator").replace("{n}", String(page + 1)).replace("{t}", String(totalPages))}
          </span>
          <button
            type="button"
            className="rounded border border-border px-3 py-1 text-sm disabled:opacity-40"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            {t("platformCurrencies.action.next")} {"\u2192"}
          </button>
        </div>
      ) : null}
    </div>
  );
}
