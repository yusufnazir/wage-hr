"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { PlatformDateInput } from "@/components/ui/PlatformDateInput";
import { showToast } from "@/components/ui/Toast";
import {
  createTenantPayPeriod,
  fetchTenantCompanies,
  type TenantCompanyItem,
  type TenantPayPeriodUpsertPayload,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

const STATUSES = ["READY", "OPEN", "CLOSED"] as const;

function currentYear() {
  return new Date().getFullYear();
}

export default function NewPayPeriodPage() {
  const router = useRouter();
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const canManage = me.privileges.includes("PAY_PERIOD_MANAGE");

  const [load, setLoad] = useState<LoadState>("loading");
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);

  const y = currentYear();
  const [form, setForm] = useState<TenantPayPeriodUpsertPayload>({
    companyId: "",
    year: y,
    startDate: `${y}-01-01`,
    endDate: `${y}-01-31`,
    status: "READY",
  });
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      const cr = await fetchTenantCompanies({ size: 100 });
      if (!cr.ok) {
        setLoad(cr.status === 403 ? "forbidden" : "error");
        return;
      }
      setCompanies(cr.items);
      setLoad("ready");
    })();
  }, []);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!form.companyId) { setErr("Company is required."); return; }
    if (!form.year || form.year < 1900 || form.year > 2200) { setErr("Year must be a valid calendar year."); return; }
    if (!form.startDate) { setErr("Start date is required."); return; }
    if (!form.endDate) { setErr("End date is required."); return; }
    if (form.endDate < form.startDate) { setErr("End date must not be before start date."); return; }
    setBusy(true);
    setErr(null);
    try {
      await createTenantPayPeriod(form);
      showToast(t("payPeriods.msg.created"));
      router.push("/app/pay-periods");
    } catch (e) {
      setErr(t("payPeriods.msg.createFailed"));
      console.error(e);
      setBusy(false);
    }
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <p className="text-sm text-muted">{t("payPeriods.state.loading")}</p>
      </div>
    );
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("payPeriods.title.new")}</h1>
        <p className="text-sm text-muted">{t("payPeriods.error.forbidden")}</p>
        <Link href="/app/pay-periods" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("payPeriods.title")}
        </Link>
      </div>
    );
  }

  if (load === "error") {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("payPeriods.title.new")}</h1>
        <p className="text-sm text-destructive">{t("payPeriods.error.load")}</p>
        <Link href="/app/pay-periods" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("payPeriods.title")}
        </Link>
      </div>
    );
  }

  if (!canManage) {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("payPeriods.title.new")}</h1>
        <p className="text-sm text-muted">{t("payPeriods.error.forbidden")}</p>
        <Link href="/app/pay-periods" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("payPeriods.title")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="flex items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("payPeriods.title.new")}</h1>
        <Link
          href="/app/pay-periods"
          className="text-sm font-medium text-primary underline-offset-4 hover:underline"
        >
          ← {t("payPeriods.title")}
        </Link>
      </div>

      <form onSubmit={(e) => void handleSubmit(e)} className="rounded-md border border-border bg-surface p-5 space-y-4">
        <label className="block">
          <span className="text-sm text-muted">{t("payPeriods.label.companyId")} *</span>
          <select
            className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-sm text-foreground"
            value={form.companyId}
            onChange={(e) => setForm({ ...form, companyId: e.target.value })}
            required
          >
            <option value="">Select company…</option>
            {companies.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        </label>

        <label className="block">
          <span className="text-sm text-muted">{t("payPeriods.label.year")} *</span>
          <input
            type="number"
            min="1900"
            max="2200"
            className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-sm text-foreground"
            value={form.year}
            onChange={(e) => setForm({ ...form, year: parseInt(e.target.value, 10) || currentYear() })}
            required
          />
        </label>

        <label className="block">
          <span className="text-sm text-muted">{t("payPeriods.label.startDate")} *</span>
          <PlatformDateInput
            className="mt-1"
            value={form.startDate}
            dateFormat={me.dateFormat}
            onChange={(v) => setForm({ ...form, startDate: v })}
          />
        </label>

        <label className="block">
          <span className="text-sm text-muted">{t("payPeriods.label.endDate")} *</span>
          <PlatformDateInput
            className="mt-1"
            value={form.endDate}
            dateFormat={me.dateFormat}
            onChange={(v) => setForm({ ...form, endDate: v })}
          />
        </label>

        <label className="block">
          <span className="text-sm text-muted">{t("payPeriods.label.status")} *</span>
          <select
            className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-sm text-foreground"
            value={form.status}
            onChange={(e) => setForm({ ...form, status: e.target.value })}
            required
          >
            {STATUSES.map((s) => (
              <option key={s} value={s}>{t(`payPeriods.status.${s.toLowerCase()}`)}</option>
            ))}
          </select>
        </label>

        {err && <p className="text-sm text-destructive">{err}</p>}

        <div className="flex justify-end gap-2 pt-2">
          <Link
            href="/app/pay-periods"
            className="rounded border border-border px-3 py-1.5 text-sm hover:bg-surface-alt"
          >
            {t("payPeriods.action.cancel")}
          </Link>
          <button
            type="submit"
            disabled={busy}
            className="rounded bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40"
          >
            {t("payPeriods.action.create")}
          </button>
        </div>
      </form>
    </div>
  );
}
