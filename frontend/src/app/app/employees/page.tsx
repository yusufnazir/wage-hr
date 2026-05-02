"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  createTenantEmployee,
  fetchTenantCompanies,
  fetchTenantDepartments,
  fetchTenantEmployeeGroups,
  fetchTenantEmployees,
  fetchTenantJobs,
  patchTenantEmployeeActive,
  patchTenantEmployeeStatus,
  putTenantEmployee,
  type TenantCompanyItem,
  type TenantDepartmentItem,
  type TenantEmployeeGroupItem,
  type TenantEmployeeItem,
  type TenantEmployeeUpsertPayload,
  type TenantJobItem,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";
type ModalMode =
  | { kind: "create" }
  | { kind: "edit"; item: TenantEmployeeItem }
  | { kind: "status"; item: TenantEmployeeItem }
  | null;

const STATUSES = ["ACTIVE", "ON_LEAVE", "TERMINATED", "INACTIVE"];

function emptyPayload(): TenantEmployeeUpsertPayload {
  return {
    companyId: "",
    departmentId: "",
    jobId: "",
    employeeGroupId: "",
    firstName: "",
    lastName: "",
    hireDate: new Date().toISOString().slice(0, 10),
    status: "ACTIVE",
    active: true,
  };
}

function itemToPayload(item: TenantEmployeeItem): TenantEmployeeUpsertPayload {
  return {
    companyId: item.companyId,
    departmentId: item.departmentId,
    jobId: item.jobId,
    employeeGroupId: item.employeeGroupId,
    firstName: item.firstName,
    lastName: item.lastName,
    dateOfBirth: item.dateOfBirth ?? "",
    hireDate: item.hireDate,
    email: item.email ?? "",
    phone: item.phone ?? "",
    status: item.status,
    active: item.active,
  };
}

