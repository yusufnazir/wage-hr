"use client";

import { useEffect, type ReactNode } from "react";

import type { PlatformCountryRow, TenantCurrencyItem } from "@/lib/api";

// ─── Shared validation types & utilities ────────────────────────────────────

export type ValidationIssue = {
  fieldId: string;
  message: string;
};

export function focusField(fieldId: string): void {
  const el = document.getElementById(fieldId) as HTMLElement | null;
  if (!el) return;
  el.scrollIntoView({ behavior: "smooth", block: "center" });
  if ("focus" in el) (el as HTMLInputElement | HTMLSelectElement).focus();
}

export function useUnsavedChangesGuard(isDirty: boolean, busy: boolean): void {
  useEffect(() => {
    if (!isDirty || busy) return;
    const confirmLeave = () => window.confirm("You have unsaved changes. Leave this page?");
    const beforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    const onDocumentClick = (e: MouseEvent) => {
      const target = e.target as HTMLElement | null;
      const anchor = target?.closest("a[href]") as HTMLAnchorElement | null;
      if (!anchor) return;
      const href = anchor.getAttribute("href");
      if (!href || href.startsWith("#")) return;
      if (anchor.target && anchor.target !== "_self") return;
      if (anchor.hasAttribute("download")) return;
      if (!confirmLeave()) {
        e.preventDefault();
        e.stopPropagation();
      }
    };
    window.addEventListener("beforeunload", beforeUnload);
    document.addEventListener("click", onDocumentClick, true);
    return () => {
      window.removeEventListener("beforeunload", beforeUnload);
      document.removeEventListener("click", onDocumentClick, true);
    };
  }, [isDirty, busy]);
}

