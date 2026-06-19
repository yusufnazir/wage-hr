"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useState, type FormEvent, type ReactNode } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { createPlatformPayrollBase } from "@/lib/api";
import { navLabel } from "@/messages/nav";

const CATEGORIES = ["", "TAX", "CONTRIBUTION", "ACCRUAL", "NET", "GROSS", "STATUTORY"] as const;

export default function PlatformPayrollBaseNewPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [category, setCategory] = useState("");
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!me.platformSuperadmin) {
    return (
      <PageShell>
        <h1 className="text-lg font-semibold text-foreground">{t("platformPayrollBases.title.new")}</h1>
        <p className="text-sm text-muted">{t("platformPayrollBases.error.notOperator")}</p>
        <Link href="/app/platform-payroll-bases" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformPayrollBases.action.backToList")}
        </Link>
      </PageShell>
    );
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await createPlatformPayrollBase({
        code: code.trim().toUpperCase(),
        name: name.trim(),
        category: category || null,
        active,
      });
      router.push("/app/platform-payroll-bases");
    } catch {
      setError(t("platformPayrollBases.msg.createFailed"));
      setBusy(false);
    }
  }

  return (
    <PageShell>
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformPayrollBases.title.new")}</h1>
        <Link href="/app/platform-payroll-bases" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformPayrollBases.action.backToList")}
        </Link>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5 shadow-sm" data-testid="platform-payroll-base-form-new">
        <p className="text-xs text-muted">{t("platformPayrollBases.hint.code")}</p>
        <FormField label={t("platformPayrollBases.label.code")}>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono uppercase"
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, ""))}
            required
            maxLength={50}
            data-testid="payroll-base-code"
          />
        </FormField>
        <FormField label={t("platformPayrollBases.label.name")}>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            maxLength={255}
          />
        </FormField>
        <FormField label={t("platformPayrollBases.label.category")}>
          <select
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          >
            {CATEGORIES.map((c) => (
              <option key={c || "none"} value={c}>
                {c || t("platformPayrollBases.filter.categoryNone")}
              </option>
            ))}
          </select>
        </FormField>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
          {t("platformPayrollBases.label.active")}
        </label>
        <button
          type="submit"
          disabled={busy}
          className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
        >
          {t("platformPayrollBases.action.create")}
        </button>
      </form>
    </PageShell>
  );
}

function FormField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="space-y-1">
      <label className="text-xs font-medium uppercase text-muted">{label}</label>
      {children}
    </div>
  );
}

function PageShell({ children }: { children: ReactNode }) {
  return <div className="mx-auto max-w-2xl space-y-6">{children}</div>;
}
