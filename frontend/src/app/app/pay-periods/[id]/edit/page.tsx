"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { PlatformDateInput } from "@/components/ui/PlatformDateInput";
import { showToast } from "@/components/ui/Toast";
import {
  createTenantPayPeriodRun,
  fetchTenantPayPeriod,
  fetchTenantPayPeriodRuns,
  fetchTenantEmployees,
  postTenantPayPeriodFormulaPreview,
  putTenantPayPeriod,
  type TenantEmployeeItem,
  type TenantFormulaPreviewResult,
  type TenantPayPeriodItem,
  type TenantPayPeriodRunItem,
  type TenantPayPeriodUpsertPayload,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error" | "not-found";

const STATUSES = ["READY", "OPEN", "CLOSED"] as const;

function runTypeBadgeClass(runType: string) {
  return runType === "FINAL"
    ? "rounded px-1.5 py-0.5 text-xs font-medium bg-success/10 text-success"
    : "rounded px-1.5 py-0.5 text-xs font-medium bg-primary/10 text-primary";
}

export default function EditPayPeriodPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const canManage = me.privileges.includes("PAY_PERIOD_MANAGE");
  const canManageRuns = me.privileges.includes("PAY_PERIOD_RUN_MANAGE");
  const canFormulaPreview = me.privileges.includes("PAY_PERIOD_VIEW");

  const [load, setLoad] = useState<LoadState>("loading");
  const [item, setItem] = useState<TenantPayPeriodItem | null>(null);
  const [form, setForm] = useState<TenantPayPeriodUpsertPayload>({
    companyId: "",
    year: new Date().getFullYear(),
    startDate: "",
    endDate: "",
    status: "READY",
  });
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  // runs
  const [runs, setRuns] = useState<TenantPayPeriodRunItem[]>([]);
  const [runsLoad, setRunsLoad] = useState<"loading" | "ready" | "error">("loading");
  const [runFormOpen, setRunFormOpen] = useState(false);
  const [runType, setRunType] = useState("INTERIM");
  const [runFormBusy, setRunFormBusy] = useState(false);
  const [runFormErr, setRunFormErr] = useState<string | null>(null);

  const [formulaEmployees, setFormulaEmployees] = useState<TenantEmployeeItem[]>([]);
  const [formulaEmpLoad, setFormulaEmpLoad] = useState<"idle" | "loading" | "ready" | "error">("idle");
  const [selectedEmpIds, setSelectedEmpIds] = useState<Set<string>>(() => new Set());
  const [formulaPreview, setFormulaPreview] = useState<TenantFormulaPreviewResult | null>(null);
  const [formulaPreviewBusy, setFormulaPreviewBusy] = useState(false);
  const [formulaPreviewErr, setFormulaPreviewErr] = useState<string | null>(null);

  const loadFormulaEmployees = useCallback(async () => {
    if (!form.companyId) return;
    setFormulaEmpLoad("loading");
    setFormulaPreviewErr(null);
    const r = await fetchTenantEmployees({ companyId: form.companyId, page: 0, size: 200, active: true });
    if (!r.ok) {
      setFormulaEmpLoad("error");
      return;
    }
    setFormulaEmployees(r.items);
    setSelectedEmpIds(new Set(r.items.map((e) => e.id)));
    setFormulaEmpLoad("ready");
  }, [form.companyId]);

  useEffect(() => {
    if (load === "ready" && canFormulaPreview && form.companyId) {
      void loadFormulaEmployees();
    }
  }, [load, id, canFormulaPreview, form.companyId, loadFormulaEmployees]);

  const loadRuns = useCallback(async () => {
    setRunsLoad("loading");
    const r = await fetchTenantPayPeriodRuns(id);
    if (!r.ok) { setRunsLoad("error"); return; }
    setRuns(r.items);
    setRunsLoad("ready");
  }, [id]);

  useEffect(() => {
    void (async () => {
      const r = await fetchTenantPayPeriod(id);
      if (!r.ok) {
        setLoad(r.status === 403 ? "forbidden" : r.status === 404 ? "not-found" : "error");
        return;
      }
      const loaded = r.item;
      setItem(loaded);
      setForm({
        companyId: loaded.companyId,
        year: loaded.year,
        startDate: loaded.startDate,
        endDate: loaded.endDate,
        status: loaded.status,
      });
      setLoad("ready");
    })();
    void loadRuns();
  }, [id, loadRuns]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!form.startDate) { setErr("Start date is required."); return; }
    if (!form.endDate) { setErr("End date is required."); return; }
    if (form.endDate < form.startDate) { setErr("End date must not be before start date."); return; }
    setBusy(true);
    setErr(null);
    try {
      await putTenantPayPeriod(id, form);
      showToast(t("payPeriods.msg.saved"));
    } catch (e) {
      setErr(t("payPeriods.msg.saveFailed"));
      console.error(e);
      setBusy(false);
    } finally {
      setBusy(false);
    }
  }

  async function handleCreateRun() {
    setRunFormBusy(true);
    setRunFormErr(null);
    try {
      await createTenantPayPeriodRun({ payPeriodId: id, runType });
      showToast(t("payPeriodRuns.msg.runCreated"));
      setRunFormOpen(false);
      await loadRuns();
    } catch (e) {
      setRunFormErr(t("payPeriodRuns.msg.runCreateFailed"));
      console.error(e);
    } finally {
      setRunFormBusy(false);
    }
  }

  async function runFormulaPreview() {
    const ids = [...selectedEmpIds];
    if (ids.length === 0) {
      setFormulaPreviewErr(t("payPeriods.formulaPreview.emptySelection"));
      return;
    }
    setFormulaPreviewBusy(true);
    setFormulaPreviewErr(null);
    const r = await postTenantPayPeriodFormulaPreview(id, { employeeIds: ids });
    if (!r.ok) {
      setFormulaPreviewErr(t("payPeriods.formulaPreview.msg.failed"));
      setFormulaPreviewBusy(false);
      return;
    }
    setFormulaPreview(r.result);
    setFormulaPreviewBusy(false);
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <p className="text-sm text-muted">{t("payPeriods.state.loading")}</p>
      </div>
    );
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("payPeriods.title.edit")}</h1>
        <p className="text-sm text-muted">{t("payPeriods.error.forbidden")}</p>
        <Link href="/app/pay-periods" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("payPeriods.title")}
        </Link>
      </div>
    );
  }

  if (load === "not-found" || load === "error") {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("payPeriods.title.edit")}</h1>
        <p className="text-sm text-destructive">
          {load === "not-found" ? t("payPeriods.error.notFound") : t("payPeriods.error.load")}
        </p>
        <Link href="/app/pay-periods" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("payPeriods.title")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="flex items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("payPeriods.title.edit")}</h1>
        <Link
          href="/app/pay-periods"
          className="text-sm font-medium text-primary underline-offset-4 hover:underline"
        >
          ← {t("payPeriods.title")}
        </Link>
      </div>

      {/* Details form */}
      <form onSubmit={(e) => void handleSubmit(e)} className="rounded-md border border-border bg-surface p-5 space-y-4">
        <h2 className="text-sm font-semibold text-foreground">Details</h2>

        <label className="block">
          <span className="text-sm text-muted">{t("payPeriods.label.companyId")}</span>
          <input
            type="text"
            className="mt-1 w-full rounded border border-border bg-surface-alt px-2 py-1.5 text-sm text-muted"
            value={form.companyId}
            readOnly
            disabled
          />
        </label>

        <label className="block">
          <span className="text-sm text-muted">{t("payPeriods.label.year")} *</span>
          <input
            type="number"
            min="1900"
            max="2200"
            className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-sm text-foreground"
            value={form.year}
            onChange={(e) => setForm({ ...form, year: parseInt(e.target.value, 10) || form.year })}
            disabled={!canManage}
            required
          />
        </label>

        <label className="block">
          <span className="text-sm text-muted">{t("payPeriods.label.startDate")} *</span>
          <PlatformDateInput
            className="mt-1"
            value={form.startDate}
            dateFormat={me.dateFormat}
            onChange={(v) => setForm({ ...form, startDate: v })}
            disabled={!canManage}
          />
        </label>

        <label className="block">
          <span className="text-sm text-muted">{t("payPeriods.label.endDate")} *</span>
          <PlatformDateInput
            className="mt-1"
            value={form.endDate}
            dateFormat={me.dateFormat}
            onChange={(v) => setForm({ ...form, endDate: v })}
            disabled={!canManage}
          />
        </label>

        <label className="block">
          <span className="text-sm text-muted">{t("payPeriods.label.status")} *</span>
          <select
            className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-sm text-foreground"
            value={form.status}
            onChange={(e) => setForm({ ...form, status: e.target.value })}
            disabled={!canManage}
            required
          >
            {STATUSES.map((s) => (
              <option key={s} value={s}>{t(`payPeriods.status.${s.toLowerCase()}`)}</option>
            ))}
          </select>
        </label>

        {err && <p className="text-sm text-destructive">{err}</p>}

        {canManage && (
          <div className="flex justify-end gap-2 pt-2">
            <Link
              href="/app/pay-periods"
              className="rounded border border-border px-3 py-1.5 text-sm hover:bg-surface-alt"
            >
              {t("payPeriods.action.cancel")}
            </Link>
            <button
              type="submit"
              disabled={busy}
              className="rounded bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40"
            >
              {t("payPeriods.action.save")}
            </button>
          </div>
        )}
      </form>

      {canFormulaPreview && (
        <div className="rounded-md border border-border bg-surface p-5 space-y-4">
          <h2 className="text-sm font-semibold text-foreground">{t("payPeriods.formulaPreview.title")}</h2>
          <p className="text-xs text-muted">{t("payPeriods.formulaPreview.intro")}</p>

          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => void loadFormulaEmployees()}
              disabled={formulaEmpLoad === "loading" || !form.companyId}
              className="rounded border border-border px-2 py-1 text-xs font-medium hover:bg-surface-alt disabled:opacity-40"
            >
              {t("payPeriods.formulaPreview.loadEmployees")}
            </button>
            <button
              type="button"
              onClick={() => setSelectedEmpIds(new Set(formulaEmployees.map((e) => e.id)))}
              disabled={formulaEmployees.length === 0}
              className="rounded border border-border px-2 py-1 text-xs hover:bg-surface-alt disabled:opacity-40"
            >
              {t("payPeriods.formulaPreview.selectAll")}
            </button>
            <button
              type="button"
              onClick={() => setSelectedEmpIds(new Set())}
              disabled={formulaEmployees.length === 0}
              className="rounded border border-border px-2 py-1 text-xs hover:bg-surface-alt disabled:opacity-40"
            >
              {t("payPeriods.formulaPreview.selectNone")}
            </button>
            <button
              type="button"
              onClick={() => void runFormulaPreview()}
              disabled={formulaPreviewBusy || selectedEmpIds.size === 0}
              className="rounded bg-primary px-2 py-1 text-xs font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40"
            >
              {t("payPeriods.formulaPreview.preview")}
            </button>
          </div>

          {formulaEmpLoad === "loading" && <p className="text-xs text-muted">Loading…</p>}
          {formulaEmpLoad === "error" && <p className="text-xs text-destructive">{t("payPeriods.error.load")}</p>}
          {formulaEmpLoad === "ready" && formulaEmployees.length === 0 && (
            <p className="text-xs text-muted">{t("payPeriods.formulaPreview.noEmployees")}</p>
          )}
          {formulaEmpLoad === "ready" && formulaEmployees.length > 0 && (
            <div className="max-h-48 space-y-1 overflow-y-auto rounded border border-border bg-surface-alt p-2 text-xs">
              {formulaEmployees.map((emp) => (
                <label key={emp.id} className="flex cursor-pointer items-center gap-2 py-0.5 hover:bg-surface">
                  <input
                    type="checkbox"
                    checked={selectedEmpIds.has(emp.id)}
                    onChange={() => {
                      setSelectedEmpIds((prev) => {
                        const n = new Set(prev);
                        if (n.has(emp.id)) n.delete(emp.id);
                        else n.add(emp.id);
                        return n;
                      });
                    }}
                  />
                  <span className="text-foreground">
                    {emp.firstName} {emp.lastName}
                  </span>
                  <span className="font-mono text-[10px] text-muted">{emp.id}</span>
                </label>
              ))}
            </div>
          )}

          {formulaPreviewErr && <p className="text-xs text-destructive">{formulaPreviewErr}</p>}

          {formulaPreview !== null && (
            <div className="space-y-4">
              <div className="overflow-x-auto rounded border border-border">
                <table className="min-w-full divide-y divide-border text-xs">
                  <thead className="bg-surface-alt">
                    <tr>
                      <th className="px-2 py-2 text-left font-medium text-muted">{t("payPeriods.formulaPreview.col.employee")}</th>
                      <th className="px-2 py-2 text-left font-medium text-muted">{t("payPeriods.formulaPreview.col.component")}</th>
                      <th className="px-2 py-2 text-left font-medium text-muted">{t("payPeriods.formulaPreview.col.source")}</th>
                      <th className="px-2 py-2 text-left font-medium text-muted">{t("payPeriods.formulaPreview.col.method")}</th>
                      <th className="px-2 py-2 text-right font-medium text-muted">{t("payPeriods.formulaPreview.col.amount")}</th>
                      <th className="px-2 py-2 text-left font-medium text-muted">{t("payPeriods.formulaPreview.col.formula")}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border bg-surface">
                    {formulaPreview.items.map((row, idx) => {
                      const emp = formulaEmployees.find((e) => e.id === row.employeeId);
                      const empLabel = emp ? `${emp.firstName} ${emp.lastName}` : row.employeeId;
                      return (
                        <tr key={`${row.employeeId}-${row.tenantWageComponentId ?? row.platformWageComponentId}-${idx}`}>
                          <td className="px-2 py-1.5 text-foreground">{empLabel}</td>
                          <td className="px-2 py-1.5 font-mono text-[11px] text-foreground">{row.tenantWageComponentCode}</td>
                          <td className="px-2 py-1.5 text-muted">{row.componentSource ?? "TENANT"}</td>
                          <td className="px-2 py-1.5 text-muted">{row.calculationMethod}</td>
                          <td className="px-2 py-1.5 text-right font-mono text-foreground">
                            {typeof row.evaluatedAmount === "number"
                              ? row.evaluatedAmount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 })
                              : String(row.evaluatedAmount)}
                          </td>
                          <td className="max-w-[200px] truncate px-2 py-1.5 font-mono text-[10px] text-muted" title={row.formulaExpression ?? ""}>
                            {row.formulaExpression ?? "—"}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
                {formulaPreview.items.length === 0 && (
                  <p className="border-t border-border px-2 py-2 text-xs text-muted">No evaluated lines.</p>
                )}
              </div>
              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <h3 className="mb-2 text-xs font-semibold text-foreground">{t("payPeriods.formulaPreview.basesTitle")}</h3>
                  <pre className="max-h-40 overflow-auto rounded border border-border bg-surface-alt p-2 font-mono text-[10px] text-muted">
                    {JSON.stringify(formulaPreview.employeeBaseTotals, null, 2)}
                  </pre>
                </div>
                <div>
                  <h3 className="mb-2 text-xs font-semibold text-foreground">{t("payPeriods.formulaPreview.netPayTitle")}</h3>
                  <ul className="space-y-1 text-xs">
                    {Object.entries(formulaPreview.employeeNetPay).map(([empId, net]) => {
                      const emp = formulaEmployees.find((e) => e.id === empId);
                      const label = emp ? `${emp.firstName} ${emp.lastName}` : empId;
                      return (
                        <li key={empId} className="flex justify-between gap-2">
                          <span className="text-foreground">{label}</span>
                          <span className="font-mono">{Number(net).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 })}</span>
                        </li>
                      );
                    })}
                  </ul>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Runs panel */}
      <div className="rounded-md border border-border bg-surface p-5 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-foreground">{t("payPeriodRuns.title")}</h2>
          {canManageRuns && !runFormOpen && item?.status === "OPEN" && (
            <button
              onClick={() => { setRunFormOpen(true); setRunFormErr(null); }}
              className="rounded bg-primary px-2 py-1 text-xs font-medium text-primary-foreground hover:opacity-90"
            >
              {t("payPeriodRuns.action.newRun")}
            </button>
          )}
        </div>

        {runFormOpen && canManageRuns && (
          <div className="flex items-end gap-3 rounded border border-border bg-surface-alt p-3">
            <label className="flex flex-col gap-1 text-xs text-muted">
              {t("payPeriodRuns.label.runType")}
              <select
                className="rounded border border-border bg-surface px-2 py-1 text-sm text-foreground"
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
          <div className="overflow-x-auto rounded border border-border">
            <table className="min-w-full divide-y divide-border text-xs">
              <thead className="bg-surface-alt">
                <tr>
                  <th className="px-3 py-2 text-left font-medium text-muted">{t("payPeriodRuns.col.runNumber")}</th>
                  <th className="px-3 py-2 text-left font-medium text-muted">{t("payPeriodRuns.col.runType")}</th>
                  <th className="px-3 py-2 text-left font-medium text-muted">{t("payPeriodRuns.col.createdAt")}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border bg-surface">
                {runs.map((run) => (
                  <tr key={run.id}>
                    <td className="px-3 py-2 font-medium text-foreground">#{run.runNumber}</td>
                    <td className="px-3 py-2">
                      <span className={runTypeBadgeClass(run.runType)}>
                        {t(`payPeriodRuns.runType.${run.runType.toLowerCase()}`)}
                      </span>
                    </td>
                    <td className="px-3 py-2 text-muted">{new Date(run.createdAt).toLocaleDateString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
