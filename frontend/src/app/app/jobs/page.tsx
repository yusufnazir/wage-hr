"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  createTenantJob,
  fetchTenantCompanies,
  fetchTenantDepartments,
  fetchTenantJobs,
  patchTenantJobActive,
  putTenantJob,
  type TenantCompanyItem,
  type TenantDepartmentItem,
  type TenantJobItem,
  type TenantJobUpsertPayload,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";
type ModalMode = { kind: "create" } | { kind: "edit"; item: TenantJobItem } | null;

function emptyPayload(): TenantJobUpsertPayload {
  return { companyId: "", departmentId: "", title: "", code: "", salaryType: "MONTHLY", active: true };
}

function itemToPayload(item: TenantJobItem): TenantJobUpsertPayload {
  return {
    companyId: item.companyId,
    departmentId: item.departmentId,
    title: item.title,
    code: item.code,
    description: item.description ?? "",
    salaryType: item.salaryType,
    defaultSalary: item.defaultSalary,
    defaultHourlyRate: item.defaultHourlyRate,
    standardHoursPerWeek: item.standardHoursPerWeek,
    jobLevel: item.jobLevel ?? "",
    jobCategory: item.jobCategory ?? "",
    active: item.active,
  };
}

export default function JobsPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<TenantJobItem[]>([]);
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [departments, setDepartments] = useState<TenantDepartmentItem[]>([]);
  const [selectedCompanyId, setSelectedCompanyId] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [msg, setMsg] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const [modal, setModal] = useState<ModalMode>(null);
  const [form, setForm] = useState<TenantJobUpsertPayload>(emptyPayload());
  const [formBusy, setFormBusy] = useState(false);
  const [formMsg, setFormMsg] = useState<string | null>(null);

  const canManage = me.privileges.includes("JOB_MANAGE");

  const reload = useCallback(
    async (p = 0, companyId = selectedCompanyId) => {
      setLoad("loading");
      setMsg(null);
      const r = await fetchTenantJobs({ page: p, size: 20, companyId: companyId || undefined });
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
      const cr = await fetchTenantCompanies({ size: 100 });
      if (cr.ok) setCompanies(cr.items);
      const dr = await fetchTenantDepartments({ size: 200 });
      if (dr.ok) setDepartments(dr.items);
    })();
    void reload(0);
  }, [reload]);

  // When company changes in form, filter departments
  const formDepartments = departments.filter((d) => !form.companyId || d.companyId === form.companyId);

  function openCreate() {
    setForm(emptyPayload());
    setFormMsg(null);
    setModal({ kind: "create" });
  }

  function openEdit(item: TenantJobItem) {
    setForm(itemToPayload(item));
    setFormMsg(null);
    setModal({ kind: "edit", item });
  }

  function closeModal() {
    setModal(null);
    setFormMsg(null);
  }

  async function handleSubmit() {
    if (!form.companyId) { setFormMsg("Company is required."); return; }
    if (!form.departmentId) { setFormMsg("Department is required."); return; }
    if (!form.title.trim()) { setFormMsg("Title is required."); return; }
    if (!form.code.trim()) { setFormMsg("Code is required."); return; }
    if (form.salaryType === "MONTHLY" && (form.defaultSalary ?? 0) <= 0) {
      setFormMsg("Monthly salary requires a default salary > 0.");
      return;
    }
    if (form.salaryType === "HOURLY" && (form.defaultHourlyRate ?? 0) <= 0) {
      setFormMsg("Hourly salary type requires a default hourly rate > 0.");
      return;
    }
    setFormBusy(true);
    setFormMsg(null);
    try {
      const payload: TenantJobUpsertPayload = {
        ...form,
        description: form.description?.toString().trim() || null,
        jobLevel: form.jobLevel?.toString().trim() || null,
        jobCategory: form.jobCategory?.toString().trim() || null,
      };
      if (modal?.kind === "create") {
        await createTenantJob(payload);
        setMsg(t("jobs.msg.created"));
      } else if (modal?.kind === "edit") {
        await putTenantJob(modal.item.id, payload);
        setMsg(t("jobs.msg.saved"));
      }
      closeModal();
      await reload(page);
    } catch (e) {
      setFormMsg(modal?.kind === "create" ? t("jobs.msg.createFailed") : t("jobs.msg.saveFailed"));
      console.error(e);
    } finally {
      setFormBusy(false);
    }
  }

  async function toggleActive(item: TenantJobItem) {
    setBusyId(item.id);
    setMsg(null);
    try {
      await patchTenantJobActive(item.id, !item.active);
      await reload(page);
    } catch {
      setMsg(t("jobs.msg.saveFailed"));
    } finally {
      setBusyId(null);
    }
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("jobs.title")}</h1>
        <p className="text-sm text-muted">{t("jobs.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">← {t("nav.dashboard")}</Link>
      </div>
    );
  }

  const deptName = (id: string) => departments.find((d) => d.id === id)?.name ?? id;

  return (
    <div className="mx-auto max-w-5xl space-y-6" data-testid="jobs-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("jobs.title")}</h1>
        <div className="flex gap-3 text-sm">
          <Link href="/app/departments" className="font-medium text-primary underline-offset-4 hover:underline">
            ← {t("departments.title")}
          </Link>
          {canManage && (
            <button onClick={openCreate} className="rounded bg-primary px-3 py-1 text-sm font-medium text-primary-foreground hover:opacity-90">
              {t("jobs.action.new")}
            </button>
          )}
        </div>
      </div>

      <div className="flex flex-wrap gap-3 text-sm">
        <label className="flex items-center gap-2 text-muted">
          {t("jobs.label.companyId")}:
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
      {load === "loading" && <p className="text-sm text-muted">{t("jobs.state.loading")}</p>}
      {load === "error" && <p className="text-sm text-destructive">{t("jobs.error.load")}</p>}

      {load === "ready" && (
        <>
          {items.length === 0 ? (
            <p className="text-sm text-muted">{t("jobs.state.empty")}</p>
          ) : (
            <div className="overflow-x-auto rounded-md border border-border">
              <table className="min-w-full divide-y divide-border text-sm">
                <thead className="bg-surface-alt">
                  <tr>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("jobs.col.title")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("jobs.col.code")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("jobs.col.department")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("jobs.col.salaryType")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("jobs.col.status")}</th>
                    {canManage && <th className="px-4 py-2" />}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface">
                  {items.map((item) => (
                    <tr key={item.id}>
                      <td className="px-4 py-2 font-medium text-foreground">{item.title}</td>
                      <td className="px-4 py-2 text-muted">{item.code}</td>
                      <td className="px-4 py-2 text-muted">{deptName(item.departmentId)}</td>
                      <td className="px-4 py-2 text-muted">{item.salaryType}</td>
                      <td className="px-4 py-2">
                        <span className={item.active ? "rounded px-1.5 py-0.5 text-xs font-medium bg-success/10 text-success" : "rounded px-1.5 py-0.5 text-xs font-medium bg-muted/20 text-muted"}>
                          {item.active ? t("jobs.status.active") : t("jobs.status.inactive")}
                        </span>
                      </td>
                      {canManage && (
                        <td className="px-4 py-2 text-right">
                          <button onClick={() => openEdit(item)} className="mr-3 text-sm text-primary underline-offset-4 hover:underline">{t("jobs.action.edit")}</button>
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
              <button onClick={() => void reload(page - 1)} disabled={page === 0} className="rounded border border-border px-3 py-1 disabled:opacity-40">{t("jobs.action.prev")}</button>
              <span className="py-1 text-muted">{page + 1} / {totalPages}</span>
              <button onClick={() => void reload(page + 1)} disabled={page >= totalPages - 1} className="rounded border border-border px-3 py-1 disabled:opacity-40">{t("jobs.action.next")}</button>
            </div>
          )}
        </>
      )}

      {modal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-lg border border-border bg-surface p-6 shadow-xl">
            <h2 className="mb-4 text-base font-semibold text-foreground">
              {modal.kind === "create" ? t("jobs.action.new") : t("jobs.action.edit")}
            </h2>
            <div className="space-y-3 text-sm">
              <label className="block">
                <span className="text-muted">{t("jobs.label.companyId")} *</span>
                <select
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.companyId}
                  onChange={(e) => setForm({ ...form, companyId: e.target.value, departmentId: "" })}
                >
                  <option value="">Select company…</option>
                  {companies.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </label>
              <label className="block">
                <span className="text-muted">{t("jobs.label.departmentId")} *</span>
                <select
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.departmentId}
                  onChange={(e) => setForm({ ...form, departmentId: e.target.value })}
                >
                  <option value="">Select department…</option>
                  {formDepartments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
                </select>
              </label>
              <label className="block">
                <span className="text-muted">{t("jobs.label.title")} *</span>
                <input className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
              </label>
              <label className="block">
                <span className="text-muted">{t("jobs.label.code")} *</span>
                <input className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
              </label>
              <label className="block">
                <span className="text-muted">{t("jobs.label.salaryType")} *</span>
                <select
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.salaryType}
                  onChange={(e) => setForm({ ...form, salaryType: e.target.value as "HOURLY" | "MONTHLY" })}
                >
                  <option value="MONTHLY">MONTHLY</option>
                  <option value="HOURLY">HOURLY</option>
                </select>
              </label>
              {form.salaryType === "MONTHLY" && (
                <label className="block">
                  <span className="text-muted">{t("jobs.label.defaultSalary")} *</span>
                  <input type="number" min="0" step="0.01" className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground" value={form.defaultSalary ?? ""} onChange={(e) => setForm({ ...form, defaultSalary: e.target.value ? parseFloat(e.target.value) : null })} />
                </label>
              )}
              {form.salaryType === "HOURLY" && (
                <label className="block">
                  <span className="text-muted">{t("jobs.label.defaultHourlyRate")} *</span>
                  <input type="number" min="0" step="0.01" className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground" value={form.defaultHourlyRate ?? ""} onChange={(e) => setForm({ ...form, defaultHourlyRate: e.target.value ? parseFloat(e.target.value) : null })} />
                </label>
              )}
              <label className="flex items-center gap-2">
                <input type="checkbox" checked={form.active !== false} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
                <span className="text-muted">{t("jobs.label.active")}</span>
              </label>
            </div>
            {formMsg && <p className="mt-3 text-sm text-destructive">{formMsg}</p>}
            <div className="mt-4 flex justify-end gap-2">
              <button onClick={closeModal} disabled={formBusy} className="rounded border border-border px-3 py-1.5 text-sm hover:bg-surface-alt disabled:opacity-40">{t("jobs.action.cancel")}</button>
              <button onClick={() => void handleSubmit()} disabled={formBusy} className="rounded bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40">
                {modal.kind === "create" ? t("jobs.action.create") : t("jobs.action.save")}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
