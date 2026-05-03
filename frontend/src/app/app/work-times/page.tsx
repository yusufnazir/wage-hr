"use client";
"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { showToast } from "@/components/ui/Toast";
import {
  fetchTenantCompanies,
  fetchTenantWorkTimes,
  patchTenantWorkTimeActive,
  type TenantCompanyItem,
  type TenantWorkTimeItem,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function WorkTimesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<TenantWorkTimeItem[]>([]);
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [selectedCompanyId, setSelectedCompanyId] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<{ item: TenantWorkTimeItem } | null>(null);
  const [confirmBusy, setConfirmBusy] = useState(false);

  const canManage = me.privileges.includes("WORK_TIME_MANAGE");

  const reload = useCallback(
    async (p = 0, companyId = selectedCompanyId) => {
      setLoad("loading");
      const r = await fetchTenantWorkTimes({ page: p, size: 20, companyId: companyId || undefined });
      if (!r.ok) {
        setLoad(r.status === 403 ? "forbidden" : "error");
        return;
      }
      setItems(r.items);
      setTotalPages(r.totalPages);
      setPage(p);
      setLoad("ready");
    },
    [selectedCompanyId],
  );

  useEffect(() => {
    void (async () => {
      const cr = await fetchTenantCompanies({ size: 100 });
      if (cr.ok) setCompanies(cr.items);
    })();
    void reload(0);
  }, [reload]);

  async function toggleActive(item: TenantWorkTimeItem) {
    if (item.active) {
      setConfirm({ item });
      return;
    }
    setBusyId(item.id);
    try {
      await patchTenantWorkTimeActive(item.id, true);
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
      await patchTenantWorkTimeActive(item.id, false);
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
        <h1 className="text-lg font-semibold text-foreground">{t("workTimes.title")}</h1>
        <p className="text-sm text-muted">{t("workTimes.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">{"<- "}{t("nav.dashboard")}</Link>
      </div>
    );
  }

  const companyName = (id: string) => companies.find((c) => c.id === id)?.name ?? id;

  return (
    <div className="mx-auto max-w-5xl space-y-6" data-testid="work-times-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("workTimes.title")}</h1>
        <div className="flex gap-3 text-sm">
          <Link href="/app/companies" className="font-medium text-primary underline-offset-4 hover:underline">
            {"<- "}{t("companies.title")}
          </Link>
          {canManage ? (
            <Link
              href="/app/work-times/new"
              className="rounded bg-primary px-3 py-1 text-sm font-medium text-primary-foreground hover:opacity-90"
            >
              {t("workTimes.action.new")}
            </Link>
          ) : null}
        </div>
      </div>

      <div className="flex flex-wrap gap-3 text-sm">
        <label className="flex items-center gap-2 text-muted">
          {t("workTimes.col.company")}:
          <select
            className="rounded border border-border bg-surface px-2 py-1 text-foreground"
            value={selectedCompanyId}
            onChange={(e) => {
              setSelectedCompanyId(e.target.value);
              void reload(0, e.target.value);
            }}
          >
            <option value="">All companies</option>
            {companies.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        </label>
      </div>

      {load === "loading" ? <p className="text-sm text-muted">{t("workTimes.state.loading")}</p> : null}
      {load === "error" ? <p className="text-sm text-destructive">{t("workTimes.error.load")}</p> : null}

      {load === "ready" ? (
        <>
          {items.length === 0 ? (
            <p className="text-sm text-muted">{t("workTimes.state.empty")}</p>
          ) : (
            <div className="overflow-x-auto rounded-md border border-border">
              <table className="min-w-full divide-y divide-border text-sm">
                <thead className="bg-surface-alt">
                  <tr>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("workTimes.col.name")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("workTimes.col.code")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("workTimes.col.hoursPerDay")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("workTimes.col.workDaysPerWeek")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("workTimes.col.company")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("workTimes.col.status")}</th>
                    {canManage ? <th className="px-4 py-2" /> : null}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface">
                  {items.map((item) => (
                    <tr key={item.id}>
                      <td className="px-4 py-2 font-medium text-foreground">{item.name}</td>
                      <td className="px-4 py-2 text-muted">{item.code}</td>
                      <td className="px-4 py-2 text-muted">{item.hoursPerDay}</td>
                      <td className="px-4 py-2 text-muted">{item.workDaysPerWeek}</td>
                      <td className="px-4 py-2 text-muted">{companyName(item.companyId)}</td>
                      <td className="px-4 py-2">
                        <span className={item.active ? "rounded px-1.5 py-0.5 text-xs font-medium bg-success/10 text-success" : "rounded px-1.5 py-0.5 text-xs font-medium bg-muted/20 text-muted"}>
                          {item.active ? t("workTimes.status.active") : t("workTimes.status.inactive")}
                        </span>
                      </td>
                      {canManage ? (
                        <td className="px-4 py-2 text-right">
                          <Link href={`/app/work-times/${item.id}/edit`} className="mr-3 text-sm text-primary underline-offset-4 hover:underline">
                            {t("workTimes.action.edit")}
                          </Link>
                          <button
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
              <button onClick={() => void reload(page - 1)} disabled={page === 0} className="rounded border border-border px-3 py-1 disabled:opacity-40">{t("workTimes.action.prev")}</button>
              <span className="py-1 text-muted">{page + 1} / {totalPages}</span>
              <button onClick={() => void reload(page + 1)} disabled={page >= totalPages - 1} className="rounded border border-border px-3 py-1 disabled:opacity-40">{t("workTimes.action.next")}</button>
            </div>
          ) : null}
        </>
      ) : null}

      <ConfirmDialog
        open={!!confirm}
        title="Deactivate work time?"
        description={confirm ? `This will deactivate "${confirm.item.name}" and hide it from all payroll operations.` : ""}
        confirmLabel="Deactivate"
        busy={confirmBusy}
        onConfirm={() => void confirmDeactivate()}
        onCancel={() => setConfirm(null)}
      />
    </div>
  );
}
