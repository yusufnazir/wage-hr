"use client";

import {
  useCallback,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type Dispatch,
  type FormEvent,
  type KeyboardEvent,
  type SetStateAction,
} from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { PlatformDateInput } from "@/components/ui/PlatformDateInput";
import { showToast } from "@/components/ui/Toast";
import { formatUserFacingDate } from "@/lib/user-date-format";
import {
  createTenantPayrollStandingInstruction,
  fetchTenantCompanies,
  fetchTenantEmployees,
  fetchTenantPayPeriods,
  fetchTenantPayrollStandingInstructions,
  fetchTenantWageComponentTransactions,
  fetchTenantWageComponents,
  postTenantPayPeriodFormulaPreview,
  putTenantPayrollStandingInstruction,
  type TenantCompanyItem,
  type TenantEmployeeItem,
  type TenantFormulaPreviewLine,
  type TenantPayPeriodItem,
  type TenantPayrollStandingInstructionItem,
  type TenantWageComponentItem,
  type TenantWageComponentTransactionItem,
} from "@/lib/api";
import { resolveActivePayPeriod } from "@/lib/pay-period-calendar";
import { resolveTenantWageComponentIdForPreviewLine } from "@/lib/statutory-component-display";
import { downloadTextFile, payrollCalculationLogFilename } from "@/lib/payroll-calculation-log";
import {
  applyArt17AttributionToCalcDisplay,
  resolveArt17AttributionPeriods,
  usesArt17AttributionFactor,
  usesChildAllowanceQuantityFactor,
} from "@/lib/suriname-art17-display";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";
type ListFilter = "active" | "inactive";

type StandingDraft = {
  effectiveFrom: string;
  effectiveTo: string;
  factor: string;
  amount: string;
  factorOverride: boolean;
  amountOverride: boolean;
  remarks: string;
  active: boolean;
};

const inputCls =
  "w-full rounded border border-border bg-background px-3 py-1.5 text-sm text-foreground focus:border-primary focus:outline-none";
const sectionCls = "rounded-md border border-border bg-surface p-4";
const thCls = "border-b border-border py-2 pr-3 text-left text-xs font-medium uppercase tracking-wide text-muted";
const tdCls = "border-b border-border py-2 pr-3 align-top text-sm";

function formatFactor(s: TenantPayrollStandingInstructionItem): string {
  if (s.quantity != null) return String(s.quantity);
  if (s.rate != null && s.quantity != null) return `${s.quantity} × ${s.rate}`;
  return "—";
}

function formatAmount(s: TenantPayrollStandingInstructionItem): string {
  return s.amount != null ? String(s.amount) : "—";
}

type ComponentCalcDisplay = {
  factor: string | null;
  amount: string | null;
};

function formatCalcNumber(value: number | null | undefined): string | null {
  if (value == null || !Number.isFinite(value)) return null;
  return String(value);
}

function formatCalcFactor(quantity: number | null, rate: number | null): string | null {
  if (quantity != null && rate != null) return `${quantity} × ${rate}`;
  if (quantity != null) return String(quantity);
  if (rate != null) return String(rate);
  return null;
}

function buildCalcByComponentFromPreviewAndTransactions(
  previewItems: TenantFormulaPreviewLine[],
  txs: TenantWageComponentTransactionItem[],
  wageComponents: TenantWageComponentItem[],
  wageComponentById: Map<string, TenantWageComponentItem>,
): Map<string, ComponentCalcDisplay> {
  const byComponent = new Map<string, ComponentCalcDisplay>();
  for (const line of previewItems) {
    const tenantComponentId = resolveTenantWageComponentIdForPreviewLine(line, wageComponents);
    if (!tenantComponentId) continue;
    const existing = byComponent.get(tenantComponentId) ?? { factor: null, amount: null };
    existing.amount = formatCalcNumber(line.evaluatedAmount);
    byComponent.set(tenantComponentId, existing);
  }
  for (const tx of txs) {
    const wc = wageComponentById.get(tx.tenantWageComponentId);
    if (usesArt17AttributionFactor(wc?.countryRuleKey)) continue;
    const existing = byComponent.get(tx.tenantWageComponentId) ?? { factor: null, amount: null };
    existing.factor = formatCalcFactor(tx.quantity, tx.rate);
    if (existing.amount == null && tx.amount != null) {
      existing.amount = formatCalcNumber(tx.amount);
    }
    byComponent.set(tx.tenantWageComponentId, existing);
  }
  return byComponent;
}

function buildCalcByComponentFromTransactionsOnly(
  txs: TenantWageComponentTransactionItem[],
  wageComponentById: Map<string, TenantWageComponentItem>,
): Map<string, ComponentCalcDisplay> {
  const byComponent = new Map<string, ComponentCalcDisplay>();
  for (const tx of txs) {
    const wc = wageComponentById.get(tx.tenantWageComponentId);
    if (usesArt17AttributionFactor(wc?.countryRuleKey)) continue;
    const factor = formatCalcFactor(tx.quantity, tx.rate);
    const amount = tx.amount != null ? formatCalcNumber(tx.amount) : null;
    if (factor == null && amount == null) continue;
    byComponent.set(tx.tenantWageComponentId, { factor, amount });
  }
  return byComponent;
}

function displayFactor(
  s: TenantPayrollStandingInstructionItem,
  calc: Map<string, ComponentCalcDisplay> | null,
  wageComponentById: Map<string, TenantWageComponentItem>,
  art17Periods: number | null,
): string {
  if (s.factorOverride) return formatFactor(s);
  const c = calc?.get(s.tenantWageComponentId);
  if (c?.factor != null) return c.factor;
  const wc = wageComponentById.get(s.tenantWageComponentId);
  if (art17Periods != null && usesArt17AttributionFactor(wc?.countryRuleKey)) {
    return String(art17Periods);
  }
  return formatFactor(s);
}

