"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchTenantCompanies,
  patchTenantCompanyActive,
  type TenantCompanyItem,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function CompaniesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<TenantCompanyItem[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [msg, setMsg] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const canManage = me.privileges.includes("COMPANY_MANAGE");

  const reload = useCallback(async (p = 0) => {
    setLoad("loading");
    setMsg(null);
    const r = await fetchTenantCompanies({ page: p, size: 20 });
    if (!r.ok) {
      setLoad(r.status === 403 ? "forbidden" : "error");
      return;
    }
    setItems(r.items);
    setTotalPages(r.totalPages);
    setPage(p);
    setLoad("ready");
  }, []);

  useEffect(() => {
    void reload(0);
  }, [reload]);

  async function toggleActive(item: TenantCompanyItem) {
    const next = !item.active;
    const confirmed = window.confirm(
      next
        ? t("companies.confirm.activate").replace("{name}", item.name)
        : t("companies.confirm.deactivate").replace("{name}", item.name),
    );
    if (!confirmed) return;
    setBusyId(item.id);
    setMsg(null);
    try {
      await patchTenantCompanyActive(item.id, next);
      await reload(page);
    } catch {
      setMsg(t("companies.msg.saveFailed"));
    } finally {
      setBusyId(null);
    }
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("companies.title")}</h1>
        <p className="text-sm text-muted">{t("companies.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6" data-testid="companies-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("companies.title")}</h1>
        <div className="flex gap-3 text-sm">
          <Link href="/app" className="font-medium text-primary underline-offset-4 hover:underline">
            ← {t("nav.dashboard")}
          </Link>
          {canManage && (
            <Link
              href="/app/companies/new"
              className="rounded bg-primary px-3 py-1 text-sm font-medium text-primary-foreground hover:opacity-90"
            >
              {t("companies.action.new")}
            </Link>
          )}
        </div>
      </div>

      {msg && <p className="text-sm text-foreground">{msg}</p>}

      {load === "loading" && <p className="text-sm text-muted">{t("companies.state.loading")}</p>}
      {load === "error" && <p className="text-sm text-destructive">{t("companies.error.load")}</p>}

      {load === "ready" && (
        <>
          {items.length === 0 ? (
            <p className="text-sm text-muted">{t("companies.state.empty")}</p>
          ) : (
            <div className="overflow-x-auto rounded-md border border-border">
              <table className="min-w-full divide-y divide-border text-sm">
                <thead className="bg-surface-alt">
                  <tr>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("companies.col.name")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("companies.col.taxId")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("companies.col.country")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("companies.col.currency")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("companies.col.status")}</th>
                    {canManage && <th className="px-4 py-2" />}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface">
                  {items.map((item) => (
                    <tr key={item.id}>
                      <td className="px-4 py-2 font-medium text-foreground">{item.name}</td>
                      <td className="px-4 py-2 text-muted">{item.taxId ?? "—"}</td>
                      <td className="px-4 py-2 text-muted">{item.payrollCountry}</td>
                      <td className="px-4 py-2 text-muted">{item.currency}</td>
                      <td className="px-4 py-2">
                        <span
                          className={
                            item.active
                              ? "rounded px-1.5 py-0.5 text-xs font-medium bg-success/10 text-success"
                              : "rounded px-1.5 py-0.5 text-xs font-medium bg-muted/20 text-muted"
                          }
                        >
                          {item.active ? t("companies.status.active") : t("companies.status.inactive")}
                        </span>
                      </td>
                      {canManage && (
                        <td className="px-4 py-2 text-right">
                          <Link
                            href={`/app/companies/${item.id}/edit`}
                            className="mr-3 text-sm text-primary underline-offset-4 hover:underline"
                          >
                            {t("companies.action.edit")}
                          </Link>
                          <button
                            onClick={() => void toggleActive(item)}
                            disabled={busyId === item.id}
                            className="text-sm text-muted underline-offset-4 hover:underline disabled:opacity-50"
                          >
                            {item.active ? t("companies.action.deactivate") : t("companies.action.activate")}
                          </button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {totalPages > 1 && (
            <div className="flex gap-2 text-sm">
              <button
                onClick={() => void reload(page - 1)}
                disabled={page === 0}
                className="rounded border border-border px-3 py-1 disabled:opacity-40"
              >
                {t("companies.action.prev")}
              </button>
              <span className="py-1 text-muted">
                {page + 1} / {totalPages}
              </span>
              <button
                onClick={() => void reload(page + 1)}
                disabled={page >= totalPages - 1}
                className="rounded border border-border px-3 py-1 disabled:opacity-40"
              >
                {t("companies.action.next")}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
