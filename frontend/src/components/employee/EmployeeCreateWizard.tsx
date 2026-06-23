"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import { EmployeePaymentPanel } from "@/components/employee/EmployeePaymentPanel";
import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { HorizontalStepper } from "@/components/ui/HorizontalStepper";
import { PlatformDateInput } from "@/components/ui/PlatformDateInput";
import { showToast } from "@/components/ui/Toast";
import {
  createTenantEmployee,
  completeTenantEmployeeOnboarding,
  fetchAllCountries,
  fetchTenantCompanies,
  fetchTenantCurrencies,
  fetchTenantDepartments,
  fetchTenantEmployee,
  fetchTenantEmployeeGroups,
  fetchTenantJobs,
  fetchTenantWorkTimes,
  putTenantEmployee,
  putTenantEmployeeCompensation,
  type PlatformCountryRow,
  type TenantCompanyItem,
  type TenantCurrencyItem,
  type TenantDepartmentItem,
  type TenantEmployeeCompensationPayload,
  type TenantEmployeeGroupItem,
  type TenantEmployeeItem,
  type TenantEmployeeUpsertPayload,
  type TenantJobItem,
  type TenantWorkTimeItem,
} from "@/lib/api";
import { deriveCompensationRates } from "@/lib/compensation-derived-rates";
import {
  getCompensationStatutoryPanel,
  isCompensationStatutoryFieldChecked,
  patchCompensationStatutoryField,
} from "@/lib/compensation-statutory-config";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "error";

const WIZARD_STEPS = [
  { id: "personal", labelKey: "employees.wizard.step.personal" },
  { id: "contact", labelKey: "employees.wizard.step.contact" },
  { id: "employment", labelKey: "employees.wizard.step.employment" },
  { id: "compensation", labelKey: "employees.wizard.step.compensation" },
  { id: "payment", labelKey: "employees.wizard.step.payment" },
  { id: "userAccount", labelKey: "employees.wizard.step.userAccount" },
] as const;

const STATUSES = ["ACTIVE", "ON_LEAVE", "TERMINATED", "INACTIVE", "DRAFT"];
const GENDERS: { value: string; label: string }[] = [
  { value: "MALE", label: "Male" },
  { value: "FEMALE", label: "Female" },
  { value: "OTHER", label: "Other" },
  { value: "PREFER_NOT_TO_SAY", label: "Prefer not to say" },
];
const CIVIL_STATES: { value: string; label: string }[] = [
  { value: "SINGLE", label: "Single" },
  { value: "MARRIED", label: "Married" },
  { value: "DOMESTIC_PARTNERSHIP", label: "Domestic partnership" },
  { value: "DIVORCED", label: "Divorced" },
  { value: "WIDOWED", label: "Widowed" },
];
const WAGE_TYPES: { value: TenantEmployeeCompensationPayload["wageType"]; label: string; perLabel: string }[] = [
  { value: "PER_HOUR", label: "Per hour", perLabel: "Wage per hour" },
  { value: "PER_PERIOD", label: "Per period", perLabel: "Wage per period" },
];

function emptyPayload(companyId = ""): TenantEmployeeUpsertPayload {
  return {
    companyId,
    departmentId: "",
    jobId: "",
    employeeGroupId: "",
    firstName: "",
    lastName: "",
    hireDate: "",
    status: "DRAFT",
    active: false,
    badgeNumber: "",
  };
}

function itemToForm(item: TenantEmployeeItem): TenantEmployeeUpsertPayload {
  return {
    companyId: item.companyId,
    departmentId: item.departmentId ?? "",
    jobId: item.jobId ?? "",
    employeeGroupId: item.employeeGroupId ?? "",
    firstName: item.firstName,
    lastName: item.lastName,
    dateOfBirth: item.dateOfBirth,
    hireDate: item.hireDate ?? "",
    email: item.email,
    phone: item.phone,
    status: "DRAFT",
    active: false,
    badgeNumber: item.badgeNumber,
    idNumber: item.idNumber,
    gender: item.gender,
    nationality: item.nationality,
    placeOfBirth: item.placeOfBirth,
    civilState: item.civilState,
    resignationDate: item.resignationDate,
    addressStreet: item.addressStreet,
    addressNumber: item.addressNumber,
    addressCity: item.addressCity,
    addressCountry: item.addressCountry,
    addressPostalCode: item.addressPostalCode,
  };
}