function displayAmount(
  s: TenantPayrollStandingInstructionItem,
  calc: Map<string, ComponentCalcDisplay> | null,
): string {
  if (s.amountOverride) return formatAmount(s);
  const c = calc?.get(s.tenantWageComponentId);
  if (c?.amount != null) return c.amount;
  return formatAmount(s);
}

function isFactorCalcShown(
  s: TenantPayrollStandingInstructionItem,
  calc: Map<string, ComponentCalcDisplay> | null,
  wageComponentById: Map<string, TenantWageComponentItem>,
  art17Periods: number | null,
): boolean {
  if (s.factorOverride) return false;
  if (calc?.get(s.tenantWageComponentId)?.factor != null) return true;
  const wc = wageComponentById.get(s.tenantWageComponentId);
  return art17Periods != null && usesArt17AttributionFactor(wc?.countryRuleKey);
}

function isAmountCalcShown(
  s: TenantPayrollStandingInstructionItem,
  calc: Map<string, ComponentCalcDisplay> | null,
): boolean {
  if (s.amountOverride || !calc?.has(s.tenantWageComponentId)) return false;
  return calc.get(s.tenantWageComponentId)?.amount != null;
}

function CalculationProgressBar({ label }: { label: string }) {
  return (
    <div className="mb-3 space-y-1.5" role="status" aria-live="polite" aria-busy="true">
      <p className="text-xs font-medium text-foreground">{label}</p>
      <div
        className="h-1 w-full overflow-hidden rounded-full bg-border"
        role="progressbar"
        aria-valuetext={label}
      >
        <div className="h-full w-2/5 rounded-full bg-primary animate-progress-indeterminate" />
      </div>
    </div>
  );
}

function standingDraftFromRow(
  s: TenantPayrollStandingInstructionItem,
  wageComponent: TenantWageComponentItem | undefined,
  art17Periods: number | null,
): StandingDraft {
  let factor = s.quantity != null ? String(s.quantity) : "";
  if (!factor && art17Periods != null && usesArt17AttributionFactor(wageComponent?.countryRuleKey)) {
    factor = String(art17Periods);
  }
  return {
    effectiveFrom: s.effectiveFrom,
    effectiveTo: s.effectiveTo ?? "",
    factor,
    amount: s.amount != null ? String(s.amount) : "",
    factorOverride: s.factorOverride,
    amountOverride: s.amountOverride,
    remarks: s.remarks ?? "",
    active: s.active,
  };
}

function emptyStandingDraft(active: boolean): StandingDraft {
  return {
    effectiveFrom: new Date().toISOString().slice(0, 10),
    effectiveTo: "",
    factor: "",
    amount: "",
    factorOverride: false,
    amountOverride: false,
    remarks: "",
    active,
  };
}

function standingDraftMatchesRow(s: TenantPayrollStandingInstructionItem, d: StandingDraft): boolean {
  const to = d.effectiveTo.trim() || null;
  const factor = d.factorOverride && d.factor.trim() ? Number(d.factor) : null;
  const amount = d.amountOverride && d.amount.trim() ? Number(d.amount) : null;
  return (
    d.effectiveFrom === s.effectiveFrom &&
    to === s.effectiveTo &&
    d.factorOverride === s.factorOverride &&
    d.amountOverride === s.amountOverride &&
    d.active === s.active &&
    (d.remarks.trim() || null) === (s.remarks ?? null) &&
    factor === s.quantity &&
    amount === s.amount
  );
}

function parseOptionalNumber(raw: string, override: boolean): number | null {
  if (!override) return null;
  const t = raw.trim();
  if (!t) return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : NaN;
}

function formatEffectiveRange(
  effectiveFrom: string,
  effectiveTo: string | null,
  dateFormat: string,
): string {
  const from = formatUserFacingDate(effectiveFrom, dateFormat);
  if (!effectiveTo) {
    return from;
  }
  return `${from} → ${formatUserFacingDate(effectiveTo, dateFormat)}`;
}

