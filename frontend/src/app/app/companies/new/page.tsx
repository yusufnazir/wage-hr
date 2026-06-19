"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";

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
import { showToast } from "@/components/ui/Toast";
import {
  createTenantCompany,
  fetchCountries,
  fetchTenantCurrencies,
  type PlatformCountryRow,
  type TenantCompanyUpsertPayload,
  type TenantCurrencyItem,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

const FREQUENCIES = ["WEEKLY", "BIWEEKLY", "SEMIMONTHLY", "MONTHLY"] as const;

type LoadState = "loading" | "ready" | "error";

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

function initialForm(): CompanyFormState {
  const todayIso = new Date().toISOString().slice(0, 10);
  const browserTz = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  const payrollFrequency = "MONTHLY";
  return {
    name: "",
    legalName: "",
    registrationNumber: "",
    taxId: "",
    payrollCountry: "",
    currency: "",
    payrollFrequency,
    timezone: browserTz,
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

export default function CompanyNewPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { me, markCompanyCreated } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [payrollCountries, setPayrollCountries] = useState<PlatformCountryRow[]>([]);
  const [allCountries, setAllCountries] = useState<PlatformCountryRow[]>([]);
  const [countryInput, setCountryInput] = useState("");
  const [tenantCurrencies, setTenantCurrencies] = useState<TenantCurrencyItem[]>([]);
  const [initialSnapshot, setInitialSnapshot] = useState<string | null>(null);
  const [form, setForm] = useState<CompanyFormState>(initialForm());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [validationIssues, setValidationIssues] = useState<ValidationIssue[]>([]);

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

  useEffect(() => {
    setForm(initialForm());
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

      const [payrollCountriesResult, allCountriesResult, currenciesResult] = await Promise.all([
        fetchCountries({ size: 500, locale: me.locale, payrollEnabled: true }),
        fetchAllCountries(),
        fetchTenantCurrencies(),
      ]);
      if (!payrollCountriesResult.ok || !allCountriesResult.ok || !currenciesResult.ok) {
        setLoad("error");
        return;
      }
      setPayrollCountries(payrollCountriesResult.items);
      setAllCountries(allCountriesResult.items);
      // Show the full platform currency catalog so the picker is never empty.
      // Backend auto-links tenant_currency for the chosen code on save.
      setTenantCurrencies(currenciesResult.items);
      const defaultCurrency =
        currenciesResult.items.find((x) => x.assigned)?.code ?? currenciesResult.items[0]?.code ?? "";
      const loadedForm: CompanyFormState = {
        ...initialForm(),
        payrollCountry: payrollCountriesResult.items[0]?.isoAlpha2 ?? "",
        currency: defaultCurrency,
      };
      setForm(loadedForm);
      setInitialSnapshot(JSON.stringify(loadedForm));
      setCountryInput("");
      setLoad("ready");
    })();
  }, [me.locale]);

  const unsavedGuard = useUnsavedChangesGuard(isDirty, busy);

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
      const created = await createTenantCompany(payload);
      markCompanyCreated(created.id);
      showToast(`"${form.name.trim()}" created successfully.`);
      const returnTo = searchParams.get("returnTo")?.trim();
      if (returnTo && returnTo.startsWith("/app")) {
        router.push(returnTo);
      } else {
        router.push(`/app/companies/${created.id}/edit`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : t("companies.msg.createFailed"));
      setBusy(false);
    }
  }

  if (!canManage) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("companies.title.new")}</h1>
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

  if (load === "error") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("companies.title.new")}</h1>
        <p className="text-sm text-muted">{t("companies.error.load")}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6" data-testid="company-form-new">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("companies.title.new")}</h1>
        <Link href="/app/companies" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("companies.action.backToList")}
        </Link>
      </div>

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
              <p className="text-sm text-muted">{isDirty ? "You have unsaved changes." : "Complete required fields to create this company."}</p>
              <div className="flex gap-3">
                <button type="submit" disabled={busy} className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50">
                  {t("companies.action.create")}
                </button>
                <Link href="/app/companies" className="rounded border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-surface-alt">
                  {t("companies.action.cancel")}
                </Link>
              </div>
            </div>
          }
        />
      </form>
      <UnsavedChangesDialog guard={unsavedGuard} onConfirm={(href) => router.push(href)} />
    </div>
  );
}
