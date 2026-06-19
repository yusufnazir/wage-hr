"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import React, { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";

import {
  CompanyFormSections,
  UnsavedChangesDialog,
  ValidationSummary,
  focusField,
  useUnsavedChangesGuard,
  type CompanyFormState,
  type ValidationIssue,
} from "@/components/company/CompanyFormSections";
import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { EntityDocumentsTab } from "@/components/ui/EntityDocumentsTab";
import { showToast } from "@/components/ui/Toast";
import {
  createTenantPayPeriodRun,
  deleteCompanyLogo,
  fetchCountries,
  fetchTenantCompany,
  fetchTenantCurrencies,
  fetchTenantPayPeriodRuns,
  fetchTenantPayPeriods,
  patchTenantPayPeriodStatus,
  putTenantCompany,
  uploadCompanyLogo,
  type PlatformCountryRow,
  type TenantCompanyUpsertPayload,
  type TenantCurrencyItem,
  type TenantPayPeriodItem,
  type TenantPayPeriodRunItem,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

const FREQUENCIES = ["WEEKLY", "BIWEEKLY", "SEMIMONTHLY", "MONTHLY"] as const;

type LoadState = "loading" | "ready" | "forbidden" | "notFound" | "error";

function parseGmtOffset(value: string): string | null {
  if (value === "GMT" || value === "UTC") return "UTC+00:00";
  const m = value.match(/^(?:GMT|UTC)([+-])(\d{1,2})(?::?(\d{2}))?$/);
  if (!m) return null;
  const sign = m[1];
  const hours = m[2].padStart(2, "0");
  const minutes = (m[3] ?? "00").padStart(2, "0");
  return `UTC${sign}${hours}:${minutes}`;
}

function getUtcOffsetLabel(timeZone: string): string {
  const now = new Date();
  const tryWith = (timeZoneName: "longOffset" | "shortOffset"): string | null => {
    try {
      const parts = new Intl.DateTimeFormat("en-US", { timeZone, timeZoneName }).formatToParts(now);
      const zoneName = parts.find((p) => p.type === "timeZoneName")?.value;
      return zoneName ? parseGmtOffset(zoneName) : null;
    } catch {
      return null;
    }
  };
  return tryWith("longOffset") ?? tryWith("shortOffset") ?? "UTC";
}

function dayOfYear(date: Date): number {
  const start = new Date(date.getFullYear(), 0, 0);
  const diffMs = date.getTime() - start.getTime();
  return Math.floor(diffMs / 86400000);
}

function deriveCurrentPeriod(dateIso: string, frequency: string): string {
  const d = new Date(`${dateIso}T00:00:00`);
  if (Number.isNaN(d.getTime())) return "";
  if (frequency === "WEEKLY") return String(Math.ceil(dayOfYear(d) / 7));
  if (frequency === "BIWEEKLY") return String(Math.ceil(dayOfYear(d) / 14));
  if (frequency === "SEMIMONTHLY") return String((d.getMonth() * 2) + (d.getDate() <= 15 ? 1 : 2) + 1);
  return String(d.getMonth() + 1);
}

function emptyForm(): CompanyFormState {
  const todayIso = new Date().toISOString().slice(0, 10);
  const payrollFrequency = "MONTHLY";
  return {
    name: "",
    legalName: "",
    registrationNumber: "",
    taxId: "",
    payrollCountry: "",
    currency: "",
    payrollFrequency,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
    contactEmail: "",
    contactPhone: "",
    addressLine1: "",
    addressLine2: "",
    city: "",
    stateRegion: "",
    postalCode: "",
    country: "",
    payPeriodEndDate: todayIso,
    timesheetEndDate: todayIso,
    currentYear: String(new Date(`${todayIso}T00:00:00`).getFullYear()),
    currentPeriod: deriveCurrentPeriod(todayIso, payrollFrequency),
    active: true,
  };
}

const STATUSES = ["READY", "OPEN", "CLOSED"] as const;

function statusBadgeClass(status: string) {
  switch (status) {
    case "OPEN": return "rounded px-1.5 py-0.5 text-xs font-medium bg-success/10 text-success";
    case "READY": return "rounded px-1.5 py-0.5 text-xs font-medium bg-primary/10 text-primary";
    default: return "rounded px-1.5 py-0.5 text-xs font-medium bg-muted/20 text-muted";
  }
}

function runTypeBadgeClass(runType: string) {
  return runType === "FINAL"
    ? "rounded px-1.5 py-0.5 text-xs font-medium bg-success/10 text-success"
    : "rounded px-1.5 py-0.5 text-xs font-medium bg-primary/10 text-primary";
}

function CompanyPayPeriodsTab({
  companyId,
  canManage,
  canManageRuns,
  t,
}: {
  companyId: string;
  canManage: boolean;
  canManageRuns: boolean;
  t: (key: string) => string;
}) {
  const [items, setItems] = useState<TenantPayPeriodItem[]>([]);
  const [ppLoad, setPpLoad] = useState<"loading" | "ready" | "error">("loading");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [statusBusyId, setStatusBusyId] = useState<string | null>(null);
  const [filterYear, setFilterYear] = useState<string>("");
  const [filterStatus, setFilterStatus] = useState<string>("");
  // runs
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [runs, setRuns] = useState<TenantPayPeriodRunItem[]>([]);
  const [runsLoad, setRunsLoad] = useState<"loading" | "ready" | "error">("loading");
  const [runFormOpen, setRunFormOpen] = useState(false);
  const [runType, setRunType] = useState("INTERIM");
  const [runFormBusy, setRunFormBusy] = useState(false);
  const [runFormErr, setRunFormErr] = useState<string | null>(null);

  const load = useCallback(async (p: number, year: string, status: string) => {
    setPpLoad("loading");
    const r = await fetchTenantPayPeriods({
      companyId,
      page: p,
      size: 20,
      year: year ? Number(year) : null,
      status: status || null,
    });
    if (!r.ok) { setPpLoad("error"); return; }
    setItems(r.items);
    setTotalPages(r.totalPages);
    setPage(p);
    setPpLoad("ready");
  }, [companyId]);

  useEffect(() => { void load(0, filterYear, filterStatus); }, [load]); // eslint-disable-line react-hooks/exhaustive-deps

  async function patchStatus(item: TenantPayPeriodItem, newStatus: string) {
    setStatusBusyId(item.id);
    try {
      await patchTenantPayPeriodStatus(item.id, newStatus);
      await load(page, filterYear, filterStatus);
    } catch {
      /* ignore */
    } finally {
      setStatusBusyId(null);
    }
  }

  function applyFilters(year: string, status: string) {
    setFilterYear(year);
    setFilterStatus(status);
    void load(0, year, status);
  }

  async function loadRuns(payPeriodId: string) {
    setRunsLoad("loading");
    const r = await fetchTenantPayPeriodRuns(payPeriodId);
    if (!r.ok) { setRunsLoad("error"); return; }
    setRuns(r.items);
    setRunsLoad("ready");
  }

  async function toggleRuns(item: TenantPayPeriodItem) {
    if (expandedId === item.id) { setExpandedId(null); return; }
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
      setRunFormOpen(false);
      await loadRuns(expandedId);
    } catch {
      setRunFormErr(t("payPeriodRuns.msg.runCreateFailed"));
    } finally {
      setRunFormBusy(false);
    }
  }

  return (
    <div className="space-y-4">
      {/* toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <input
            type="number"
            placeholder={t("payPeriods.filter.year")}
            value={filterYear}
            min={2000}
            max={2099}
            className="w-28 rounded border border-border bg-surface px-2 py-1 text-sm text-foreground placeholder:text-muted"
            onChange={(e) => applyFilters(e.target.value, filterStatus)}
          />
          <select
            value={filterStatus}
            className="rounded border border-border bg-surface px-2 py-1 text-sm text-foreground"
            onChange={(e) => applyFilters(filterYear, e.target.value)}
          >
            <option value="">{t("payPeriods.filter.allStatuses")}</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>{t(`payPeriods.status.${s.toLowerCase()}`)}</option>
            ))}
          </select>
          {(filterYear || filterStatus) && (
            <button
              type="button"
              onClick={() => applyFilters("", "")}
              className="text-sm text-muted underline-offset-4 hover:underline"
            >
              {t("payPeriods.filter.clear")}
            </button>
          )}
        </div>
        <Link
          href={`/app/pay-periods/new?companyId=${companyId}`}
          className="rounded bg-primary px-3 py-1 text-sm font-medium text-primary-foreground hover:opacity-90"
        >
          {t("payPeriods.action.new")}
        </Link>
      </div>
      {/* content */}
      {ppLoad === "loading" ? (
        <p className="text-sm text-muted">{t("payPeriods.state.loading")}</p>
      ) : ppLoad === "error" ? (
        <p className="text-sm text-destructive">{t("payPeriods.error.load")}</p>
      ) : items.length === 0 ? (
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
                              <option key={s} value={s}>{t(`payPeriods.status.${s.toLowerCase()}`)}</option>
                            ))}
                          </select>
                        )}
                      </div>
                    </td>
                  </tr>
                  {expandedId === item.id && (
                    <tr>
                      <td colSpan={6} className="bg-surface-alt px-6 py-3">
                        <div className="space-y-3">
                          <div className="flex items-center justify-between">
                            <span className="text-sm font-medium text-foreground">{t("payPeriodRuns.title")}</span>
                            {canManageRuns && !runFormOpen && (
                              <button
                                onClick={() => { setRunFormOpen(true); setRunFormErr(null); }}
                                className="rounded bg-primary px-2 py-1 text-xs font-medium text-primary-foreground hover:opacity-90"
                              >
                                {t("payPeriodRuns.action.newRun")}
                              </button>
                            )}
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
        <button onClick={() => void load(page - 1, filterYear, filterStatus)} disabled={page === 0} className="rounded border border-border px-3 py-1 disabled:opacity-40">
          {t("payPeriods.action.prev")}
        </button>
        <span className="py-1 text-muted">{t("payPeriods.pagination.page")} {page + 1} / {totalPages}</span>
        <button onClick={() => void load(page + 1, filterYear, filterStatus)} disabled={page >= totalPages - 1} className="rounded border border-border px-3 py-1 disabled:opacity-40">
          {t("payPeriods.action.next")}
        </button>
      </div>
    </div>
  );
}

