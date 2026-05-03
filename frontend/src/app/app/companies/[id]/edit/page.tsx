"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";

import {
  CompanyFormSections,
  ValidationSummary,
  focusField,
  useUnsavedChangesGuard,
  type CompanyFormState,
  type ValidationIssue,
} from "@/components/company/CompanyFormSections";
import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { EntityDocumentsTab } from "@/components/ui/EntityDocumentsTab";
import {
  deleteCompanyLogo,
  fetchCountries,
  fetchTenantCompany,
  fetchTenantCurrencies,
  putTenantCompany,
  uploadCompanyLogo,
  type PlatformCountryRow,
  type TenantCompanyUpsertPayload,
  type TenantCurrencyItem,
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

function emptyForm(dateFormat: string): CompanyFormState {
  const todayIso = new Date().toISOString().slice(0, 10);
  return {
    name: "",
    legalName: "",
    registrationNumber: "",
    taxId: "",
    payrollCountry: "",
    currency: "",
    payrollFrequency: "MONTHLY",
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
    dateFormat,
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
    active: true,
  };
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
  const [form, setForm] = useState<CompanyFormState>(emptyForm(me.dateFormat));
  const [initialSnapshot, setInitialSnapshot] = useState<string | null>(null);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [validationIssues, setValidationIssues] = useState<ValidationIssue[]>([]);
  const [tab, setTab] = useState<"details" | "documents">("details");
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

  useUnsavedChangesGuard(isDirty, busy);

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
      setTenantCurrencies(currenciesResult.items.filter((x) => x.assigned));

      const loadedForm: CompanyFormState = {
        name: companyResult.item.name,
        legalName: companyResult.item.legalName ?? "",
        registrationNumber: companyResult.item.registrationNumber ?? "",
        taxId: companyResult.item.taxId ?? "",
        payrollCountry: companyResult.item.payrollCountry,
        currency: companyResult.item.currency,
        payrollFrequency: companyResult.item.payrollFrequency,
        timezone: companyResult.item.timezone,
        dateFormat: companyResult.item.dateFormat || me.dateFormat,
        contactEmail: companyResult.item.contactEmail ?? "",
        contactPhone: companyResult.item.contactPhone ?? "",
        addressLine1: companyResult.item.addressLine1 ?? "",
        addressLine2: companyResult.item.addressLine2 ?? "",
        city: companyResult.item.city ?? "",
        stateRegion: companyResult.item.stateRegion ?? "",
        postalCode: companyResult.item.postalCode ?? "",
        country: companyResult.item.country ?? "",
        payPeriodEndDate: companyResult.item.payPeriodEndDate ?? new Date().toISOString().slice(0, 10),
        timesheetEndDate: companyResult.item.timesheetEndDate ?? new Date().toISOString().slice(0, 10),
        active: companyResult.item.active,
      };
      setForm(loadedForm);
      setInitialSnapshot(JSON.stringify(loadedForm));
      setLogoUrl(companyResult.item.logoUrl ?? null);
      const matchedAddressCountry = allCountriesResult.items.find((c) => c.isoAlpha2 === (companyResult.item.country ?? ""));
      setCountryInput(matchedAddressCountry ? `${matchedAddressCountry.isoAlpha2} - ${matchedAddressCountry.name}` : "");
      setLoad("ready");
    })();
  }, [canManage, id, me.dateFormat, me.locale]);

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
    if (!form.payrollCountry) issues.push({ fieldId: "company-payroll-country", message: t("companies.validation.payrollCountryRequired") });
    if (!form.currency) issues.push({ fieldId: "company-currency", message: t("companies.validation.currencyRequired") });
    if (!form.payPeriodEndDate) issues.push({ fieldId: "company-pay-period-end", message: "Pay period end date is required." });
    if (!form.timesheetEndDate) issues.push({ fieldId: "company-timesheet-end", message: "Timesheet period end date is required." });
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
      dateFormat: form.dateFormat.trim() || me.dateFormat,
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
      active: form.active,
    };

    setBusy(true);
    setError(null);
    setValidationIssues([]);
    try {
      await putTenantCompany(id, payload);
      router.push("/app/companies");
    } catch {
      setError(t("companies.msg.saveFailed"));
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
    <div className="mx-auto max-w-3xl space-y-6" data-testid="company-form-edit">
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
          onClick={() => setTab("documents")}
          className={`px-4 py-2 text-sm font-medium ${tab === "documents" ? "border-b-2 border-primary text-foreground" : "text-muted hover:text-foreground"}`}
        >
          Documents
        </button>
      </div>

      {tab === "documents" ? (
        <EntityDocumentsTab entityType="COMPANY" entityId={id} canEdit={canManage} />
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
              showAdvanced={showAdvanced}
              onToggleAdvanced={() => setShowAdvanced((prev) => !prev)}
              payrollCountries={payrollCountries}
              tenantCurrencies={tenantCurrencies}
              timezoneOptionLabels={timezoneOptionLabels}
              countryOptions={countryOptions}
              countryInput={countryInput}
              setCountryInput={setCountryInput}
              selectedCountryLabel={selectedCountryLabel}
              countryListId="company-country-options-edit"
              brandingSection={
                <section id="branding" className="rounded-md border border-border bg-surface p-5">
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
              }
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
    </div>
  );
}
