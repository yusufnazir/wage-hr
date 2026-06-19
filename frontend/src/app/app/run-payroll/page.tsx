"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { NoCompanyEmptyState } from "@/components/onboarding/NoCompanyEmptyState";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { showToast } from "@/components/ui/Toast";
import {
  createTenantPayPeriodRun,
  fetchTenantCompanies,
  fetchTenantEmployees,
  fetchTenantPayPeriodRuns,
  fetchTenantPayPeriods,
  fetchTenantPayrollResultLines,
  finalizeTenantPayPeriodRun,
  generateTenantCompanyPayPeriods,
  materializeTenantPayrollInputs,
  postTenantPayPeriodFormulaPreview,
  type TenantCompanyItem,
  type TenantEmployeeItem,
  type TenantFormulaPreviewResult,
  type TenantMaterializePayrollInputsResult,
  type TenantPayPeriodFinalizeResult,
  type TenantPayPeriodItem,
  type TenantPayPeriodRunItem,
  type TenantPayrollResultLineItem,
} from "@/lib/api";
import { resolveActivePayPeriod } from "@/lib/pay-period-calendar";
import { formatUserFacingDate } from "@/lib/user-date-format";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

type WorkflowStepId = "prepare" | "preview" | "run" | "review";

function statusBadgeClass(status: string) {
  switch (status) {
    case "OPEN":
      return "rounded-full px-2 py-0.5 text-xs font-medium bg-success/10 text-success";
    case "READY":
      return "rounded-full px-2 py-0.5 text-xs font-medium bg-primary/10 text-primary";
    case "CLOSED":
      return "rounded-full px-2 py-0.5 text-xs font-medium bg-muted/20 text-muted";
    default:
      return "rounded-full px-2 py-0.5 text-xs font-medium bg-muted/20 text-muted";
  }
}