function emptyCompensation(currencyCode = ""): TenantEmployeeCompensationPayload {
  return {
    currencyCode,
    wageType: "PER_PERIOD",
    wageAmount: 0,
    workTimeId: "",
    applyTaxes: true,
    applyTaxExempt: true,
    applyAov: true,
    notes: "",
  };
}

function statusLabel(s: string): string {
  const map: Record<string, string> = {
    ACTIVE: "Active",
    INACTIVE: "Inactive",
    ON_LEAVE: "On leave",
    TERMINATED: "Terminated",
    DRAFT: "Draft",
  };
  return map[s] ?? s;
}

function normalizeEmployeePayload(form: TenantEmployeeUpsertPayload): TenantEmployeeUpsertPayload {
  return {
    ...form,
    status: "DRAFT",
    active: false,
    firstName: form.firstName.trim(),
    lastName: form.lastName.trim(),
    departmentId: form.departmentId?.toString().trim() || null,
    jobId: form.jobId?.toString().trim() || null,
    dateOfBirth: form.dateOfBirth?.toString().trim() || null,
    hireDate: form.hireDate?.toString().trim() || null,
    email: form.email?.toString().trim() || null,
    phone: form.phone?.toString().trim() || null,
    badgeNumber: form.badgeNumber?.toString().trim() || null,
    employeeGroupId: form.employeeGroupId?.toString().trim() || null,
    idNumber: form.idNumber?.toString().trim() || null,
    gender: form.gender?.toString().trim() || null,
    nationality: form.nationality?.toString().trim() || null,
    placeOfBirth: form.placeOfBirth?.toString().trim() || null,
    civilState: form.civilState?.toString().trim() || null,
    resignationDate: form.resignationDate?.toString().trim() || null,
    addressStreet: form.addressStreet?.toString().trim() || null,
    addressNumber: form.addressNumber?.toString().trim() || null,
    addressCity: form.addressCity?.toString().trim() || null,
    addressCountry: form.addressCountry?.toString().trim() || null,
    addressPostalCode: form.addressPostalCode?.toString().trim() || null,
  };
}

function validateCompleteEmployment(form: TenantEmployeeUpsertPayload): string | null {
  if (!form.companyId) return "Company is required.";
  if (!form.departmentId?.toString().trim()) return "Department is required.";
  if (!form.jobId?.toString().trim()) return "Job is required.";
  if (!form.hireDate?.toString().trim()) return "Employment date is required.";
  return null;
}

function formatWageAmountForInput(amount: number): string {
  if (!Number.isFinite(amount) || amount <= 0) return "";
  return String(amount);
}

function parseWageAmountInput(raw: string): number {
  const trimmed = raw.trim();
  if (!trimmed) return Number.NaN;
  const n = Number.parseFloat(trimmed.replace(",", "."));
  return Number.isFinite(n) ? n : Number.NaN;
}

function compensationHasData(comp: TenantEmployeeCompensationPayload): boolean {
  return (
    Boolean(comp.currencyCode) ||
    (Number.isFinite(comp.wageAmount) && comp.wageAmount > 0) ||
    Boolean(comp.workTimeId?.trim())
  );
}

