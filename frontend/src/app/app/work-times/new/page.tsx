"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { showToast } from "@/components/ui/Toast";
import {
  createTenantWorkTime,
  fetchTenantCompanies,
  type TenantCompanyItem,
  type TenantWorkTimeUpsertPayload,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "error";

function emptyPayload(companyId = ""): TenantWorkTimeUpsertPayload {
  return { companyId, name: "", code: "", hoursPerDay: 8, workDaysPerWeek: 5, description: "", active: true };
}

export default function WorkTimeNewPage() {
  const router = useRouter();
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [form, setForm] = useState<TenantWorkTimeUpsertPayload>(emptyPayload());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canManage = me.privileges.includes("WORK_TIME_MANAGE");

  useEffect(() => {
    void (async () => {
      setLoad("loading");
      const cr = await fetchTenantCompanies({ size: 100 });
      if (!cr.ok) {
        setLoad("error");
        return;
      }
      setCompanies(cr.items);
      setForm((prev) => ({ ...prev, companyId: cr.items[0]?.id ?? "" }));
      setLoad("ready");
    })();
  }, []);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!form.companyId) { setError("Company is required."); return; }
    if (!form.name?.trim()) { setError("Name is required."); return; }
    if (!form.code?.trim()) { setError("Code is required."); return; }
    if (!form.hoursPerDay || form.hoursPerDay <= 0 || form.hoursPerDay > 24) {
      setError("Hours per day must be between 0.5 and 24.");
      return;
    }
    if (!form.workDaysPerWeek || form.workDaysPerWeek < 1 || form.workDaysPerWeek > 7) {
      setError("Work days per week must be between 1 and 7.");
      return;
    }

    setBusy(true);
    setError(null);
    try {
      const created = await createTenantWorkTime({
        ...form,
        name: form.name.trim(),
        code: form.code.trim(),
        description: form.description?.trim() || null,
      });
      showToast(`"${form.name.trim()}" created successfully.`);
      router.push(`/app/work-times/${created.id}/edit`);
    } catch {
      setError(t("workTimes.msg.createFailed"));
      setBusy(false);
    }
  }

  if (!canManage) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("workTimes.action.new")}</h1>
        <p className="text-sm text-muted">{t("workTimes.error.forbidden")}</p>
        <Link href="/app/work-times" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}{t("workTimes.title")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return <p className="mx-auto max-w-xl text-sm text-muted">{t("workTimes.state.loading")}</p>;
  }

  if (load === "error") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("workTimes.action.new")}</h1>
        <p className="text-sm text-muted">{t("workTimes.error.load")}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6" data-testid="work-time-form-new">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("workTimes.action.new")}</h1>
        <Link href="/app/work-times" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}{t("workTimes.title")}
        </Link>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5">
        <label className="block space-y-1">
          <span className="text-sm text-muted">{t("workTimes.label.companyId")} *</span>
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
          <span className="text-sm text-muted">{t("workTimes.label.name")} *</span>
          <input className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </label>

        <label className="block space-y-1">
          <span className="text-sm text-muted">{t("workTimes.label.code")} *</span>
          <input className="w-full rounded border border-border bg-background px-3 py-2 text-sm" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
        </label>

        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block space-y-1">
            <span className="text-sm text-muted">{t("workTimes.label.hoursPerDay")} *</span>
            <input
              type="number"
              min="0.5"
              max="24"
              step="0.5"
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
              value={form.hoursPerDay}
              onChange={(e) => setForm({ ...form, hoursPerDay: parseFloat(e.target.value) || 0 })}
            />
          </label>

          <label className="block space-y-1">
            <span className="text-sm text-muted">{t("workTimes.label.workDaysPerWeek")} *</span>
            <input
              type="number"
              min="1"
              max="7"
              step="1"
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
              value={form.workDaysPerWeek}
              onChange={(e) => setForm({ ...form, workDaysPerWeek: parseInt(e.target.value, 10) || 0 })}
            />
          </label>
        </div>

        <label className="block space-y-1">
          <span className="text-sm text-muted">{t("workTimes.label.description")}</span>
          <textarea className="w-full rounded border border-border bg-background px-3 py-2 text-sm" rows={3} value={form.description ?? ""} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </label>

        <label className="flex items-center gap-2">
          <input type="checkbox" checked={form.active !== false} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
          <span className="text-sm text-foreground">{t("workTimes.label.active")}</span>
        </label>

        <div className="flex gap-3">
          <button type="submit" disabled={busy} className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50">
            {t("workTimes.action.create")}
          </button>
          <Link href="/app/work-times" className="rounded border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-surface-alt">
            {t("workTimes.action.cancel")}
          </Link>
        </div>
      </form>
    </div>
  );
}
