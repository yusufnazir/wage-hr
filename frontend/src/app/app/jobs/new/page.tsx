"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { showToast } from "@/components/ui/Toast";
import {
  createTenantJob,
  fetchTenantCompanies,
  fetchTenantDepartments,
  type TenantCompanyItem,
  type TenantDepartmentItem,
  type TenantJobUpsertPayload,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "error";

function emptyPayload(companyId = "", departmentId = ""): TenantJobUpsertPayload {
  return {
    companyId,
    departmentId,
    title: "",
    code: "",
    salaryType: "MONTHLY",
    description: "",
    defaultSalary: null,
    defaultHourlyRate: null,
    active: true,
  };
}

export default function JobNewPage() {
  const router = useRouter();
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [departments, setDepartments] = useState<TenantDepartmentItem[]>([]);
  const [form, setForm] = useState<TenantJobUpsertPayload>(emptyPayload());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canManage = me.privileges.includes("JOB_MANAGE");

  const formDepartments = useMemo(
    () => departments.filter((d) => !form.companyId || d.companyId === form.companyId),
    [departments, form.companyId],
  );

  useEffect(() => {
    void (async () => {
      setLoad("loading");
      const [cr, dr] = await Promise.all([
        fetchTenantCompanies({ size: 100 }),
        fetchTenantDepartments({ size: 500 }),
      ]);
      if (!cr.ok || !dr.ok) {
        setLoad("error");
        return;
      }
      setCompanies(cr.items);
      setDepartments(dr.items);
      const defaultCompanyId = cr.items[0]?.id ?? "";
      const defaultDepartmentId = dr.items.find((d) => d.companyId === defaultCompanyId)?.id ?? "";
      setForm(emptyPayload(defaultCompanyId, defaultDepartmentId));
      setLoad("ready");
    })();
  }, []);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();

    if (!form.companyId) {
      setError("Company is required.");
      return;
    }
    if (!form.departmentId) {
      setError("Department is required.");
      return;
    }
    if (!form.title?.trim()) {
      setError("Title is required.");
      return;
    }
    if (!form.code?.trim()) {
      setError("Code is required.");
      return;
    }

    setBusy(true);
    setError(null);
    try {
      const created = await createTenantJob({
        ...form,
        title: form.title.trim(),
        code: form.code.trim(),
        description: form.description?.toString().trim() || null,
      });
      showToast(`"${form.title.trim()}" created successfully.`);
      router.push(`/app/jobs/${created.id}/edit`);
    } catch {
      setError(t("jobs.msg.createFailed"));
      setBusy(false);
    }
  }

  if (!canManage) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("jobs.action.new")}</h1>
        <p className="text-sm text-muted">{t("jobs.error.forbidden")}</p>
        <Link href="/app/jobs" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}{t("jobs.title")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return <p className="mx-auto max-w-xl text-sm text-muted">{t("jobs.state.loading")}</p>;
  }

  if (load === "error") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("jobs.action.new")}</h1>
        <p className="text-sm text-muted">{t("jobs.error.load")}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6" data-testid="job-form-new">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("jobs.action.new")}</h1>
        <Link href="/app/jobs" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}{t("jobs.title")}
        </Link>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5">
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block space-y-1">
            <span className="text-sm text-muted">{t("jobs.label.companyId")} *</span>
            <select
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
              value={form.companyId}
              onChange={(e) => {
                const companyId = e.target.value;
                const departmentId = departments.find((d) => d.companyId === companyId)?.id ?? "";
                setForm({ ...form, companyId, departmentId });
              }}
            >
              <option value="">Select company…</option>
              {companies.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </label>

          <label className="block space-y-1">
            <span className="text-sm text-muted">{t("jobs.label.departmentId")} *</span>
            <select
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
              value={form.departmentId}
              onChange={(e) => setForm({ ...form, departmentId: e.target.value })}
            >
              <option value="">Select department…</option>
              {formDepartments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            </select>
          </label>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block space-y-1">
            <span className="text-sm text-muted">{t("jobs.label.title")} *</span>
            <input className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
          </label>

          <label className="block space-y-1">
            <span className="text-sm text-muted">{t("jobs.label.code")} *</span>
            <input className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
          </label>
        </div>

        <label className="block space-y-1">
          <span className="text-sm text-muted">{t("jobs.label.description")}</span>
          <textarea className="w-full rounded border border-border bg-background px-3 py-2 text-sm" rows={3} value={form.description?.toString() ?? ""} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </label>

        <label className="flex items-center gap-2">
          <input type="checkbox" checked={form.active !== false} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
          <span className="text-sm text-foreground">{t("jobs.label.active")}</span>
        </label>

        <div className="flex gap-3">
          <button type="submit" disabled={busy} className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50">
            {t("jobs.action.create")}
          </button>
          <Link href="/app/jobs" className="rounded border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-surface-alt">
            {t("jobs.action.cancel")}
          </Link>
        </div>
      </form>
    </div>
  );
}