export function EmployeeCreateWizard() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const draftId = searchParams.get("draft");
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const steps = useMemo(
    () => WIZARD_STEPS.map((s) => ({ id: s.id, label: t(s.labelKey) })),
    [t],
  );

  const [load, setLoad] = useState<LoadState>("loading");
  const [stepIndex, setStepIndex] = useState(0);
  const [maxReachedStep, setMaxReachedStep] = useState(0);
  const [createdEmployeeId, setCreatedEmployeeId] = useState<string | null>(null);
  const [compSaved, setCompSaved] = useState(false);

  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [departments, setDepartments] = useState<TenantDepartmentItem[]>([]);
  const [jobs, setJobs] = useState<TenantJobItem[]>([]);
  const [groups, setGroups] = useState<TenantEmployeeGroupItem[]>([]);
  const [countries, setCountries] = useState<PlatformCountryRow[]>([]);
  const [currencies, setCurrencies] = useState<TenantCurrencyItem[]>([]);
  const [workTimes, setWorkTimes] = useState<TenantWorkTimeItem[]>([]);

  const [form, setForm] = useState<TenantEmployeeUpsertPayload>(emptyPayload());
  const [targetStatus, setTargetStatus] = useState("ACTIVE");
  const [compForm, setCompForm] = useState<TenantEmployeeCompensationPayload>(emptyCompensation());
  const [wageAmountInput, setWageAmountInput] = useState("");

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canViewPayment = me.privileges.includes("EMPLOYEE_PAYMENT_VIEW");

  const companyCurrency = useMemo(
    () => companies.find((c) => c.id === form.companyId)?.currency ?? "SRD",
    [companies, form.companyId],
  );

  const companyPayrollCountry = useMemo(
    () => companies.find((c) => c.id === form.companyId)?.payrollCountry ?? "",
    [companies, form.companyId],
  );

  const formDepartments = useMemo(
    () => departments.filter((d) => !form.companyId || d.companyId === form.companyId),
    [departments, form.companyId],
  );
  const formJobs = useMemo(
    () => jobs.filter((j) => !form.departmentId || j.departmentId === form.departmentId),
    [jobs, form.departmentId],
  );
  const formGroups = useMemo(
    () => groups.filter((g) => !form.companyId || g.companyId === form.companyId),
    [groups, form.companyId],
  );
  const formWorkTimes = useMemo(
    () => workTimes.filter((w) => !form.companyId || w.companyId === form.companyId),
    [workTimes, form.companyId],
  );

  const compDerivedRates = useMemo(() => {
    const company = companies.find((c) => c.id === form.companyId);
    const workTime = workTimes.find((w) => w.id === compForm.workTimeId);
    return deriveCompensationRates({
      wageType: compForm.wageType,
      wageAmount: compForm.wageAmount,
      hoursPerDay: workTime?.hoursPerDay ?? null,
      workDaysPerWeek: workTime?.workDaysPerWeek ?? null,
      payrollFrequency: company?.payrollFrequency ?? null,
    });
  }, [companies, workTimes, form.companyId, compForm.wageType, compForm.wageAmount, compForm.workTimeId]);

  useEffect(() => {
    void (async () => {
      setLoad("loading");
      const [cr, dr, jr, gr, countriesR, currenciesR, workTimesR] = await Promise.all([
        fetchTenantCompanies({ size: 100 }),
        fetchTenantDepartments({ size: 200 }),
        fetchTenantJobs({ size: 200 }),
        fetchTenantEmployeeGroups({ size: 200 }),
        fetchAllCountries({ locale: me.locale }),
        fetchTenantCurrencies(),
        fetchTenantWorkTimes({ size: 200, active: true }),
      ]);
      if (!cr.ok || !dr.ok || !jr.ok || !gr.ok) {
        setLoad("error");
        return;
      }
      setCompanies(cr.items);
      setDepartments(dr.items);
      setJobs(jr.items);
      setGroups(gr.items);
      if (countriesR.ok) setCountries(countriesR.items);
      if (currenciesR.ok) setCurrencies(currenciesR.items.filter((c) => c.assigned));
      if (workTimesR.ok) setWorkTimes(workTimesR.items);

      if (draftId) {
        const er = await fetchTenantEmployee(draftId);
        if (!er.ok) {
          setLoad("error");
          return;
        }
        if (er.item.status !== "DRAFT") {
          router.replace(`/app/employees/${draftId}/edit/employee`);
          return;
        }
        setCreatedEmployeeId(er.item.id);
        setForm(itemToForm(er.item));
        setMaxReachedStep(steps.length - 1);
        const company = cr.items.find((c) => c.id === er.item.companyId);
        setCompForm(emptyCompensation(company?.currency ?? "SRD"));
      } else {
        const defaultCompanyId = cr.items[0]?.id ?? "";
        const defaultCurrency = cr.items[0]?.currency ?? "SRD";
        setForm((prev) => ({ ...prev, companyId: defaultCompanyId }));
        setCompForm(emptyCompensation(defaultCurrency));
      }
      setLoad("ready");
    })();
  }, [me.locale, draftId, router, steps.length]);

  useEffect(() => {
    if (!form.companyId) return;
    const company = companies.find((c) => c.id === form.companyId);
    if (!company) return;
    setCompForm((prev) => (prev.currencyCode ? prev : { ...prev, currencyCode: company.currency }));
  }, [form.companyId, companies]);

  function patch(values: Partial<TenantEmployeeUpsertPayload>) {
    setForm((prev) => ({ ...prev, ...values }));
  }

  function patchComp(values: Partial<TenantEmployeeCompensationPayload>) {
    setCompForm((prev) => ({ ...prev, ...values }));
  }

  function validateStep(index: number): string | null {
    if (index === 0) {
      if (!form.companyId) return "Company is required.";
      if (!form.firstName.trim()) return "First name is required.";
      if (!form.lastName.trim()) return "Last name is required.";
    }
    if (index === 3 && compensationHasData(compForm)) {
      if (!compForm.currencyCode) return "Currency is required when setting compensation.";
      if (!compForm.wageType) return "Wage type is required.";
      if (!Number.isFinite(compForm.wageAmount) || compForm.wageAmount <= 0) {
        return "Wage amount must be greater than zero.";
      }
    }
    return null;
  }

  async function persistDraft(): Promise<string> {
    const payload = normalizeEmployeePayload(form);
    if (createdEmployeeId) {
      await putTenantEmployee(createdEmployeeId, payload);
      return createdEmployeeId;
    }
    const created = await createTenantEmployee(payload);
    setCreatedEmployeeId(created.id);
    router.replace(`/app/employees/new?draft=${encodeURIComponent(created.id)}`, { scroll: false });
    return created.id;
  }

  async function persistCompensation(employeeId: string): Promise<void> {
    if (!compensationHasData(compForm)) return;
    await putTenantEmployeeCompensation(employeeId, {
      ...compForm,
      workTimeId: compForm.workTimeId?.toString().trim() || null,
      notes: compForm.notes?.toString().trim() || null,
    });
    setCompSaved(true);
  }

  async function goNext() {
    const validationError = validateStep(stepIndex);
    if (validationError) {
      setError(validationError);
      return;
    }
    setError(null);
    setBusy(true);
    try {
      const employeeId = await persistDraft();
      if (stepIndex === 3) {
        await persistCompensation(employeeId);
      }
      const next = Math.min(stepIndex + 1, steps.length - 1);
      setStepIndex(next);
      setMaxReachedStep((prev) => Math.max(prev, next));
      showToast(t("employees.wizard.stepSaved"));
    } catch (err) {
      const msg = err instanceof Error && err.message ? err.message : t("employees.msg.createFailed");
      setError(msg);
    } finally {
      setBusy(false);
    }
  }

  function goPrev() {
    setError(null);
    setStepIndex((i) => Math.max(0, i - 1));
  }

  function goToStep(index: number) {
    if (index > maxReachedStep) return;
    setError(null);
    setStepIndex(index);
  }

  async function finishWizard() {
    const employmentError = validateCompleteEmployment(form);
    if (employmentError) {
      setError(employmentError);
      return;
    }
    if (!form.firstName.trim() || !form.lastName.trim()) {
      setError("First name and last name are required.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const employeeId = await persistDraft();
      if (compensationHasData(compForm)) {
        await persistCompensation(employeeId);
      }
      const completed = await completeTenantEmployeeOnboarding(
        employeeId,
        normalizeEmployeePayload(form),
        targetStatus,
      );
      showToast(`"${completed.firstName} ${completed.lastName}" is ready for payroll.`);
      router.push(`/app/employees/${completed.id}/edit/employee`);
    } catch (err) {
      const msg = err instanceof Error && err.message ? err.message : t("employees.msg.createFailed");
      setError(msg);
      setBusy(false);
    }
  }

  const wageTypeMeta = WAGE_TYPES.find((w) => w.value === compForm.wageType) ?? WAGE_TYPES[0];
  const statutoryPanel = getCompensationStatutoryPanel(companyPayrollCountry);
  const hasStatutoryPanel = statutoryPanel != null && statutoryPanel.fields.length > 0;
  const isLastStep = stepIndex === steps.length - 1;

  if (load === "loading") {
    return <p className="mx-auto max-w-xl text-sm text-muted">{t("employees.state.loading")}</p>;
  }

  if (load === "error") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("employees.action.new")}</h1>
        <p className="text-sm text-muted">{t("employees.error.load")}</p>
        <Link href="/app/employees" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}{t("employees.title")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl space-y-5" data-testid="employee-create-wizard">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav className="flex items-center gap-1 text-sm text-muted" aria-label="Breadcrumb">
          <Link href="/app/employees" className="font-medium text-primary underline-offset-4 hover:underline">
            {t("employees.title")}
          </Link>
          <span aria-hidden="true">›</span>
          <span className="font-medium text-foreground">{t("employees.action.new")}</span>
        </nav>
      </div>

      <p className="text-sm text-muted">{t("employees.wizard.intro")}</p>

      {createdEmployeeId ? (
        <div className="rounded-md border border-amber-300/50 bg-amber-50/10 px-4 py-2 text-sm text-foreground">
          {t("employees.wizard.draftBanner")}
        </div>
      ) : null}

      <HorizontalStepper
        steps={steps}
        currentIndex={stepIndex}
        completedThrough={maxReachedStep}
        onStepClick={goToStep}
      />

      {error ? (
        <div className="rounded-md border border-destructive/40 bg-destructive/5 px-4 py-2 text-sm text-destructive">
          {error}
        </div>
      ) : null}

      <div className="rounded-lg border border-border bg-surface p-5 shadow-sm">
        {stepIndex === 0 ? (
          <Section title={t("employees.wizard.step.personal")}>
            <Row>
              <Field label={t("employees.label.firstName")} required>
                <input className={inputCls} value={form.firstName} onChange={(e) => patch({ firstName: e.target.value })} />
              </Field>
              <Field label={t("employees.label.lastName")} required>
                <input className={inputCls} value={form.lastName} onChange={(e) => patch({ lastName: e.target.value })} />
              </Field>
            </Row>
            <Row>
              <Field label="ID number" hint="Passport or national identification number.">
                <input className={inputCls} value={form.idNumber ?? ""} onChange={(e) => patch({ idNumber: e.target.value })} />
              </Field>
              <Field label="Gender">
                <select className={inputCls} value={form.gender ?? ""} onChange={(e) => patch({ gender: e.target.value })}>
                  <option value="">Select…</option>
                  {GENDERS.map((g) => (
                    <option key={g.value} value={g.value}>{g.label}</option>
                  ))}
                </select>
              </Field>
            </Row>
            <Row>
              <Field label={t("employees.label.dateOfBirth")}>
                <PlatformDateInput value={form.dateOfBirth ?? ""} dateFormat={me.dateFormat} onChange={(v) => patch({ dateOfBirth: v })} />
              </Field>
              <Field label="Place of birth">
                <input className={inputCls} value={form.placeOfBirth ?? ""} onChange={(e) => patch({ placeOfBirth: e.target.value })} />
              </Field>
            </Row>
            <Row>
              <Field label="Nationality">
                <CountrySelect value={form.nationality ?? ""} countries={countries} onChange={(v) => patch({ nationality: v })} />
              </Field>
              <Field label="Civil state">
                <select className={inputCls} value={form.civilState ?? ""} onChange={(e) => patch({ civilState: e.target.value })}>
                  <option value="">Select…</option>
                  {CIVIL_STATES.map((c) => (
                    <option key={c.value} value={c.value}>{c.label}</option>
                  ))}
                </select>
              </Field>
            </Row>
            <Row>
              <Field label="Badge number" hint="Unique employee number within the company. Leave blank to assign later.">
                <input className={inputCls} value={form.badgeNumber ?? ""} onChange={(e) => patch({ badgeNumber: e.target.value })} />
              </Field>
              <div />
            </Row>
          </Section>
        ) : null}

        {stepIndex === 1 ? (
          <Section title={t("employees.wizard.step.contact")}>
            <Row>
              <Field label={t("employees.label.email")}>
                <input type="email" className={inputCls} value={form.email ?? ""} onChange={(e) => patch({ email: e.target.value })} />
              </Field>
              <Field label={t("employees.label.phone")}>
                <input className={inputCls} value={form.phone ?? ""} onChange={(e) => patch({ phone: e.target.value })} />
              </Field>
            </Row>
            <Row>
              <Field label="Street" wide>
                <input className={inputCls} value={form.addressStreet ?? ""} onChange={(e) => patch({ addressStreet: e.target.value })} />
              </Field>
              <Field label="Number">
                <input className={inputCls} value={form.addressNumber ?? ""} onChange={(e) => patch({ addressNumber: e.target.value })} />
              </Field>
            </Row>
            <Row>
              <Field label="City">
                <input className={inputCls} value={form.addressCity ?? ""} onChange={(e) => patch({ addressCity: e.target.value })} />
              </Field>
              <Field label="Postal code">
                <input className={inputCls} value={form.addressPostalCode ?? ""} onChange={(e) => patch({ addressPostalCode: e.target.value })} />
              </Field>
            </Row>
            <Row>
              <Field label="Country">
                <CountrySelect value={form.addressCountry ?? ""} countries={countries} onChange={(v) => patch({ addressCountry: v })} />
              </Field>
              <div />
            </Row>
          </Section>
        ) : null}

        {stepIndex === 2 ? (
          <Section title={t("employees.wizard.step.employment")}>
            <Row>
              <Field label={t("employees.label.companyId")} required>
                <select
                  className={inputCls}
                  value={form.companyId}
                  onChange={(e) => patch({ companyId: e.target.value, departmentId: "", jobId: "", employeeGroupId: "" })}
                >
                  <option value="">Select company…</option>
                  {companies.map((c) => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </Field>
              <Field label={t("employees.label.employeeGroupId")} hint="Optional. Assign a group later from Employee groups.">
                <select className={inputCls} value={form.employeeGroupId ?? ""} onChange={(e) => patch({ employeeGroupId: e.target.value })}>
                  <option value="">Select group…</option>
                  {formGroups.map((g) => (
                    <option key={g.id} value={g.id}>{g.name}</option>
                  ))}
                </select>
              </Field>
            </Row>
            <Row>
              <Field label={t("employees.label.departmentId")} required>
                <select className={inputCls} value={form.departmentId} onChange={(e) => patch({ departmentId: e.target.value, jobId: "" })}>
                  <option value="">Select department…</option>
                  {formDepartments.map((d) => (
                    <option key={d.id} value={d.id}>{d.name}</option>
                  ))}
                </select>
              </Field>
              <Field label={t("employees.label.jobId")} required>
                <select className={inputCls} value={form.jobId} onChange={(e) => patch({ jobId: e.target.value })}>
                  <option value="">Select job…</option>
                  {formJobs.map((j) => (
                    <option key={j.id} value={j.id}>{j.title}</option>
                  ))}
                </select>
              </Field>
            </Row>
            <Row>
              <Field label={t("employees.label.hireDate")} hint="Required before the employee can participate in payroll.">
                <PlatformDateInput value={form.hireDate ?? ""} dateFormat={me.dateFormat} onChange={(v) => patch({ hireDate: v })} />
              </Field>
              <Field label={t("employees.label.status")} hint="Applied when you finish onboarding.">
                <select className={inputCls} value={targetStatus} onChange={(e) => setTargetStatus(e.target.value)}>
                  {STATUSES.filter((s) => s !== "DRAFT").map((s) => (
                    <option key={s} value={s}>{statusLabel(s)}</option>
                  ))}
                </select>
              </Field>
            </Row>
            <p className="text-xs text-muted">{t("employees.wizard.draftInactiveHint")}</p>
          </Section>
        ) : null}

        {stepIndex === 3 ? (
          <div className="space-y-4">
            <p className="text-sm text-muted">{t("employees.wizard.compensationIntro")}</p>
            <div className={`grid grid-cols-1 gap-4 ${hasStatutoryPanel ? "md:grid-cols-3" : ""}`}>
              <div className={`space-y-4 ${hasStatutoryPanel ? "md:col-span-2" : ""}`}>
                <Section title={t("employees.wizard.step.compensation")}>
                  <Row>
                    <Field label="Currency" required={compensationHasData(compForm)}>
                      <select className={inputCls} value={compForm.currencyCode} onChange={(e) => patchComp({ currencyCode: e.target.value })}>
                        <option value="">Select currency…</option>
                        {currencies.map((c) => (
                          <option key={c.code} value={c.code}>
                            [{c.code}] {c.displayName}
                          </option>
                        ))}
                      </select>
                    </Field>
                    <Field label="Wage type" required={compensationHasData(compForm)}>
                      <select
                        className={inputCls}
                        value={compForm.wageType}
                        onChange={(e) => patchComp({ wageType: e.target.value as TenantEmployeeCompensationPayload["wageType"] })}
                      >
                        {WAGE_TYPES.map((w) => (
                          <option key={w.value} value={w.value}>{w.label}</option>
                        ))}
                      </select>
                    </Field>
                  </Row>
                  <Row>
                    <Field label={wageTypeMeta.perLabel} required={compensationHasData(compForm)}>
                      <input
                        type="text"
                        inputMode="decimal"
                        autoComplete="off"
                        className={inputCls}
                        value={wageAmountInput}
                        onChange={(e) => {
                          const raw = e.target.value;
                          setWageAmountInput(raw);
                          patchComp({ wageAmount: parseWageAmountInput(raw) });
                        }}
                        onBlur={() => setWageAmountInput(formatWageAmountForInput(compForm.wageAmount))}
                      />
                    </Field>
                    <Field
                      label="Work time"
                      hint={
                        compForm.wageType === "PER_HOUR"
                          ? "Required to derive period/yearly salary."
                          : "Hours setup — used to derive hourly rate."
                      }
                    >
                      <select className={inputCls} value={compForm.workTimeId ?? ""} onChange={(e) => patchComp({ workTimeId: e.target.value })}>
                        <option value="">No work time</option>
                        {formWorkTimes.map((w) => (
                          <option key={w.id} value={w.id}>
                            {w.name} ({w.workDaysPerWeek} × {w.hoursPerDay}h)
                          </option>
                        ))}
                      </select>
                    </Field>
                  </Row>
                  {compForm.currencyCode && Number.isFinite(compForm.wageAmount) && compForm.wageAmount > 0 ? (
                    <div className="rounded-md border border-border bg-surface-alt px-4 py-3">
                      <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">Salary rates</p>
                      <div className="grid grid-cols-2 gap-3 text-sm md:grid-cols-4">
                        <DerivedAmount label="Hourly" value={compDerivedRates.derivedHourlyAmount} currency={compForm.currencyCode} digits={4} />
                        <DerivedAmount label="Per period" value={compDerivedRates.derivedPeriodAmount} currency={compForm.currencyCode} digits={2} />
                        <DerivedAmount label="Monthly" value={compDerivedRates.derivedMonthlyAmount} currency={compForm.currencyCode} digits={2} />
                        <DerivedAmount label="Yearly" value={compDerivedRates.derivedYearlyAmount} currency={compForm.currencyCode} digits={2} />
                      </div>
                    </div>
                  ) : null}
                  <Row>
                    <Field label="Notes" wide>
                      <textarea className={`${inputCls} h-20`} value={compForm.notes ?? ""} onChange={(e) => patchComp({ notes: e.target.value })} />
                    </Field>
                  </Row>
                </Section>
              </div>
              {hasStatutoryPanel && statutoryPanel ? (
                <Section title={statutoryPanel.title}>
                  {statutoryPanel.fields.map((field) => (
                    <ToggleRow
                      key={field.key}
                      label={field.label}
                      description={field.description}
                      checked={isCompensationStatutoryFieldChecked(compForm, field)}
                      onChange={(v) => setCompForm(patchCompensationStatutoryField(compForm, field, v))}
                    />
                  ))}
                </Section>
              ) : companyPayrollCountry ? (
                <p className="text-sm text-muted">
                  No statutory compensation options are configured for payroll country {companyPayrollCountry} yet.
                </p>
              ) : null}
            </div>
            {!compensationHasData(compForm) ? (
              <p className="text-xs text-muted">{t("employees.wizard.compensationSkip")}</p>
            ) : compSaved ? (
              <p className="text-xs text-success">{t("employees.wizard.compensationSaved")}</p>
            ) : null}
          </div>
        ) : null}

        {stepIndex === 4 ? (
          !canViewPayment ? (
            <p className="text-sm text-muted">{t("employeePayment.error.forbidden")}</p>
          ) : !createdEmployeeId || !form.companyId ? (
            <p className="text-sm text-muted">{t("employees.hint.paymentNeedsCompany")}</p>
          ) : (
            <div className="space-y-3">
              <p className="text-sm text-muted">{t("employees.wizard.paymentIntro")}</p>
              <EmployeePaymentPanel
                employeeId={createdEmployeeId}
                companyId={form.companyId}
                defaultCurrency={companyCurrency}
              />
            </div>
          )
        ) : null}

        {stepIndex === 5 ? (
          <Section title={t("employees.wizard.step.userAccount")}>
            <p className="text-sm text-muted">{t("employees.wizard.userAccountIntro")}</p>
            {form.email ? (
              <p className="mt-3 text-sm text-foreground">
                {t("employees.wizard.userAccountEmailHint").replace("{email}", form.email)}
              </p>
            ) : null}
            <Link
              href="/app/users"
              className="mt-4 inline-flex text-sm font-medium text-primary underline-offset-4 hover:underline"
            >
              {t("employees.wizard.userAccountLink")}
            </Link>
          </Section>
        ) : null}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          {stepIndex > 0 ? (
            <button
              type="button"
              disabled={busy}
              onClick={goPrev}
              className="rounded border border-border px-4 py-1.5 text-sm font-medium text-foreground hover:bg-surface-alt disabled:opacity-50"
            >
              {t("employees.action.prev")}
            </button>
          ) : (
            <Link
              href="/app/employees"
              className="rounded border border-border px-4 py-1.5 text-sm font-medium text-foreground hover:bg-surface-alt"
            >
              {t("employees.action.cancel")}
            </Link>
          )}
        </div>
        <div className="flex items-center gap-2">
          {isLastStep ? (
            <button
              type="button"
              disabled={busy}
              onClick={() => void finishWizard()}
              className="rounded bg-primary px-4 py-1.5 text-sm font-semibold text-primary-foreground hover:opacity-90 disabled:opacity-50"
            >
              {busy ? "…" : t("employees.wizard.finish")}
            </button>
          ) : (
            <button
              type="button"
              disabled={busy}
              onClick={() => void goNext()}
              className="rounded bg-primary px-4 py-1.5 text-sm font-semibold text-primary-foreground hover:opacity-90 disabled:opacity-50"
            >
              {busy ? "…" : t("employees.action.next")}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

const inputCls =
  "w-full rounded border border-border bg-background px-3 py-1.5 text-sm text-foreground focus:border-primary focus:outline-none";

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-3">
      <h2 className="text-sm font-semibold text-primary">{title}</h2>
      <div className="space-y-3">{children}</div>
    </section>
  );
}

function Row({ children }: { children: ReactNode }) {
  return <div className="grid grid-cols-1 gap-3 md:grid-cols-2">{children}</div>;
}

function Field({
  label,
  required,
  hint,
  wide,
  children,
}: {
  label: string;
  required?: boolean;
  hint?: string;
  wide?: boolean;
  children: ReactNode;
}) {
  return (
    <label className={`block space-y-1 ${wide ? "md:col-span-2" : ""}`}>
      <span className="text-xs font-medium text-foreground">
        {label}
        {required ? <span className="ml-0.5 text-destructive">*</span> : null}
      </span>
      {children}
      {hint ? <span className="block text-[11px] text-muted">{hint}</span> : null}
    </label>
  );
}

function CountrySelect({
  value,
  countries,
  onChange,
}: {
  value: string;
  countries: PlatformCountryRow[];
  onChange: (v: string) => void;
}) {
  return (
    <select className={inputCls} value={value} onChange={(e) => onChange(e.target.value)}>
      <option value="">Select country…</option>
      {countries.map((c) => (
        <option key={c.isoAlpha2} value={c.isoAlpha2}>
          {c.name} ({c.isoAlpha2})
        </option>
      ))}
    </select>
  );
}

function DerivedAmount({
  label,
  value,
  currency,
  digits,
}: {
  label: string;
  value: number | null;
  currency: string;
  digits: number;
}) {
  return (
    <div>
      <div className="text-xs text-muted">{label}</div>
      <div className="font-mono text-sm font-semibold text-foreground">
        {value == null
          ? "—"
          : `${value.toLocaleString(undefined, { minimumFractionDigits: digits, maximumFractionDigits: digits })} ${currency}`}
      </div>
    </div>
  );
}

function ToggleRow({
  label,
  description,
  checked,
  onChange,
}: {
  label: string;
  description?: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <div className="flex items-start justify-between gap-3 py-1">
      <div>
        <div className="text-sm font-medium text-foreground">{label}</div>
        {description ? <div className="text-[11px] text-muted">{description}</div> : null}
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={`relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors ${
          checked ? "bg-primary" : "bg-border"
        }`}
      >
        <span
          className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${
            checked ? "translate-x-4" : "translate-x-0.5"
          }`}
        />
      </button>
    </div>
  );
}
