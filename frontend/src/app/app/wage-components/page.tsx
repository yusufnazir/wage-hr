"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { showToast } from "@/components/ui/Toast";
import {
  fetchTenantCompanies,
  fetchTenantWageComponents,
  patchTenantWageComponentActive,
  type TenantCompanyItem,
  type TenantWageComponentItem,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function WageComponentsPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<TenantWageComponentItem[]>([]);
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [selectedCompanyId, setSelectedCompanyId] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<{ item: TenantWageComponentItem } | null>(null);
  const [confirmBusy, setConfirmBusy] = useState(false);

  const canManage = me.privileges.includes("WAGE_COMPONENT_MANAGE");

  const reload = useCallback(
    async (p = 0, companyId = selectedCompanyId) => {
      if (!companyId) {
        setItems([]);
        setTotalPages(1);
        setPage(0);
        setLoad("ready");
        return;
      }
      setLoad("loading");
      const listR = await fetchTenantWageComponents({ page: p, size: 20, companyId });
      if (!listR.ok) {
        setLoad(listR.status === 403 ? "forbidden" : "error");
        return;
      }
      setItems(listR.items);
      setTotalPages(listR.totalPages);
      setPage(p);
      setLoad("ready");
    },
    [selectedCompanyId],
  );

  useEffect(() => {
    void (async () => {
      const cr = await fetchTenantCompanies({ size: 100 });
      if (cr.ok) {
        setCompanies(cr.items);
        if (cr.items.length === 1) {
          setSelectedCompanyId((prev) => prev || cr.items[0].id);
        }
      }
    })();
  }, [me.userId]);

  useEffect(() => {
    void reload(0, selectedCompanyId);
  }, [reload, selectedCompanyId]);

  async function toggleActive(item: TenantWageComponentItem) {
    if (item.active) {
      setConfirm({ item });
      return;
    }
    setBusyId(item.id);
    try {
      await patchTenantWageComponentActive(item.id, true);
      showToast(`"${item.name}" set to active.`);
      await reload(page);
    } catch {
      showToast("Could not update status. Please try again.", "error");
    } finally {
      setBusyId(null);
    }
  }

  async function confirmDeactivate() {
    if (!confirm) return;
    const { item } = confirm;
    setConfirmBusy(true);
    try {
      await patchTenantWageComponentActive(item.id, false);
      showToast(`"${item.name}" set to inactive.`);
      await reload(page);
    } catch {
      showToast("Could not update status. Please try again.", "error");
    } finally {
      setConfirmBusy(false);
      setConfirm(null);
    }
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("wageComponents.title")}</h1>
        <p className="text-sm text-muted">{t("wageComponents.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}
          {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  const companyName = (id: string) => companies.find((c) => c.id === id)?.name ?? id;

  return (
    <div className="mx-auto max-w-5xl space-y-8" data-testid="wage-components-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("wageComponents.title")}</h1>
        <div className="flex gap-3 text-sm">
          <Link href="/app/companies" className="font-medium text-primary underline-offset-4 hover:underline">
            {"<- "}
            {t("companies.title")}
          </Link>
          {canManage && selectedCompanyId ? (
            <Link
              href={`/app/wage-components/new?companyId=${encodeURIComponent(selectedCompanyId)}`}
              className="rounded bg-primary px-3 py-1 text-sm font-medium text-primary-foreground hover:opacity-90"
            >
              {t("wageComponents.action.new")}
            </Link>
          ) : null}
        </div>
      </div>

      <p className="text-sm text-muted">{t("wageComponents.intro")}</p>

      <div className="flex flex-wrap gap-3 text-sm">
        <label className="flex items-center gap-2 text-muted">
          {t("wageComponents.label.companyId")}:
          <select
            className="rounded border border-border bg-surface px-2 py-1 text-foreground"
            value={selectedCompanyId}
            onChange={(e) => setSelectedCompanyId(e.target.value)}
          >
            <option value="">—</option>
            {companies.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      {!selectedCompanyId ? (
        <p className="text-sm text-muted">{t("wageComponents.state.pickCompany")}</p>
      ) : null}

      {selectedCompanyId && load === "loading" ? <p className="text-sm text-muted">{t("wageComponents.state.loading")}</p> : null}
      {selectedCompanyId && load === "error" ? (
        <p className="text-sm text-destructive">{t("wageComponents.error.load")}</p>
      ) : null}

      {selectedCompanyId && load === "ready" ? (
        <section className="space-y-2">
            {items.length === 0 ? (
              <p className="text-sm text-muted">{t("wageComponents.state.empty")}</p>
            ) : (
              <div className="overflow-x-auto rounded-md border border-border">
                <table className="min-w-full divide-y divide-border text-sm">
                  <thead className="bg-surface-alt">
                    <tr>
                      <th className="px-4 py-2 text-left font-medium text-muted">{t("wageComponents.col.name")}</th>
                      <th className="px-4 py-2 text-left font-medium text-muted">{t("wageComponents.col.code")}</th>
                      <th className="px-4 py-2 text-left font-medium text-muted">{t("wageComponents.col.type")}</th>
                      <th className="px-4 py-2 text-left font-medium text-muted">{t("wageComponents.col.phase")}</th>
                      <th className="px-4 py-2 text-left font-medium text-muted">{t("wageComponents.col.company")}</th>
                      <th className="px-4 py-2 text-left font-medium text-muted">{t("wageComponents.col.status")}</th>
                      {canManage ? <th className="px-4 py-2" /> : null}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border bg-surface">
                    {items.map((item) => (
                      <tr key={item.id}>
                        <td className="px-4 py-2 font-medium text-foreground">{item.name}</td>
                        <td className="px-4 py-2 font-mono text-xs text-muted">{item.code}</td>
                        <td className="px-4 py-2 text-muted">{item.componentType}</td>
                        <td className="px-4 py-2 text-muted">{item.phase}</td>
                        <td className="px-4 py-2 text-muted">{companyName(item.companyId)}</td>
                        <td className="px-4 py-2">
                          <span
                            className={
                              item.active
                                ? "rounded px-1.5 py-0.5 text-xs font-medium bg-success/10 text-success"
                                : "rounded px-1.5 py-0.5 text-xs font-medium bg-muted/20 text-muted"
                            }
                          >
                            {item.active ? t("wageComponents.status.active") : t("wageComponents.status.inactive")}
                          </span>
                        </td>
                        {canManage ? (
                          <td className="px-4 py-2 text-right">
                            <Link
                              href={`/app/wage-components/${item.id}/edit`}
                              className="mr-3 text-sm text-primary underline-offset-4 hover:underline"
                            >
                              {t("wageComponents.action.edit")}
                            </Link>
                            <button
                              type="button"
                              onClick={() => void toggleActive(item)}
                              disabled={busyId === item.id}
                              className="text-sm text-muted underline-offset-4 hover:underline disabled:opacity-50"
                            >
                              {item.active ? "Deactivate" : "Activate"}
                            </button>
                          </td>
                        ) : null}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {totalPages > 1 ? (
              <div className="flex gap-2 text-sm">
                <button
                  type="button"
                  onClick={() => void reload(page - 1)}
                  disabled={page === 0}
                  className="rounded border border-border px-3 py-1 disabled:opacity-40"
                >
                  {t("wageComponents.action.prev")}
                </button>
                <span className="py-1 text-muted">
                  {page + 1} / {totalPages}
                </span>
                <button
                  type="button"
                  onClick={() => void reload(page + 1)}
                  disabled={page >= totalPages - 1}
                  className="rounded border border-border px-3 py-1 disabled:opacity-40"
                >
                  {t("wageComponents.action.next")}
                </button>
              </div>
            ) : null}
        </section>
      ) : null}

      <ConfirmDialog
        open={!!confirm}
        title="Deactivate wage component?"
        description={
          confirm ? `This will deactivate "${confirm.item.name}" and hide it from payroll configuration picks.` : ""
        }
        confirmLabel="Deactivate"
        busy={confirmBusy}
        onConfirm={() => void confirmDeactivate()}
        onCancel={() => setConfirm(null)}
      />
    </div>
  );
}
