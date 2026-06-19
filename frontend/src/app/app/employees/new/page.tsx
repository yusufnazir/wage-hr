"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { PlatformDateInput } from "@/components/ui/PlatformDateInput";
import { showToast } from "@/components/ui/Toast";
import {
  createTenantEmployee,
  fetchTenantCompanies,
  fetchTenantDepartments,
  fetchTenantEmployeeGroups,
  fetchTenantJobs,
  type TenantCompanyItem,
  type TenantDepartmentItem,
  type TenantEmployeeGroupItem,
  type TenantEmployeeUpsertPayload,
  type TenantJobItem,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "error";

const STATUSES = ["ACTIVE", "ON_LEAVE", "TERMINATED", "INACTIVE"];

function emptyPayload(companyId = ""): TenantEmployeeUpsertPayload {
  return {
    companyId,
    departmentId: "",
    jobId: "",
    employeeGroupId: "",
    firstName: "",
    lastName: "",
    hireDate: new Date().toISOString().slice(0, 10),
    status: "ACTIVE",
    active: true,
    badgeNumber: "",
  };
}

function statusLabel(s: string): string {
  const map: Record<string, string> = {
    ACTIVE: "Active",
    INACTIVE: "Inactive",
    ON_LEAVE: "On leave",
    TERMINATED: "Terminated",
  };
  return map[s] ?? s;
}

export default function EmployeeNewPage() {
  const router = useRouter();
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [departments, setDepartments] = useState<TenantDepartmentItem[]>([]);
  const [jobs, setJobs] = useState<TenantJobItem[]>([]);
  const [groups, setGroups] = useState<TenantEmployeeGroupItem[]>([]);
  const [form, setForm] = useState<TenantEmployeeUpsertPayload>(emptyPayload());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canManage = me.privileges.includes("EMPLOYEE_MANAGE");

  useEffect(() => {
    if (!canManage) return;
    void (async () => {
      setLoad("loading");
      const [cr, dr, jr, gr] = await Promise.all([
        fetchTenantCompanies({ size: 100 }),
        fetchTenantDepartments({ size: 200 }),
        fetchTenantJobs({ size: 200 }),
        fetchTenantEmployeeGroups({ size: 200 }),
      ]);
      if (!cr.ok || !dr.ok || !jr.ok || !gr.ok) {
        setLoad("error");
        return;
      }
      setCompanies(cr.items);
      setDepartments(dr.items);
      setJobs(jr.items);
      setGroups(gr.items);
      setForm((prev) => ({ ...prev, companyId: cr.items[0]?.id ?? "" }));
      setLoad("ready");
    })();
  }, [canManage]);

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

  function patch(values: Partial<TenantEmployeeUpsertPayload>) {
    setForm((prev) => ({ ...prev, ...values }));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!form.companyId) { setError("Company is required."); return; }
    if (!form.departmentId) { setError("Department is required."); return; }
    if (!form.jobId) { setError("Job is required."); return; }
    if (!form.employeeGroupId) { setError("Employee group is required."); return; }
    if (!form.firstName.trim()) { setError("First name is required."); return; }
    if (!form.lastName.trim()) { setError("Last name is required."); return; }
    if (!form.hireDate) { setError("Hire date is required."); return; }

    setBusy(true);
    setError(null);
    try {
      const created = await createTenantEmployee({
        ...form,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        badgeNumber: form.badgeNumber?.toString().trim() || null,
      });
      showToast(`"${form.firstName.trim()} ${form.lastName.trim()}" created successfully.`);
      router.push(`/app/employees/${created.id}/edit/employee`);
    } catch (err) {
      const msg = err instanceof Error && err.message ? err.message : t("employees.msg.createFailed");
      setError(msg);
      setBusy(false);
    }
  }

  if (!canManage) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("employees.action.new")}</h1>
        <p className="text-sm text-muted">{t("employees.error.forbidden")}</p>
        <Link href="/app/employees" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}{t("employees.title")}
        </Link>
      </div>
    );
  }

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
    <div className="mx-auto max-w-3xl space-y-4" data-testid="employee-form-new">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav className="flex items-center gap-1 text-sm text-muted" aria-label="Breadcrumb">
          <Link href="/app/employees" className="font-medium text-primary underline-offset-4 hover:underline">
            {t("employees.title")}
          </Link>
          <span aria-hidden="true">›</span>
          <span className="font-medium text-foreground">{t("employees.action.new")}</span>
        </nav>
      </div>

      <p className="text-sm text-muted">
        Create the employee with the essentials. You can fill in personal details, contact information and documents
        on the next step.
      </p>

      {error ? (
        <div className="rounded-md border border-destructive/40 bg-destructive/5 px-4 py-2 text-sm text-destructive">
          {error}
        </div>
      ) : null}

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4">
        <Section title="Essentials">
          <Row>
            <Field label="First name" required>
              <input className={inputCls} value={form.firstName} onChange={(e) => patch({ firstName: e.target.value })} />
            </Field>
            <Field label="Last name" required>
              <input className={inputCls} value={form.lastName} onChange={(e) => patch({ lastName: e.target.value })} />
            </Field>
          </Row>
          <Row>
            <Field label="Badge number" hint="Unique employee number within the company. Leave blank to assign later.">
              <input className={inputCls} value={form.badgeNumber ?? ""} onChange={(e) => patch({ badgeNumber: e.target.value })} />
            </Field>
            <Field label="Employment date" required>
              <PlatformDateInput value={form.hireDate} dateFormat={me.dateFormat} onChange={(v) => patch({ hireDate: v })} />
            </Field>
          </Row>
        </Section>

        <Section title="Organization">
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
            <Field label="Employee group" required>
              <select className={inputCls} value={form.employeeGroupId} onChange={(e) => patch({ employeeGroupId: e.target.value })}>
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
            <Field label="Active">
              <label className="flex items-center gap-2 pt-1">
                <input type="checkbox" checked={form.active !== false} onChange={(e) => patch({ active: e.target.checked })} />
                <span className="text-sm text-foreground">Active</span>
              </label>
            </Field>
          </Row>
        </Section>

        <div className="flex items-center gap-2">
          <button type="submit" disabled={busy} className="rounded bg-primary px-4 py-1.5 text-sm font-semibold text-primary-foreground hover:opacity-90 disabled:opacity-50">
            {busy ? "Creating…" : t("employees.action.create")}
          </button>
          <Link href="/app/employees" className="rounded border border-border px-4 py-1.5 text-sm font-medium text-foreground hover:bg-surface-alt">
            {t("employees.action.cancel")}
          </Link>
        </div>
      </form>
    </div>
  );
}

const inputCls = "w-full rounded border border-border bg-background px-3 py-1.5 text-sm text-foreground focus:border-primary focus:outline-none";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-md border border-border bg-surface">
      <header className="border-b border-border bg-surface-alt px-4 py-2">
        <h2 className="text-sm font-semibold text-primary">{title}</h2>
      </header>
      <div className="space-y-3 p-4">{children}</div>
    </section>
  );
}

function Row({ children }: { children: React.ReactNode }) {
  return <div className="grid grid-cols-1 gap-3 md:grid-cols-2">{children}</div>;
}

function Field({
  label,
  required,
  hint,
  children,
}: {
  label: string;
  required?: boolean;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block space-y-1">
      <span className="text-xs font-medium text-foreground">
        {label}
        {required ? <span className="ml-0.5 text-destructive">*</span> : null}
      </span>
      {children}
      {hint ? <span className="block text-[11px] text-muted">{hint}</span> : null}
    </label>
  );
}
