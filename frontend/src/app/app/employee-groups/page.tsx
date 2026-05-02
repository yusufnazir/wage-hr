"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  createTenantEmployeeGroup,
  fetchTenantCompanies,
  fetchTenantEmployeeGroups,
  patchTenantEmployeeGroupActive,
  putTenantEmployeeGroup,
  type TenantCompanyItem,
  type TenantEmployeeGroupItem,
  type TenantEmployeeGroupUpsertPayload,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";
type ModalMode = { kind: "create" } | { kind: "edit"; item: TenantEmployeeGroupItem } | null;

function emptyPayload(): TenantEmployeeGroupUpsertPayload {
  return { companyId: "", name: "", code: "", description: "", active: true };
}

function itemToPayload(item: TenantEmployeeGroupItem): TenantEmployeeGroupUpsertPayload {
  return {
    companyId: item.companyId,
    name: item.name,
    code: item.code,
    description: item.description ?? "",
    active: item.active,
  };
}

export default function EmployeeGroupsPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<TenantEmployeeGroupItem[]>([]);
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [selectedCompanyId, setSelectedCompanyId] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [msg, setMsg] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const [modal, setModal] = useState<ModalMode>(null);
  const [form, setForm] = useState<TenantEmployeeGroupUpsertPayload>(emptyPayload());
  const [formBusy, setFormBusy] = useState(false);
  const [formMsg, setFormMsg] = useState<string | null>(null);

  const canManage = me.privileges.includes("EMPLOYEE_GROUP_MANAGE");

  const reload = useCallback(
    async (p = 0, companyId = selectedCompanyId) => {
      setLoad("loading");
      setMsg(null);
      const r = await fetchTenantEmployeeGroups({ page: p, size: 20, companyId: companyId || undefined });
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
    })();
    void reload(0);
  }, [reload]);

  function openCreate() {
    setForm({ ...emptyPayload(), companyId: selectedCompanyId });
    setFormMsg(null);
    setModal({ kind: "create" });
  }

  function openEdit(item: TenantEmployeeGroupItem) {
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
    if (!form.name.trim()) { setFormMsg("Name is required."); return; }
    if (!form.code.trim()) { setFormMsg("Code is required."); return; }
    setFormBusy(true);
    setFormMsg(null);
    try {
      const payload = { ...form, description: form.description?.trim() || null };
      if (modal?.kind === "create") {
        await createTenantEmployeeGroup(payload);
        setMsg(t("employeeGroups.msg.created"));
      } else if (modal?.kind === "edit") {
        await putTenantEmployeeGroup(modal.item.id, payload);
        setMsg(t("employeeGroups.msg.saved"));
      }
      closeModal();
      await reload(page);
    } catch (e) {
      setFormMsg(modal?.kind === "create" ? t("employeeGroups.msg.createFailed") : t("employeeGroups.msg.saveFailed"));
      console.error(e);
    } finally {
      setFormBusy(false);
    }
  }

  async function toggleActive(item: TenantEmployeeGroupItem) {
    setBusyId(item.id);
    setMsg(null);
    try {
      await patchTenantEmployeeGroupActive(item.id, !item.active);
      await reload(page);
    } catch {
      setMsg(t("employeeGroups.msg.saveFailed"));
    } finally {
      setBusyId(null);
    }
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("employeeGroups.title")}</h1>
        <p className="text-sm text-muted">{t("employeeGroups.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">← {t("nav.dashboard")}</Link>
      </div>
    );
  }

  const companyName = (id: string) => companies.find((c) => c.id === id)?.name ?? id;

  return (
    <div className="mx-auto max-w-5xl space-y-6" data-testid="employee-groups-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("employeeGroups.title")}</h1>
        <div className="flex gap-3 text-sm">
          <Link href="/app/companies" className="font-medium text-primary underline-offset-4 hover:underline">
            ← {t("companies.title")}
          </Link>
          {canManage && (
            <button onClick={openCreate} className="rounded bg-primary px-3 py-1 text-sm font-medium text-primary-foreground hover:opacity-90">
              {t("employeeGroups.action.new")}
            </button>
          )}
        </div>
      </div>

      <div className="flex flex-wrap gap-3 text-sm">
        <label className="flex items-center gap-2 text-muted">
          {t("employeeGroups.col.company")}:
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
      {load === "loading" && <p className="text-sm text-muted">{t("employeeGroups.state.loading")}</p>}
      {load === "error" && <p className="text-sm text-destructive">{t("employeeGroups.error.load")}</p>}

      {load === "ready" && (
        <>
          {items.length === 0 ? (
            <p className="text-sm text-muted">{t("employeeGroups.state.empty")}</p>
          ) : (
            <div className="overflow-x-auto rounded-md border border-border">
              <table className="min-w-full divide-y divide-border text-sm">
                <thead className="bg-surface-alt">
                  <tr>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("employeeGroups.col.name")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("employeeGroups.col.code")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("employeeGroups.col.company")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("employeeGroups.col.status")}</th>
                    {canManage && <th className="px-4 py-2" />}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface">
                  {items.map((item) => (
                    <tr key={item.id}>
                      <td className="px-4 py-2 font-medium text-foreground">{item.name}</td>
                      <td className="px-4 py-2 text-muted">{item.code}</td>
                      <td className="px-4 py-2 text-muted">{companyName(item.companyId)}</td>
                      <td className="px-4 py-2">
                        <span className={item.active ? "rounded px-1.5 py-0.5 text-xs font-medium bg-success/10 text-success" : "rounded px-1.5 py-0.5 text-xs font-medium bg-muted/20 text-muted"}>
                          {item.active ? t("employeeGroups.status.active") : t("employeeGroups.status.inactive")}
                        </span>
                      </td>
                      {canManage && (
                        <td className="px-4 py-2 text-right">
                          <button onClick={() => openEdit(item)} className="mr-3 text-sm text-primary underline-offset-4 hover:underline">{t("employeeGroups.action.edit")}</button>
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
              <button onClick={() => void reload(page - 1)} disabled={page === 0} className="rounded border border-border px-3 py-1 disabled:opacity-40">{t("employeeGroups.action.prev")}</button>
              <span className="py-1 text-muted">{page + 1} / {totalPages}</span>
              <button onClick={() => void reload(page + 1)} disabled={page >= totalPages - 1} className="rounded border border-border px-3 py-1 disabled:opacity-40">{t("employeeGroups.action.next")}</button>
            </div>
          )}
        </>
      )}

      {modal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-lg border border-border bg-surface p-6 shadow-xl">
            <h2 className="mb-4 text-base font-semibold text-foreground">
              {modal.kind === "create" ? t("employeeGroups.action.new") : t("employeeGroups.action.edit")}
            </h2>
            <div className="space-y-3 text-sm">
              <label className="block">
                <span className="text-muted">{t("employeeGroups.label.companyId")} *</span>
                <select
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.companyId}
                  onChange={(e) => setForm({ ...form, companyId: e.target.value })}
                >
                  <option value="">Select company…</option>
                  {companies.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </label>
              <label className="block">
                <span className="text-muted">{t("employeeGroups.label.name")} *</span>
                <input className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
              </label>
              <label className="block">
                <span className="text-muted">{t("employeeGroups.label.code")} *</span>
                <input className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
              </label>
              <label className="block">
                <span className="text-muted">{t("employeeGroups.label.description")}</span>
                <textarea className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground" rows={2} value={form.description ?? ""} onChange={(e) => setForm({ ...form, description: e.target.value })} />
              </label>
              <label className="flex items-center gap-2">
                <input type="checkbox" checked={form.active !== false} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
                <span className="text-muted">{t("employeeGroups.label.active")}</span>
              </label>
            </div>
            {formMsg && <p className="mt-3 text-sm text-destructive">{formMsg}</p>}
            <div className="mt-4 flex justify-end gap-2">
              <button onClick={closeModal} disabled={formBusy} className="rounded border border-border px-3 py-1.5 text-sm hover:bg-surface-alt disabled:opacity-40">{t("employeeGroups.action.cancel")}</button>
              <button onClick={() => void handleSubmit()} disabled={formBusy} className="rounded bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40">
                {modal.kind === "create" ? t("employeeGroups.action.create") : t("employeeGroups.action.save")}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