function StandingInstructionFields({
  draft,
  setDraft,
  busy,
  t,
  showActiveToggle,
  dateFormat,
  countryRuleKey,
  art17AttributionPeriods,
}: {
  draft: StandingDraft;
  setDraft: Dispatch<SetStateAction<StandingDraft | null>>;
  busy: boolean;
  t: (key: string) => string;
  showActiveToggle: boolean;
  dateFormat: string;
  countryRuleKey: string | null;
  art17AttributionPeriods: number | null;
}) {
  const factorReadOnly = !draft.factorOverride;
  const amountReadOnly = !draft.amountOverride;
  const art17Factor =
    art17AttributionPeriods != null && usesArt17AttributionFactor(countryRuleKey);
  const childAllowanceFactor = usesChildAllowanceQuantityFactor(countryRuleKey);
  const factorPlaceholder = factorReadOnly
    ? art17Factor
      ? t("employeePayrollInputs.hint.art17Periods").replace("{n}", String(art17AttributionPeriods))
      : childAllowanceFactor
        ? t("employeePayrollInputs.hint.childAllowanceChildren")
        : t("employeePayrollInputs.hint.systemCalculated")
    : childAllowanceFactor
      ? t("employeePayrollInputs.hint.childAllowanceChildrenEdit")
      : undefined;
  const factorValue =
    factorReadOnly && art17Factor && art17AttributionPeriods != null
      ? draft.factor || String(art17AttributionPeriods)
      : draft.factor;

  return (
    <>
      <label className="block text-sm">
        <span className="mb-1 block text-foreground">{t("employeePayrollInputs.label.effectiveFrom")}</span>
        <PlatformDateInput
          value={draft.effectiveFrom}
          dateFormat={dateFormat}
          disabled={busy}
          onChange={(value) => setDraft((d) => (d ? { ...d, effectiveFrom: value } : d))}
        />
      </label>
      <label className="block text-sm">
        <span className="mb-1 block text-foreground">{t("employeePayrollInputs.label.effectiveTo")}</span>
        <PlatformDateInput
          value={draft.effectiveTo}
          dateFormat={dateFormat}
          disabled={busy}
          onChange={(value) => setDraft((d) => (d ? { ...d, effectiveTo: value } : d))}
        />
      </label>
      <div className="space-y-2">
        <label className="flex items-center gap-2 text-sm text-foreground">
          <input
            type="checkbox"
            checked={draft.factorOverride}
            disabled={busy}
            onChange={(e) => setDraft((d) => (d ? { ...d, factorOverride: e.target.checked } : d))}
          />
          {t("employeePayrollInputs.label.overrideFactor")}
        </label>
        <input
          type="number"
          step="0.0001"
          readOnly={factorReadOnly}
          disabled={busy}
          placeholder={factorPlaceholder}
          className={`${inputCls} font-mono ${factorReadOnly ? "cursor-default bg-surface-alt text-muted" : ""}`}
          value={factorValue}
          onChange={(e) => setDraft((d) => (d ? { ...d, factor: e.target.value } : d))}
        />
      </div>
      <div className="space-y-2">
        <label className="flex items-center gap-2 text-sm text-foreground">
          <input
            type="checkbox"
            checked={draft.amountOverride}
            disabled={busy}
            onChange={(e) => setDraft((d) => (d ? { ...d, amountOverride: e.target.checked } : d))}
          />
          {t("employeePayrollInputs.label.overrideAmount")}
        </label>
        <input
          type="number"
          step="0.0001"
          readOnly={amountReadOnly}
          disabled={busy}
          placeholder={amountReadOnly ? t("employeePayrollInputs.hint.systemCalculated") : undefined}
          className={`${inputCls} font-mono ${amountReadOnly ? "cursor-default bg-surface-alt text-muted" : ""}`}
          value={draft.amount}
          onChange={(e) => setDraft((d) => (d ? { ...d, amount: e.target.value } : d))}
        />
      </div>
      {showActiveToggle ? (
        <label className="flex items-center gap-2 text-sm text-foreground">
          <input
            type="checkbox"
            checked={draft.active}
            disabled={busy}
            onChange={(e) => setDraft((d) => (d ? { ...d, active: e.target.checked } : d))}
          />
          {t("employeePayrollInputs.col.active")}
        </label>
      ) : null}
      <label className="block text-sm">
        <span className="mb-1 block text-foreground">{t("employeePayrollInputs.label.remarks")}</span>
        <textarea
          className={`${inputCls} min-h-[72px] resize-y`}
          value={draft.remarks}
          disabled={busy}
          onChange={(e) => setDraft((d) => (d ? { ...d, remarks: e.target.value } : d))}
        />
      </label>
    </>
  );
}