function formatMoney(value: number, currency?: string | null) {
  const n = Number(value);
  if (!Number.isFinite(n)) return "—";
  try {
    return new Intl.NumberFormat(undefined, {
      style: currency ? "currency" : "decimal",
      currency: currency ?? undefined,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(n);
  } catch {
    return n.toFixed(2);
  }
}

function StepIndicator({
  index,
  title,
  done,
  active,
}: {
  index: number;
  title: string;
  done: boolean;
  active: boolean;
}) {
  return (
    <div className="flex items-center gap-2">
      <span
        className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold ${
          done
            ? "bg-success text-success-foreground"
            : active
              ? "bg-primary text-primary-foreground"
              : "border border-border bg-surface text-muted"
        }`}
        aria-hidden
      >
        {done ? "✓" : index}
      </span>
      <span className={`text-sm font-medium ${active || done ? "text-foreground" : "text-muted"}`}>{title}</span>
    </div>
  );
}

export default function RunPayrollPage() {
  const { me, hasCompany, primaryCompanyId } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const canView = me.privileges.includes("PAY_PERIOD_VIEW");
  const canMaterialize = me.privileges.includes("EMPLOYEE_PAYROLL_STANDING_MANAGE");
  const canRunManage = me.privileges.includes("PAY_PERIOD_RUN_MANAGE");
  const canGenerate = me.privileges.includes("PAY_PERIOD_MANAGE");

  const [load, setLoad] = useState<LoadState>("loading");
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [companyId, setCompanyId] = useState("");
  const [payPeriod, setPayPeriod] = useState<TenantPayPeriodItem | null>(null);
  const [resolutionHint, setResolutionHint] = useState<"incomplete" | "notFound" | "outOfRange" | null>(null);

  const [employees, setEmployees] = useState<TenantEmployeeItem[]>([]);
  const [selectedEmpIds, setSelectedEmpIds] = useState<Set<string>>(() => new Set());

  const [materializeResult, setMaterializeResult] = useState<TenantMaterializePayrollInputsResult | null>(null);
  const [materializeBusy, setMaterializeBusy] = useState(false);

  const [formulaPreview, setFormulaPreview] = useState<TenantFormulaPreviewResult | null>(null);
  const [previewBusy, setPreviewBusy] = useState(false);
  const [previewErr, setPreviewErr] = useState<string | null>(null);

  const [runs, setRuns] = useState<TenantPayPeriodRunItem[]>([]);
  const [finalizeResult, setFinalizeResult] = useState<TenantPayPeriodFinalizeResult | null>(null);
  const [resultLines, setResultLines] = useState<TenantPayrollResultLineItem[]>([]);
  const [runBusy, setRunBusy] = useState(false);
  const [finalizeConfirmOpen, setFinalizeConfirmOpen] = useState(false);
  const [generateBusy, setGenerateBusy] = useState(false);

  const company = useMemo(() => companies.find((c) => c.id === companyId) ?? null, [companies, companyId]);

  const reloadContext = useCallback(async (cid: string) => {
    const co = companies.find((c) => c.id === cid);
    if (!co) {
      setPayPeriod(null);
      setResolutionHint(null);
      return;
    }
    const pr = await fetchTenantPayPeriods({
      companyId: cid,
      year: co.currentYear ?? undefined,
      size: 50,
      page: 0,
    });
    if (!pr.ok) {
      setPayPeriod(null);
      setResolutionHint("notFound");
      return;
    }
    const resolved = resolveActivePayPeriod(co, pr.items);
    if (resolved.kind === "found") {
      setPayPeriod(resolved.payPeriod);
      setResolutionHint(null);
    } else if (resolved.kind === "incomplete") {
      setPayPeriod(null);
      setResolutionHint("incomplete");
    } else if (resolved.kind === "notFound") {
      setPayPeriod(null);
      setResolutionHint("notFound");
    } else {
      setPayPeriod(null);
      setResolutionHint("outOfRange");
    }
  }, [companies]);

  const reloadRuns = useCallback(async (periodId: string) => {
    const r = await fetchTenantPayPeriodRuns(periodId, { size: 20 });
    if (r.ok) setRuns(r.items);
  }, []);

  const reloadEmployees = useCallback(async (cid: string) => {
    const r = await fetchTenantEmployees({ companyId: cid, page: 0, size: 500, active: true });
    if (!r.ok) {
      setEmployees([]);
      return;
    }
    setEmployees(r.items);
    setSelectedEmpIds(new Set(r.items.map((e) => e.id)));
  }, []);

  useEffect(() => {
    void (async () => {
      if (!canView) {
        setLoad("forbidden");
        return;
      }
      const cr = await fetchTenantCompanies({ size: 100 });
      if (!cr.ok) {
        setLoad("error");
        return;
      }
      setCompanies(cr.items);
      const initial =
        primaryCompanyId && cr.items.some((c) => c.id === primaryCompanyId)
          ? primaryCompanyId
          : cr.items.length === 1
            ? cr.items[0]!.id
            : "";
      setCompanyId(initial);
      setLoad("ready");
    })();
  }, [canView, primaryCompanyId]);

  useEffect(() => {
    if (load !== "ready" || !companyId) return;
    void reloadContext(companyId);
    void reloadEmployees(companyId);
    setMaterializeResult(null);
    setFormulaPreview(null);
    setPreviewErr(null);
    setFinalizeResult(null);
    setResultLines([]);
  }, [load, companyId, reloadContext, reloadEmployees]);

  useEffect(() => {
    if (!payPeriod) return;
    void reloadRuns(payPeriod.id);
  }, [payPeriod, reloadRuns]);

  const latestFinalRun = useMemo(
    () => runs.filter((r) => r.runType === "FINAL").sort((a, b) => b.runNumber - a.runNumber)[0] ?? null,
    [runs],
  );

  const prepareDone = materializeResult != null && materializeResult.created + materializeResult.updated > 0;
  const previewDone = formulaPreview != null && formulaPreview.items.length > 0;
  const runDone = finalizeResult != null;
  const reviewDone = resultLines.length > 0;

  const activeStep: WorkflowStepId = runDone ? "review" : previewDone ? "run" : prepareDone ? "preview" : "prepare";

  const totalNetPreview = useMemo(() => {
    if (!formulaPreview) return null;
    return Object.values(formulaPreview.employeeNetPay).reduce((s, v) => s + Number(v), 0);
  }, [formulaPreview]);

  const employeeName = useCallback(
    (id: string) => {
      const e = employees.find((x) => x.id === id);
      return e ? `${e.firstName} ${e.lastName}` : id;
    },
    [employees],
  );

  async function handleMaterialize() {
    if (!payPeriod || !companyId) return;
    setMaterializeBusy(true);
    try {
      const result = await materializeTenantPayrollInputs(payPeriod.id, companyId);
      setMaterializeResult(result);
      showToast(t("runPayroll.toast.materialized"));
    } catch {
      showToast(t("runPayroll.toast.materializeFailed"));
    } finally {
      setMaterializeBusy(false);
    }
  }

  async function handlePreview() {
    if (!payPeriod) return;
    const ids = [...selectedEmpIds];
    if (ids.length === 0) {
      setPreviewErr(t("runPayroll.preview.emptySelection"));
      return;
    }
    setPreviewBusy(true);
    setPreviewErr(null);
    const r = await postTenantPayPeriodFormulaPreview(payPeriod.id, { employeeIds: ids });
    if (!r.ok) {
      setPreviewErr(t("runPayroll.preview.failed"));
      setPreviewBusy(false);
      return;
    }
    setFormulaPreview(r.result);
    setPreviewBusy(false);
  }

  async function handleFinalize() {
    if (!payPeriod || !companyId) return;
    setRunBusy(true);
    try {
      let run = latestFinalRun;
      if (!run) {
        run = await createTenantPayPeriodRun({ payPeriodId: payPeriod.id, runType: "FINAL" });
        await reloadRuns(payPeriod.id);
      }
      const ids = [...selectedEmpIds];
      const result = await finalizeTenantPayPeriodRun(payPeriod.id, run.id, {
        employeeIds: ids.length > 0 ? ids : undefined,
        materializeInputs: !prepareDone,
      });
      setFinalizeResult(result);
      const lines = await fetchTenantPayrollResultLines(run.id);
      if (lines.ok) setResultLines(lines.items);
      if (result.calendarAdvance?.advanced) {
        showToast(
          t("runPayroll.toast.periodAdvanced")
            .replace("{period}", String(result.calendarAdvance.currentPeriod ?? ""))
            .replace("{year}", String(result.calendarAdvance.currentYear ?? "")),
        );
        if (companyId) await reloadContext(companyId);
      } else {
        showToast(t("runPayroll.toast.finalized"));
      }
      await reloadRuns(payPeriod.id);
    } catch {
      showToast(t("runPayroll.toast.finalizeFailed"));
    } finally {
      setRunBusy(false);
      setFinalizeConfirmOpen(false);
    }
  }

  async function handleGeneratePeriods() {
    if (!companyId) return;
    setGenerateBusy(true);
    try {
      const created = await generateTenantCompanyPayPeriods(companyId, { yearsAhead: 2 });
      showToast(`${t("runPayroll.toast.periodsGenerated")} (${created.created})`);
      await reloadContext(companyId);
    } catch {
      showToast(t("runPayroll.toast.periodsGenerateFailed"));
    } finally {
      setGenerateBusy(false);
    }
  }

  if (hasCompany === false) {
    return (
      <div className="mx-auto max-w-5xl">
        <NoCompanyEmptyState
          title={t("runPayroll.empty.noCompanyTitle")}
          body={t("runPayroll.empty.noCompanyBody")}
          returnTo="/app/run-payroll"
          showViewCompanies={me.privileges.includes("COMPANY_VIEW") || me.privileges.includes("COMPANY_MANAGE")}
        />
      </div>
    );
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("runPayroll.title")}</h1>
        <p className="text-sm text-muted">{t("runPayroll.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return <p className="text-sm text-muted">{t("runPayroll.state.loading")}</p>;
  }

  if (load === "error") {
    return <p className="text-sm text-destructive">{t("runPayroll.error.load")}</p>;
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6" data-testid="run-payroll-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold text-foreground">{t("runPayroll.title")}</h1>
          <p className="mt-1 text-sm text-muted">{t("runPayroll.subtitle")}</p>
        </div>
        {companies.length > 1 && (
          <label className="flex items-center gap-2 text-sm text-muted">
            {t("runPayroll.label.company")}
            <select
              className="rounded-md border border-border bg-surface px-2 py-1.5 text-foreground"
              value={companyId}
              onChange={(e) => setCompanyId(e.target.value)}
            >
              <option value="">—</option>
              {companies.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {!companyId && (
        <p className="rounded-lg border border-dashed border-border bg-surface-alt px-4 py-6 text-sm text-muted">
          {t("runPayroll.hint.selectCompany")}
        </p>
      )}

      {company && (
        <section className="rounded-lg border border-primary/25 bg-primary/5 p-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wide text-primary">{t("runPayroll.activePeriod.label")}</p>
          {payPeriod ? (
            <div className="mt-2 flex flex-wrap items-baseline gap-x-4 gap-y-2">
              <p className="text-xl font-semibold text-foreground">
                {t("runPayroll.activePeriod.periodLine")
                  .replace("{period}", String(company.currentPeriod ?? "—"))
                  .replace("{year}", String(company.currentYear ?? "—"))}
              </p>
              <p className="text-sm text-muted">
                {formatUserFacingDate(payPeriod.startDate, me.dateFormat)} →{" "}
                {formatUserFacingDate(payPeriod.endDate, me.dateFormat)}
              </p>
              <span className={statusBadgeClass(payPeriod.status)}>
                {t(`payPeriods.status.${payPeriod.status.toLowerCase()}`)}
              </span>
              <p className="w-full text-xs text-muted">
                {t("runPayroll.activePeriod.companyCalendar").replace("{name}", company.name)}
              </p>
            </div>
          ) : (
            <div className="mt-2 space-y-2">
              <p className="text-sm text-foreground">
                {resolutionHint === "incomplete"
                  ? t("runPayroll.activePeriod.incomplete")
                  : resolutionHint === "outOfRange"
                    ? t("runPayroll.activePeriod.outOfRange")
                    : t("runPayroll.activePeriod.notFound")}
              </p>
              {canGenerate && resolutionHint === "notFound" && (
                <button
                  type="button"
                  onClick={() => void handleGeneratePeriods()}
                  disabled={generateBusy}
                  className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40"
                >
                  {generateBusy ? "…" : t("runPayroll.action.generatePeriods")}
                </button>
              )}
              <p className="text-xs text-muted">
                <Link href={`/app/companies/${company.id}/edit`} className="text-primary underline-offset-4 hover:underline">
                  {t("runPayroll.link.editCompany")}
                </Link>
                {" · "}
                <Link href="/app/pay-periods" className="text-primary underline-offset-4 hover:underline">
                  {t("runPayroll.link.payPeriods")}
                </Link>
              </p>
            </div>
          )}
        </section>
      )}

      {payPeriod && (
        <>
          <nav className="flex flex-wrap gap-4 rounded-lg border border-border bg-surface px-4 py-3" aria-label={t("runPayroll.workflow.label")}>
            <StepIndicator index={1} title={t("runPayroll.step.prepare")} done={prepareDone} active={activeStep === "prepare"} />
            <StepIndicator index={2} title={t("runPayroll.step.preview")} done={previewDone} active={activeStep === "preview"} />
            <StepIndicator index={3} title={t("runPayroll.step.run")} done={runDone} active={activeStep === "run"} />
            <StepIndicator index={4} title={t("runPayroll.step.review")} done={reviewDone} active={activeStep === "review"} />
          </nav>

          {/* Step 1 — Prepare */}
          <section className="rounded-lg border border-border bg-surface p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">{t("runPayroll.step.prepare")}</h2>
            <p className="mt-1 text-xs text-muted">{t("runPayroll.prepare.intro")}</p>
            <div className="mt-4 flex flex-wrap gap-2">
              {canMaterialize && (
                <button
                  type="button"
                  onClick={() => void handleMaterialize()}
                  disabled={materializeBusy || payPeriod.status === "CLOSED"}
                  className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40"
                >
                  {materializeBusy ? "…" : t("runPayroll.action.materialize")}
                </button>
              )}
              <Link
                href="/app/employee-payroll-inputs"
                className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-foreground hover:bg-surface-alt"
              >
                {t("runPayroll.link.employeeInputs")}
              </Link>
            </div>
            {materializeResult && (
              <p className="mt-3 text-xs text-muted">
                {t("runPayroll.prepare.summary")
                  .replace("{created}", String(materializeResult.created))
                  .replace("{updated}", String(materializeResult.updated))
                  .replace("{skipped}", String(materializeResult.skippedManualOverride))}
              </p>
            )}
            {payPeriod.status === "CLOSED" && (
              <p className="mt-2 text-xs text-destructive">{t("runPayroll.hint.periodClosed")}</p>
            )}
          </section>

          {/* Step 2 — Preview */}
          <section className="rounded-lg border border-border bg-surface p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">{t("runPayroll.step.preview")}</h2>
            <p className="mt-1 text-xs text-muted">{t("runPayroll.preview.intro")}</p>
            <div className="mt-3 flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => setSelectedEmpIds(new Set(employees.map((e) => e.id)))}
                disabled={employees.length === 0}
                className="rounded border border-border px-2 py-1 text-xs hover:bg-surface-alt disabled:opacity-40"
              >
                {t("payPeriods.formulaPreview.selectAll")}
              </button>
              <button
                type="button"
                onClick={() => setSelectedEmpIds(new Set())}
                disabled={employees.length === 0}
                className="rounded border border-border px-2 py-1 text-xs hover:bg-surface-alt disabled:opacity-40"
              >
                {t("payPeriods.formulaPreview.selectNone")}
              </button>
              <button
                type="button"
                onClick={() => void handlePreview()}
                disabled={previewBusy || selectedEmpIds.size === 0}
                className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40"
              >
                {previewBusy ? "…" : t("runPayroll.action.preview")}
              </button>
            </div>
            {employees.length > 0 && (
              <div className="mt-3 max-h-36 space-y-1 overflow-y-auto rounded border border-border bg-surface-alt p-2 text-xs">
                {employees.map((emp) => (
                  <label key={emp.id} className="flex cursor-pointer items-center gap-2 py-0.5">
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
                    <span>
                      {emp.firstName} {emp.lastName}
                    </span>
                  </label>
                ))}
              </div>
            )}
            {previewErr && <p className="mt-2 text-xs text-destructive">{previewErr}</p>}
            {formulaPreview && (
              <div className="mt-4 space-y-3">
                {totalNetPreview != null && (
                  <p className="text-sm font-medium text-foreground">
                    {t("runPayroll.preview.totalNet")}: {formatMoney(totalNetPreview, company?.currency)}
                  </p>
                )}
                <div className="overflow-x-auto rounded border border-border">
                  <table className="min-w-full divide-y divide-border text-xs">
                    <thead className="bg-surface-alt">
                      <tr>
                        <th className="px-2 py-2 text-left font-medium text-muted">{t("payPeriods.formulaPreview.col.employee")}</th>
                        <th className="px-2 py-2 text-right font-medium text-muted">{t("payPeriods.formulaPreview.col.netPay")}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                      {Object.entries(formulaPreview.employeeNetPay).map(([empId, net]) => (
                        <tr key={empId}>
                          <td className="px-2 py-1.5 text-foreground">{employeeName(empId)}</td>
                          <td className="px-2 py-1.5 text-right font-mono text-foreground">
                            {formatMoney(Number(net), company?.currency)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </section>

          {/* Step 3 — Run */}
          <section className="rounded-lg border border-border bg-surface p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">{t("runPayroll.step.run")}</h2>
            <p className="mt-1 text-xs text-muted">{t("runPayroll.run.intro")}</p>
            {runs.length > 0 && (
              <p className="mt-2 text-xs text-muted">
                {t("runPayroll.run.existingRuns").replace("{count}", String(runs.length))}
                {latestFinalRun ? ` · ${t("payPeriodRuns.runType.final")} #${latestFinalRun.runNumber}` : ""}
              </p>
            )}
            {canRunManage && (
              <button
                type="button"
                onClick={() => setFinalizeConfirmOpen(true)}
                disabled={runBusy || payPeriod.status === "CLOSED" || selectedEmpIds.size === 0}
                className="mt-4 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40"
              >
                {runBusy ? "…" : t("runPayroll.action.finalize")}
              </button>
            )}
            {!canRunManage && (
              <p className="mt-2 text-xs text-muted">{t("runPayroll.hint.noRunPrivilege")}</p>
            )}
          </section>

          {/* Step 4 — Review */}
          {(finalizeResult || resultLines.length > 0) && (
            <section className="rounded-lg border border-success/30 bg-success/5 p-5 shadow-sm">
              <h2 className="text-sm font-semibold text-success">{t("runPayroll.step.review")}</h2>
              {finalizeResult && (
                <p className="mt-2 text-sm text-foreground">
                  {t("runPayroll.review.summary")
                    .replace("{employees}", String(finalizeResult.employeeCount))
                    .replace("{lines}", String(finalizeResult.linesCreated))
                    .replace("{postings}", String(finalizeResult.postingsCreated))}
                </p>
              )}
              {resultLines.length > 0 && (
                <div className="mt-4 overflow-x-auto rounded border border-border bg-surface">
                  <table className="min-w-full divide-y divide-border text-xs">
                    <thead className="bg-surface-alt">
                      <tr>
                        <th className="px-2 py-2 text-left font-medium text-muted">{t("payPeriods.formulaPreview.col.employee")}</th>
                        <th className="px-2 py-2 text-left font-medium text-muted">{t("runPayroll.review.col.phase")}</th>
                        <th className="px-2 py-2 text-right font-medium text-muted">{t("payPeriods.formulaPreview.col.amount")}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                      {resultLines.slice(0, 80).map((line) => (
                        <tr key={line.id}>
                          <td className="px-2 py-1.5 text-foreground">{employeeName(line.employeeId)}</td>
                          <td className="px-2 py-1.5 text-muted">{line.phase}</td>
                          <td className="px-2 py-1.5 text-right font-mono text-foreground">
                            {formatMoney(line.roundedAmount, company?.currency)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {resultLines.length > 80 && (
                    <p className="px-2 py-2 text-xs text-muted">{t("runPayroll.review.truncated")}</p>
                  )}
                </div>
              )}
            </section>
          )}

          <footer className="flex flex-wrap gap-4 border-t border-border pt-4 text-sm">
            <Link href={`/app/pay-periods/${payPeriod.id}/edit`} className="font-medium text-primary underline-offset-4 hover:underline">
              {t("runPayroll.link.periodDetails")}
            </Link>
            <Link href="/app/pay-periods" className="font-medium text-primary underline-offset-4 hover:underline">
              {t("runPayroll.link.payPeriods")}
            </Link>
          </footer>
        </>
      )}

      <ConfirmDialog
        open={finalizeConfirmOpen}
        title={t("runPayroll.confirm.title")}
        description={t("runPayroll.confirm.body")
          .replace("{count}", String(selectedEmpIds.size))
          .replace("{period}", String(company?.currentPeriod ?? ""))
          .replace("{year}", String(company?.currentYear ?? ""))}
        confirmLabel={t("runPayroll.action.finalize")}
        cancelLabel={t("payPeriods.action.cancel")}
        busy={runBusy}
        onConfirm={() => void handleFinalize()}
        onCancel={() => setFinalizeConfirmOpen(false)}
      />
    </div>
  );
}
