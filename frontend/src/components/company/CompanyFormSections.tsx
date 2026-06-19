"use client";

import { useEffect, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";

import type { PlatformCountryRow, TenantCurrencyItem } from "@/lib/api";
import { formatUserFacingDate } from "@/lib/user-date-format";
import { PlatformDateInput } from "@/components/ui/PlatformDateInput";

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

export type UnsavedChangesGuardState = {
  pendingHref: string | null;
  confirmNavigation: () => void;
  cancelNavigation: () => void;
};

export function useUnsavedChangesGuard(isDirty: boolean, busy: boolean): UnsavedChangesGuardState {
  const [pendingHref, setPendingHref] = useState<string | null>(null);

  useEffect(() => {
    if (!isDirty || busy) return;
    const onDocumentClick = (e: MouseEvent) => {
      const target = e.target as HTMLElement | null;
      const anchor = target?.closest("a[href]") as HTMLAnchorElement | null;
      if (!anchor) return;
      const href = anchor.getAttribute("href");
      if (!href || href.startsWith("#")) return;
      if (anchor.target && anchor.target !== "_self") return;
      if (anchor.hasAttribute("download")) return;
      e.preventDefault();
      e.stopPropagation();
      setPendingHref(href);
    };
    document.addEventListener("click", onDocumentClick, true);
    return () => {
      document.removeEventListener("click", onDocumentClick, true);
    };
  }, [isDirty, busy]);

  return {
    pendingHref,
    confirmNavigation: () => setPendingHref(null),
    cancelNavigation: () => setPendingHref(null),
  };
}

export function UnsavedChangesDialog({
  guard,
  onConfirm,
}: {
  guard: UnsavedChangesGuardState;
  onConfirm: (href: string) => void;
}) {
  const [mounted, setMounted] = useState(false);
  useEffect(() => { setMounted(true); }, []);

  if (!mounted || guard.pendingHref === null) return null;

  return createPortal(
    <div
      className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-labelledby="unsaved-dialog-title"
      onMouseDown={(e) => { if (e.target === e.currentTarget) guard.cancelNavigation(); }}
    >
      <div className="w-full max-w-sm rounded-lg border border-border bg-surface p-6 shadow-xl">
        <h2 id="unsaved-dialog-title" className="mb-2 text-base font-semibold text-foreground">
          You have unsaved changes
        </h2>
        <p className="mb-6 text-sm text-muted">
          If you leave now your changes will be lost. Are you sure you want to leave this page?
        </p>
        <div className="flex flex-col gap-2">
          <button
            onClick={() => {
              const href = guard.pendingHref!;
              guard.confirmNavigation();
              onConfirm(href);
            }}
            className="w-full rounded bg-destructive px-4 py-2 text-sm font-semibold text-destructive-foreground hover:opacity-90"
          >
            Leave page
          </button>
          <button
            onClick={guard.cancelNavigation}
            className="w-full rounded border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-surface-alt"
          >
            Stay on page
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
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
    <div className="rounded-md border border-destructive-border bg-destructive-soft p-4">
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
  currentYear: string;
  currentPeriod: string;
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
  payrollCountries: PlatformCountryRow[];
  tenantCurrencies: TenantCurrencyItem[];
  timezoneOptionLabels: TimezoneOption[];
  countryOptions: CountryOption[];
  countryInput: string;
  setCountryInput: (value: string) => void;
  selectedCountryLabel: string;
  brandingSection?: ReactNode;
  platformDateFormat: string;
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

function formatPreviewDate(iso: string, dateFormat: string): string {
  return formatUserFacingDate(iso, dateFormat);
}

export function CompanyFormSections({
  t,
  frequencies,
  form,
  onChange,
  payrollCountries,
  tenantCurrencies,
  timezoneOptionLabels,
  countryOptions,
  countryInput,
  setCountryInput,
  selectedCountryLabel,
  brandingSection,
  platformDateFormat,
  footer,
}: CompanyFormSectionsProps) {
  const [countryMenuOpen, setCountryMenuOpen] = useState(false);
  const [countryMenuPlacement, setCountryMenuPlacement] = useState<"above" | "below">("below");
  const countryFieldRef = useRef<HTMLDivElement | null>(null);
  const filteredCountryOptions = useMemo(() => {
    const query = countryInput.trim().toLowerCase();
    if (!query) {
      return countryOptions;
    }
    return countryOptions.filter(
      (option) => option.label.toLowerCase().includes(query) || option.isoAlpha2.toLowerCase().includes(query),
    );
  }, [countryInput, countryOptions]);

  const selectCountry = (option: CountryOption) => {
    setCountryInput(option.label);
    onChange({ country: option.isoAlpha2 });
    setCountryMenuOpen(false);
  };

  useLayoutEffect(() => {
    if (!countryMenuOpen) {
      return;
    }

    const updatePlacement = () => {
      const field = countryFieldRef.current;
      if (!field) {
        return;
      }
      const rect = field.getBoundingClientRect();
      const belowSpace = window.innerHeight - rect.bottom;
      const aboveSpace = rect.top;
      const estimatedMenuHeight = Math.min(Math.max(filteredCountryOptions.length, 1) * 40 + 8, 256);
      setCountryMenuPlacement(belowSpace < estimatedMenuHeight && aboveSpace > belowSpace ? "above" : "below");
    };

    updatePlacement();
    window.addEventListener("resize", updatePlacement);
    window.addEventListener("scroll", updatePlacement, true);
    return () => {
      window.removeEventListener("resize", updatePlacement);
      window.removeEventListener("scroll", updatePlacement, true);
    };
  }, [countryMenuOpen, filteredCountryOptions.length]);

  return (
    <div className="grid gap-6 lg:grid-cols-[220px_minmax(0,1fr)] lg:items-start">
      <aside className="hidden lg:block">
        <nav className="rounded-md border border-border bg-surface p-3">
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">Sections</p>
          <div className="space-y-1 text-sm">
            {(["identity", "payroll", "periods", "locale", "contact", "address"] as const).map((id, i) => (
              <a key={id} href={`#${id}`} className="block rounded px-2 py-1 text-foreground hover:bg-surface-alt"
                onClick={(e) => { e.preventDefault(); document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" }); }}
              >{["Identity", "Payroll setup", "Period rules", "Locale & time", "Contact", "Address"][i]}</a>
            ))}
            {brandingSection ? <a href="#branding" className="block rounded px-2 py-1 text-foreground hover:bg-surface-alt"
              onClick={(e) => { e.preventDefault(); document.getElementById("branding")?.scrollIntoView({ behavior: "smooth", block: "start" }); }}
            >Branding</a> : null}
          </div>
        </nav>
      </aside>

      <div className="space-y-6 lg:max-h-[calc(100vh-10rem)] lg:overflow-y-auto lg:pr-1">
        <section id="identity" className="rounded-md border border-border bg-surface p-5">
          <h2 className="text-base font-semibold text-foreground">Identity</h2>
          <p className="mt-1 text-sm text-muted">Core company identity used across payroll and reporting.</p>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.name")} *</span>
              <input id="company-name" className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.name} onChange={(e) => onChange({ name: e.target.value })} />
            </label>
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.taxId")} *</span>
              <input id="company-tax-id" className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.taxId} onChange={(e) => onChange({ taxId: e.target.value })} />
            </label>
          </div>
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.legalName")} *</span>
              <input
                id="company-legal-name"
                className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
                value={form.legalName}
                onChange={(e) => onChange({ legalName: e.target.value })}
              />
            </label>
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.registrationNumber")}</span>
              <input className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.registrationNumber} onChange={(e) => onChange({ registrationNumber: e.target.value })} />
            </label>
          </div>
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
              {(() => {
                const selected = tenantCurrencies.find((c) => c.code === form.currency);
                if (!selected || selected.assigned) return null;
                return (
                  <p className="text-xs text-muted">
                    This currency isn&apos;t in your tenant&apos;s currency list yet. It will be added automatically when you save.
                  </p>
                );
              })()}
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
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <label className="block space-y-1">
              <span className="text-sm text-muted">Current year</span>
              <input
                id="company-current-year"
                type="number"
                min={1900}
                max={2200}
                value={form.currentYear}
                onChange={(e) => onChange({ currentYear: e.target.value })}
                className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
              />
            </label>
            <label className="block space-y-1">
              <span className="text-sm text-muted">Current period</span>
              <input
                id="company-current-period"
                type="number"
                min={1}
                value={form.currentPeriod}
                onChange={(e) => onChange({ currentPeriod: e.target.value })}
                className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
              />
            </label>
          </div>
        </section>

        <section id="periods" className="rounded-md border border-border bg-surface p-5">
          <h2 className="text-base font-semibold text-foreground">Period rules</h2>
          <p className="mt-1 text-sm text-muted">Define pay period and timesheet cut-off anchors.</p>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.payPeriodEndDate")} *</span>
              <PlatformDateInput id="company-pay-period-end" value={form.payPeriodEndDate} dateFormat={platformDateFormat} onChange={(value) => onChange({ payPeriodEndDate: value })} />
              {form.payrollFrequency === "MONTHLY" && isLastDayOfMonth(form.payPeriodEndDate) ? (
                <p className="text-xs text-muted">{t("companies.hint.monthlyMonthEnd")}</p>
              ) : null}
              {(() => {
                const previews = nextPayPeriodDates(form.payPeriodEndDate, form.payrollFrequency, 3);
                if (previews.length === 0) return null;
                return <p className="text-xs text-muted">Next: {previews.map((preview) => formatPreviewDate(preview, platformDateFormat)).join(" · ")}</p>;
              })()}
            </label>
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.timesheetEndDate")} *</span>
              <PlatformDateInput id="company-timesheet-end" value={form.timesheetEndDate} dateFormat={platformDateFormat} onChange={(value) => onChange({ timesheetEndDate: value })} />
            </label>
          </div>
          <p className="mt-3 text-xs text-muted">{t("companies.label.dateFormatInherited")}: {platformDateFormat}</p>
        </section>

        <section id="locale" className="rounded-md border border-border bg-surface p-5">
          <h2 className="text-base font-semibold text-foreground">Locale & time</h2>
          <p className="mt-1 text-sm text-muted">Controls timezone handling. Date display follows the platform format.</p>
          <div className="mt-4 grid gap-3 sm:grid-cols-1">
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("companies.label.timezone")} *</span>
              <select className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.timezone} onChange={(e) => onChange({ timezone: e.target.value })}>
                {timezoneOptionLabels.map((tz) => (
                  <option key={tz.value} value={tz.value}>{tz.label}</option>
                ))}
              </select>
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
          <div className="mt-4 grid gap-3 grid-cols-1">
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
            <div ref={countryFieldRef} className="relative">
              <input
                className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
                placeholder="Search countries"
                value={countryInput}
                onFocus={() => setCountryMenuOpen(true)}
                onChange={(e) => {
                  const value = e.target.value;
                  setCountryInput(value);
                  setCountryMenuOpen(true);
                  const matched = countryOptions.find((option) => option.label.toLowerCase() === value.trim().toLowerCase());
                  onChange({ country: matched?.isoAlpha2 ?? "" });
                }}
                onBlur={() => {
                  window.setTimeout(() => {
                    setCountryMenuOpen(false);
                    setCountryInput(form.country ? selectedCountryLabel : "");
                  }, 100);
                }}
              />
              {countryMenuOpen ? (
                <div className={`absolute z-20 max-h-64 w-full overflow-auto rounded-md border border-border bg-surface shadow-lg ${countryMenuPlacement === "above" ? "bottom-full mb-1" : "top-full mt-1"}`}>
                  {filteredCountryOptions.length > 0 ? (
                    filteredCountryOptions.map((option) => (
                      <button
                        key={option.isoAlpha2}
                        type="button"
                        className="block w-full px-3 py-2 text-left text-sm text-foreground hover:bg-surface-alt"
                        onMouseDown={(e) => {
                          e.preventDefault();
                          selectCountry(option);
                        }}
                      >
                        {option.label}
                      </button>
                    ))
                  ) : (
                    <div className="px-3 py-2 text-sm text-muted">No countries found.</div>
                  )}
                </div>
              ) : null}
            </div>
          </label>
        </section>

        {brandingSection ?? null}

        {footer}
      </div>
    </div>
  );
}