export default function EmployeesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<TenantEmployeeItem[]>([]);
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [departments, setDepartments] = useState<TenantDepartmentItem[]>([]);
  const [jobs, setJobs] = useState<TenantJobItem[]>([]);
  const [employeeGroups, setEmployeeGroups] = useState<TenantEmployeeGroupItem[]>([]);
  const [selectedCompanyId, setSelectedCompanyId] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [msg, setMsg] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const [modal, setModal] = useState<ModalMode>(null);
  const [form, setForm] = useState<TenantEmployeeUpsertPayload>(emptyPayload());
  const [newStatus, setNewStatus] = useState("ACTIVE");
  const [formBusy, setFormBusy] = useState(false);
  const [formMsg, setFormMsg] = useState<string | null>(null);

  const canManage = me.privileges.includes("EMPLOYEE_MANAGE");

  const reload = useCallback(
    async (p = 0, companyId = selectedCompanyId) => {
      setLoad("loading");
      setMsg(null);
      const r = await fetchTenantEmployees({ page: p, size: 20, companyId: companyId || undefined });
      if (!r.ok) {
        setLoad(r.status === 403 ? "forbidden" : "error");
        return;
      }
      setItems(r.items);
      setTotalPages(r.totalPages);
      setPage(p);
      setLoad("ready");
    },
    [selectedCompanyId],
  );

  useEffect(() => {
    void (async () => {
      const [cr, dr, jr, gr] = await Promise.all([
        fetchTenantCompanies({ size: 100 }),
        fetchTenantDepartments({ size: 200 }),
        fetchTenantJobs({ size: 200 }),
        fetchTenantEmployeeGroups({ size: 200 }),
      ]);
      if (cr.ok) setCompanies(cr.items);
      if (dr.ok) setDepartments(dr.items);
      if (jr.ok) setJobs(jr.items);
      if (gr.ok) setEmployeeGroups(gr.items);
    })();
    void reload(0);
  }, [reload]);

  const formDepartments = departments.filter((d) => !form.companyId || d.companyId === form.companyId);
  const formJobs = jobs.filter((j) => !form.departmentId || j.departmentId === form.departmentId);
  const formGroups = employeeGroups.filter((g) => !form.companyId || g.companyId === form.companyId);

  function openCreate() {
    setForm({ ...emptyPayload(), companyId: selectedCompanyId });
    setFormMsg(null);
    setModal({ kind: "create" });
  }

  function openEdit(item: TenantEmployeeItem) {
    setForm(itemToPayload(item));
    setFormMsg(null);
    setModal({ kind: "edit", item });
  }

  function openStatus(item: TenantEmployeeItem) {
    setNewStatus(item.status);
    setFormMsg(null);
    setModal({ kind: "status", item });
  }

  function closeModal() {
    setModal(null);
    setFormMsg(null);
  }

  async function handleSubmit() {
    if (modal?.kind === "status") {
      setFormBusy(true);
      setFormMsg(null);
      try {
        await patchTenantEmployeeStatus(modal.item.id, newStatus);
        setMsg(t("employees.msg.statusUpdated"));
        closeModal();
        await reload(page);
      } catch {
        setFormMsg(t("employees.msg.statusFailed"));
      } finally {
        setFormBusy(false);
      }
      return;
    }

    if (!form.companyId) { setFormMsg("Company is required."); return; }
    if (!form.departmentId) { setFormMsg("Department is required."); return; }
    if (!form.jobId) { setFormMsg("Job is required."); return; }
    if (!form.employeeGroupId) { setFormMsg("Employee group is required."); return; }
    if (!form.firstName.trim()) { setFormMsg("First name is required."); return; }
    if (!form.lastName.trim()) { setFormMsg("Last name is required."); return; }
    if (!form.hireDate) { setFormMsg("Hire date is required."); return; }
    setFormBusy(true);
    setFormMsg(null);
    try {
      const payload: TenantEmployeeUpsertPayload = {
        ...form,
        dateOfBirth: form.dateOfBirth?.toString().trim() || null,
        email: form.email?.toString().trim() || null,
        phone: form.phone?.toString().trim() || null,
      };
      if (modal?.kind === "create") {
        await createTenantEmployee(payload);
        setMsg(t("employees.msg.created"));
      } else if (modal?.kind === "edit") {
        await putTenantEmployee(modal.item.id, payload);
        setMsg(t("employees.msg.saved"));
      }
      closeModal();
      await reload(page);
    } catch (e) {
      setFormMsg(modal?.kind === "create" ? t("employees.msg.createFailed") : t("employees.msg.saveFailed"));
      console.error(e);
    } finally {
      setFormBusy(false);
    }
  }

  async function toggleActive(item: TenantEmployeeItem) {
    setBusyId(item.id);
    setMsg(null);
    try {
      await patchTenantEmployeeActive(item.id, !item.active);
      await reload(page);
    } catch {
      setMsg(t("employees.msg.saveFailed"));
    } finally {
      setBusyId(null);
    }
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("employees.title")}</h1>
        <p className="text-sm text-muted">{t("employees.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">← {t("nav.dashboard")}</Link>
      </div>
    );
  }

  const deptName = (id: string) => departments.find((d) => d.id === id)?.name ?? id;
  const jobTitle = (id: string) => jobs.find((j) => j.id === id)?.title ?? id;

  const statusLabel = (s: string) => {
    const key = `employees.status.${s.toLowerCase()}`;
    const label = navLabel(me.locale, key);
    return label === key ? s : label;
  };

  return (
    <div className="mx-auto max-w-5xl space-y-6" data-testid="employees-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("employees.title")}</h1>
        <div className="flex gap-3 text-sm">
          <Link href="/app/departments" className="font-medium text-primary underline-offset-4 hover:underline">
            ← {t("departments.title")}
          </Link>
          {canManage && (
            <button onClick={openCreate} className="rounded bg-primary px-3 py-1 text-sm font-medium text-primary-foreground hover:opacity-90">
              {t("employees.action.new")}
            </button>
          )}
        </div>
      </div>

      <div className="flex flex-wrap gap-3 text-sm">
        <label className="flex items-center gap-2 text-muted">
          {t("employees.label.companyId")}:
          <select
            className="rounded border border-border bg-surface px-2 py-1 text-foreground"
            value={selectedCompanyId}
            onChange={(e) => {
              setSelectedCompanyId(e.target.value);
              void reload(0, e.target.value);
            }}
          >
            <option value="">All companies</option>
            {companies.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </label>
      </div>

      {msg && <p className="text-sm text-foreground">{msg}</p>}
      {load === "loading" && <p className="text-sm text-muted">{t("employees.state.loading")}</p>}
      {load === "error" && <p className="text-sm text-destructive">{t("employees.error.load")}</p>}

      {load === "ready" && (
        <>
          {items.length === 0 ? (
            <p className="text-sm text-muted">{t("employees.state.empty")}</p>
          ) : (
            <div className="overflow-x-auto rounded-md border border-border">
              <table className="min-w-full divide-y divide-border text-sm">
                <thead className="bg-surface-alt">
                  <tr>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("employees.col.name")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("employees.col.email")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("employees.col.department")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("employees.col.job")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("employees.col.status")}</th>
                    {canManage && <th className="px-4 py-2" />}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface">
                  {items.map((item) => (
                    <tr key={item.id}>
                      <td className="px-4 py-2 font-medium text-foreground">{item.firstName} {item.lastName}</td>
                      <td className="px-4 py-2 text-muted">{item.email ?? "—"}</td>
                      <td className="px-4 py-2 text-muted">{deptName(item.departmentId)}</td>
                      <td className="px-4 py-2 text-muted">{jobTitle(item.jobId)}</td>
                      <td className="px-4 py-2">
                        <span className={
                          item.status === "ACTIVE"
                            ? "rounded px-1.5 py-0.5 text-xs font-medium bg-success/10 text-success"
                            : item.status === "TERMINATED"
                            ? "rounded px-1.5 py-0.5 text-xs font-medium bg-destructive/10 text-destructive"
                            : "rounded px-1.5 py-0.5 text-xs font-medium bg-muted/20 text-muted"
                        }>
                          {statusLabel(item.status)}
                        </span>
                      </td>
                      {canManage && (
                        <td className="px-4 py-2 text-right">
                          <button onClick={() => openEdit(item)} className="mr-2 text-sm text-primary underline-offset-4 hover:underline">{t("employees.action.edit")}</button>
                          <button onClick={() => openStatus(item)} className="mr-2 text-sm text-primary underline-offset-4 hover:underline">Status</button>
                          <button onClick={() => void toggleActive(item)} disabled={busyId === item.id} className="text-sm text-muted underline-offset-4 hover:underline disabled:opacity-50">
                            {item.active ? "Deactivate" : "Activate"}
                          </button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {totalPages > 1 && (
            <div className="flex gap-2 text-sm">
              <button onClick={() => void reload(page - 1)} disabled={page === 0} className="rounded border border-border px-3 py-1 disabled:opacity-40">{t("employees.action.prev")}</button>
              <span className="py-1 text-muted">{page + 1} / {totalPages}</span>
              <button onClick={() => void reload(page + 1)} disabled={page >= totalPages - 1} className="rounded border border-border px-3 py-1 disabled:opacity-40">{t("employees.action.next")}</button>
            </div>
          )}
        </>
      )}

      {modal && modal.kind === "status" && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-sm rounded-lg border border-border bg-surface p-6 shadow-xl">
            <h2 className="mb-4 text-base font-semibold text-foreground">
              Update status — {modal.item.firstName} {modal.item.lastName}
            </h2>
            <label className="block text-sm">
              <span className="text-muted">{t("employees.label.status")}</span>
              <select
                className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                value={newStatus}
                onChange={(e) => setNewStatus(e.target.value)}
              >
                {STATUSES.map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
              </select>
            </label>
            {formMsg && <p className="mt-3 text-sm text-destructive">{formMsg}</p>}
            <div className="mt-4 flex justify-end gap-2">
              <button onClick={closeModal} disabled={formBusy} className="rounded border border-border px-3 py-1.5 text-sm hover:bg-surface-alt disabled:opacity-40">{t("employees.action.cancel")}</button>
              <button onClick={() => void handleSubmit()} disabled={formBusy} className="rounded bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40">
                {t("employees.action.save")}
              </button>
            </div>
          </div>
        </div>
      )}

      {modal && (modal.kind === "create" || modal.kind === "edit") && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-lg border border-border bg-surface p-6 shadow-xl overflow-y-auto max-h-[90vh]">
            <h2 className="mb-4 text-base font-semibold text-foreground">
              {modal.kind === "create" ? t("employees.action.new") : t("employees.action.edit")}
            </h2>
            <div className="space-y-3 text-sm">
              <label className="block">
                <span className="text-muted">{t("employees.label.companyId")} *</span>
                <select
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.companyId}
                  onChange={(e) => setForm({ ...form, companyId: e.target.value, departmentId: "", jobId: "", employeeGroupId: "" })}
                >
                  <option value="">Select company…</option>
                  {companies.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </label>
              <label className="block">
                <span className="text-muted">{t("employees.label.departmentId")} *</span>
                <select
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.departmentId}
                  onChange={(e) => setForm({ ...form, departmentId: e.target.value, jobId: "" })}
                >
                  <option value="">Select department…</option>
                  {formDepartments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
                </select>
              </label>
              <label className="block">
                <span className="text-muted">{t("employees.label.jobId")} *</span>
                <select
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.jobId}
                  onChange={(e) => setForm({ ...form, jobId: e.target.value })}
                >
                  <option value="">Select job…</option>
                  {formJobs.map((j) => <option key={j.id} value={j.id}>{j.title}</option>)}
                </select>
              </label>
              <label className="block">
                <span className="text-muted">{t("employees.label.employeeGroupId")} *</span>
                <select
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.employeeGroupId}
                  onChange={(e) => setForm({ ...form, employeeGroupId: e.target.value })}
                >
                  <option value="">Select group…</option>
                  {formGroups.map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
                </select>
              </label>
              <div className="grid grid-cols-2 gap-3">
                <label className="block">
                  <span className="text-muted">{t("employees.label.firstName")} *</span>
                  <input className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground" value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} />
                </label>
                <label className="block">
                  <span className="text-muted">{t("employees.label.lastName")} *</span>
                  <input className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground" value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} />
                </label>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <label className="block">
                  <span className="text-muted">{t("employees.label.hireDate")} *</span>
                  <input type="date" className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground" value={form.hireDate} onChange={(e) => setForm({ ...form, hireDate: e.target.value })} />
                </label>
                <label className="block">
                  <span className="text-muted">{t("employees.label.dateOfBirth")}</span>
                  <input type="date" className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground" value={form.dateOfBirth ?? ""} onChange={(e) => setForm({ ...form, dateOfBirth: e.target.value })} />
                </label>
              </div>
              <label className="block">
                <span className="text-muted">{t("employees.label.email")}</span>
                <input type="email" className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground" value={form.email ?? ""} onChange={(e) => setForm({ ...form, email: e.target.value })} />
              </label>
              <label className="block">
                <span className="text-muted">{t("employees.label.status")} *</span>
                <select
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.status}
                  onChange={(e) => setForm({ ...form, status: e.target.value })}
                >
                  {STATUSES.map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
                </select>
              </label>
              <label className="flex items-center gap-2">
                <input type="checkbox" checked={form.active !== false} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
                <span className="text-muted">{t("employees.label.active")}</span>
              </label>
            </div>
            {formMsg && <p className="mt-3 text-sm text-destructive">{formMsg}</p>}
            <div className="mt-4 flex justify-end gap-2">
              <button onClick={closeModal} disabled={formBusy} className="rounded border border-border px-3 py-1.5 text-sm hover:bg-surface-alt disabled:opacity-40">{t("employees.action.cancel")}</button>
              <button onClick={() => void handleSubmit()} disabled={formBusy} className="rounded bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40">
                {modal.kind === "create" ? t("employees.action.create") : t("employees.action.save")}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
