"use client";

import { useCallback, useEffect, useMemo, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { FilterChip } from "@/components/ui/FilterChip";
import { showToast } from "@/components/ui/Toast";
import {
  fetchTenantBankTemplates,
  fetchTenantEmployeePaymentHistory,
  fetchTenantEmployeePaymentOverview,
  fetchTenantPaymentLocations,
  fetchTenantPayPeriods,
  putTenantEmployeePaymentDestinations,
  type TenantBankTemplateRow,
  type TenantEmployeePayPeriodPaymentItem,
  type TenantEmployeePaymentDestinationPutItem,
  type TenantEmployeePaymentOverview,
  type TenantPaymentLocationRow,
  type TenantPayPeriodItem,
} from "@/lib/api";
import { formatUserFacingDate } from "@/lib/user-date-format";
import { navLabel } from "@/messages/nav";

type DestDraft = {
  key: string;
  channelType: "BANK" | "CASH";
  paymentLocationId: string;
  bankTemplateId: string;
  accountNumber: string;
  currency: string;
  splitType: "PERCENT" | "AMOUNT";
  splitValue: string;
};

type TabId = "current" | "history";
type Selection =
  | { kind: "destination"; key: string }
  | { kind: "disbursement"; id: string }
  | null;

const inputCls =
  "w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground shadow-sm";

let nextDraftKey = 0;
function newDraftKey(): string {
  nextDraftKey += 1;
  return `draft-${nextDraftKey}`;
}

function defaultCashLocationId(
  locations: TenantPaymentLocationRow[],
  currency: string,
): string {
  const cash = locations.filter((l) => l.paymentType === "CASH");
  const match = cash.find((l) => l.currency === currency);
  return (match ?? cash[0])?.id ?? "";
}

function emptyDraft(currency: string, paymentLocationId = ""): DestDraft {
  return {
    key: newDraftKey(),
    channelType: "CASH",
    paymentLocationId,
    bankTemplateId: "",
    accountNumber: "",
    currency,
    splitType: "PERCENT",
    splitValue: "100",
  };
}

function draftFromRow(row: TenantEmployeePaymentOverview["destinations"][0]): DestDraft {
  return {
    key: row.id,
    channelType: row.channelType as "BANK" | "CASH",
    paymentLocationId: row.paymentLocationId ?? "",
    bankTemplateId: row.bankTemplateId ?? "",
    accountNumber: row.accountNumber ?? "",
    currency: row.currency,
    splitType: row.splitType as "PERCENT" | "AMOUNT",
    splitValue: String(row.splitValue),
  };
}

function formatMoney(n: number, currency: string) {
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency, minimumFractionDigits: 2 }).format(n);
  } catch {
    return `${n.toFixed(2)} ${currency}`;
  }
}

function splitLabel(splitType: string, splitValue: number, t: (k: string) => string) {
  if (splitType === "AMOUNT") {
    return `${t("employeePayment.split.amount")}: ${splitValue}`;
  }
  return `${t("employeePayment.split.percent")}: ${splitValue}%`;
}

function periodRangeLabel(start: string, end: string, dateFormat: string) {
  if (!start || !end) return "—";
  return `${formatUserFacingDate(start, dateFormat)} – ${formatUserFacingDate(end, dateFormat)}`;
}

export type EmployeePaymentPanelProps = {
  employeeId: string;
  companyId: string;
  defaultCurrency: string;
};

