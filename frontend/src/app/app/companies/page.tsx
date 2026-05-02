"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  createTenantCompany,
  fetchTenantCompanies,
  patchTenantCompanyActive,
  putTenantCompany,
  type TenantCompanyItem,
  type TenantCompanyUpsertPayload,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

type ModalMode = { kind: "create" } | { kind: "edit"; item: TenantCompanyItem } | null;

const FREQUENCIES = ["WEEKLY", "BIWEEKLY", "SEMIMONTHLY", "MONTHLY"];

function emptyPayload(): TenantCompanyUpsertPayload {
  return {
    name: "",
    legalName: "",
    registrationNumber: "",
    taxId: "",
    payrollCountry: "",
    currency: "",
    payrollFrequency: "MONTHLY",
    timezone: "UTC",
    dateFormat: "yyyy-MM-dd",
    contactEmail: "",
    contactPhone: "",
    active: true,
  };
}

function itemToPayload(item: TenantCompanyItem): TenantCompanyUpsertPayload {
  return {
    name: item.name,
    legalName: item.legalName ?? "",
    registrationNumber: item.registrationNumber ?? "",
    taxId: item.taxId ?? "",
    payrollCountry: item.payrollCountry,
    currency: item.currency,
    payrollFrequency: item.payrollFrequency,
    timezone: item.timezone,
    dateFormat: item.dateFormat,
    contactEmail: item.contactEmail ?? "",
    contactPhone: item.contactPhone ?? "",
    active: item.active,
  };
}