export function ValidationSummary({
  issues,
  onFocus,
}: {
  issues: ValidationIssue[];
  onFocus: (fieldId: string) => void;
}) {
  if (issues.length === 0) return null;
  return (
    <div className="rounded-md border border-destructive/40 bg-destructive/5 p-4">
      <p className="text-sm font-semibold text-destructive">Please fix the following fields:</p>
      <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-destructive">
        {issues.map((issue) => (
          <li key={issue.fieldId}>
            <button
              type="button"
              onClick={() => onFocus(issue.fieldId)}
              className="underline underline-offset-2 hover:no-underline"
            >
              {issue.message}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

export type CompanyFormState = {
  name: string;
  legalName: string;
  registrationNumber: string;
  taxId: string;
  payrollCountry: string;
  currency: string;
  payrollFrequency: string;
  timezone: string;
  dateFormat: string;
  contactEmail: string;
  contactPhone: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  stateRegion: string;
  postalCode: string;
  country: string;
  payPeriodEndDate: string;
  timesheetEndDate: string;
  active: boolean;
};

type CountryOption = {
  isoAlpha2: string;
  label: string;
};

type TimezoneOption = {
  value: string;
  label: string;
};

type CompanyFormSectionsProps = {
  t: (key: string) => string;
  frequencies: readonly string[];
  form: CompanyFormState;
  onChange: (patch: Partial<CompanyFormState>) => void;
  showAdvanced: boolean;
  onToggleAdvanced: () => void;
  payrollCountries: PlatformCountryRow[];
  tenantCurrencies: TenantCurrencyItem[];
  timezoneOptionLabels: TimezoneOption[];
  countryOptions: CountryOption[];
  countryInput: string;
  setCountryInput: (value: string) => void;
  selectedCountryLabel: string;
  countryListId: string;
  brandingSection?: ReactNode;
  footer: ReactNode;
};

function isLastDayOfMonth(dateIso: string): boolean {
  if (!dateIso) return false;
  const d = new Date(`${dateIso}T00:00:00`);
  if (Number.isNaN(d.getTime())) return false;
  const month = d.getMonth();
  d.setDate(d.getDate() + 1);
  return d.getMonth() !== month;
}

function nextPayPeriodDates(endDateIso: string, frequency: string, count: number): string[] {
  if (!endDateIso) return [];
  const base = new Date(`${endDateIso}T00:00:00`);
  if (Number.isNaN(base.getTime())) return [];
  const lastDayOfMonth = (y: number, m: number) => new Date(y, m + 1, 0).getDate();
  const baseIsMonthEnd = isLastDayOfMonth(endDateIso);
  const results: string[] = [];

  if (frequency === "WEEKLY" || frequency === "BIWEEKLY") {
    const step = frequency === "WEEKLY" ? 7 : 14;
    for (let i = 1; i <= count; i++) {
      const next = new Date(base);
      next.setDate(next.getDate() + step * i);
      results.push(next.toISOString().slice(0, 10));
    }
    return results;
  }

  if (frequency === "MONTHLY") {
    for (let i = 1; i <= count; i++) {
      const targetMonth = base.getMonth() + i;
      const y = base.getFullYear() + Math.floor(targetMonth / 12);
      const m = targetMonth % 12;
      const day = baseIsMonthEnd ? lastDayOfMonth(y, m) : Math.min(base.getDate(), lastDayOfMonth(y, m));
      results.push(new Date(y, m, day).toISOString().slice(0, 10));
    }
    return results;
  }

  if (frequency === "SEMIMONTHLY") {
    let current = base;
    for (let i = 0; i < count; i++) {
      const day = current.getDate();
      const m = current.getMonth();
      const y = current.getFullYear();
      let next: Date;
      if (day <= 15) {
        next = new Date(y, m, lastDayOfMonth(y, m));
      } else {
        const nm = m + 1;
        next = new Date(nm > 11 ? y + 1 : y, nm % 12, 15);
      }
      results.push(next.toISOString().slice(0, 10));
      current = next;
    }
  }

  return results;
}

function formatPreviewDate(iso: string): string {
  return new Date(`${iso}T00:00:00`).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export function CompanyFormSections({
  t,
  frequencies,
  form,
  onChange,
  showAdvanced,
  onToggleAdvanced,
  payrollCountries,
  tenantCurrencies,
  timezoneOptionLabels,
  countryOptions,
  countryInput,
  setCountryInput,
  selectedCountryLabel,
  countryListId,
  brandingSection,
  footer,
}: CompanyFormSectionsProps) {
  return (
    <div className="grid gap-6 lg:grid-cols-[220px_minmax(0,1fr)]">
      <aside className="hidden lg:block">
        <nav className="sticky top-4 rounded-md border border-border bg-surface p-3">
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">Sections</p>
          <div className="space-y-1 text-sm">
            <a href="#identity" className="block rounded px-2 py-1 text-foreground hover:bg-surface-alt">Identity</a>
            <a href="#payroll" className="block rounded px-2 py-1 text-foreground hover:bg-surface-alt">Payroll setup</a>
            <a href="#periods" className="block rounded px-2 py-1 text-foreground hover:bg-surface-alt">Period rules</a>
            <a href="#locale" className="block rounded px-2 py-1 text-foreground hover:bg-surface-alt">Locale & time</a>
            <a href="#contact" className="block rounded px-2 py-1 text-foreground hover:bg-surface-alt">Contact</a>
            <a href="#address" className="block rounded px-2 py-1 text-foreground hover:bg-surface-alt">Address</a>
            {brandingSection ? <a href="#branding" className="block rounded px-2 py-1 text-foreground hover:bg-surface-alt">Branding</a> : null}
          </div>
        </nav>
      </aside>

      <div className="space-y-6">
        <section id="identity" className="rounded-md border border-border bg-surface p-5">
          <h2 className="text-base font-semibold text-foreground">Identity</h2>
          <p className="mt-1 text-sm text-muted">Core company identity used across payroll and reporting.</p>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.name")} *</span>
              <input id="company-name" className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.name} onChange={(e) => onChange({ name: e.target.value })} />
            </label>
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.taxId")}</span>
              <input className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.taxId} onChange={(e) => onChange({ taxId: e.target.value })} />
            </label>
          </div>
          <button
            type="button"
            onClick={onToggleAdvanced}
            className="mt-4 text-sm font-medium text-primary underline-offset-4 hover:underline"
          >
            {showAdvanced ? "Hide advanced fields" : "Show advanced fields"}
          </button>
          {showAdvanced ? (
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <label className="block space-y-1">
                <span className="text-sm text-muted">{t("companies.label.legalName")}</span>
                <input className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.legalName} onChange={(e) => onChange({ legalName: e.target.value })} />
              </label>
              <label className="block space-y-1">
                <span className="text-sm text-muted">{t("companies.label.registrationNumber")}</span>
                <input className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.registrationNumber} onChange={(e) => onChange({ registrationNumber: e.target.value })} />
              </label>
            </div>
          ) : null}
        </section>

        <section id="payroll" className="rounded-md border border-border bg-surface p-5">
          <h2 className="text-base font-semibold text-foreground">Payroll setup</h2>
          <p className="mt-1 text-sm text-muted">Select the payroll jurisdiction, currency, and recurrence model.</p>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.payrollCountry")} *</span>
              <select id="company-payroll-country" className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.payrollCountry} onChange={(e) => onChange({ payrollCountry: e.target.value })}>
                {payrollCountries.map((c) => (
                  <option key={c.id} value={c.isoAlpha2}>{c.isoAlpha2} - {c.name}</option>
                ))}
              </select>
            </label>
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.currency")} *</span>
              <select id="company-currency" className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.currency} onChange={(e) => onChange({ currency: e.target.value })}>
                {tenantCurrencies.map((c) => (
                  <option key={c.id} value={c.code}>{c.code} - {c.displayName}</option>
                ))}
              </select>
            </label>
          </div>
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.payrollFrequency")} *</span>
              <select className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.payrollFrequency} onChange={(e) => onChange({ payrollFrequency: e.target.value })}>
                {frequencies.map((freq) => (
                  <option key={freq} value={freq}>{t(`companies.frequency.${freq.toLowerCase()}`)}</option>
                ))}
              </select>
            </label>
            <label className="flex items-center gap-2 self-end pt-6">
              <input type="checkbox" checked={form.active} onChange={(e) => onChange({ active: e.target.checked })} />
              <span className="text-sm text-foreground">{t("companies.label.active")}</span>
            </label>
          </div>
        </section>

        <section id="periods" className="rounded-md border border-border bg-surface p-5">
          <h2 className="text-base font-semibold text-foreground">Period rules</h2>
          <p className="mt-1 text-sm text-muted">Define pay period and timesheet cut-off anchors.</p>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.payPeriodEndDate")} *</span>
              <input
                id="company-pay-period-end"
                type="date"
                className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
                value={form.payPeriodEndDate}
                onChange={(e) => onChange({ payPeriodEndDate: e.target.value })}
              />
              {form.payrollFrequency === "MONTHLY" && isLastDayOfMonth(form.payPeriodEndDate) ? (
                <p className="text-xs text-muted">{t("companies.hint.monthlyMonthEnd")}</p>
              ) : null}
              {(() => {
                const previews = nextPayPeriodDates(form.payPeriodEndDate, form.payrollFrequency, 3);
                if (previews.length === 0) return null;
                return <p className="text-xs text-muted">Next: {previews.map(formatPreviewDate).join(" · ")}</p>;
              })()}
            </label>
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.timesheetEndDate")} *</span>
              <input
                id="company-timesheet-end"
                type="date"
                className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
                value={form.timesheetEndDate}
                onChange={(e) => onChange({ timesheetEndDate: e.target.value })}
              />
            </label>
          </div>
        </section>

        <section id="locale" className="rounded-md border border-border bg-surface p-5">
          <h2 className="text-base font-semibold text-foreground">Locale & time</h2>
          <p className="mt-1 text-sm text-muted">Controls how dates and cutoffs are interpreted and displayed.</p>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.timezone")} *</span>
              <select className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.timezone} onChange={(e) => onChange({ timezone: e.target.value })}>
                {timezoneOptionLabels.map((tz) => (
                  <option key={tz.value} value={tz.value}>{tz.label}</option>
                ))}
              </select>
            </label>
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.dateFormat")}</span>
              <input
                className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
                value={form.dateFormat}
                onChange={(e) => onChange({ dateFormat: e.target.value })}
              />
            </label>
          </div>
        </section>

        <section id="contact" className="rounded-md border border-border bg-surface p-5">
          <h2 className="text-base font-semibold text-foreground">Contact</h2>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.contactEmail")}</span>
              <input type="email" className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.contactEmail} onChange={(e) => onChange({ contactEmail: e.target.value })} />
            </label>
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.contactPhone")}</span>
              <input className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.contactPhone} onChange={(e) => onChange({ contactPhone: e.target.value })} />
            </label>
          </div>
        </section>

        <section id="address" className="rounded-md border border-border bg-surface p-5">
          <h2 className="text-base font-semibold text-foreground">Address</h2>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.addressLine1")}</span>
              <input className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.addressLine1} onChange={(e) => onChange({ addressLine1: e.target.value })} />
            </label>
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.addressLine2")}</span>
              <input className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.addressLine2} onChange={(e) => onChange({ addressLine2: e.target.value })} />
            </label>
          </div>

          <div className="mt-3 grid gap-3 sm:grid-cols-3">
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.city")}</span>
              <input className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.city} onChange={(e) => onChange({ city: e.target.value })} />
            </label>
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.stateRegion")}</span>
              <input className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.stateRegion} onChange={(e) => onChange({ stateRegion: e.target.value })} />
            </label>
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.postalCode")}</span>
              <input className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.postalCode} onChange={(e) => onChange({ postalCode: e.target.value })} />
            </label>
          </div>

          <label className="mt-3 block space-y-1">
            <span className="text-sm text-muted">{t("companies.label.country")}</span>
            <input
              list={countryListId}
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
              value={countryInput}
              onChange={(e) => {
                const value = e.target.value;
                setCountryInput(value);
                const matched = countryOptions.find((c) => c.label.toLowerCase() === value.trim().toLowerCase());
                onChange({ country: matched?.isoAlpha2 ?? "" });
              }}
              onBlur={() => {
                if (form.country) {
                  setCountryInput(selectedCountryLabel);
                }
              }}
            />
            <datalist id={countryListId}>
              {countryOptions.map((c) => (
                <option key={c.isoAlpha2} value={c.label} />
              ))}
            </datalist>
          </label>
        </section>

        {brandingSection ?? null}

        {footer}
      </div>
    </div>
  );
}