function WageComponentSearchPick({
  items,
  value,
  onChange,
  disabled,
  placeholder,
  noResults,
  inputClassName,
}: {
  items: TenantWageComponentItem[];
  value: string;
  onChange: (id: string) => void;
  disabled?: boolean;
  placeholder: string;
  noResults: string;
  inputClassName: string;
}) {
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState("");
  const rootRef = useRef<HTMLDivElement>(null);

  const selected = useMemo(() => items.find((i) => i.id === value) ?? null, [items, value]);

  const filtered = useMemo(() => {
    const t = q.trim().toLowerCase();
    if (!t) return items.slice(0, 60);
    return items
      .filter(
        (i) => i.name.toLowerCase().includes(t) || i.code.toLowerCase().includes(t) || i.id === value,
      )
      .slice(0, 60);
  }, [items, q, value]);

  useEffect(() => {
    if (!open) return;
    function onDoc(e: MouseEvent) {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, [open]);

  const displayValue = open ? q : selected ? `${selected.name} (${selected.code})` : q;

  return (
    <div ref={rootRef} className="relative">
      <input
        type="text"
        role="combobox"
        aria-expanded={open}
        aria-autocomplete="list"
        disabled={disabled}
        className={inputClassName}
        placeholder={placeholder}
        value={displayValue}
        onChange={(e) => {
          setQ(e.target.value);
          setOpen(true);
          if (value) onChange("");
        }}
        onFocus={() => {
          setOpen(true);
          if (selected) setQ("");
        }}
      />
      {open && !disabled ? (
        <ul
          className="absolute z-20 mt-1 max-h-56 w-full overflow-auto rounded-md border border-border bg-background py-1 text-sm shadow-md"
          role="listbox"
        >
          {filtered.length === 0 ? (
            <li className="px-3 py-2 text-muted">{noResults}</li>
          ) : (
            filtered.map((wc) => (
              <li key={wc.id} role="presentation">
                <button
                  type="button"
                  role="option"
                  aria-selected={wc.id === value}
                  className="w-full px-3 py-1.5 text-left hover:bg-surface-alt"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => {
                    onChange(wc.id);
                    setQ("");
                    setOpen(false);
                  }}
                >
                  <span className="font-medium text-foreground">{wc.name}</span>{" "}
                  <span className="text-muted">({wc.code})</span>
                </button>
              </li>
            ))
          )}
        </ul>
      ) : null}
    </div>
  );
}

export type EmployeePayrollInputsPanelProps = {
  fixedCompanyId?: string;
  fixedEmployeeId?: string;
};

export function EmployeePayrollInputsPanel({ fixedCompanyId, fixedEmployeeId }: EmployeePayrollInputsPanelProps) {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);
  const detailPanelId = useId();

  const embedded = Boolean(fixedCompanyId && fixedEmployeeId);

  const [load, setLoad] = useState<LoadState>("loading");
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [selectedCompanyId, setSelectedCompanyId] = useState("");
  const [employees, setEmployees] = useState<TenantEmployeeItem[]>([]);
  const [selectedEmployeeId, setSelectedEmployeeId] = useState("");
  const [standing, setStanding] = useState<TenantPayrollStandingInstructionItem[]>([]);
  const [wageComponents, setWageComponents] = useState<TenantWageComponentItem[]>([]);

  const [listFilter, setListFilter] = useState<ListFilter>("active");
  const [selectedStandingId, setSelectedStandingId] = useState<string | null>(null);
  const [standingCreateMode, setStandingCreateMode] = useState(false);
  const [standingDraft, setStandingDraft] = useState<StandingDraft | null>(null);
  const [createComponentId, setCreateComponentId] = useState("");
  const [standingFormError, setStandingFormError] = useState<string | null>(null);

  const [busy, setBusy] = useState(false);
  const [calcBusy, setCalcBusy] = useState(false);
  const [calcError, setCalcError] = useState<string | null>(null);
  const [calcSummary, setCalcSummary] = useState<string | null>(null);
  const [calcByComponentId, setCalcByComponentId] = useState<Map<string, ComponentCalcDisplay> | null>(null);
  const [calcTraceText, setCalcTraceText] = useState<string | null>(null);
  const [calcTraceMeta, setCalcTraceMeta] = useState<{ year: number; period: number; employeeLabel: string } | null>(
    null,
  );
  const [calcTraceOpen, setCalcTraceOpen] = useState(false);
  const [art17PreviewByEmployee, setArt17PreviewByEmployee] = useState<Record<string, number> | null>(
    null,
  );

  const canView = me.privileges.includes("EMPLOYEE_PAYROLL_STANDING_VIEW");
  const canManage = me.privileges.includes("EMPLOYEE_PAYROLL_STANDING_MANAGE");
  const canPreviewPayroll = me.privileges.includes("PAY_PERIOD_VIEW");

  const companyId = fixedCompanyId ?? selectedCompanyId;
  const employeeId = fixedEmployeeId ?? selectedEmployeeId;

  const company = useMemo(
    () => companies.find((c) => c.id === companyId) ?? null,
    [companies, companyId],
  );

  const wageComponentById = useMemo(
    () => new Map(wageComponents.map((wc) => [wc.id, wc])),
    [wageComponents],
  );

  const art17AttributionPeriods = useMemo(
    () =>
      resolveArt17AttributionPeriods(
        company?.payrollCountry,
        employeeId || null,
        art17PreviewByEmployee ?? undefined,
      ),
    [company?.payrollCountry, employeeId, art17PreviewByEmployee],
  );

  const selectedStanding = useMemo(
    () => standing.find((s) => s.id === selectedStandingId) ?? null,
    [standing, selectedStandingId],
  );

  const selectedWageComponent = useMemo(
    () =>
      selectedStanding
        ? wageComponentById.get(selectedStanding.tenantWageComponentId)
        : createComponentId
          ? wageComponentById.get(createComponentId)
          : undefined,
    [selectedStanding, createComponentId, wageComponentById],
  );
  const filteredStanding = useMemo(
    () => standing.filter((s) => (listFilter === "active" ? s.active : !s.active)),
    [standing, listFilter],
  );

  const standingDirty = useMemo(() => {
    if (!selectedStanding || !standingDraft || standingCreateMode) return false;
    return !standingDraftMatchesRow(selectedStanding, standingDraft);
  }, [selectedStanding, standingDraft, standingCreateMode]);

  useEffect(() => {
    if (fixedCompanyId) setSelectedCompanyId(fixedCompanyId);
    if (fixedEmployeeId) setSelectedEmployeeId(fixedEmployeeId);
  }, [fixedCompanyId, fixedEmployeeId]);

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
      setLoad("ready");
    })();
  }, [canView]);

  const reloadEmployees = useCallback(async (cid: string) => {
    if (!cid) {
      setEmployees([]);
      return;
    }
    if (fixedEmployeeId) return;
    const r = await fetchTenantEmployees({ companyId: cid, size: 200 });
    if (!r.ok) return;
    setEmployees(r.items);
  }, [fixedEmployeeId]);

  const reloadStanding = useCallback(async (cid: string, eid: string) => {
    if (!cid || !eid) {
      setStanding([]);
      return;
    }
    const r = await fetchTenantPayrollStandingInstructions({ companyId: cid, employeeId: eid });
    if (!r.ok) return;
    setStanding(r.items);
  }, []);

  const reloadWageComponents = useCallback(async (cid: string) => {
    if (!cid) {
      setWageComponents([]);
      return;
    }
    const r = await fetchTenantWageComponents({ companyId: cid, size: 200, active: true });
    if (!r.ok) return;
    setWageComponents(r.items);
  }, []);

  useEffect(() => {
    void reloadEmployees(companyId);
    void reloadWageComponents(companyId);
    if (!fixedEmployeeId) {
      setSelectedEmployeeId("");
    }
    setStanding([]);
    setSelectedStandingId(null);
    setStandingCreateMode(false);
    setStandingDraft(null);
  }, [companyId, fixedEmployeeId, reloadEmployees, reloadWageComponents]);

  useEffect(() => {
    void reloadStanding(companyId, employeeId);
  }, [companyId, employeeId, reloadStanding]);

  const loadPersistedPeriodCalcDisplay = useCallback(async () => {
    if (!canView || !companyId || !employeeId || !company) {
      setCalcByComponentId(null);
      setCalcSummary(null);
      return;
    }
    try {
      const pr = await fetchTenantPayPeriods({
        companyId,
        year: company.currentYear ?? undefined,
        size: 50,
        page: 0,
      });
      if (!pr.ok) return;
      const resolved = resolveActivePayPeriod(company, pr.items);
      if (resolved.kind !== "found") return;
      const payPeriod = resolved.payPeriod;
      const txR = await fetchTenantWageComponentTransactions({
        companyId,
        payPeriodId: payPeriod.id,
        employeeId,
        size: 200,
        page: 0,
      });
      if (!txR.ok) return;
      const byComponent = buildCalcByComponentFromTransactionsOnly(txR.items, wageComponentById);
      if (byComponent.size === 0) {
        setCalcByComponentId(null);
        setCalcSummary(null);
        return;
      }
      setCalcByComponentId(byComponent);
      setCalcSummary(
        t("employeePayrollInputs.calc.summaryPersisted")
          .replace("{year}", String(resolved.year))
          .replace("{period}", String(resolved.period))
          .replace("{start}", formatUserFacingDate(payPeriod.startDate, me.dateFormat))
          .replace("{end}", formatUserFacingDate(payPeriod.endDate, me.dateFormat)),
      );
    } catch {
      /* keep list usable if period transactions cannot be loaded */
    }
  }, [canView, companyId, employeeId, company, wageComponentById, me.dateFormat, t]);

  useEffect(() => {
    setCalcError(null);
    setArt17PreviewByEmployee(null);
    void loadPersistedPeriodCalcDisplay();
  }, [companyId, employeeId, loadPersistedPeriodCalcDisplay]);

  useEffect(() => {
    setSelectedStandingId(null);
    setStandingCreateMode(false);
    setStandingDraft(null);
    setStandingFormError(null);
  }, [listFilter]);

  useEffect(() => {
    if (selectedStandingId && !filteredStanding.some((s) => s.id === selectedStandingId)) {
      setSelectedStandingId(null);
      setStandingDraft(null);
    }
  }, [filteredStanding, selectedStandingId]);

  useEffect(() => {
    if (standingCreateMode) {
      setStandingDraft(emptyStandingDraft(listFilter === "active"));
      return;
    }
    if (!selectedStanding) {
      setStandingDraft(null);
      return;
    }
    setStandingDraft(
      standingDraftFromRow(
        selectedStanding,
        wageComponentById.get(selectedStanding.tenantWageComponentId),
        art17AttributionPeriods,
      ),
    );
    setStandingFormError(null);
  }, [selectedStanding, standingCreateMode, listFilter, wageComponentById, art17AttributionPeriods]);

  function beginAddStanding() {
    setStandingCreateMode(true);
    setSelectedStandingId(null);
    setCreateComponentId("");
    setStandingFormError(null);
  }

  function selectStandingRow(id: string) {
    setSelectedStandingId(id);
    setStandingCreateMode(false);
  }

  function onStandingRowKeyDown(e: KeyboardEvent<HTMLTableRowElement>, id: string) {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      selectStandingRow(id);
    }
  }

  function validateStandingDraft(draft: StandingDraft): string | null {
    if (!draft.effectiveFrom.trim()) {
      return t("employeePayrollInputs.msg.fixValidation");
    }
    const factor = parseOptionalNumber(draft.factor, draft.factorOverride);
    const amount = parseOptionalNumber(draft.amount, draft.amountOverride);
    if (Number.isNaN(factor) || Number.isNaN(amount)) {
      return t("employeePayrollInputs.msg.fixValidation");
    }
    if (draft.factorOverride && factor == null) {
      return t("employeePayrollInputs.msg.fixValidation");
    }
    if (draft.amountOverride && amount == null) {
      return t("employeePayrollInputs.msg.fixValidation");
    }
    return null;
  }

  function standingValuesFromDraft(draft: StandingDraft) {
    const factor = parseOptionalNumber(draft.factor, draft.factorOverride);
    const amount = parseOptionalNumber(draft.amount, draft.amountOverride);
    return {
      effectiveFrom: draft.effectiveFrom,
      effectiveTo: draft.effectiveTo.trim() || null,
      quantity: factor,
      rate: null as number | null,
      amount,
      recurrence: "EACH_PAY_PERIOD",
      amountOverride: draft.amountOverride,
      factorOverride: draft.factorOverride,
      remarks: draft.remarks.trim() || null,
    };
  }

  async function onCreateStanding(e: FormEvent) {
    e.preventDefault();
    if (!canManage || !companyId || !employeeId || !createComponentId || !standingDraft) return;
    const validation = validateStandingDraft(standingDraft);
    if (validation) {
      setStandingFormError(validation);
      return;
    }
    setStandingFormError(null);
    setBusy(true);
    try {
      await createTenantPayrollStandingInstruction({
        companyId,
        employeeId,
        tenantWageComponentId: createComponentId,
        ...standingValuesFromDraft(standingDraft),
      });
      showToast(t("employeePayrollInputs.toast.standingCreated"), "success");
      await reloadStanding(companyId, employeeId);
      setStandingCreateMode(false);
      setCreateComponentId("");
    } catch (err) {
      showToast(err instanceof Error ? err.message : t("employeePayrollInputs.toast.error"), "error");
    } finally {
      setBusy(false);
    }
  }

  async function onSaveStanding() {
    if (!canManage || !companyId || !employeeId || !selectedStanding || !standingDraft || standingCreateMode) return;
    const validation = validateStandingDraft(standingDraft);
    if (validation) {
      setStandingFormError(validation);
      return;
    }
    setStandingFormError(null);
    setBusy(true);
    try {
      await putTenantPayrollStandingInstruction(selectedStanding.id, {
        companyId,
        employeeId,
        tenantWageComponentId: selectedStanding.tenantWageComponentId,
        active: standingDraft.active,
        ...standingValuesFromDraft(standingDraft),
      });
      showToast(t("employeePayrollInputs.toast.standingUpdated"), "success");
      await reloadStanding(companyId, employeeId);
    } catch (err) {
      showToast(err instanceof Error ? err.message : t("employeePayrollInputs.toast.error"), "error");
    } finally {
      setBusy(false);
    }
  }

  async function runAdhocCalculation() {
    if (!canPreviewPayroll) {
      setCalcError(t("employeePayrollInputs.calc.forbidden"));
      return;
    }
    if (!companyId || !employeeId || !company) {
      return;
    }
    setCalcBusy(true);
    setCalcError(null);
    setCalcSummary(null);
    setCalcTraceText(null);
    setCalcTraceMeta(null);
    try {
      const pr = await fetchTenantPayPeriods({
        companyId,
        year: company.currentYear ?? undefined,
        size: 50,
        page: 0,
      });
      if (!pr.ok) {
        setCalcError(t("employeePayrollInputs.calc.failed"));
        return;
      }
      const resolved = resolveActivePayPeriod(company, pr.items);
      if (resolved.kind === "incomplete") {
        setCalcError(t("employeePayrollInputs.calc.calendarIncomplete"));
        return;
      }
      if (resolved.kind !== "found") {
        setCalcError(t("employeePayrollInputs.calc.noPeriod"));
        return;
      }
      const payPeriod: TenantPayPeriodItem = resolved.payPeriod;

      // Formula preview runs the payroll engine, which materializes standing inputs for the
      // requested employee(s) only. A separate full-company materialize here was redundant and slow.
      const previewR = await postTenantPayPeriodFormulaPreview(payPeriod.id, {
        employeeIds: [employeeId],
        persistToPeriodInputs: canManage,
      });
      if (!previewR.ok) {
        setCalcError(previewR.message || t("employeePayrollInputs.calc.failed"));
        return;
      }

      const txR = await fetchTenantWageComponentTransactions({
        companyId,
        payPeriodId: payPeriod.id,
        employeeId,
        size: 200,
        page: 0,
      });

      const byComponent = buildCalcByComponentFromPreviewAndTransactions(
        previewR.result.items,
        txR.ok ? txR.items : [],
        wageComponents,
        wageComponentById,
      );

      const art17N = previewR.result.employeeArt17AttributionPeriods?.[employeeId];
      if (art17N != null) {
        setArt17PreviewByEmployee(previewR.result.employeeArt17AttributionPeriods ?? {});
        applyArt17AttributionToCalcDisplay(byComponent, wageComponents, art17N);
      }

      setCalcByComponentId(byComponent);

      const traceText = previewR.result.employeeCalculationTraceText?.[employeeId] ?? null;
      setCalcTraceText(traceText);
      const emp = employees.find((e) => e.id === employeeId);
      const empLabel = emp ? `[${emp.badgeNumber ?? ""}] ${emp.firstName} ${emp.lastName}`.trim() : employeeId;
      setCalcTraceMeta({ year: resolved.year, period: resolved.period, employeeLabel: empLabel });

      const net = previewR.result.employeeNetPay[employeeId];
      const netLabel =
        net != null && Number.isFinite(net)
          ? net.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
          : "—";
      setCalcSummary(
        t("employeePayrollInputs.calc.summary")
          .replace("{year}", String(resolved.year))
          .replace("{period}", String(resolved.period))
          .replace("{start}", formatUserFacingDate(payPeriod.startDate, me.dateFormat))
          .replace("{end}", formatUserFacingDate(payPeriod.endDate, me.dateFormat))
          .replace("{net}", netLabel),
      );

      if (selectedStanding && standingDraft && !standingCreateMode) {
        const c = byComponent.get(selectedStanding.tenantWageComponentId);
        if (c) {
          setStandingDraft((d) => {
            if (!d) return d;
            return {
              ...d,
              factor: d.factorOverride ? d.factor : (c.factor ?? d.factor),
              amount: d.amountOverride ? d.amount : d.amount,
            };
          });
        }
      }

      showToast(
        canManage
          ? t("employeePayrollInputs.toast.calculated")
          : t("employeePayrollInputs.toast.calculatedPreviewOnly"),
        "success",
      );
    } catch (err) {
      setCalcError(err instanceof Error ? err.message : t("employeePayrollInputs.calc.failed"));
    } finally {
      setCalcBusy(false);
    }
  }

  function onCancelStandingDetail() {
    if (standingCreateMode) {
      setStandingCreateMode(false);
      setCreateComponentId("");
      setStandingDraft(null);
    } else if (selectedStanding) {
      setStandingDraft(
        standingDraftFromRow(
          selectedStanding,
          wageComponentById.get(selectedStanding.tenantWageComponentId),
          art17AttributionPeriods,
        ),
      );
    }
    setStandingFormError(null);
  }

  const detailRegionLabel = t("employeePayrollInputs.detail.regionLabel");

  if (load === "forbidden") {
    return (
      <div className="max-w-6xl">
        <p className="text-sm text-muted">{t("employeePayrollInputs.state.forbidden")}</p>
      </div>
    );
  }

  if (load !== "ready") {
    return (
      <div className="max-w-6xl">
        <p className="text-sm text-muted">{t("employeePayrollInputs.state.loading")}</p>
      </div>
    );
  }

  const showMasterPlaceholder = !employeeId;

  const mainSectionClass = embedded ? "rounded-md border border-border bg-surface p-3" : sectionCls;

  return (
    <div className={embedded ? "max-w-6xl space-y-2" : "max-w-6xl space-y-4"}>
      {!embedded ? (
        <header className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">{t("employeePayrollInputs.title")}</h1>
          <p className="text-sm text-muted">{t("employeePayrollInputs.subtitle")}</p>
        </header>
      ) : null}

      {!embedded ? (
        <section className={sectionCls}>
          <div className="grid gap-4 md:grid-cols-2">
            <label className="block text-sm">
              <span className="mb-1 block font-medium text-foreground">{t("employeePayrollInputs.label.company")}</span>
              <select
                className={inputCls}
                value={selectedCompanyId}
                onChange={(e) => setSelectedCompanyId(e.target.value)}
              >
                <option value="">{t("employeePayrollInputs.placeholder.company")}</option>
                {companies.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-medium text-foreground">{t("employeePayrollInputs.label.employee")}</span>
              <select
                className={inputCls}
                value={selectedEmployeeId}
                onChange={(e) => setSelectedEmployeeId(e.target.value)}
                disabled={!companyId}
              >
                <option value="">{t("employeePayrollInputs.placeholder.employee")}</option>
                {employees.map((em) => (
                  <option key={em.id} value={em.id}>
                    {em.lastName}, {em.firstName}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </section>
      ) : null}

      <section className={mainSectionClass} aria-busy={busy || calcBusy}>
        <div
          className={
            embedded
              ? "mb-2 flex flex-wrap gap-2 border-b border-border pb-2"
              : "mb-4 flex flex-wrap gap-2 border-b border-border pb-4"
          }
          role="tablist"
          aria-label={t("employeePayrollInputs.workspace.ariaLabel")}
        >
          <button
            type="button"
            role="tab"
            aria-selected={listFilter === "active"}
            className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
              listFilter === "active"
                ? "bg-primary/15 text-primary ring-1 ring-primary/40"
                : "text-muted hover:bg-surface-alt hover:text-foreground"
            }`}
            onClick={() => setListFilter("active")}
          >
            {t("employeePayrollInputs.workspace.active")}
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={listFilter === "inactive"}
            className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
              listFilter === "inactive"
                ? "bg-primary/15 text-primary ring-1 ring-primary/40"
                : "text-muted hover:bg-surface-alt hover:text-foreground"
            }`}
            onClick={() => setListFilter("inactive")}
          >
            {t("employeePayrollInputs.workspace.inactive")}
          </button>
        </div>

        {employeeId ? (
          <div className={embedded ? "mb-2 flex flex-wrap items-center gap-2" : "mb-3 flex flex-wrap items-center gap-2"}>
            {canManage ? (
              <button
                type="button"
                onClick={beginAddStanding}
                disabled={calcBusy || busy}
                className="rounded-md bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground hover:opacity-90 disabled:opacity-50"
              >
                {t("employeePayrollInputs.toolbar.addStanding")}
              </button>
            ) : null}
            {canPreviewPayroll ? (
              <button
                type="button"
                onClick={() => void runAdhocCalculation()}
                disabled={calcBusy || busy || !company}
                className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-foreground hover:bg-surface-alt disabled:opacity-50"
              >
                {calcBusy ? t("employeePayrollInputs.calc.inProgress") : t("employeePayrollInputs.action.calculate")}
              </button>
            ) : null}
            {canPreviewPayroll && calcTraceText ? (
              <>
                <button
                  type="button"
                  onClick={() => setCalcTraceOpen(true)}
                  className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-foreground hover:bg-surface-alt"
                >
                  {t("employeePayrollInputs.action.viewCalculationLog")}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    if (!calcTraceText || !calcTraceMeta) return;
                    downloadTextFile(
                      payrollCalculationLogFilename(
                        calcTraceMeta.employeeLabel,
                        calcTraceMeta.year,
                        calcTraceMeta.period,
                      ),
                      calcTraceText,
                    );
                  }}
                  className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-foreground hover:bg-surface-alt"
                >
                  {t("employeePayrollInputs.action.downloadCalculationLog")}
                </button>
              </>
            ) : null}
          </div>
        ) : null}

        {calcBusy ? <CalculationProgressBar label={t("employeePayrollInputs.calc.inProgress")} /> : null}

        {calcError ? (
          <p className="mb-2 text-sm text-destructive" role="alert">
            {calcError}
          </p>
        ) : null}
        {calcSummary && !calcBusy ? (
          <p className="mb-2 text-xs text-muted">{calcSummary}</p>
        ) : null}

        <div
          className={
            embedded
              ? "grid min-h-[min(520px,58vh)] grid-cols-1 gap-3 lg:grid-cols-[minmax(0,1fr)_minmax(280px,38%)] lg:items-stretch"
              : "grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(280px,38%)] lg:items-start"
          }
        >
          <div
            className={
              embedded
                ? "flex min-h-0 min-w-0 flex-1 flex-col rounded-md border border-border bg-background"
                : "min-w-0 rounded-md border border-border bg-background"
            }
          >
            {showMasterPlaceholder ? (
              <p className="p-4 text-sm text-muted">{t("employeePayrollInputs.hint.selectEmployee")}</p>
            ) : (
              <div className={embedded ? "min-h-0 flex-1 overflow-auto" : "overflow-x-auto"}>
                <table className="min-w-full text-left">
                  <thead>
                    <tr>
                      <th className={thCls}>{t("employeePayrollInputs.col.component")}</th>
                      <th className={thCls}>{t("employeePayrollInputs.col.effective")}</th>
                      <th
                        className={`${thCls} font-mono`}
                        title={
                          art17AttributionPeriods != null
                            ? t("employeePayrollInputs.col.factorArt17Hint")
                            : undefined
                        }
                      >
                        {t("employeePayrollInputs.col.factor")}
                      </th>
                      <th className={`${thCls} font-mono pr-3`}>{t("employeePayrollInputs.col.amount")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredStanding.map((s) => {
                      const selected = s.id === selectedStandingId && !standingCreateMode;
                      return (
                        <tr
                          key={s.id}
                          tabIndex={0}
                          aria-selected={selected}
                          aria-controls={detailPanelId}
                          className={`cursor-pointer outline-none transition-colors hover:bg-surface-alt focus-visible:ring-2 focus-visible:ring-primary/50 ${
                            selected ? "bg-primary/10" : ""
                          }`}
                          onClick={() => selectStandingRow(s.id)}
                          onKeyDown={(e) => onStandingRowKeyDown(e, s.id)}
                        >
                          <td className={tdCls}>
                            <span className="font-mono text-xs text-primary">{s.wageComponentCode}</span>{" "}
                            <span className="text-foreground">{s.wageComponentName}</span>
                          </td>
                          <td className={tdCls}>
                            {formatEffectiveRange(s.effectiveFrom, s.effectiveTo, me.dateFormat)}
                          </td>
                          <td
                            className={`${tdCls} font-mono ${
                              isFactorCalcShown(s, calcByComponentId, wageComponentById, art17AttributionPeriods)
                                ? "text-primary"
                                : ""
                            }`}
                          >
                            {displayFactor(s, calcByComponentId, wageComponentById, art17AttributionPeriods)}
                          </td>
                          <td
                            className={`${tdCls} font-mono pr-3 ${
                              isAmountCalcShown(s, calcByComponentId) ? "text-primary" : ""
                            }`}
                          >
                            {displayAmount(s, calcByComponentId)}
                          </td>
                        </tr>
                      );
                    })}
                    {filteredStanding.length === 0 ? (
                      <tr>
                        <td colSpan={4} className="px-3 py-6 text-sm text-muted">
                          {t("employeePayrollInputs.state.emptyStanding")}
                        </td>
                      </tr>
                    ) : null}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <aside
            id={detailPanelId}
            role="region"
            aria-label={detailRegionLabel}
            className={
              embedded
                ? "min-h-0 min-w-0 rounded-md border border-border bg-surface p-3 lg:h-full lg:max-h-full lg:overflow-y-auto lg:self-stretch"
                : "min-w-0 rounded-md border border-border bg-surface p-4 lg:sticky lg:top-4"
            }
          >
            {standingCreateMode && canManage && employeeId && standingDraft ? (
              <form className="space-y-3" onSubmit={(e) => void onCreateStanding(e)}>
                <h3 className="text-sm font-semibold text-primary">
                  {t("employeePayrollInputs.detail.createStandingTitle")}
                </h3>
                <label className="block text-sm">
                  <span className="mb-1 block text-foreground">{t("employeePayrollInputs.label.wageComponent")}</span>
                  <WageComponentSearchPick
                    items={wageComponents}
                    value={createComponentId}
                    onChange={setCreateComponentId}
                    disabled={busy}
                    placeholder={t("employeePayrollInputs.wcSearch.placeholder")}
                    noResults={t("employeePayrollInputs.wcSearch.noResults")}
                    inputClassName={inputCls}
                  />
                </label>
                <StandingInstructionFields
                  draft={standingDraft}
                  setDraft={setStandingDraft}
                  busy={busy}
                  t={t}
                  showActiveToggle={false}
                  dateFormat={me.dateFormat}
                  countryRuleKey={selectedWageComponent?.countryRuleKey ?? null}
                  art17AttributionPeriods={art17AttributionPeriods}
                />
                {standingFormError ? (
                  <p className="text-sm text-destructive" role="alert">
                    {standingFormError}
                  </p>
                ) : null}
                <div className="flex flex-wrap gap-2 pt-1">
                  <button
                    type="submit"
                    disabled={busy || !createComponentId}
                    className="rounded-md bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground hover:opacity-90 disabled:opacity-50"
                  >
                    {t("employeePayrollInputs.action.createStanding")}
                  </button>
                  <button
                    type="button"
                    className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-foreground hover:bg-surface-alt"
                    onClick={onCancelStandingDetail}
                  >
                    {t("employeePayrollInputs.action.cancelDetail")}
                  </button>
                </div>
              </form>
            ) : selectedStanding && standingDraft && canManage ? (
              <div className="space-y-3">
                <h3 className="text-sm font-semibold text-primary">{t("employeePayrollInputs.detail.viewStandingTitle")}</h3>
                <p className="text-xs text-muted">
                  <span className="font-mono text-primary">{selectedStanding.wageComponentCode}</span>{" "}
                  {selectedStanding.wageComponentName}
                </p>
                <StandingInstructionFields
                  draft={standingDraft}
                  setDraft={setStandingDraft}
                  busy={busy}
                  t={t}
                  showActiveToggle
                  dateFormat={me.dateFormat}
                  countryRuleKey={selectedWageComponent?.countryRuleKey ?? null}
                  art17AttributionPeriods={art17AttributionPeriods}
                />
                {standingFormError ? (
                  <p className="text-sm text-destructive" role="alert">
                    {standingFormError}
                  </p>
                ) : null}
                <div className="flex flex-wrap gap-2 pt-1">
                  <button
                    type="button"
                    disabled={busy || !standingDirty}
                    onClick={() => void onSaveStanding()}
                    className="rounded-md bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground hover:opacity-90 disabled:opacity-50"
                  >
                    {t("employeePayrollInputs.action.saveChanges")}
                  </button>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={onCancelStandingDetail}
                    className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-foreground hover:bg-surface-alt disabled:opacity-50"
                  >
                    {t("employeePayrollInputs.action.cancelDetail")}
                  </button>
                </div>
                {!standingDirty ? <p className="text-xs text-muted">{t("employeePayrollInputs.msg.noChanges")}</p> : null}
              </div>
            ) : selectedStanding && standingDraft ? (
              <div className="space-y-2 text-sm text-muted">
                <p className="font-mono text-xs text-primary">{selectedStanding.wageComponentCode}</p>
                <p>{selectedStanding.wageComponentName}</p>
              </div>
            ) : (
              <p className="text-sm text-muted">{t("employeePayrollInputs.detail.empty")}</p>
            )}
          </aside>
        </div>
      </section>

      {calcTraceOpen && calcTraceText ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="calc-trace-title"
        >
          <div className="flex max-h-[90vh] w-full max-w-4xl flex-col rounded-lg border border-border bg-surface shadow-lg">
            <header className="flex items-start justify-between gap-3 border-b border-border px-4 py-3">
              <div>
                <h2 id="calc-trace-title" className="text-base font-semibold text-foreground">
                  {t("employeePayrollInputs.calculationLog.title")}
                </h2>
                <p className="mt-1 text-xs text-muted">{t("employeePayrollInputs.calculationLog.intro")}</p>
              </div>
              <div className="flex shrink-0 gap-2">
                {calcTraceMeta ? (
                  <button
                    type="button"
                    className="rounded-md border border-border px-2 py-1 text-xs font-medium text-foreground hover:bg-surface-alt"
                    onClick={() =>
                      downloadTextFile(
                        payrollCalculationLogFilename(
                          calcTraceMeta.employeeLabel,
                          calcTraceMeta.year,
                          calcTraceMeta.period,
                        ),
                        calcTraceText,
                      )
                    }
                  >
                    {t("employeePayrollInputs.action.downloadCalculationLog")}
                  </button>
                ) : null}
                <button
                  type="button"
                  className="rounded-md border border-border px-2 py-1 text-xs font-medium text-foreground hover:bg-surface-alt"
                  onClick={() => setCalcTraceOpen(false)}
                >
                  {t("employeePayrollInputs.action.cancelDetail")}
                </button>
              </div>
            </header>
            <pre className="min-h-0 flex-1 overflow-auto whitespace-pre-wrap p-4 font-mono text-xs leading-relaxed text-foreground">
              {calcTraceText}
            </pre>
          </div>
        </div>
      ) : null}
    </div>
  );
}

