"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";

import { EmployeePaymentPanel } from "@/components/employee/EmployeePaymentPanel";
import { EmployeePayrollInputsPanel } from "@/components/employee/EmployeePayrollInputsPanel";
import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { EntityDocumentsTab } from "@/components/ui/EntityDocumentsTab";
import { PlatformDateInput } from "@/components/ui/PlatformDateInput";
import { showToast } from "@/components/ui/Toast";
import {
  deleteTenantEmployee,
  fetchAllCountries,
  fetchTenantCompanies,
  fetchTenantCurrencies,
  fetchTenantDepartments,
  fetchTenantEmployee,
  fetchTenantEmployeeCompensation,
  fetchTenantEmployeeGroups,
  fetchTenantJobs,
  fetchTenantWorkTimes,
  putTenantEmployee,
  putTenantEmployeeCompensation,
  type PlatformCountryRow,
  type TenantCompanyItem,
  type TenantCurrencyItem,
  type TenantDepartmentItem,
  type TenantEmployeeCompensationItem,
  type TenantEmployeeCompensationPayload,
  type TenantEmployeeGroupItem,
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
import {
  DEFAULT_EMPLOYEE_EDIT_TAB_SLUG,
  employeeEditHref,
  employeeEditTabFromSlug,
  type EmployeeEditTabId,
} from "@/lib/employee-edit-tabs";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "notFound" | "error";
type TabId = EmployeeEditTabId;

const WAGE_TYPES: { value: TenantEmployeeCompensationPayload["wageType"]; label: string; perLabel: string }[] = [
  { value: "PER_HOUR", label: "Per hour", perLabel: "Wage per hour" },
  { value: "PER_PERIOD", label: "Per period", perLabel: "Wage per period" },
];

function normalizeCompensationWageType(
  wageType: TenantEmployeeCompensationItem["wageType"],
): TenantEmployeeCompensationPayload["wageType"] {
  return wageType === "PER_HOUR" ? "PER_HOUR" : "PER_PERIOD";
}

const STATUSES = ["ACTIVE", "ON_LEAVE", "TERMINATED", "INACTIVE"];
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

function emptyPayload(): TenantEmployeeUpsertPayload {
  return {
    companyId: "",
    departmentId: "",
    jobId: "",
    employeeGroupId: "",
    firstName: "",
    lastName: "",
    hireDate: "",
    status: "ACTIVE",
    active: true,
  };
}

export default function EmployeeEditPage() {
  const router = useRouter();
  const { id, tab: tabSlug } = useParams<{ id: string; tab: string }>();
  const tab = employeeEditTabFromSlug(tabSlug);
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  useEffect(() => {
    if (!id) {
      return;
    }
    if (!tabSlug || !tab) {
      router.replace(`/app/employees/${id}/edit/${DEFAULT_EMPLOYEE_EDIT_TAB_SLUG}`);
    }
  }, [id, tabSlug, tab, router]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [departments, setDepartments] = useState<TenantDepartmentItem[]>([]);
  const [jobs, setJobs] = useState<TenantJobItem[]>([]);
  const [groups, setGroups] = useState<TenantEmployeeGroupItem[]>([]);
  const [countries, setCountries] = useState<PlatformCountryRow[]>([]);
  const [currencies, setCurrencies] = useState<TenantCurrencyItem[]>([]);
  const [workTimes, setWorkTimes] = useState<TenantWorkTimeItem[]>([]);
  const [form, setForm] = useState<TenantEmployeeUpsertPayload>(emptyPayload());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [compForm, setCompForm] = useState<TenantEmployeeCompensationPayload>({
    currencyCode: "",
    wageType: "PER_PERIOD",
    wageAmount: 0,
    workTimeId: "",
    applyTaxes: true,
    applyTaxExempt: true,
    applyAov: true,
    notes: "",
  });
  const [compInitialized, setCompInitialized] = useState(false);
  const [compBusy, setCompBusy] = useState(false);
  const [compError, setCompError] = useState<string | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);

  const canManage = me.privileges.includes("EMPLOYEE_MANAGE");
  const canViewPayrollInputs = me.privileges.includes("EMPLOYEE_PAYROLL_STANDING_VIEW");
  const canViewPayment = me.privileges.includes("EMPLOYEE_PAYMENT_VIEW");

  const companyCurrency = useMemo(
    () => companies.find((c) => c.id === form.companyId)?.currency ?? "SRD",
    [companies, form.companyId],
  );

  const companyPayrollCountry = useMemo(
    () => companies.find((c) => c.id === form.companyId)?.payrollCountry ?? "",
    [companies, form.companyId],
  );

  useEffect(() => {
    if (!canManage) return;
    void (async () => {
      setLoad("loading");
      const [cr, dr, jr, gr, er, countriesR, currenciesR, workTimesR, compR] = await Promise.all([
        fetchTenantCompanies({ size: 100 }),
        fetchTenantDepartments({ size: 200 }),
        fetchTenantJobs({ size: 200 }),
        fetchTenantEmployeeGroups({ size: 200 }),
        fetchTenantEmployee(id),
        fetchAllCountries({ locale: me.locale }),
        fetchTenantCurrencies(),
        fetchTenantWorkTimes({ size: 200, active: true }),
        fetchTenantEmployeeCompensation(id),
      ]);

      if (!cr.ok || !dr.ok || !jr.ok || !gr.ok) {
        setLoad("error");
        return;
      }
      if (!er.ok) {
        setLoad(er.status === 403 ? "forbidden" : er.status === 404 ? "notFound" : "error");
        return;
      }
      if (er.item.status === "DRAFT") {
        router.replace(`/app/employees/new?draft=${encodeURIComponent(id)}`);
        return;
      }

      setCompanies(cr.items);
      setDepartments(dr.items);
      setJobs(jr.items);
      setGroups(gr.items);
      if (countriesR.ok) setCountries(countriesR.items);
      if (currenciesR.ok) setCurrencies(currenciesR.items.filter((c) => c.assigned));
      if (workTimesR.ok) setWorkTimes(workTimesR.items);
      if (compR.ok) {
        setCompForm({
          currencyCode: compR.item.currencyCode,
          wageType: normalizeCompensationWageType(compR.item.wageType),
          wageAmount: compR.item.wageAmount,
          workTimeId: compR.item.workTimeId ?? "",
          applyTaxes: compR.item.applyTaxes,
          applyTaxExempt: compR.item.applyTaxExempt,
          applyAov: compR.item.applyAov,
          notes: compR.item.notes ?? "",
        });
        setCompInitialized(true);
      } else {
        setCompInitialized(false);
      }
      setForm({
        companyId: er.item.companyId,
        departmentId: er.item.departmentId,
        jobId: er.item.jobId,
        employeeGroupId: er.item.employeeGroupId ?? "",
        firstName: er.item.firstName,
        lastName: er.item.lastName,
        dateOfBirth: er.item.dateOfBirth ?? "",
        hireDate: er.item.hireDate,
        email: er.item.email ?? "",
        phone: er.item.phone ?? "",
        status: er.item.status,
        active: er.item.active,
        badgeNumber: er.item.badgeNumber ?? "",
        idNumber: er.item.idNumber ?? "",
        gender: er.item.gender ?? "",
        nationality: er.item.nationality ?? "",
        placeOfBirth: er.item.placeOfBirth ?? "",
        civilState: er.item.civilState ?? "",
        resignationDate: er.item.resignationDate ?? "",
        addressStreet: er.item.addressStreet ?? "",
        addressNumber: er.item.addressNumber ?? "",
        addressCity: er.item.addressCity ?? "",
        addressCountry: er.item.addressCountry ?? "",
        addressPostalCode: er.item.addressPostalCode ?? "",
      });
      setLoad("ready");
    })();
  }, [canManage, id, me.locale]);

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
  }, [
    companies,
    workTimes,
    form.companyId,
    compForm.wageType,
    compForm.wageAmount,
    compForm.workTimeId,
  ]);

  function patch(values: Partial<TenantEmployeeUpsertPayload>) {
    setForm((prev) => ({ ...prev, ...values }));
  }

  async function onCompSubmit(e: FormEvent) {
    e.preventDefault();
    if (!compForm.currencyCode) { setCompError("Currency is required."); return; }
    if (!compForm.wageType) { setCompError("Wage type is required."); return; }
    if (!Number.isFinite(compForm.wageAmount) || compForm.wageAmount <= 0) {
      setCompError("Wage amount must be greater than zero.");
      return;
    }

    setCompBusy(true);
    setCompError(null);
    try {
      await putTenantEmployeeCompensation(id, {
        ...compForm,
        workTimeId: compForm.workTimeId?.toString().trim() || null,
        notes: compForm.notes?.toString().trim() || null,
      });
      setCompInitialized(true);
      showToast("Compensation saved.");
    } catch (err) {
      const msg = err instanceof Error && err.message ? err.message : "Failed to save compensation.";
      setCompError(msg);
    } finally {
      setCompBusy(false);
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!form.companyId) { setError("Company is required."); return; }
    if (!form.departmentId) { setError("Department is required."); return; }
    if (!form.jobId) { setError("Job is required."); return; }
    if (!form.firstName.trim()) { setError("First name is required."); return; }
    if (!form.lastName.trim()) { setError("Last name is required."); return; }
    if (!form.hireDate) { setError("Hire date is required."); return; }

    setBusy(true);
    setError(null);
    try {
      await putTenantEmployee(id, {
        ...form,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        dateOfBirth: form.dateOfBirth?.toString().trim() || null,
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
      });
      showToast(`"${form.firstName.trim()} ${form.lastName.trim()}" updated successfully.`);
      router.push("/app/employees");
    } catch (e) {
      const msg = e instanceof Error && e.message ? e.message : t("employees.msg.saveFailed");
      setError(msg);
      setBusy(false);
    }
  }

  async function confirmDelete() {
    setDeleteBusy(true);
    try {
      await deleteTenantEmployee(id);
      showToast(`"${form.firstName.trim()} ${form.lastName.trim()}" deleted.`);
      router.push("/app/employees");
    } catch (err) {
      const msg = err instanceof Error && err.message ? err.message : t("employees.msg.deleteFailed");
      showToast(msg, "error");
      setDeleteBusy(false);
      setDeleteOpen(false);
    }
  }

  if (!canManage) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("employees.action.edit")}</h1>
        <p className="text-sm text-muted">{t("employees.error.forbidden")}</p>
        <Link href="/app/employees" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}{t("employees.title")}
        </Link>
      </div>
    );
  }

  if (!tab) {
    return <p className="mx-auto max-w-xl text-sm text-muted">{t("employees.state.loading")}</p>;
  }

  if (load === "loading") {
    return <p className="mx-auto max-w-xl text-sm text-muted">{t("employees.state.loading")}</p>;
  }

  if (load !== "ready") {
    const message = load === "notFound" ? "Employee not found." : t("employees.error.load");
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("employees.action.edit")}</h1>
        <p className="text-sm text-muted">{message}</p>
        <Link href="/app/employees" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}{t("employees.title")}
        </Link>
      </div>
    );
  }

  const headline = [form.firstName, form.lastName].filter(Boolean).join(" ");
  const badge = form.badgeNumber?.toString().trim();

  return (
    <div className="mx-auto max-w-7xl space-y-4" data-testid="employee-form-edit">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav className="flex items-center gap-1 text-sm text-muted" aria-label="Breadcrumb">
          <Link href="/app/employees" className="font-medium text-primary underline-offset-4 hover:underline">
            {t("employees.title")}
          </Link>
          <span aria-hidden="true">›</span>
          <span className="font-medium text-foreground">
            {badge ? `[${badge}] ` : ""}{headline || t("employees.action.edit")}
          </span>
        </nav>
        <div className="flex items-center gap-2">
          {tab === "compensation" ? (
            <button
              type="submit"
              form="employee-comp-form"
              disabled={compBusy}
              className="rounded bg-primary px-4 py-1.5 text-sm font-semibold text-primary-foreground hover:opacity-90 disabled:opacity-50"
            >
              {compBusy ? "Saving…" : t("employees.action.save")}
            </button>
          ) : tab !== "documents" && tab !== "payrollInput" && tab !== "payment" ? (
            <button
              type="submit"
              form="employee-edit-form"
              disabled={busy}
              className="rounded bg-primary px-4 py-1.5 text-sm font-semibold text-primary-foreground hover:opacity-90 disabled:opacity-50"
            >
              {busy ? "Saving…" : t("employees.action.save")}
            </button>
          ) : null}
          {tab !== "payrollInput" && tab !== "payment" ? (
            <Link href="/app/employees" className="rounded border border-border px-4 py-1.5 text-sm font-medium text-foreground hover:bg-surface-alt">
              {t("employees.action.cancel")}
            </Link>
          ) : null}
          <button
            type="button"
            onClick={() => setDeleteOpen(true)}
            className="rounded border border-destructive/40 px-4 py-1.5 text-sm font-medium text-destructive hover:bg-destructive/5"
          >
            {t("employees.action.delete")}
          </button>
        </div>
      </div>

      <ConfirmDialog
        open={deleteOpen}
        title={t("employees.delete.title")}
        description={t("employees.delete.description").replace("{name}", headline || t("employees.action.edit"))}
        confirmLabel={t("employees.action.delete")}
        busy={deleteBusy}
        onConfirm={() => void confirmDelete()}
        onCancel={() => setDeleteOpen(false)}
      />

      {error ? (
        <div className="rounded-md border border-destructive/40 bg-destructive/5 px-4 py-2 text-sm text-destructive">
          {error}
        </div>
      ) : null}

      <div className="grid grid-cols-1 gap-4 md:grid-cols-[200px_1fr]">
        <aside className="space-y-1">
          <SideTab id="basic" employeeId={id} active={tab} label="Employee" />
          <SideTab id="employment" employeeId={id} active={tab} label="Employment" />
          <SideTab id="compensation" employeeId={id} active={tab} label="Compensation" />
          <SideTab id="payment" employeeId={id} active={tab} label={t("employees.tab.payment")} />
          <SideTab id="payrollInput" employeeId={id} active={tab} label={t("employees.tab.payrollInput")} />
          <SideTab id="contact" employeeId={id} active={tab} label="Contact information" />
          <SideTab id="documents" employeeId={id} active={tab} label="Documents" />
        </aside>

        <div>
          {tab === "documents" ? (
            <EntityDocumentsTab entityType="EMPLOYEE" entityId={id} canEdit={canManage} />
          ) : tab === "compensation" ? (
            <CompensationPanel
              form={compForm}
              onChange={setCompForm}
              currencies={currencies}
              workTimes={workTimes.filter((w) => w.companyId === form.companyId)}
              derivedRates={compDerivedRates}
              currencyCode={compForm.currencyCode}
              payrollCountry={companyPayrollCountry}
              initialized={compInitialized}
              busy={compBusy}
              error={compError}
              onSubmit={(e) => void onCompSubmit(e)}
            />
          ) : tab === "payment" ? (
            !canViewPayment ? (
              <p className="text-sm text-muted">{t("employeePayment.error.forbidden")}</p>
            ) : !form.companyId ? (
              <p className="text-sm text-muted">{t("employees.hint.paymentNeedsCompany")}</p>
            ) : (
              <EmployeePaymentPanel employeeId={id} companyId={form.companyId} defaultCurrency={companyCurrency} />
            )
          ) : tab === "payrollInput" ? (
            !canViewPayrollInputs ? (
              <p className="text-sm text-muted">{t("employeePayrollInputs.state.forbidden")}</p>
            ) : !form.companyId ? (
              <p className="text-sm text-muted">{t("employees.hint.payrollInputNeedsCompany")}</p>
            ) : (
              <EmployeePayrollInputsPanel fixedCompanyId={form.companyId} fixedEmployeeId={id} />
            )
          ) : (
            <form id="employee-edit-form" onSubmit={(e) => void onSubmit(e)} className="space-y-4">
              {tab === "basic" ? (
                <Section title="Basic information">
                  <Row>
                    <Field label="First name" required>
                      <input className={inputCls} value={form.firstName} onChange={(e) => patch({ firstName: e.target.value })} />
                    </Field>
                    <Field label="Last name" required>
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
                        {GENDERS.map((g) => <option key={g.value} value={g.value}>{g.label}</option>)}
                      </select>
                    </Field>
                  </Row>
                  <Row>
                    <Field label="Date of birth">
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
                        {CIVIL_STATES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
                      </select>
                    </Field>
                  </Row>
                  <Row>
                    <Field label="Email">
                      <input type="email" className={inputCls} value={form.email ?? ""} onChange={(e) => patch({ email: e.target.value })} />
                    </Field>
                    <div />
                  </Row>
                </Section>
              ) : null}

              {tab === "employment" ? (
                <>
                  <div className="rounded-md border-l-4 border-primary bg-primary/5 px-4 py-3 text-sm text-foreground">
                    <p className="font-semibold">Active state and resignation date</p>
                    <p className="mt-1 text-muted">
                      The employee stays active until the resignation date. After the final payroll is processed past
                      that date the system flips the employee to inactive. Inactive employees without a resignation
                      date do not participate in payroll.
                    </p>
                  </div>
                  <Section title="Employment">
                    <Row>
                      <Field label="Active">
                        <ToggleSwitch checked={form.active !== false} onChange={(v) => patch({ active: v })} />
                      </Field>
                      <Field label="Badge number">
                        <input className={inputCls} value={form.badgeNumber ?? ""} onChange={(e) => patch({ badgeNumber: e.target.value })} />
                      </Field>
                    </Row>
                    <Row>
                      <Field label="Employment date" required hint="Original hire date.">
                        <PlatformDateInput value={form.hireDate} dateFormat={me.dateFormat} onChange={(v) => patch({ hireDate: v })} />
                      </Field>
                      <Field label="Resignation date" hint="Last working day. Leave empty if the employee is still employed.">
                        <PlatformDateInput value={form.resignationDate ?? ""} dateFormat={me.dateFormat} onChange={(v) => patch({ resignationDate: v })} />
                      </Field>
                    </Row>
                    <Row>
                      <Field label="Company" required>
                        <select
                          className={inputCls}
                          value={form.companyId}
                          onChange={(e) => patch({ companyId: e.target.value, departmentId: "", jobId: "", employeeGroupId: "" })}
                        >
                          <option value="">Select company…</option>
                          {companies.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                        </select>
                      </Field>
                      <Field label="Employee group" hint="Optional. Assign a group later from Employee groups.">
                        <select className={inputCls} value={form.employeeGroupId ?? ""} onChange={(e) => patch({ employeeGroupId: e.target.value })}>
                          <option value="">Select group…</option>
                          {formGroups.map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
                        </select>
                      </Field>
                    </Row>
                    <Row>
                      <Field label="Department" required>
                        <select className={inputCls} value={form.departmentId} onChange={(e) => patch({ departmentId: e.target.value, jobId: "" })}>
                          <option value="">Select department…</option>
                          {formDepartments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
                        </select>
                      </Field>
                      <Field label="Position" required>
                        <select className={inputCls} value={form.jobId} onChange={(e) => patch({ jobId: e.target.value })}>
                          <option value="">Select job…</option>
                          {formJobs.map((j) => <option key={j.id} value={j.id}>{j.title}</option>)}
                        </select>
                      </Field>
                    </Row>
                    <Row>
                      <Field label="Status" required>
                        <select className={inputCls} value={form.status} onChange={(e) => patch({ status: e.target.value })}>
                          {STATUSES.map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
                        </select>
                      </Field>
                      <div />
                    </Row>
                  </Section>
                </>
              ) : null}

              {tab === "contact" ? (
                <Section title="Contact information">
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
                    <Field label="Phone number">
                      <input className={inputCls} value={form.phone ?? ""} onChange={(e) => patch({ phone: e.target.value })} />
                    </Field>
                  </Row>
                </Section>
              ) : null}
            </form>
          )}
        </div>
      </div>
    </div>
  );
}

const inputCls = "w-full rounded border border-border bg-background px-3 py-1.5 text-sm text-foreground focus:border-primary focus:outline-none";

function statusLabel(s: string): string {
  const map: Record<string, string> = {
    ACTIVE: "Active",
    INACTIVE: "Inactive",
    ON_LEAVE: "On leave",
    TERMINATED: "Terminated",
  };
  return map[s] ?? s;
}

function SideTab({
  id,
  employeeId,
  active,
  label,
}: {
  id: TabId;
  employeeId: string;
  active: TabId;
  label: string;
}) {
  const isActive = id === active;
  return (
    <Link
      href={employeeEditHref(employeeId, id)}
      aria-current={isActive ? "page" : undefined}
      className={`block w-full rounded px-3 py-2 text-left text-sm transition-colors ${
        isActive ? "bg-primary/10 font-semibold text-primary" : "text-foreground hover:bg-surface-alt"
      }`}
    >
      {label}
    </Link>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-md border border-border bg-surface">
      <header className="border-b border-border bg-surface-alt px-4 py-2">
        <h2 className="text-sm font-semibold text-primary">{title}</h2>
      </header>
      <div className="space-y-3 p-4">{children}</div>
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

function ToggleSwitch({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
        checked ? "bg-primary" : "bg-border"
      }`}
    >
      <span
        className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${
          checked ? "translate-x-4" : "translate-x-0.5"
        }`}
      />
    </button>
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

function formatWageAmountForInput(amount: number): string {
  if (!Number.isFinite(amount) || amount <= 0) {
    return "";
  }
  return String(amount);
}

function parseWageAmountInput(raw: string): number {
  const trimmed = raw.trim();
  if (!trimmed) {
    return Number.NaN;
  }
  const n = Number.parseFloat(trimmed.replace(",", "."));
  return Number.isFinite(n) ? n : Number.NaN;
}

function CompensationPanel({
  form,
  onChange,
  currencies,
  workTimes,
  derivedRates,
  currencyCode,
  payrollCountry,
  initialized,
  busy,
  error,
  onSubmit,
}: {
  form: TenantEmployeeCompensationPayload;
  onChange: (next: TenantEmployeeCompensationPayload) => void;
  currencies: TenantCurrencyItem[];
  workTimes: TenantWorkTimeItem[];
  derivedRates: ReturnType<typeof deriveCompensationRates>;
  currencyCode: string;
  payrollCountry: string;
  initialized: boolean;
  busy: boolean;
  error: string | null;
  onSubmit: (e: FormEvent) => void;
}) {
  const [wageAmountInput, setWageAmountInput] = useState(() => formatWageAmountForInput(form.wageAmount));

  useEffect(() => {
    if (!initialized) {
      setWageAmountInput("");
      return;
    }
    setWageAmountInput(formatWageAmountForInput(form.wageAmount));
  }, [initialized]);

  function patch(values: Partial<TenantEmployeeCompensationPayload>) {
    onChange({ ...form, ...values });
  }

  const wageTypeMeta = WAGE_TYPES.find((w) => w.value === form.wageType) ?? WAGE_TYPES[0];
  const statutoryPanel = getCompensationStatutoryPanel(payrollCountry);
  const hasStatutoryPanel = statutoryPanel != null && statutoryPanel.fields.length > 0;

  return (
    <form id="employee-comp-form" onSubmit={onSubmit} className="space-y-4">
      {!initialized ? (
        <div className="rounded-md border border-amber-300/60 bg-amber-50 px-4 py-2 text-sm text-amber-800">
          No compensation set yet. Fill the form below and save to make this employee eligible for payroll.
        </div>
      ) : null}
      {error ? (
        <div className="rounded-md border border-destructive/40 bg-destructive/5 px-4 py-2 text-sm text-destructive">
          {error}
        </div>
      ) : null}

      <div className={`grid grid-cols-1 gap-4 ${hasStatutoryPanel ? "md:grid-cols-3" : ""}`}>
        <div className={`space-y-4 ${hasStatutoryPanel ? "md:col-span-2" : ""}`}>
          <Section title="Compensation">
            <Row>
              <Field label="Currency" required>
                <select className={inputCls} value={form.currencyCode} onChange={(e) => patch({ currencyCode: e.target.value })}>
                  <option value="">Select currency…</option>
                  {currencies.map((c) => (
                    <option key={c.code} value={c.code}>
                      [{c.code}] {c.displayName}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Wage type" required>
                <select className={inputCls} value={form.wageType} onChange={(e) => patch({ wageType: e.target.value as TenantEmployeeCompensationPayload["wageType"] })}>
                  {WAGE_TYPES.map((w) => <option key={w.value} value={w.value}>{w.label}</option>)}
                </select>
              </Field>
            </Row>
            <Row>
              <Field label={wageTypeMeta.perLabel} required>
                <input
                  type="text"
                  inputMode="decimal"
                  autoComplete="off"
                  className={inputCls}
                  value={wageAmountInput}
                  onChange={(e) => {
                    const raw = e.target.value;
                    setWageAmountInput(raw);
                    patch({ wageAmount: parseWageAmountInput(raw) });
                  }}
                />
              </Field>
              <Field label="Work time" hint={form.wageType === "PER_HOUR" ? "Required to derive period/yearly salary." : "Used to derive hourly rate."}>
                <select className={inputCls} value={form.workTimeId ?? ""} onChange={(e) => patch({ workTimeId: e.target.value })}>
                  <option value="">No work time</option>
                  {workTimes.map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.name} ({w.workDaysPerWeek} × {w.hoursPerDay}h)
                    </option>
                  ))}
                </select>
              </Field>
            </Row>
            {currencyCode && Number.isFinite(form.wageAmount) && form.wageAmount > 0 ? (
              <div className="rounded-md border border-border bg-surface-alt px-4 py-3">
                <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">Salary rates</p>
                <div className="grid grid-cols-2 gap-3 text-sm md:grid-cols-4">
                  <DerivedAmount label="Hourly" value={derivedRates.derivedHourlyAmount} currency={currencyCode} digits={4} />
                  <DerivedAmount label="Per period" value={derivedRates.derivedPeriodAmount} currency={currencyCode} digits={2} />
                  <DerivedAmount label="Monthly" value={derivedRates.derivedMonthlyAmount} currency={currencyCode} digits={2} />
                  <DerivedAmount label="Yearly" value={derivedRates.derivedYearlyAmount} currency={currencyCode} digits={2} />
                </div>
              </div>
            ) : null}
            <Row>
              <Field label="Notes" wide>
                <textarea className={`${inputCls} h-20`} value={form.notes ?? ""} onChange={(e) => patch({ notes: e.target.value })} />
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
                checked={isCompensationStatutoryFieldChecked(form, field)}
                onChange={(v) => onChange(patchCompensationStatutoryField(form, field, v))}
              />
            ))}
          </Section>
        ) : payrollCountry ? (
          <p className="text-sm text-muted">
            No statutory compensation options are configured for payroll country {payrollCountry} yet.
          </p>
        ) : null}
      </div>

      <p className="text-xs text-muted">
        Saving compensation updates the rate that the payroll engine uses for the next pay-period materialization.
        Historic compensation is preserved on each payroll result snapshot.
      </p>

      {busy ? null : null}
    </form>
  );
}

function DerivedAmount({ label, value, currency, digits }: { label: string; value: number | null; currency: string; digits: number }) {
  return (
    <div>
      <div className="text-xs text-muted">{label}</div>
      <div className="font-mono text-sm font-semibold text-foreground">
        {value == null ? "—" : `${value.toLocaleString(undefined, { minimumFractionDigits: digits, maximumFractionDigits: digits })} ${currency}`}
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
      <ToggleSwitch checked={checked} onChange={onChange} />
    </div>
  );
}
