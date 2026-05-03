"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { showToast } from "@/components/ui/Toast";
import {
  fetchTenantCompanies,
  fetchTenantDepartments,
  fetchTenantJobs,
  patchTenantJobActive,
  type TenantCompanyItem,
  type TenantDepartmentItem,
  type TenantJobItem,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function JobsPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<TenantJobItem[]>([]);
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [departments, setDepartments] = useState<TenantDepartmentItem[]>([]);
  const [selectedCompanyId, setSelectedCompanyId] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<{ item: TenantJobItem } | null>(null);
  const [confirmBusy, setConfirmBusy] = useState(false);

  const canManage = me.privileges.includes("JOB_MANAGE");

  const reload = useCallback(
    async (p = 0, companyId = selectedCompanyId) => {
      setLoad("loading");
      const r = await fetchTenantJobs({ page: p, size: 20, companyId: companyId || undefined });
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
      const [cr, dr] = await Promise.all([
        fetchTenantCompanies({ size: 100 }),
        fetchTenantDepartments({ size: 200 }),
      ]);
      if (cr.ok) setCompanies(cr.items);
      if (dr.ok) setDepartments(dr.items);
    })();
    void reload(0);
  }, [reload]);

  async function toggleActive(item: TenantJobItem) {
    if (item.active) {
      setConfirm({ item });
      return;
    }
    setBusyId(item.id);
    try {
      await patchTenantJobActive(item.id, true);
      showToast(`"${item.title}" set to active.`);
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
      await patchTenantJobActive(item.id, false);
      showToast(`"${item.title}" set to inactive.`);
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
        <h1 className="text-lg font-semibold text-foreground">{t("jobs.title")}</h1>
        <p className="text-sm text-muted">{t("jobs.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">{"<- "}{t("nav.dashboard")}</Link>
      </div>
    );
  }

  const deptName = (id: string) => departments.find((d) => d.id === id)?.name ?? id;

  return (
    <div className="mx-auto max-w-5xl space-y-6" data-testid="jobs-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("jobs.title")}</h1>
        <div className="flex gap-3 text-sm">
          <Link href="/app/departments" className="font-medium text-primary underline-offset-4 hover:underline">
            {"<- "}{t("departments.title")}
          </Link>
          {canManage ? (
            <Link href="/app/jobs/new" className="rounded bg-primary px-3 py-1 text-sm font-medium text-primary-foreground hover:opacity-90">
              {t("jobs.action.new")}
            </Link>
          ) : null}
        </div>
      </div>

      <div className="flex flex-wrap gap-3 text-sm">
        <label className="flex items-center gap-2 text-muted">
          {t("jobs.label.companyId")}:
          <select
            className="rounded border border-border bg-surface px-2 py-1 text-foreground"
            value={selectedCompanyId}
            onChange={(e) => {
              setSelectedCompanyId(e.target.value);
              void reload(0, e.target.value);
            }}
          >
            <option value="">All companies</option>
            {companies.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </label>
      </div>

      {load === "loading" ? <p className="text-sm text-muted">{t("jobs.state.loading")}</p> : null}
      {load === "error" ? <p className="text-sm text-destructive">{t("jobs.error.load")}</p> : null}

      {load === "ready" ? (
        <>
          {items.length === 0 ? (
            <p className="text-sm text-muted">{t("jobs.state.empty")}</p>
          ) : (
            <div className="overflow-x-auto rounded-md border border-border">
              <table className="min-w-full divide-y divide-border text-sm">
                <thead className="bg-surface-alt">
                  <tr>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("jobs.col.title")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("jobs.col.code")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("jobs.col.department")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("jobs.col.status")}</th>
                    {canManage ? <th className="px-4 py-2" /> : null}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface">
                  {items.map((item) => (
                    <tr key={item.id}>
                      <td className="px-4 py-2 font-medium text-foreground">{item.title}</td>
                      <td className="px-4 py-2 text-muted">{item.code}</td>
                      <td className="px-4 py-2 text-muted">{deptName(item.departmentId)}</td>
                      <td className="px-4 py-2">
                        <span className={item.active ? "rounded px-1.5 py-0.5 text-xs font-medium bg-success/10 text-success" : "rounded px-1.5 py-0.5 text-xs font-medium bg-muted/20 text-muted"}>
                          {item.active ? t("jobs.status.active") : t("jobs.status.inactive")}
                        </span>
                      </td>
                      {canManage ? (
                        <td className="px-4 py-2 text-right">
                          <Link href={`/app/jobs/${item.id}/edit`} className="mr-3 text-sm text-primary underline-offset-4 hover:underline">
                            {t("jobs.action.edit")}
                          </Link>
                          <button onClick={() => void toggleActive(item)} disabled={busyId === item.id} className="text-sm text-muted underline-offset-4 hover:underline disabled:opacity-50">
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
              <button onClick={() => void reload(page - 1)} disabled={page === 0} className="rounded border border-border px-3 py-1 disabled:opacity-40">{t("jobs.action.prev")}</button>
              <span className="py-1 text-muted">{page + 1} / {totalPages}</span>
              <button onClick={() => void reload(page + 1)} disabled={page >= totalPages - 1} className="rounded border border-border px-3 py-1 disabled:opacity-40">{t("jobs.action.next")}</button>
            </div>
          ) : null}
        </>
      ) : null}

      <ConfirmDialog
        open={!!confirm}
        title="Deactivate job?"
        description={confirm ? `This will deactivate "${confirm.item.title}" and hide it from all payroll operations.` : ""}
        confirmLabel="Deactivate"
        busy={confirmBusy}
        onConfirm={() => void confirmDeactivate()}
        onCancel={() => setConfirm(null)}
      />
    </div>
  );
}
