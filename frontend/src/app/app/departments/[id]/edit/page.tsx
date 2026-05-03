"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { EntityDocumentsTab } from "@/components/ui/EntityDocumentsTab";
import { showToast } from "@/components/ui/Toast";
import {
  fetchTenantCompanies,
  fetchTenantDepartment,
  putTenantDepartment,
  type TenantCompanyItem,
  type TenantDepartmentUpsertPayload,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "notFound" | "error";

function emptyPayload(): TenantDepartmentUpsertPayload {
  return { companyId: "", name: "", code: "", description: "", active: true };
}

export default function DepartmentEditPage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [form, setForm] = useState<TenantDepartmentUpsertPayload>(emptyPayload());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<"details" | "documents">("details");

  const canManage = me.privileges.includes("DEPARTMENT_MANAGE");

  useEffect(() => {
    if (!canManage) return;
    void (async () => {
      setLoad("loading");
      const [companyResult, departmentResult] = await Promise.all([
        fetchTenantCompanies({ size: 100 }),
        fetchTenantDepartment(id),
      ]);

      if (!companyResult.ok) {
        setLoad("error");
        return;
      }
      if (!departmentResult.ok) {
        setLoad(departmentResult.status === 403 ? "forbidden" : departmentResult.status === 404 ? "notFound" : "error");
        return;
      }

      setCompanies(companyResult.items);
      setForm({
        companyId: departmentResult.item.companyId,
        name: departmentResult.item.name,
        code: departmentResult.item.code,
        description: departmentResult.item.description ?? "",
        active: departmentResult.item.active,
      });
      setLoad("ready");
    })();
  }, [canManage, id]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!form.companyId) {
      setError("Company is required.");
      return;
    }
    if (!form.name?.trim()) {
      setError("Name is required.");
      return;
    }
    if (!form.code?.trim()) {
      setError("Code is required.");
      return;
    }

    setBusy(true);
    setError(null);
    try {
      await putTenantDepartment(id, {
        ...form,
        name: form.name.trim(),
        code: form.code.trim(),
        description: form.description?.trim() || null,
      });
      showToast(`"${form.name.trim()}" updated successfully.`);
      router.push("/app/departments");
    } catch {
      setError(t("departments.msg.saveFailed"));
      setBusy(false);
    }
  }

  if (!canManage) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("departments.action.edit")}</h1>
        <p className="text-sm text-muted">{t("departments.error.forbidden")}</p>
        <Link href="/app/departments" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}{t("departments.title")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return <p className="mx-auto max-w-xl text-sm text-muted">{t("departments.state.loading")}</p>;
  }

  if (load !== "ready") {
    const message = load === "notFound" ? "Department not found." : t("departments.error.load");
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("departments.action.edit")}</h1>
        <p className="text-sm text-muted">{message}</p>
        <Link href="/app/departments" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}{t("departments.title")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6" data-testid="department-form-edit">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("departments.action.edit")}</h1>
        <Link href="/app/departments" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}{t("departments.title")}
        </Link>
      </div>

      <div className="flex gap-1 border-b border-border">
        <button
          type="button"
          onClick={() => setTab("details")}
          className={`px-4 py-2 text-sm font-medium ${tab === "details" ? "border-b-2 border-primary text-foreground" : "text-muted hover:text-foreground"}`}
        >
          Details
        </button>
        <button
          type="button"
          onClick={() => setTab("documents")}
          className={`px-4 py-2 text-sm font-medium ${tab === "documents" ? "border-b-2 border-primary text-foreground" : "text-muted hover:text-foreground"}`}
        >
          Documents
        </button>
      </div>

      {tab === "documents" ? (
        <EntityDocumentsTab entityType="DEPARTMENT" entityId={id} canEdit={canManage} />
      ) : (
        <>
          {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

          <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5">
        <label className="block space-y-1">
          <span className="text-sm text-muted">{t("departments.label.companyId")} *</span>
          <select
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={form.companyId}
            onChange={(e) => setForm({ ...form, companyId: e.target.value })}
          >
            <option value="">Select company…</option>
            {companies.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </label>

        <label className="block space-y-1">
          <span className="text-sm text-muted">{t("departments.label.name")} *</span>
          <input className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </label>

        <label className="block space-y-1">
          <span className="text-sm text-muted">{t("departments.label.code")} *</span>
          <input className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
        </label>

        <label className="block space-y-1">
          <span className="text-sm text-muted">{t("departments.label.description")}</span>
          <textarea className="w-full rounded border border-border bg-background px-3 py-2 text-sm" rows={3} value={form.description ?? ""} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </label>

        <label className="flex items-center gap-2">
          <input type="checkbox" checked={form.active !== false} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
          <span className="text-sm text-foreground">{t("departments.label.active")}</span>
        </label>

        <div className="flex gap-3">
          <button type="submit" disabled={busy} className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50">
            {t("departments.action.save")}
          </button>
          <Link href="/app/departments" className="rounded border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-surface-alt">
            {t("departments.action.cancel")}
          </Link>
        </div>
      </form>
        </>
      )}
    </div>
  );
}
