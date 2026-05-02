"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  createTenantPayPeriod,
  createTenantPayPeriodRun,
  fetchTenantCompanies,
  fetchTenantPayPeriodRuns,
  fetchTenantPayPeriods,
  patchTenantPayPeriodStatus,
  putTenantPayPeriod,
  type TenantCompanyItem,
  type TenantPayPeriodItem,
  type TenantPayPeriodRunItem,
  type TenantPayPeriodUpsertPayload,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";
type ModalMode = { kind: "create" } | { kind: "edit"; item: TenantPayPeriodItem } | null;

const STATUSES = ["READY", "OPEN", "CLOSED"] as const;

function currentYear() {
  return new Date().getFullYear();
}

function emptyPayload(companyId = ""): TenantPayPeriodUpsertPayload {
  const y = currentYear();
  return {
    companyId,
    year: y,
    startDate: `${y}-01-01`,
    endDate: `${y}-01-31`,
    status: "READY",
  };
}

function itemToPayload(item: TenantPayPeriodItem): TenantPayPeriodUpsertPayload {
  return {
    companyId: item.companyId,
    year: item.year,
    startDate: item.startDate,
    endDate: item.endDate,
    status: item.status,
  };
}

function statusBadgeClass(status: string) {
  switch (status) {
    case "OPEN":
      return "rounded px-1.5 py-0.5 text-xs font-medium bg-success/10 text-success";
    case "READY":
      return "rounded px-1.5 py-0.5 text-xs font-medium bg-primary/10 text-primary";
    case "CLOSED":
      return "rounded px-1.5 py-0.5 text-xs font-medium bg-muted/20 text-muted";
    default:
      return "rounded px-1.5 py-0.5 text-xs font-medium bg-muted/20 text-muted";
  }
}

function runTypeBadgeClass(runType: string) {
  return runType === "FINAL"
    ? "rounded px-1.5 py-0.5 text-xs font-medium bg-success/10 text-success"
    : "rounded px-1.5 py-0.5 text-xs font-medium bg-primary/10 text-primary";
}