export default function CompaniesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<TenantCompanyItem[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [msg, setMsg] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const [modal, setModal] = useState<ModalMode>(null);
  const [form, setForm] = useState<TenantCompanyUpsertPayload>(emptyPayload());
  const [formBusy, setFormBusy] = useState(false);
  const [formMsg, setFormMsg] = useState<string | null>(null);

  const canManage = me.privileges.includes("COMPANY_MANAGE");

  const reload = useCallback(
    async (p = 0) => {
      setLoad("loading");
      setMsg(null);
      const r = await fetchTenantCompanies({ page: p, size: 20 });
      if (!r.ok) {
        setLoad(r.status === 403 ? "forbidden" : "error");
        return;
      }
      setItems(r.items);
      setTotalPages(r.totalPages);
      setPage(p);
      setLoad("ready");
    },
    [],
  );

  useEffect(() => {
    void reload(0);
  }, [reload]);

  function openCreate() {
    setForm(emptyPayload());
    setFormMsg(null);
    setModal({ kind: "create" });
  }

  function openEdit(item: TenantCompanyItem) {
    setForm(itemToPayload(item));
    setFormMsg(null);
    setModal({ kind: "edit", item });
  }

  function closeModal() {
    setModal(null);
    setFormMsg(null);
  }

  async function handleSubmit() {
    if (!form.name.trim()) {
      setFormMsg("Name is required.");
      return;
    }
    if (!form.payrollCountry.trim() || form.payrollCountry.trim().length !== 2) {
      setFormMsg("Payroll country must be a 2-letter ISO code.");
      return;
    }
    if (!form.currency.trim() || form.currency.trim().length !== 3) {
      setFormMsg("Currency must be a 3-letter ISO code.");
      return;
    }
    setFormBusy(true);
    setFormMsg(null);
    try {
      const payload: TenantCompanyUpsertPayload = {
        ...form,
        payrollCountry: form.payrollCountry.toUpperCase(),
        currency: form.currency.toUpperCase(),
        legalName: form.legalName?.trim() || null,
        registrationNumber: form.registrationNumber?.trim() || null,
        taxId: form.taxId?.trim() || null,
        contactEmail: form.contactEmail?.trim() || null,
        contactPhone: form.contactPhone?.trim() || null,
      };
      if (modal?.kind === "create") {
        await createTenantCompany(payload);
        setMsg(t("companies.msg.created"));
      } else if (modal?.kind === "edit") {
        await putTenantCompany(modal.item.id, payload);
        setMsg(t("companies.msg.saved"));
      }
      closeModal();
      await reload(page);
    } catch (e) {
      setFormMsg(
        modal?.kind === "create"
          ? t("companies.msg.createFailed")
          : t("companies.msg.saveFailed"),
      );
      console.error(e);
    } finally {
      setFormBusy(false);
    }
  }

  async function toggleActive(item: TenantCompanyItem) {
    const next = !item.active;
    const confirmed = window.confirm(
      next
        ? t("companies.confirm.activate").replace("{name}", item.name)
        : t("companies.confirm.deactivate").replace("{name}", item.name),
    );
    if (!confirmed) return;
    setBusyId(item.id);
    setMsg(null);
    try {
      await patchTenantCompanyActive(item.id, next);
      await reload(page);
    } catch {
      setMsg(t("companies.msg.saveFailed"));
    } finally {
      setBusyId(null);
    }
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("companies.title")}</h1>
        <p className="text-sm text-muted">{t("companies.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6" data-testid="companies-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("companies.title")}</h1>
        <div className="flex gap-3 text-sm">
          <Link href="/app" className="font-medium text-primary underline-offset-4 hover:underline">
            ← {t("nav.dashboard")}
          </Link>
          {canManage && (
            <button
              onClick={openCreate}
              className="rounded bg-primary px-3 py-1 text-sm font-medium text-primary-foreground hover:opacity-90"
            >
              {t("companies.action.new")}
            </button>
          )}
        </div>
      </div>

      {msg && <p className="text-sm text-foreground">{msg}</p>}

      {load === "loading" && <p className="text-sm text-muted">{t("companies.state.loading")}</p>}
      {load === "error" && <p className="text-sm text-destructive">{t("companies.error.load")}</p>}

      {load === "ready" && (
        <>
          {items.length === 0 ? (
            <p className="text-sm text-muted">{t("companies.state.empty")}</p>
          ) : (
            <div className="overflow-x-auto rounded-md border border-border">
              <table className="min-w-full divide-y divide-border text-sm">
                <thead className="bg-surface-alt">
                  <tr>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("companies.col.name")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("companies.col.taxId")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("companies.col.country")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("companies.col.currency")}</th>
                    <th className="px-4 py-2 text-left font-medium text-muted">{t("companies.col.status")}</th>
                    {canManage && <th className="px-4 py-2" />}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface">
                  {items.map((item) => (
                    <tr key={item.id}>
                      <td className="px-4 py-2 font-medium text-foreground">{item.name}</td>
                      <td className="px-4 py-2 text-muted">{item.taxId ?? "—"}</td>
                      <td className="px-4 py-2 text-muted">{item.payrollCountry}</td>
                      <td className="px-4 py-2 text-muted">{item.currency}</td>
                      <td className="px-4 py-2">
                        <span
                          className={
                            item.active
                              ? "rounded px-1.5 py-0.5 text-xs font-medium bg-success/10 text-success"
                              : "rounded px-1.5 py-0.5 text-xs font-medium bg-muted/20 text-muted"
                          }
                        >
                          {item.active ? t("companies.status.active") : t("companies.status.inactive")}
                        </span>
                      </td>
                      {canManage && (
                        <td className="px-4 py-2 text-right">
                          <button
                            onClick={() => openEdit(item)}
                            className="mr-3 text-sm text-primary underline-offset-4 hover:underline"
                          >
                            {t("companies.action.edit")}
                          </button>
                          <button
                            onClick={() => void toggleActive(item)}
                            disabled={busyId === item.id}
                            className="text-sm text-muted underline-offset-4 hover:underline disabled:opacity-50"
                          >
                            {item.active ? t("companies.confirm.deactivate").replace(" {name}?", "") : t("companies.confirm.activate").replace(" {name}?", "")}
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
              <button
                onClick={() => void reload(page - 1)}
                disabled={page === 0}
                className="rounded border border-border px-3 py-1 disabled:opacity-40"
              >
                {t("companies.action.prev")}
              </button>
              <span className="py-1 text-muted">
                {page + 1} / {totalPages}
              </span>
              <button
                onClick={() => void reload(page + 1)}
                disabled={page >= totalPages - 1}
                className="rounded border border-border px-3 py-1 disabled:opacity-40"
              >
                {t("companies.action.next")}
              </button>
            </div>
          )}
        </>
      )}

      {modal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-lg rounded-lg border border-border bg-surface p-6 shadow-xl">
            <h2 className="mb-4 text-base font-semibold text-foreground">
              {modal.kind === "create" ? t("companies.action.new") : t("companies.action.edit")}
            </h2>
            <div className="space-y-3 text-sm">
              <label className="block">
                <span className="text-muted">{t("companies.label.name")} *</span>
                <input
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                />
              </label>
              <label className="block">
                <span className="text-muted">{t("companies.label.legalName")}</span>
                <input
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.legalName ?? ""}
                  onChange={(e) => setForm({ ...form, legalName: e.target.value })}
                />
              </label>
              <label className="block">
                <span className="text-muted">{t("companies.label.taxId")}</span>
                <input
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.taxId ?? ""}
                  onChange={(e) => setForm({ ...form, taxId: e.target.value })}
                />
              </label>
              <div className="grid grid-cols-2 gap-3">
                <label className="block">
                  <span className="text-muted">{t("companies.label.payrollCountry")} *</span>
                  <input
                    className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 uppercase text-foreground"
                    maxLength={2}
                    value={form.payrollCountry}
                    onChange={(e) => setForm({ ...form, payrollCountry: e.target.value.toUpperCase() })}
                  />
                </label>
                <label className="block">
                  <span className="text-muted">{t("companies.label.currency")} *</span>
                  <input
                    className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 uppercase text-foreground"
                    maxLength={3}
                    value={form.currency}
                    onChange={(e) => setForm({ ...form, currency: e.target.value.toUpperCase() })}
                  />
                </label>
              </div>
              <label className="block">
                <span className="text-muted">{t("companies.label.payrollFrequency")} *</span>
                <select
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.payrollFrequency}
                  onChange={(e) => setForm({ ...form, payrollFrequency: e.target.value })}
                >
                  {FREQUENCIES.map((f) => (
                    <option key={f} value={f}>{f}</option>
                  ))}
                </select>
              </label>
              <div className="grid grid-cols-2 gap-3">
                <label className="block">
                  <span className="text-muted">{t("companies.label.timezone")} *</span>
                  <input
                    className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                    value={form.timezone}
                    onChange={(e) => setForm({ ...form, timezone: e.target.value })}
                  />
                </label>
                <label className="block">
                  <span className="text-muted">{t("companies.label.dateFormat")} *</span>
                  <input
                    className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                    value={form.dateFormat}
                    onChange={(e) => setForm({ ...form, dateFormat: e.target.value })}
                  />
                </label>
              </div>
              <label className="block">
                <span className="text-muted">{t("companies.label.contactEmail")}</span>
                <input
                  type="email"
                  className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-foreground"
                  value={form.contactEmail ?? ""}
                  onChange={(e) => setForm({ ...form, contactEmail: e.target.value })}
                />
              </label>
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={form.active !== false}
                  onChange={(e) => setForm({ ...form, active: e.target.checked })}
                />
                <span className="text-muted">{t("companies.label.active")}</span>
              </label>
            </div>

            {formMsg && <p className="mt-3 text-sm text-destructive">{formMsg}</p>}

            <div className="mt-4 flex justify-end gap-2">
              <button
                onClick={closeModal}
                disabled={formBusy}
                className="rounded border border-border px-3 py-1.5 text-sm hover:bg-surface-alt disabled:opacity-40"
              >
                {t("companies.action.cancel")}
              </button>
              <button
                onClick={() => void handleSubmit()}
                disabled={formBusy}
                className="rounded bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40"
              >
                {modal.kind === "create" ? t("companies.action.create") : t("companies.action.save")}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
