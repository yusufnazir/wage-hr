"use client";

import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { NoCompanyEmptyState } from "@/components/onboarding/NoCompanyEmptyState";
import { showToast } from "@/components/ui/Toast";
import {
  createTenantPayPeriodRun,
  fetchTenantCompanies,
  fetchTenantPayPeriodRuns,
  fetchTenantPayPeriods,
  generateTenantCompanyPayPeriods,
  patchTenantPayPeriodStatus,
  supervisorApproveTenantPayPeriod,
  type TenantCompanyItem,
  type TenantPayPeriodItem,
  type TenantPayPeriodRunItem,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

const STATUSES = ["READY", "OPEN", "CLOSED"] as const;

function currentYear() {
  return new Date().getFullYear();
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

// ── Pay period helpers (mirrors backend frequency logic) ──
type PeriodOption = { startDate: string; endDate: string };

function _iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function _nextEnd(cur: Date, freq: string, anchorDay: number, anchorIsEOM: boolean): Date {
  if (freq === "WEEKLY") { const d = new Date(cur); d.setDate(d.getDate() + 7); return d; }
  if (freq === "BIWEEKLY") { const d = new Date(cur); d.setDate(d.getDate() + 14); return d; }
  if (freq === "SEMIMONTHLY") {
    if (cur.getDate() === 15) return new Date(cur.getFullYear(), cur.getMonth(), new Date(cur.getFullYear(), cur.getMonth() + 1, 0).getDate());
    const nm = (cur.getMonth() + 1) % 12; const ny = nm === 0 ? cur.getFullYear() + 1 : cur.getFullYear();
    return new Date(ny, nm, 15);
  }
  const nm = (cur.getMonth() + 1) % 12; const ny = nm === 0 ? cur.getFullYear() + 1 : cur.getFullYear();
  const dim = new Date(ny, nm + 1, 0).getDate();
  return new Date(ny, nm, anchorIsEOM ? dim : Math.min(anchorDay, dim));
}

function _prevEnd(cur: Date, freq: string, anchorDay: number, anchorIsEOM: boolean): Date {
  if (freq === "WEEKLY") { const d = new Date(cur); d.setDate(d.getDate() - 7); return d; }
  if (freq === "BIWEEKLY") { const d = new Date(cur); d.setDate(d.getDate() - 14); return d; }
  if (freq === "SEMIMONTHLY") {
    if (cur.getDate() !== 15) return new Date(cur.getFullYear(), cur.getMonth(), 15);
    const pm = cur.getMonth() === 0 ? 11 : cur.getMonth() - 1; const py = cur.getMonth() === 0 ? cur.getFullYear() - 1 : cur.getFullYear();
    return new Date(py, pm, new Date(py, pm + 1, 0).getDate());
  }
  const pm = cur.getMonth() === 0 ? 11 : cur.getMonth() - 1; const py = cur.getMonth() === 0 ? cur.getFullYear() - 1 : cur.getFullYear();
  const dim = new Date(py, pm + 1, 0).getDate();
  return new Date(py, pm, anchorIsEOM ? dim : Math.min(anchorDay, dim));
}

function computePeriodsForYear(company: TenantCompanyItem, year: number): PeriodOption[] {
  if (!company.payPeriodEndDate) return [];
  const [ay, am, ad] = company.payPeriodEndDate.split("-").map(Number) as [number, number, number];
  const freq = company.payrollFrequency;
  const anchorIsEOM = ad === new Date(ay, am, 0).getDate();
  let end = new Date(ay, am - 1, ad);
  const jan1 = new Date(year, 0, 1);
  while (end >= jan1) end = _prevEnd(end, freq, ad, anchorIsEOM);
  end = _nextEnd(end, freq, ad, anchorIsEOM);
  const results: PeriodOption[] = [];
  const dec31 = new Date(year, 11, 31);
  while (end <= dec31) {
    const prev = _prevEnd(end, freq, ad, anchorIsEOM);
    const start = new Date(prev); start.setDate(start.getDate() + 1);
    results.push({ startDate: _iso(start), endDate: _iso(end) });
    end = _nextEnd(end, freq, ad, anchorIsEOM);
  }
  return results;
}

export default function PayPeriodsPage() {
  const { me, hasCompany } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const canManage = me.privileges.includes("PAY_PERIOD_MANAGE");
  const canManageRuns = me.privileges.includes("PAY_PERIOD_RUN_MANAGE");
  const canSupervisorApprove = me.privileges.includes("PAY_PERIOD_SUPERVISOR_APPROVE");

  // ── list state ──
  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<TenantPayPeriodItem[]>([]);
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [selectedCompanyId, setSelectedCompanyId] = useState("");
  const [selectedYear, setSelectedYear] = useState<string>("");
  const [selectedStatus, setSelectedStatus] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  // ── status patch busy ──
  const [statusBusyId, setStatusBusyId] = useState<string | null>(null);

  // ── regenerate dialog ──
  const [generateOpen, setGenerateOpen] = useState(false);
  const [generateCompanyId, setGenerateCompanyId] = useState("");
  const [generateYear, setGenerateYear] = useState(currentYear());
  const [generatePeriodOptions, setGeneratePeriodOptions] = useState<PeriodOption[]>([]);
  const [generatePeriodIdx, setGeneratePeriodIdx] = useState<number | "">("" );
  const [generateYearsAhead, setGenerateYearsAhead] = useState(2);
  const [generateBusy, setGenerateBusy] = useState(false);
  const [generateErr, setGenerateErr] = useState<string | null>(null);

  // ── runs panel ──
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [runs, setRuns] = useState<TenantPayPeriodRunItem[]>([]);
  const [runsLoad, setRunsLoad] = useState<"loading" | "ready" | "error">("loading");
  const [runFormOpen, setRunFormOpen] = useState(false);
  const [runType, setRunType] = useState("INTERIM");
  const [runFormBusy, setRunFormBusy] = useState(false);
  const [runFormErr, setRunFormErr] = useState<string | null>(null);
  const [supervisorBusyId, setSupervisorBusyId] = useState<string | null>(null);

  const expandedItem = expandedId ? items.find((i) => i.id === expandedId) ?? null : null;

  const reload = useCallback(
    async (p = 0, companyId = selectedCompanyId, year = selectedYear, status = selectedStatus) => {
      setLoad("loading");
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

  if (hasCompany === false) {
    const returnTo =
      typeof window !== "undefined"
        ? `${window.location.pathname}${window.location.search}${window.location.hash}`
        : "/app/pay-periods";
    return (
      <div className="mx-auto max-w-5xl">
        <NoCompanyEmptyState
          title="Pay periods need a company"
          body="Create a company first so we can generate and manage pay periods."
          returnTo={returnTo}
          showViewCompanies={me.privileges.includes("COMPANY_VIEW") || me.privileges.includes("COMPANY_MANAGE")}
        />
      </div>
    );
  }

  async function patchStatus(item: TenantPayPeriodItem, newStatus: string) {
    setStatusBusyId(item.id);
    try {
      await patchTenantPayPeriodStatus(item.id, newStatus);
      await reload(page);
    } catch (e) {
      const msg = e instanceof Error ? e.message : t("payPeriods.msg.saveFailed");
      showToast(msg.includes("SUPERVISOR_APPROVAL") ? t("payPeriods.msg.supervisorRequired") : msg);
    } finally {
      setStatusBusyId(null);
    }
  }

  async function handleSupervisorApprove(payPeriodId: string) {
    setSupervisorBusyId(payPeriodId);
    try {
      await supervisorApproveTenantPayPeriod(payPeriodId);
      showToast(t("payPeriods.msg.supervisorApproved"));
      await reload(page);
    } catch (e) {
      const msg = e instanceof Error ? e.message : t("payPeriods.msg.supervisorApproveFailed");
      showToast(msg.includes("FINAL_RUN_REQUIRED") ? t("payPeriods.msg.finalRunRequired") : msg);
    } finally {
      setSupervisorBusyId(null);
    }
  }

  async function loadRuns(payPeriodId: string) {
    setRunsLoad("loading");
    const r = await fetchTenantPayPeriodRuns(payPeriodId);
    if (!r.ok) { setRunsLoad("error"); return; }
    setRuns(r.items);
    setRunsLoad("ready");
  }

  async function toggleRuns(item: TenantPayPeriodItem) {
    if (expandedId === item.id) {
      setExpandedId(null);
      return;
    }
    setExpandedId(item.id);
    setRunFormOpen(false);
    setRunFormErr(null);
    await loadRuns(item.id);
  }

  async function handleCreateRun() {
    if (!expandedId) return;
    setRunFormBusy(true);
    setRunFormErr(null);
    try {
      await createTenantPayPeriodRun({ payPeriodId: expandedId, runType });
      showToast(t("payPeriodRuns.msg.runCreated"));
      setRunFormOpen(false);
      await loadRuns(expandedId);
    } catch (e) {
      setRunFormErr(t("payPeriodRuns.msg.runCreateFailed"));
      console.error(e);
    } finally {
      setRunFormBusy(false);
    }
  }

  const companyName = (id: string) => companies.find((c) => c.id === id)?.name ?? id;

  function openGenerateDialog() {
    const cid = selectedCompanyId || (companies.length === 1 ? companies[0]!.id : "");
    const company = companies.find((c) => c.id === cid);
    const year = company?.currentYear ?? currentYear();
    setGenerateCompanyId(cid);
    setGenerateYear(year);
    setGeneratePeriodOptions(company ? computePeriodsForYear(company, year) : []);
    setGeneratePeriodIdx("");
    setGenerateYearsAhead(2);
    setGenerateErr(null);
    setGenerateOpen(true);
  }

  async function handleGenerate() {
    if (!generateCompanyId) { setGenerateErr("Please select a company."); return; }
    setGenerateBusy(true);
    setGenerateErr(null);
    try {
      const selected = generatePeriodIdx !== "" ? generatePeriodOptions[generatePeriodIdx - 1] : undefined;
      const result = await generateTenantCompanyPayPeriods(generateCompanyId, {
        fromDate: selected?.startDate,
        yearsAhead: generateYearsAhead,
      });
      showToast(`${t("payPeriods.generate.msg.done")} (${result.created} created)`);
      setGenerateOpen(false);
      void reload(0);
    } catch {
      setGenerateErr(t("payPeriods.generate.msg.failed"));
    } finally {
      setGenerateBusy(false);
    }
  }

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
        {canManage && (
          <div className="flex items-center gap-2">
            <button
              onClick={openGenerateDialog}
              className="rounded border border-border px-3 py-1 text-sm font-medium text-foreground hover:bg-surface-alt"
            >
              {t("payPeriods.action.generate")}
            </button>
            <Link
              href="/app/pay-periods/new"
              className="rounded bg-primary px-3 py-1 text-sm font-medium text-primary-foreground hover:opacity-90"
            >
              {t("payPeriods.action.new")}
            </Link>
          </div>
        )}
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
                    <th className="px-4 py-2" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface">
                  {items.map((item) => (
                    <React.Fragment key={item.id}>
                      <tr>
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
                        <td className="px-4 py-2 text-right">
                          <div className="flex items-center justify-end gap-3">
                            {canManage && (
                              <Link
                                href={`/app/pay-periods/${item.id}/edit`}
                                className="text-sm text-primary underline-offset-4 hover:underline"
                              >
                                {t("payPeriods.action.edit")}
                              </Link>
                            )}
                            {canManage && (
                              <select
                                className="rounded border border-border bg-surface px-1 py-0.5 text-xs text-foreground disabled:opacity-50"
                                value={item.status}
                                disabled={statusBusyId === item.id}
                                onChange={(e) => void patchStatus(item, e.target.value)}
                              >
                                {STATUSES.map((s) => (
                                  <option
                                    key={s}
                                    value={s}
                                    disabled={s === "CLOSED" && !item.supervisorApprovedAt}
                                  >
                                    {t(`payPeriods.status.${s.toLowerCase()}`)}
                                  </option>
                                ))}
                              </select>
                            )}
                          </div>
                        </td>
                      </tr>
                      {expandedId === item.id && (
                        <tr key={`${item.id}-runs`}>
                          <td colSpan={7} className="bg-surface-alt px-6 py-3">
                            <div className="space-y-3">
                              <div className="flex flex-wrap items-center justify-between gap-2">
                                <span className="text-sm font-medium text-foreground">{t("payPeriodRuns.title")}</span>
                                <div className="flex flex-wrap items-center gap-2">
                                  {canSupervisorApprove && expandedItem && expandedItem.status !== "CLOSED" && (
                                    expandedItem.supervisorApprovedAt ? (
                                      <span className="text-xs text-success">{t("payPeriods.supervisor.approved")}</span>
                                    ) : (
                                      <button
                                        type="button"
                                        onClick={() => void handleSupervisorApprove(expandedItem.id)}
                                        disabled={supervisorBusyId === expandedItem.id}
                                        className="rounded border border-border px-2 py-1 text-xs hover:bg-surface-alt disabled:opacity-40"
                                      >
                                        {t("payPeriods.action.supervisorApprove")}
                                      </button>
                                    )
                                  )}
                                  {canManageRuns && !runFormOpen && (
                                    <button
                                      onClick={() => { setRunFormOpen(true); setRunFormErr(null); }}
                                      className="rounded bg-primary px-2 py-1 text-xs font-medium text-primary-foreground hover:opacity-90"
                                    >
                                      {t("payPeriodRuns.action.newRun")}
                                    </button>
                                  )}
                                </div>
                              </div>

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
                                    onClick={() => { setRunFormOpen(false); setRunFormErr(null); }}
                                    disabled={runFormBusy}
                                    className="rounded border border-border px-2 py-1 text-xs hover:bg-surface-alt disabled:opacity-40"
                                  >
                                    {t("payPeriodRuns.action.cancel")}
                                  </button>
                                  {runFormErr && <span className="text-xs text-destructive">{runFormErr}</span>}
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
                    </React.Fragment>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="flex items-center gap-2 text-sm">
            <button onClick={() => void reload(page - 1)} disabled={page === 0} className="rounded border border-border px-3 py-1 disabled:opacity-40">
              {t("payPeriods.action.prev")}
            </button>
            <span className="py-1 text-muted">{t("payPeriods.pagination.page")} {page + 1} / {totalPages}</span>
            <button onClick={() => void reload(page + 1)} disabled={page >= totalPages - 1} className="rounded border border-border px-3 py-1 disabled:opacity-40">
              {t("payPeriods.action.next")}
            </button>
          </div>
        </>
      )}
      {/* Regenerate dialog */}
      {generateOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="w-full max-w-sm rounded-lg border border-border bg-surface p-6 shadow-xl">
            <h2 className="mb-4 text-base font-semibold text-foreground">{t("payPeriods.generate.title")}</h2>
            <div className="space-y-4">
              {companies.length !== 1 && (
                <label className="flex flex-col gap-1 text-sm">
                  <span className="text-muted">{t("payPeriods.col.company")}</span>
                  <select
                    className="rounded border border-border bg-surface px-2 py-1 text-foreground"
                    value={generateCompanyId}
                    onChange={(e) => {
                      const cid = e.target.value;
                      const company = companies.find((c) => c.id === cid);
                      const year = company?.currentYear ?? currentYear();
                      setGenerateCompanyId(cid);
                      setGenerateYear(year);
                      setGeneratePeriodOptions(company ? computePeriodsForYear(company, year) : []);
                      setGeneratePeriodIdx("");
                    }}
                    disabled={generateBusy}
                  >
                    <option value="">— select —</option>
                    {companies.map((c) => (
                      <option key={c.id} value={c.id}>{c.name}</option>
                    ))}
                  </select>
                </label>
              )}
              <div className="flex gap-3">
                <label className="flex flex-col gap-1 text-sm">
                  <span className="text-muted">{t("payPeriods.generate.label.year")}</span>
                  <input
                    type="number"
                    className="w-24 rounded border border-border bg-surface px-2 py-1 text-foreground disabled:opacity-50"
                    value={generateYear}
                    onChange={(e) => {
                      const y = Number(e.target.value);
                      setGenerateYear(y);
                      const company = companies.find((c) => c.id === generateCompanyId);
                      setGeneratePeriodOptions(company ? computePeriodsForYear(company, y) : []);
                      setGeneratePeriodIdx("");
                    }}
                    disabled={generateBusy || !generateCompanyId}
                  />
                </label>
                <label className="flex flex-1 flex-col gap-1 text-sm">
                  <span className="text-muted">{t("payPeriods.generate.label.fromPeriod")}</span>
                  <select
                    className="rounded border border-border bg-surface px-2 py-1 text-foreground disabled:opacity-50"
                    value={generatePeriodIdx}
                    onChange={(e) => setGeneratePeriodIdx(e.target.value === "" ? "" : Number(e.target.value))}
                    disabled={generateBusy || !generateCompanyId}
                  >
                    <option value="">— from beginning —</option>
                    {generatePeriodOptions.map((_, i) => (
                      <option key={i + 1} value={i + 1}>{t("payPeriods.generate.period")} {i + 1}</option>
                    ))}
                  </select>
                </label>
              </div>
              {generatePeriodIdx !== "" && generatePeriodOptions[generatePeriodIdx - 1] && (
                <p className="-mt-2 text-xs text-primary">
                  {generatePeriodOptions[generatePeriodIdx - 1]!.startDate} → {generatePeriodOptions[generatePeriodIdx - 1]!.endDate}
                </p>
              )}
              <label className="flex flex-col gap-1 text-sm">
                <span className="text-muted">{t("payPeriods.generate.label.yearsAhead")}</span>
                <input
                  type="number"
                  min={1}
                  max={5}
                  className="w-24 rounded border border-border bg-surface px-2 py-1 text-foreground disabled:opacity-50"
                  value={generateYearsAhead}
                  onChange={(e) => setGenerateYearsAhead(Math.min(5, Math.max(1, Number(e.target.value))))}
                  disabled={generateBusy}
                />
              </label>
              <p className="text-xs text-muted">{t("payPeriods.generate.hint")}</p>
              {generateErr && <p className="text-sm text-destructive">{generateErr}</p>}
            </div>
            <div className="mt-5 flex justify-end gap-3">
              <button
                onClick={() => setGenerateOpen(false)}
                disabled={generateBusy}
                className="rounded border border-border px-3 py-1 text-sm hover:bg-surface-alt disabled:opacity-40"
              >
                {t("payPeriods.action.cancel")}
              </button>
              <button
                onClick={() => void handleGenerate()}
                disabled={generateBusy || !generateCompanyId}
                className="rounded bg-primary px-3 py-1 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40"
              >
                {generateBusy ? "…" : t("payPeriods.generate.action.submit")}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