export default function PayPeriodsPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const canManage = me.privileges.includes("PAY_PERIOD_MANAGE");
  const canManageRuns = me.privileges.includes("PAY_PERIOD_RUN_MANAGE");

  // ── list state ──
  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<TenantPayPeriodItem[]>([]);
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [selectedCompanyId, setSelectedCompanyId] = useState("");
  const [selectedYear, setSelectedYear] = useState<string>(String(currentYear()));
  const [selectedStatus, setSelectedStatus] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [msg, setMsg] = useState<string | null>(null);

  // ── create/edit modal ──
  const [modal, setModal] = useState<ModalMode>(null);
  const [form, setForm] = useState<TenantPayPeriodUpsertPayload>(emptyPayload());
  const [formBusy, setFormBusy] = useState(false);
  const [formMsg, setFormMsg] = useState<string | null>(null);

  // ── status patch busy ──
  const [statusBusyId, setStatusBusyId] = useState<string | null>(null);

  // ── runs panel ──
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [runs, setRuns] = useState<TenantPayPeriodRunItem[]>([]);
  const [runsLoad, setRunsLoad] = useState<"loading" | "ready" | "error">("loading");
  const [runFormOpen, setRunFormOpen] = useState(false);
  const [runType, setRunType] = useState("INTERIM");
  const [runFormBusy, setRunFormBusy] = useState(false);
  const [runFormMsg, setRunFormMsg] = useState<string | null>(null);
  const [runMsg, setRunMsg] = useState<string | null>(null);

  const reload = useCallback(
    async (p = 0, companyId = selectedCompanyId, year = selectedYear, status = selectedStatus) => {
      setLoad("loading");
      setMsg(null);
      const r = await fetchTenantPayPeriods({
        page: p,
        size: 20,
        companyId: companyId || undefined,
        year: year ? Number(year) : null,
        status: status || null,
      });
      if (!r.ok) {
        setLoad(r.status === 403 ? "forbidden" : "error");
        return;
      }
      setItems(r.items);
      setTotalPages(r.totalPages);
      setPage(p);
      setLoad("ready");
    },
    [selectedCompanyId, selectedYear, selectedStatus],
  );

  useEffect(() => {
    void (async () => {
      const cr = await fetchTenantCompanies({ size: 100 });
      if (cr.ok) setCompanies(cr.items);
    })();
    void reload(0);
  }, [reload]);

  // ── modal helpers ──
  function openCreate() {
    setForm(emptyPayload(selectedCompanyId));
    setFormMsg(null);
    setModal({ kind: "create" });
  }

  function openEdit(item: TenantPayPeriodItem) {
    setForm(itemToPayload(item));
    setFormMsg(null);
    setModal({ kind: "edit", item });
  }

  function closeModal() {
    setModal(null);
    setFormMsg(null);
  }

  async function handleSubmit() {
    if (!form.companyId) { setFormMsg("Company is required."); return; }
    if (!form.year || form.year < 1900 || form.year > 2200) { setFormMsg("Year must be a valid calendar year."); return; }
    if (!form.startDate) { setFormMsg("Start date is required."); return; }
    if (!form.endDate) { setFormMsg("End date is required."); return; }
    if (form.endDate < form.startDate) { setFormMsg("End date must not be before start date."); return; }
    if (!form.status) { setFormMsg("Status is required."); return; }
    setFormBusy(true);
    setFormMsg(null);
    try {
      if (modal?.kind === "create") {
        await createTenantPayPeriod(form);
        setMsg(t("payPeriods.msg.created"));
      } else if (modal?.kind === "edit") {
        await putTenantPayPeriod(modal.item.id, form);
        setMsg(t("payPeriods.msg.saved"));
      }
      closeModal();
      await reload(page);
    } catch (e) {
      setFormMsg(modal?.kind === "create" ? t("payPeriods.msg.createFailed") : t("payPeriods.msg.saveFailed"));
      console.error(e);
    } finally {
      setFormBusy(false);
    }
  }

  // ── status patch ──
  async function patchStatus(item: TenantPayPeriodItem, newStatus: string) {
    setStatusBusyId(item.id);
    setMsg(null);
    try {
      await patchTenantPayPeriodStatus(item.id, newStatus);
      await reload(page);
    } catch {
      setMsg(t("payPeriods.msg.saveFailed"));
    } finally {
      setStatusBusyId(null);
    }
  }

  // ── runs panel ──
  async function toggleRuns(item: TenantPayPeriodItem) {
    if (expandedId === item.id) {
      setExpandedId(null);
      return;
    }
    setExpandedId(item.id);
    setRunFormOpen(false);
    setRunMsg(null);
    setRunsLoad("loading");
    const r = await fetchTenantPayPeriodRuns(item.id);
    if (!r.ok) {
      setRunsLoad("error");
      return;
    }
    setRuns(r.items);
    setRunsLoad("ready");
  }

  async function reloadRuns(payPeriodId: string) {
    setRunsLoad("loading");
    const r = await fetchTenantPayPeriodRuns(payPeriodId);
    if (!r.ok) { setRunsLoad("error"); return; }
    setRuns(r.items);
    setRunsLoad("ready");
  }

  async function handleCreateRun() {
    if (!expandedId) return;
    setRunFormBusy(true);
    setRunFormMsg(null);
    try {
      await createTenantPayPeriodRun({ payPeriodId: expandedId, runType });
      setRunMsg(t("payPeriodRuns.msg.runCreated"));
      setRunFormOpen(false);
      await reloadRuns(expandedId);
    } catch (e) {
      setRunFormMsg(t("payPeriodRuns.msg.runCreateFailed"));
      console.error(e);
    } finally {
      setRunFormBusy(false);
    }
  }

  const companyName = (id: string) => companies.find((c) => c.id === id)?.name ?? id;

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("payPeriods.title")}</h1>
        <p className="text-sm text-muted">{t("payPeriods.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">← {t("nav.dashboard")}</Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6" data-testid="pay-periods-page">
      {/* Header */}
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("payPeriods.title")}</h1>
        <div className="flex gap-3 text-sm">
          <Link href="/app/companies" className="font-medium text-primary underline-offset-4 hover:underline">
            ← {t("companies.title")}
          </Link>
          {canManage && (
            <button
              onClick={openCreate}
              className="rounded bg-primary px-3 py-1 text-sm font-medium text-primary-foreground hover:opacity-90"
            >
              {t("payPeriods.action.new")}
            </button>
          )}
        </div>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3 text-sm">
        <label className="flex items-center gap-2 text-muted">
          {t("payPeriods.col.company")}:
          <select
            className="rounded border border-border bg-surface px-2 py-1 text-foreground"
            value={selectedCompanyId}
            onChange={(e) => {
              setSelectedCompanyId(e.target.value);
              void reload(0, e.target.value, selectedYear, selectedStatus);
            }}
          >
            <option value="">All companies</option>
            {companies.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        </label>
        <label className="flex items-center gap-2 text-muted">
          {t("payPeriods.col.year")}:
          <input
            type="number"
            className="w-24 rounded border border-border bg-surface px-2 py-1 text-foreground"
            value={selectedYear}
            onChange={(e) => {
              setSelectedYear(e.target.value);
              void reload(0, selectedCompanyId, e.target.value, selectedStatus);
            }}
          />
        </label>
        <label className="flex items-center gap-2 text-muted">
          {t("payPeriods.col.status")}:
          <select
            className="rounded border border-border bg-surface px-2 py-1 text-foreground"
            value={selectedStatus}
            onChange={(e) => {
              setSelectedStatus(e.target.value);
              void reload(0, selectedCompanyId, selectedYear, e.target.value);
            }}
          >
            <option value="">All statuses</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>{t(`payPeriods.status.${s.toLowerCase()}`)}</option>
            ))}
          </select>
        </label>
      </div>

      {msg && <p className="text-sm text-foreground">{msg}</p>}
      {load === "loading" && <p className="text-sm text-muted">{t("payPeriods.state.loading")}</p>}
      {load === "error" && <p className="text-sm text-destructive">{t("payPeriods.error.load")}</p>}

      {load === "ready" && (
        <>
          {items.length === 0 ? (
            <p className="text-sm text-muted">{t("payPeriods.state.empty")}</p>
          ) : (
            <div className="overflow-x-auto rounded-md border border-border">
              <table className="min-w-full divide-y divide-border text-sm">
                <thead className="bg-surface-alt">
                  <tr>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("payPeriods.col.year")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("payPeriods.col.startDate")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("payPeriods.col.endDate")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("payPeriods.col.status")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("payPeriods.col.company")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("payPeriods.col.runs")}</th>
                    {canManage && <th className="px-4 py-2" />}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface">
                  {items.map((item) => (
                    <>
                      <tr key={item.id}>
                        <td className="px-4 py-2 font-medium text-foreground">{item.year}</td>
                        <td className="px-4 py-2 text-muted">{item.startDate}</td>
                        <td className="px-4 py-2 text-muted">{item.endDate}</td>
                        <td className="px-4 py-2">
                          <span className={statusBadgeClass(item.status)}>
                            {t(`payPeriods.status.${item.status.toLowerCase()}`)}
                          </span>
                        </td>
                        <td className="px-4 py-2 text-muted">{companyName(item.companyId)}</td>
                        <td className="px-4 py-2">
                          <button
                            onClick={() => void toggleRuns(item)}
                            className="text-sm text-primary underline-offset-4 hover:underline"
                          >
                            {expandedId === item.id ? "▲ Hide" : "▼ " + t("payPeriods.col.runs")}
                          </button>
                        </td>
                        {canManage && (
                          <td className="px-4 py-2 text-right">
                            <button
                              onClick={() => openEdit(item)}
                              className="mr-3 text-sm text-primary underline-offset-4 hover:underline"
                            >
                              {t("payPeriods.action.edit")}
                            </button>
                            <select
                              className="rounded border border-border bg-surface px-1 py-0.5 text-xs text-foreground disabled:opacity-50"
                              value={item.status}
                              disabled={statusBusyId === item.id}
                              onChange={(e) => void patchStatus(item, e.target.value)}
                            >
                              {STATUSES.map((s) => (
                                <option key={s} value={s}>{t(`payPeriods.status.${s.toLowerCase()}`)}</option>
                              ))}
                            </select>
                          </td>
                        )}
                      </tr>
                      {expandedId === item.id && (
                        <tr key={`${item.id}-runs`}>
                          <td colSpan={canManage ? 7 : 6} className="bg-surface-alt px-6 py-3">
                            <div className="space-y-3">
                              <div className="flex items-center justify-between">
                                <span className="text-sm font-medium text-foreground">{t("payPeriodRuns.title")}</span>
                                {canManageRuns && !runFormOpen && (
                                  <button
                                    onClick={() => { setRunFormOpen(true); setRunFormMsg(null); }}
                                    className="rounded bg-primary px-2 py-1 text-xs font-medium text-primary-foreground hover:opacity-90"
                                  >
                                    {t("payPeriodRuns.action.newRun")}
                                  </button>
                                )}
                              </div>

                              {runMsg && <p className="text-xs text-foreground">{runMsg}</p>}

                              {runFormOpen && canManageRuns && (
                                <div className="flex items-end gap-3 rounded border border-border bg-surface p-3">
                                  <label className="flex flex-col gap-1 text-xs text-muted">
                                    {t("payPeriodRuns.label.runType")}
                                    <select
                                      className="rounded border border-border bg-surface px-2 py-1 text-foreground"
                                      value={runType}
                                      onChange={(e) => setRunType(e.target.value)}
                                    >
                                      <option value="INTERIM">{t("payPeriodRuns.runType.interim")}</option>
                                      <option value="FINAL">{t("payPeriodRuns.runType.final")}</option>
                                    </select>
                                  </label>
                                  <button
                                    onClick={() => void handleCreateRun()}
                                    disabled={runFormBusy}
                                    className="rounded bg-primary px-2 py-1 text-xs font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40"
                                  >
                                    {t("payPeriodRuns.action.createRun")}
                                  </button>
                                  <button
                                    onClick={() => { setRunFormOpen(false); setRunFormMsg(null); }}
                                    disabled={runFormBusy}
                                    className="rounded border border-border px-2 py-1 text-xs hover:bg-surface-alt disabled:opacity-40"
                                  >
                                    {t("payPeriodRuns.action.cancel")}
                                  </button>
                                  {runFormMsg && <span className="text-xs text-destructive">{runFormMsg}</span>}
                                </div>
                              )}

                              {runsLoad === "loading" && <p className="text-xs text-muted">Loading…</p>}
                              {runsLoad === "error" && <p className="text-xs text-destructive">Could not load runs.</p>}
                              {runsLoad === "ready" && runs.length === 0 && (
                                <p className="text-xs text-muted">No runs yet.</p>
                              )}
                              {runsLoad === "ready" && runs.length > 0 && (
                                <table className="min-w-full divide-y divide-border text-xs">
                                  <thead>
                                    <tr>
                                      <th className="px-3 py-1 text-left font-medium text-muted">{t("payPeriodRuns.col.runNumber")}</th>
                                      <th className="px-3 py-1 text-left font-medium text-muted">{t("payPeriodRuns.col.runType")}</th>
                                      <th className="px-3 py-1 text-left font-medium text-muted">{t("payPeriodRuns.col.createdAt")}</th>
                                    </tr>
                                  </thead>
                                  <tbody className="divide-y divide-border">
                                    {runs.map((run) => (
                                      <tr key={run.id}>
                                        <td className="px-3 py-1 font-medium text-foreground">#{run.runNumber}</td>
                                        <td className="px-3 py-1">
                                          <span className={runTypeBadgeClass(run.runType)}>
                                            {t(`payPeriodRuns.runType.${run.runType.toLowerCase()}`)}
                                          </span>
                                        </td>
                                        <td className="px-3 py-1 text-muted">{new Date(run.createdAt).toLocaleDateString()}</td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              )}
                            </div>
                          </td>
                        </tr>
                      )}
                    </>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {totalPages > 1 && (
            <div className="flex gap-2 text-sm">
              <button onClick={() => void reload(page - 1)} disabled={page === 0} className="rounded border border-border px-3 py-1 disabled:opacity-40">
                {t("payPeriods.action.prev")}
              </button>
              <span className="py-1 text-muted">{page + 1} / {totalPages}</span>
              <button onClick={() => void reload(page + 1)} disabled={page >= totalPages - 1} className="rounded border border-border px-3 py-1 disabled:opacity-40">
                {t("payPeriods.action.next")}
              </button>
            </div>
          )}
        </>
      )}

      {/* Create / Edit Modal */}
      {modal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-lg border border-border bg-surface p-6 shadow-xl">
            <h2 className="mb-4 text-base font-semibold text-foreground">
              {modal.kind === "create" ? t("payPeriods.action.new") : t("payPeriods.action.edit")}
            </h2>
            <div className="space-y-3 text-sm">
              <label className="block">
                <span className="text-muted">{t("payPeriods.label.companyId")} *</span>
                <select
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.companyId}
                  onChange={(e) => setForm({ ...form, companyId: e.target.value })}
                >
                  <option value="">Select company…</option>
                  {companies.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </label>
              <label className="block">
                <span className="text-muted">{t("payPeriods.label.year")} *</span>
                <input
                  type="number"
                  min="1900"
                  max="2200"
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.year}
                  onChange={(e) => setForm({ ...form, year: parseInt(e.target.value, 10) || currentYear() })}
                />
              </label>
              <label className="block">
                <span className="text-muted">{t("payPeriods.label.startDate")} *</span>
                <input
                  type="date"
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.startDate}
                  onChange={(e) => setForm({ ...form, startDate: e.target.value })}
                />
              </label>
              <label className="block">
                <span className="text-muted">{t("payPeriods.label.endDate")} *</span>
                <input
                  type="date"
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.endDate}
                  onChange={(e) => setForm({ ...form, endDate: e.target.value })}
                />
              </label>
              <label className="block">
                <span className="text-muted">{t("payPeriods.label.status")} *</span>
                <select
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.status}
                  onChange={(e) => setForm({ ...form, status: e.target.value })}
                >
                  {STATUSES.map((s) => (
                    <option key={s} value={s}>{t(`payPeriods.status.${s.toLowerCase()}`)}</option>
                  ))}
                </select>
              </label>
            </div>
            {formMsg && <p className="mt-3 text-sm text-destructive">{formMsg}</p>}
            <div className="mt-4 flex justify-end gap-2">
              <button
                onClick={closeModal}
                disabled={formBusy}
                className="rounded border border-border px-3 py-1.5 text-sm hover:bg-surface-alt disabled:opacity-40"
              >
                {t("payPeriods.action.cancel")}
              </button>
              <button
                onClick={() => void handleSubmit()}
                disabled={formBusy}
                className="rounded bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40"
              >
                {modal.kind === "create" ? t("payPeriods.action.create") : t("payPeriods.action.save")}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