export default function CompanyEditPage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [payrollCountries, setPayrollCountries] = useState<PlatformCountryRow[]>([]);
  const [allCountries, setAllCountries] = useState<PlatformCountryRow[]>([]);
  const [countryInput, setCountryInput] = useState("");
  const [tenantCurrencies, setTenantCurrencies] = useState<TenantCurrencyItem[]>([]);
  const [form, setForm] = useState<CompanyFormState>(emptyForm());
  const [initialSnapshot, setInitialSnapshot] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [validationIssues, setValidationIssues] = useState<ValidationIssue[]>([]);
  const [tab, setTab] = useState<"details" | "documents" | "branding" | "pay-periods">("details");
  const [logoUrl, setLogoUrl] = useState<string | null>(null);
  const [logoBusy, setLogoBusy] = useState(false);
  const [logoError, setLogoError] = useState<string | null>(null);
  const MAX_LOGO_BYTES = 256 * 1024;

  const canManage = me.privileges.includes("COMPANY_MANAGE");

  const timezoneOptions = useMemo(() => {
    const intlWithSupportedValues = Intl as unknown as { supportedValuesOf?: (key: string) => string[] };
    const fromIntl = intlWithSupportedValues.supportedValuesOf?.("timeZone") ?? [];
    const browserTz = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
    const out = new Set<string>([browserTz, "UTC", ...fromIntl]);
    return Array.from(out).sort((a, b) => a.localeCompare(b));
  }, []);

  const timezoneOptionLabels = useMemo(() => {
    return timezoneOptions.map((tz) => ({ value: tz, label: `${tz} (${getUtcOffsetLabel(tz)})` }));
  }, [timezoneOptions]);

  const countryOptions = useMemo(
    () => allCountries.map((c) => ({ isoAlpha2: c.isoAlpha2, label: `${c.isoAlpha2} - ${c.name}` })),
    [allCountries],
  );

  const selectedCountryLabel = useMemo(
    () => countryOptions.find((c) => c.isoAlpha2 === form.country)?.label ?? "",
    [countryOptions, form.country],
  );

  const isDirty = useMemo(() => {
    if (initialSnapshot === null) return false;
    return JSON.stringify(form) !== initialSnapshot;
  }, [form, initialSnapshot]);

  const unsavedGuard = useUnsavedChangesGuard(isDirty, busy);

  useEffect(() => {
    if (!canManage) return;
    void (async () => {
      setLoad("loading");
      const fetchAllCountries = async (): Promise<
        { ok: true; items: PlatformCountryRow[] } | { ok: false }
      > => {
        let page = 0;
        const out: PlatformCountryRow[] = [];
        while (true) {
          const r = await fetchCountries({ page, size: 200, locale: me.locale });
          if (!r.ok) return { ok: false };
          out.push(...r.items);
          if (page + 1 >= r.totalPages) break;
          page += 1;
        }
        return { ok: true, items: out };
      };

      const [companyResult, payrollCountriesResult, allCountriesResult, currenciesResult] = await Promise.all([
        fetchTenantCompany(id),
        fetchCountries({ size: 500, locale: me.locale, payrollEnabled: true }),
        fetchAllCountries(),
        fetchTenantCurrencies(),
      ]);
      if (!companyResult.ok) {
        setLoad(companyResult.status === 403 ? "forbidden" : companyResult.status === 404 ? "notFound" : "error");
        return;
      }
      if (!payrollCountriesResult.ok || !allCountriesResult.ok || !currenciesResult.ok) {
        setLoad("error");
        return;
      }

      setPayrollCountries(payrollCountriesResult.items);
      setAllCountries(allCountriesResult.items);
      // Show the full platform currency catalog (matches the new-company page).
      // Backend auto-links tenant_currency for the chosen code on save.
      setTenantCurrencies(currenciesResult.items);

      const loadedForm: CompanyFormState = {
        payPeriodEndDate: companyResult.item.payPeriodEndDate ?? new Date().toISOString().slice(0, 10),
        timesheetEndDate: companyResult.item.timesheetEndDate ?? new Date().toISOString().slice(0, 10),
        name: companyResult.item.name,
        legalName: companyResult.item.legalName ?? "",
        registrationNumber: companyResult.item.registrationNumber ?? "",
        taxId: companyResult.item.taxId ?? "",
        payrollCountry: companyResult.item.payrollCountry,
        currency: companyResult.item.currency,
        payrollFrequency: companyResult.item.payrollFrequency,
        timezone: companyResult.item.timezone,
        contactEmail: companyResult.item.contactEmail ?? "",
        contactPhone: companyResult.item.contactPhone ?? "",
        addressLine1: companyResult.item.addressLine1 ?? "",
        addressLine2: companyResult.item.addressLine2 ?? "",
        city: companyResult.item.city ?? "",
        stateRegion: companyResult.item.stateRegion ?? "",
        postalCode: companyResult.item.postalCode ?? "",
        country: companyResult.item.country ?? "",
        currentYear: companyResult.item.currentYear != null
          ? String(companyResult.item.currentYear)
          : String(new Date(`${companyResult.item.payPeriodEndDate ?? new Date().toISOString().slice(0, 10)}T00:00:00`).getFullYear()),
        currentPeriod: companyResult.item.currentPeriod != null
          ? String(companyResult.item.currentPeriod)
          : deriveCurrentPeriod(companyResult.item.payPeriodEndDate ?? new Date().toISOString().slice(0, 10), companyResult.item.payrollFrequency),
        active: companyResult.item.active,
      };
      setForm(loadedForm);
      setInitialSnapshot(JSON.stringify(loadedForm));
      setLogoUrl(companyResult.item.logoUrl ?? null);
      const matchedAddressCountry = allCountriesResult.items.find((c) => c.isoAlpha2 === (companyResult.item.country ?? ""));
      setCountryInput(matchedAddressCountry ? `${matchedAddressCountry.isoAlpha2} - ${matchedAddressCountry.name}` : "");
      setLoad("ready");
    })();
  }, [canManage, id, me.locale]);

  async function onLogoChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    if (file.size > MAX_LOGO_BYTES) {
      setLogoError(`File too large. Maximum size is 256 KB (file is ${Math.round(file.size / 1024)} KB).`);
      return;
    }
    setLogoBusy(true);
    setLogoError(null);
    const res = await uploadCompanyLogo(id, file);
    setLogoBusy(false);
    if (!res.ok) {
      if (res.status === 503) {
        setLogoError("Storage is not configured. Logo upload is unavailable.");
      } else if (res.status === 400) {
        setLogoError(res.message ?? "Invalid file. Only PNG, JPEG, WebP, GIF and SVG are accepted (max 256 KB).");
      } else {
        setLogoError(`Upload failed (HTTP ${res.status}).`);
      }
      return;
    }
    setLogoUrl(res.item.logoUrl ?? null);
  }

  async function onLogoRemove() {
    setLogoBusy(true);
    setLogoError(null);
    const res = await deleteCompanyLogo(id);
    setLogoBusy(false);
    if (!res.ok) {
      setLogoError(`Remove failed (HTTP ${res.status}).`);
      return;
    }
    setLogoUrl(null);
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const issues: ValidationIssue[] = [];
    if (!form.name.trim()) issues.push({ fieldId: "company-name", message: t("companies.validation.nameRequired") });
    if (!form.taxId.trim()) issues.push({ fieldId: "company-tax-id", message: t("companies.validation.taxIdRequired") });
    if (!form.legalName.trim()) {
      issues.push({ fieldId: "company-legal-name", message: t("companies.validation.legalNameRequired") });
    }
    if (!form.payrollCountry) issues.push({ fieldId: "company-payroll-country", message: t("companies.validation.payrollCountryRequired") });
    if (!form.currency) issues.push({ fieldId: "company-currency", message: t("companies.validation.currencyRequired") });
    if (!form.payPeriodEndDate) issues.push({ fieldId: "company-pay-period-end", message: "Pay period end date is required." });
    if (!form.timesheetEndDate) issues.push({ fieldId: "company-timesheet-end", message: "Timesheet period end date is required." });
    if (!form.currentYear.trim()) issues.push({ fieldId: "company-current-year", message: "Current year is required." });
    if (!form.currentPeriod.trim()) issues.push({ fieldId: "company-current-period", message: "Current period is required." });
    if (issues.length > 0) {
      setValidationIssues(issues);
      setError(null);
      focusField(issues[0].fieldId);
      return;
    }

    const payload: TenantCompanyUpsertPayload = {
      name: form.name.trim(),
      legalName: form.legalName.trim() || null,
      registrationNumber: form.registrationNumber.trim() || null,
      taxId: form.taxId.trim() || null,
      payrollCountry: form.payrollCountry,
      currency: form.currency,
      payrollFrequency: form.payrollFrequency,
      timezone: form.timezone,
      dateFormat: me.dateFormat,
      contactEmail: form.contactEmail.trim() || null,
      contactPhone: form.contactPhone.trim() || null,
      addressLine1: form.addressLine1.trim() || null,
      addressLine2: form.addressLine2.trim() || null,
      city: form.city.trim() || null,
      stateRegion: form.stateRegion.trim() || null,
      postalCode: form.postalCode.trim() || null,
      country: form.country || null,
      payPeriodEndDate: form.payPeriodEndDate,
      timesheetEndDate: form.timesheetEndDate,
      currentYear: parseInt(form.currentYear, 10),
      currentPeriod: parseInt(form.currentPeriod, 10),
      active: form.active,
    };

    setBusy(true);
    setError(null);
    setValidationIssues([]);
    try {
      await putTenantCompany(id, payload);
      showToast(`"${form.name.trim()}" updated successfully.`);
      setInitialSnapshot(JSON.stringify(form));
      setBusy(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : t("companies.msg.saveFailed"));
      setBusy(false);
    }
  }

  if (!canManage) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("companies.title.edit")}</h1>
        <p className="text-sm text-muted">{t("companies.error.forbidden")}</p>
        <Link href="/app/companies" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("companies.action.backToList")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return <p className="mx-auto max-w-xl text-sm text-muted">{t("companies.state.loading")}</p>;
  }

  if (load !== "ready") {
    const key = load === "forbidden" ? "companies.error.forbidden" : load === "notFound" ? "companies.error.notFound" : "companies.error.load";
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("companies.title.edit")}</h1>
        <p className="text-sm text-muted">{t(key)}</p>
        <Link href="/app/companies" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("companies.action.backToList")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6" data-testid="company-form-edit">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("companies.title.edit")}</h1>
        <Link href="/app/companies" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("companies.action.backToList")}
        </Link>
      </div>

      <div className="flex gap-1 border-b border-border">
        <button
          type="button"
          onClick={() => setTab("details")}
          className={`px-4 py-2 text-sm font-medium ${tab === "details" ? "border-b-2 border-primary text-foreground" : "text-muted hover:text-foreground"}`}
        >
          Details
        </button>
        <button
          type="button"
          onClick={() => setTab("branding")}
          className={`px-4 py-2 text-sm font-medium ${tab === "branding" ? "border-b-2 border-primary text-foreground" : "text-muted hover:text-foreground"}`}
        >
          Branding
        </button>
        <button
          type="button"
          onClick={() => setTab("pay-periods")}
          className={`px-4 py-2 text-sm font-medium ${tab === "pay-periods" ? "border-b-2 border-primary text-foreground" : "text-muted hover:text-foreground"}`}
        >
          {t("payPeriods.title")}
        </button>
        <button
          type="button"
          onClick={() => setTab("documents")}
          className={`px-4 py-2 text-sm font-medium ${tab === "documents" ? "border-b-2 border-primary text-foreground" : "text-muted hover:text-foreground"}`}
        >
          Documents
        </button>
      </div>

      {tab === "documents" ? (
        <EntityDocumentsTab entityType="COMPANY" entityId={id} canEdit={canManage} />
      ) : tab === "pay-periods" ? (
        <CompanyPayPeriodsTab
          companyId={id}
          canManage={me.privileges.includes("PAY_PERIOD_MANAGE")}
          canManageRuns={me.privileges.includes("PAY_PERIOD_RUN_MANAGE")}
          t={t}
        />
      ) : tab === "branding" ? (
        <section className="rounded-md border border-border bg-surface p-5">
          <h2 className="text-base font-semibold text-foreground">Branding</h2>
          <p className="mt-1 text-sm text-muted">Use a square or horizontal logo for best results. Max size 256 KB.</p>
          <div className="mt-4 space-y-2 rounded-md border border-border bg-background p-4">
            <p className="text-xs text-muted">PNG, JPEG, WebP, GIF or SVG — max 256 KB</p>
            {logoUrl ? (
              <div className="flex items-center gap-4">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={logoUrl} alt="Company logo" className="h-16 w-auto max-w-[160px] rounded border border-border object-contain" />
                <button
                  type="button"
                  disabled={logoBusy || !canManage}
                  onClick={() => void onLogoRemove()}
                  className="rounded border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:bg-surface-alt disabled:opacity-50"
                >
                  {logoBusy ? "Removing…" : "Remove"}
                </button>
              </div>
            ) : (
              <p className="text-xs text-muted italic">No logo uploaded.</p>
            )}
            {canManage && (
              <label className="inline-block cursor-pointer">
                <span className="rounded bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground disabled:opacity-50">
                  {logoBusy ? "Uploading…" : logoUrl ? "Replace" : "Upload"}
                </span>
                <input
                  type="file"
                  accept="image/png,image/jpeg,image/webp,image/gif,image/svg+xml"
                  className="sr-only"
                  disabled={logoBusy}
                  onChange={(e) => void onLogoChange(e)}
                />
              </label>
            )}
            {logoError ? <p className="text-xs font-medium text-destructive">{logoError}</p> : null}
          </div>
        </section>
      ) : (
        <>
          <ValidationSummary issues={validationIssues} onFocus={focusField} />

          {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

          <form onSubmit={(e) => void onSubmit(e)} className="space-y-6">
            <CompanyFormSections
              t={t}
              frequencies={FREQUENCIES}
              form={form}
              onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
              payrollCountries={payrollCountries}
              tenantCurrencies={tenantCurrencies}
              timezoneOptionLabels={timezoneOptionLabels}
              countryOptions={countryOptions}
              countryInput={countryInput}
              setCountryInput={setCountryInput}
              selectedCountryLabel={selectedCountryLabel}
              platformDateFormat={me.dateFormat}
              footer={
                <div className="sticky bottom-0 flex flex-wrap items-center justify-between gap-3 rounded-md border border-border bg-surface/95 p-4 backdrop-blur">
                  <p className="text-sm text-muted">{isDirty ? "You have unsaved changes." : "All changes saved."}</p>
                  <div className="flex gap-3">
                    <button type="submit" disabled={busy || !isDirty} className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50">
                      {t("companies.action.save")}
                    </button>
                    <Link href="/app/companies" className="rounded border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-surface-alt">
                      {t("companies.action.cancel")}
                    </Link>
                  </div>
                </div>
              }
            />
          </form>
        </>
      )}
      <UnsavedChangesDialog guard={unsavedGuard} onConfirm={(href) => router.push(href)} />
    </div>
  );
}