export function EmployeePaymentPanel({ employeeId, companyId, defaultCurrency }: EmployeePaymentPanelProps) {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const canView = me.privileges.includes("EMPLOYEE_PAYMENT_VIEW");
  const canManage = me.privileges.includes("EMPLOYEE_PAYMENT_MANAGE");

  const [tab, setTab] = useState<TabId>("current");
  const [load, setLoad] = useState<"loading" | "ready" | "forbidden" | "error">("loading");
  const [overview, setOverview] = useState<TenantEmployeePaymentOverview | null>(null);
  const [drafts, setDrafts] = useState<DestDraft[]>([]);
  const [locations, setLocations] = useState<TenantPaymentLocationRow[]>([]);
  const [banks, setBanks] = useState<TenantBankTemplateRow[]>([]);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [selection, setSelection] = useState<Selection>(null);

  const [filterYear, setFilterYear] = useState<number | null>(null);
  const [filterPayPeriodId, setFilterPayPeriodId] = useState<string | null>(null);
  const [periodOptions, setPeriodOptions] = useState<TenantPayPeriodItem[]>([]);
  const [historyLoad, setHistoryLoad] = useState<"loading" | "ready" | "error">("loading");
  const [historyItems, setHistoryItems] = useState<TenantEmployeePayPeriodPaymentItem[]>([]);

  const reload = useCallback(async (locationRows: TenantPaymentLocationRow[] = []) => {
    const r = await fetchTenantEmployeePaymentOverview(employeeId);
    if (!r.ok) {
      setLoad(r.status === 403 ? "forbidden" : "error");
      return;
    }
    setOverview(r.item);
    const currency = defaultCurrency || "SRD";
    const nextDrafts =
      r.item.destinations.length > 0
        ? r.item.destinations.map(draftFromRow)
        : [emptyDraft(currency, defaultCashLocationId(locationRows, currency))];
    setDrafts(nextDrafts);
    setLoad("ready");
    return r.item;
  }, [employeeId, defaultCurrency]);

  const loadHistory = useCallback(async () => {
    setHistoryLoad("loading");
    const r = await fetchTenantEmployeePaymentHistory(employeeId, {
      year: filterYear,
      payPeriodId: filterPayPeriodId,
      page: 0,
      size: 100,
    });
    if (!r.ok) {
      setHistoryLoad("error");
      return;
    }
    setHistoryItems(r.items);
    setHistoryLoad("ready");
  }, [employeeId, filterYear, filterPayPeriodId]);

  const loadPeriodOptions = useCallback(
    async (year: number | null) => {
      if (!companyId || year == null) {
        setPeriodOptions([]);
        return;
      }
      const r = await fetchTenantPayPeriods({
        companyId,
        year,
        status: "CLOSED",
        size: 100,
        page: 0,
      });
      if (r.ok) {
        const sorted = [...r.items].sort((a, b) => a.endDate.localeCompare(b.endDate));
        setPeriodOptions(sorted);
      }
    },
    [companyId],
  );

  useEffect(() => {
    if (!canView) {
      setLoad("forbidden");
      return;
    }
    void (async () => {
      const [lr, br] = await Promise.all([
        fetchTenantPaymentLocations({ companyId, active: true, size: 100 }),
        fetchTenantBankTemplates({ companyId, active: true, size: 100 }),
      ]);
      if (lr.ok) setLocations(lr.items);
      if (br.ok) setBanks(br.items);
      await reload(lr.ok ? lr.items : []);
    })();
  }, [canView, companyId, reload]);

  useEffect(() => {
    if (tab !== "history") return;
    void loadHistory();
  }, [tab, loadHistory]);

  useEffect(() => {
    if (tab !== "history") return;
    void loadPeriodOptions(filterYear);
  }, [tab, filterYear, loadPeriodOptions]);

  const cashLocations = locations.filter((l) => l.paymentType === "CASH");

  const currentRecords = useMemo(() => {
    if (!overview) return [];
    const disbursements = overview.activePeriod.payments;
    if (disbursements.length > 0) {
      return disbursements.map((p) => ({ kind: "disbursement" as const, id: p.id, payment: p }));
    }
    return drafts.map((d) => ({ kind: "destination" as const, key: d.key, draft: d }));
  }, [overview, drafts]);

  const yearOptions = useMemo(() => {
    const years = new Set<number>();
    if (overview?.activePeriod.year) years.add(overview.activePeriod.year);
    for (const g of overview?.closedPeriods ?? []) {
      if (g.year) years.add(g.year);
    }
    for (const p of periodOptions) years.add(p.year);
    if (years.size === 0) years.add(new Date().getFullYear());
    return [...years].sort((a, b) => b - a);
  }, [overview, periodOptions]);

  const selectedDestination = useMemo(() => {
    if (selection?.kind !== "destination") return null;
    return drafts.find((d) => d.key === selection.key) ?? null;
  }, [selection, drafts]);

  const selectedDisbursement = useMemo(() => {
    if (selection?.kind !== "disbursement") return null;
    const fromActive = overview?.activePeriod.payments.find((p) => p.id === selection.id);
    if (fromActive) return fromActive;
    return historyItems.find((p) => p.id === selection.id) ?? null;
  }, [selection, overview, historyItems]);

  async function handleSave() {
    setBusy(true);
    setErr(null);
    try {
      const items: TenantEmployeePaymentDestinationPutItem[] = drafts.map((d, i) => ({
        channelType: d.channelType,
        paymentLocationId: d.channelType === "CASH" ? d.paymentLocationId || null : null,
        bankTemplateId: d.channelType === "BANK" ? d.bankTemplateId || null : null,
        accountNumber: d.channelType === "BANK" ? d.accountNumber.trim() || null : null,
        currency: d.currency.trim().toUpperCase(),
        splitType: d.splitType,
        splitValue: Number(d.splitValue),
        sortOrder: i,
        active: true,
      }));
      await putTenantEmployeePaymentDestinations(employeeId, items);
      showToast(t("employeePayment.toast.saved"));
      await reload();
    } catch (e) {
      setErr(e instanceof Error ? e.message : t("employeePayment.error.save"));
    } finally {
      setBusy(false);
    }
  }

  function clearHistoryFilters() {
    setFilterYear(null);
    setFilterPayPeriodId(null);
  }

  const historyFiltersActive = filterYear != null || filterPayPeriodId != null;

  if (load === "forbidden") {
    return <p className="text-sm text-muted">{t("employeePayment.error.forbidden")}</p>;
  }
  if (load === "loading") {
    return <p className="text-sm text-muted">{t("employeePayment.state.loading")}</p>;
  }
  if (load === "error" || !overview) {
    return <p className="text-sm text-destructive">{t("employeePayment.error.load")}</p>;
  }

  const active = overview.activePeriod;
  const activeRange = periodRangeLabel(active.startDate, active.endDate, me.dateFormat);
  const usingDisbursements = active.payments.length > 0;

  return (
    <div className="space-y-4">
      <div className="flex gap-1 rounded-lg border border-border bg-surface-alt p-1">
        {(["current", "history"] as const).map((tid) => (
          <button
            key={tid}
            type="button"
            onClick={() => {
              setTab(tid);
              setSelection(null);
              setErr(null);
            }}
            className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
              tab === tid ? "bg-surface text-foreground shadow-sm" : "text-muted hover:text-foreground"
            }`}
          >
            {t(`employeePayment.tab.${tid}`)}
          </button>
        ))}
      </div>

      {tab === "current" ? (
        <div className="grid gap-4 lg:grid-cols-2">
          <div className="space-y-4 min-w-0">
            <section className="rounded-lg border border-border bg-surface p-4 shadow-sm">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <h2 className="text-sm font-semibold text-foreground">{t("employeePayment.section.activePeriod")}</h2>
                {active.status && (
                  <span className="rounded-full bg-muted/20 px-2 py-0.5 text-xs font-medium text-muted">
                    {active.status}
                  </span>
                )}
              </div>
              {activeRange && <p className="mt-1 text-xs text-muted">{activeRange}</p>}
              {!usingDisbursements && (
                <p className="mt-2 text-xs text-muted">{t("employeePayment.section.setupIntro")}</p>
              )}
            </section>

            {currentRecords.length === 0 ? (
              <p className="text-sm text-muted">{t("employeePayment.state.emptyCurrent")}</p>
            ) : (
              <ul className="space-y-2">
                {currentRecords.map((rec) => {
                  if (rec.kind === "disbursement") {
                    const p = rec.payment;
                    const selected = selection?.kind === "disbursement" && selection.id === p.id;
                    const dest =
                      p.channelType === "CASH"
                        ? p.paymentLocationName ?? "—"
                        : `${p.bankName ?? "—"} · ${p.accountNumber ?? "—"}`;
                    return (
                      <li key={p.id}>
                        <button
                          type="button"
                          onClick={() => setSelection({ kind: "disbursement", id: p.id })}
                          className={`w-full rounded-lg border p-4 text-left transition-colors ${
                            selected
                              ? "border-primary bg-primary/5"
                              : "border-border bg-surface hover:border-primary/40"
                          }`}
                        >
                          <div className="flex items-center justify-between gap-2">
                            <span className="text-xs font-medium text-muted">
                              {t("employeePayment.record.disbursement")}
                            </span>
                            <span className="font-mono text-sm font-semibold text-foreground">
                              {formatMoney(p.allocatedAmount, p.currency)}
                            </span>
                          </div>
                          <p className="mt-2 text-sm text-foreground">
                            {p.channelType === "CASH"
                              ? t("employeePayment.channel.cash")
                              : t("employeePayment.channel.bank")}
                            {" · "}
                            {dest}
                          </p>
                          <p className="mt-1 text-xs text-muted">
                            {splitLabel(p.splitType, p.splitValue, t)} · {p.currency}
                          </p>
                        </button>
                      </li>
                    );
                  }
                  const d = rec.draft;
                  const selected = selection?.kind === "destination" && selection.key === d.key;
                  const dest =
                    d.channelType === "CASH"
                      ? cashLocations.find((l) => l.id === d.paymentLocationId)?.name ?? "—"
                      : `${banks.find((b) => b.id === d.bankTemplateId)?.bankName ?? "—"} · ${d.accountNumber || "—"}`;
                  return (
                    <li key={d.key}>
                      <button
                        type="button"
                        onClick={() => setSelection({ kind: "destination", key: d.key })}
                        className={`w-full rounded-lg border p-4 text-left transition-colors ${
                          selected
                            ? "border-primary bg-primary/5"
                            : "border-border bg-surface hover:border-primary/40"
                        }`}
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="text-xs font-medium text-muted">{t("employeePayment.record.setup")}</span>
                          <span className="text-xs text-muted">{d.currency}</span>
                        </div>
                        <p className="mt-2 text-sm text-foreground">
                          {d.channelType === "CASH"
                            ? t("employeePayment.channel.cash")
                            : t("employeePayment.channel.bank")}
                          {" · "}
                          {dest}
                        </p>
                        <p className="mt-1 text-xs text-muted">{splitLabel(d.splitType, Number(d.splitValue), t)}</p>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}

            {canManage && !usingDisbursements && (
              <button
                type="button"
                className="rounded border border-border px-3 py-1.5 text-sm hover:bg-surface-alt"
                onClick={() => {
                  const d = emptyDraft(defaultCurrency, defaultCashLocationId(cashLocations, defaultCurrency));
                  setDrafts((prev) => [...prev, d]);
                  setSelection({ kind: "destination", key: d.key });
                }}
              >
                {t("employeePayment.action.addDestination")}
              </button>
            )}
          </div>

          <div className="min-w-0 rounded-lg border border-border bg-surface p-4 shadow-sm lg:sticky lg:top-4 lg:self-start">
            <h3 className="text-sm font-semibold text-foreground">{t("employeePayment.section.detail")}</h3>
            {selection == null && (
              <p className="mt-3 text-sm text-muted">{t("employeePayment.state.selectRecord")}</p>
            )}
            {selection?.kind === "disbursement" && selectedDisbursement && (
              <DisbursementDetail payment={selectedDisbursement} t={t} formatMoney={formatMoney} />
            )}
            {selection?.kind === "destination" && selectedDestination && (
              <DestinationEditor
                draft={selectedDestination}
                canManage={canManage}
                busy={busy}
                err={err}
                cashLocations={cashLocations}
                banks={banks}
                t={t}
                onChange={(next) =>
                  setDrafts((prev) => prev.map((x) => (x.key === selectedDestination.key ? next : x)))
                }
                onRemove={
                  drafts.length > 1
                    ? () => {
                        setDrafts((prev) => prev.filter((x) => x.key !== selectedDestination.key));
                        setSelection(null);
                      }
                    : undefined
                }
                onSave={() => void handleSave()}
              />
            )}
            {usingDisbursements && active.payments.length === 0 && (
              <p className="mt-3 text-xs text-muted">{t("employeePayment.state.noPeriodPayments")}</p>
            )}
          </div>
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          <div className="min-w-0 space-y-4">
            <div className="flex flex-wrap items-center gap-2">
              <FilterChip<number | null>
                label={t("employeePayment.filter.year")}
                value={filterYear}
                onApply={(v) => {
                  setFilterYear(v);
                  setFilterPayPeriodId(null);
                }}
                formatValue={(y) => String(y)}
                renderInput={(draft, setDraft, apply) => (
                  <select
                    className={inputCls}
                    value={draft ?? ""}
                    onChange={(e) => {
                      const v = e.target.value ? Number(e.target.value) : null;
                      setDraft(v);
                    }}
                    onKeyDown={(e) => e.key === "Enter" && apply()}
                  >
                    <option value="">{t("employeePayment.placeholder.select")}</option>
                    {yearOptions.map((y) => (
                      <option key={y} value={y}>
                        {y}
                      </option>
                    ))}
                  </select>
                )}
              />
              <FilterChip<string | null>
                label={t("employeePayment.filter.period")}
                value={filterPayPeriodId}
                onApply={setFilterPayPeriodId}
                formatValue={(id) => {
                  if (id == null) return "";
                  const p = periodOptions.find((x) => x.id === id);
                  if (!p) return id;
                  const idx = periodOptions.findIndex((x) => x.id === id);
                  return `${idx + 1} · ${periodRangeLabel(p.startDate, p.endDate, me.dateFormat)}`;
                }}
                renderInput={(draft, setDraft, apply) => (
                  <select
                    className={inputCls}
                    value={draft ?? ""}
                    disabled={filterYear == null}
                    onChange={(e) => setDraft(e.target.value || null)}
                    onKeyDown={(e) => e.key === "Enter" && apply()}
                  >
                    <option value="">{t("employeePayment.placeholder.select")}</option>
                    {periodOptions.map((p, i) => (
                      <option key={p.id} value={p.id}>
                        {i + 1} · {periodRangeLabel(p.startDate, p.endDate, me.dateFormat)}
                      </option>
                    ))}
                  </select>
                )}
              />
              {historyFiltersActive && (
                <button
                  type="button"
                  onClick={clearHistoryFilters}
                  className="text-xs font-medium text-muted underline-offset-4 hover:text-foreground hover:underline"
                >
                  {t("employeePayment.action.clearFilters")}
                </button>
              )}
            </div>

            {historyLoad === "loading" && (
              <p className="text-sm text-muted">{t("employeePayment.state.loading")}</p>
            )}
            {historyLoad === "error" && (
              <p className="text-sm text-destructive">{t("employeePayment.error.load")}</p>
            )}
            {historyLoad === "ready" && historyItems.length === 0 && (
              <p className="text-sm text-muted">{t("employeePayment.state.emptyHistory")}</p>
            )}
            {historyLoad === "ready" && historyItems.length > 0 && (
              <div className="overflow-x-auto rounded-lg border border-border">
                <table className="min-w-full divide-y divide-border text-sm">
                  <thead className="bg-surface-alt">
                    <tr>
                      <th className="px-3 py-2 text-left font-medium text-muted">{t("employeePayment.col.year")}</th>
                      <th className="px-3 py-2 text-left font-medium text-muted">{t("employeePayment.col.period")}</th>
                      <th className="px-3 py-2 text-left font-medium text-muted">{t("employeePayment.col.channel")}</th>
                      <th className="px-3 py-2 text-left font-medium text-muted">{t("employeePayment.col.destination")}</th>
                      <th className="px-3 py-2 text-left font-medium text-muted">{t("employeePayment.col.split")}</th>
                      <th className="px-3 py-2 text-right font-medium text-muted">{t("employeePayment.col.allocated")}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border bg-surface">
                    {historyItems.map((p) => {
                      const selected = selection?.kind === "disbursement" && selection.id === p.id;
                      return (
                        <tr
                          key={p.id}
                          onClick={() => setSelection({ kind: "disbursement", id: p.id })}
                          className={`cursor-pointer transition-colors hover:bg-surface-alt ${
                            selected ? "bg-primary/5" : ""
                          }`}
                        >
                          <td className="px-3 py-2">{p.payPeriodYear}</td>
                          <td className="px-3 py-2 text-muted">
                            {periodRangeLabel(p.payPeriodStartDate, p.payPeriodEndDate, me.dateFormat)}
                          </td>
                          <td className="px-3 py-2">
                            {p.channelType === "CASH"
                              ? t("employeePayment.channel.cash")
                              : t("employeePayment.channel.bank")}
                          </td>
                          <td className="px-3 py-2 text-foreground">
                            {p.channelType === "CASH"
                              ? p.paymentLocationName ?? "—"
                              : `${p.bankName ?? "—"} · ${p.accountNumber ?? "—"}`}
                          </td>
                          <td className="px-3 py-2 text-muted">{splitLabel(p.splitType, p.splitValue, t)}</td>
                          <td className="px-3 py-2 text-right font-mono">{formatMoney(p.allocatedAmount, p.currency)}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="min-w-0 rounded-lg border border-border bg-surface p-4 shadow-sm lg:sticky lg:top-4 lg:self-start">
            <h3 className="text-sm font-semibold text-foreground">{t("employeePayment.section.detail")}</h3>
            {selection == null && (
              <p className="mt-3 text-sm text-muted">{t("employeePayment.state.selectRecord")}</p>
            )}
            {selection?.kind === "disbursement" && selectedDisbursement && (
              <DisbursementDetail payment={selectedDisbursement} t={t} formatMoney={formatMoney} />
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function DisbursementDetail({
  payment,
  t,
  formatMoney,
}: {
  payment: TenantEmployeePayPeriodPaymentItem;
  t: (key: string) => string;
  formatMoney: (n: number, c: string) => string;
}) {
  return (
    <dl className="mt-4 space-y-3 text-sm">
      <div>
        <dt className="text-xs text-muted">{t("employeePayment.col.status")}</dt>
        <dd className="font-medium text-foreground">{payment.payPeriodStatus}</dd>
      </div>
      <div>
        <dt className="text-xs text-muted">{t("employeePayment.col.channel")}</dt>
        <dd>
          {payment.channelType === "CASH" ? t("employeePayment.channel.cash") : t("employeePayment.channel.bank")}
        </dd>
      </div>
      <div>
        <dt className="text-xs text-muted">{t("employeePayment.col.destination")}</dt>
        <dd className="text-foreground">
          {payment.channelType === "CASH"
            ? payment.paymentLocationName ?? "—"
            : `${payment.bankName ?? "—"} · ${payment.accountNumber ?? "—"}`}
        </dd>
      </div>
      <div>
        <dt className="text-xs text-muted">{t("employeePayment.col.split")}</dt>
        <dd>{splitLabel(payment.splitType, payment.splitValue, t)}</dd>
      </div>
      <div>
        <dt className="text-xs text-muted">{t("employeePayment.col.allocated")}</dt>
        <dd className="font-mono font-semibold">{formatMoney(payment.allocatedAmount, payment.currency)}</dd>
      </div>
      <div>
        <dt className="text-xs text-muted">{t("employeePayment.col.period")}</dt>
        <dd className="text-muted">
          {payment.payPeriodYear} · {payment.payPeriodStartDate} – {payment.payPeriodEndDate}
        </dd>
      </div>
    </dl>
  );
}

function DestinationEditor({
  draft,
  canManage,
  busy,
  err,
  cashLocations,
  banks,
  t,
  onChange,
  onRemove,
  onSave,
}: {
  draft: DestDraft;
  canManage: boolean;
  busy: boolean;
  err: string | null;
  cashLocations: TenantPaymentLocationRow[];
  banks: TenantBankTemplateRow[];
  t: (key: string) => string;
  onChange: (next: DestDraft) => void;
  onRemove?: () => void;
  onSave: () => void;
}) {
  return (
    <div className="mt-4 space-y-3">
      <p className="text-xs text-muted">{t("employeePayment.section.setupIntro")}</p>
      <label className="block text-sm">
        <span className="mb-1 block text-xs text-muted">{t("employeePayment.label.channel")}</span>
        <select
          className={inputCls}
          value={draft.channelType}
          disabled={!canManage}
          onChange={(e) => {
            const channelType = e.target.value as "BANK" | "CASH";
            onChange({
              ...draft,
              channelType,
              paymentLocationId: "",
              bankTemplateId: "",
              accountNumber: "",
            });
          }}
        >
          <option value="BANK">{t("employeePayment.channel.bank")}</option>
          <option value="CASH">{t("employeePayment.channel.cash")}</option>
        </select>
      </label>
      <label className="block text-sm">
        <span className="mb-1 block text-xs text-muted">{t("employeePayment.label.currency")}</span>
        <input
          className={inputCls}
          maxLength={3}
          value={draft.currency}
          disabled={!canManage}
          onChange={(e) => onChange({ ...draft, currency: e.target.value.toUpperCase() })}
        />
      </label>
      {draft.channelType === "CASH" ? (
        <label className="block text-sm">
          <span className="mb-1 block text-xs text-muted">{t("employeePayment.label.cashLocation")}</span>
          <select
            className={inputCls}
            value={draft.paymentLocationId}
            disabled={!canManage}
            onChange={(e) => onChange({ ...draft, paymentLocationId: e.target.value })}
          >
            <option value="">{t("employeePayment.placeholder.select")}</option>
            {cashLocations.map((loc) => (
              <option key={loc.id} value={loc.id}>
                {loc.name} ({loc.currency})
              </option>
            ))}
          </select>
        </label>
      ) : (
        <>
          <label className="block text-sm">
            <span className="mb-1 block text-xs text-muted">{t("employeePayment.label.bank")}</span>
            <select
              className={inputCls}
              value={draft.bankTemplateId}
              disabled={!canManage}
              onChange={(e) => onChange({ ...draft, bankTemplateId: e.target.value })}
            >
              <option value="">{t("employeePayment.placeholder.select")}</option>
              {banks.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.bankName} ({b.countryCode})
                </option>
              ))}
            </select>
          </label>
          <label className="block text-sm">
            <span className="mb-1 block text-xs text-muted">{t("employeePayment.label.accountNumber")}</span>
            <input
              className={inputCls}
              value={draft.accountNumber}
              disabled={!canManage}
              onChange={(e) => onChange({ ...draft, accountNumber: e.target.value })}
            />
          </label>
        </>
      )}
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-sm">
          <span className="mb-1 block text-xs text-muted">{t("employeePayment.label.splitType")}</span>
          <select
            className={inputCls}
            value={draft.splitType}
            disabled={!canManage}
            onChange={(e) => onChange({ ...draft, splitType: e.target.value as "PERCENT" | "AMOUNT" })}
          >
            <option value="PERCENT">{t("employeePayment.split.percent")}</option>
            <option value="AMOUNT">{t("employeePayment.split.amount")}</option>
          </select>
        </label>
        <label className="block text-sm">
          <span className="mb-1 block text-xs text-muted">{t("employeePayment.label.splitValue")}</span>
          <input
            type="number"
            step="0.01"
            className={inputCls}
            value={draft.splitValue}
            disabled={!canManage}
            onChange={(e) => onChange({ ...draft, splitValue: e.target.value })}
          />
        </label>
      </div>
      {canManage && (
        <div className="flex flex-wrap gap-2 pt-2">
          {onRemove && (
            <button
              type="button"
              className="text-xs text-destructive hover:underline"
              onClick={onRemove}
            >
              {t("employeePayment.action.remove")}
            </button>
          )}
          <button
            type="button"
            disabled={busy}
            onClick={onSave}
            className="rounded bg-primary px-4 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40"
          >
            {busy ? "…" : t("employeePayment.action.save")}
          </button>
        </div>
      )}
      {err && <p className="text-sm text-destructive">{err}</p>}
    </div>
  );
}
