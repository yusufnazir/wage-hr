"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { fetchPlatformPayrollBase, putPlatformPayrollBase } from "@/lib/api";
import { navLabel } from "@/messages/nav";

const CATEGORIES = ["", "TAX", "CONTRIBUTION", "ACCRUAL", "NET", "GROSS", "STATUTORY"] as const;

type LoadState = "loading" | "ready" | "forbidden" | "notFound" | "error";

export default function PlatformPayrollBaseEditPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const id = params.id;
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [category, setCategory] = useState("");
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!me.platformSuperadmin) return;
    void (async () => {
      const r = await fetchPlatformPayrollBase(id);
      if (!r.ok) {
        setLoad(r.status === 403 ? "forbidden" : r.status === 404 ? "notFound" : "error");
        return;
      }
      setCode(r.item.code);
      setName(r.item.name);
      setCategory(r.item.category ?? "");
      setActive(r.item.active);
      setLoad("ready");
    })();
  }, [id, me.platformSuperadmin]);

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformPayrollBases.title.edit")}</h1>
        <p className="text-sm text-muted">{t("platformPayrollBases.error.notOperator")}</p>
        <Link href="/app/platform-payroll-bases" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformPayrollBases.action.backToList")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-lg">
        <p className="text-sm text-muted">{t("platformPayrollBases.state.loading")}</p>
      </div>
    );
  }

  if (load !== "ready") {
    const key =
      load === "forbidden"
        ? "platformPayrollBases.error.forbidden"
        : load === "notFound"
          ? "platformPayrollBases.error.notFound"
          : "platformPayrollBases.error.load";
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformPayrollBases.title.edit")}</h1>
        <p className="text-sm text-muted">{t(key)}</p>
        <Link href="/app/platform-payroll-bases" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformPayrollBases.action.backToList")}
        </Link>
      </div>
    );
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await putPlatformPayrollBase(id, {
        name: name.trim(),
        category: category || null,
        active,
      });
      router.push("/app/platform-payroll-bases");
    } catch {
      setError(t("platformPayrollBases.msg.saveFailed"));
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6" data-testid="platform-payroll-base-form-edit">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformPayrollBases.title.edit")}</h1>
        <Link href="/app/platform-payroll-bases" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformPayrollBases.action.backToList")}
        </Link>
      </div>

      <div className="rounded-md border border-border bg-surface p-4 text-sm text-muted">
        <p>
          <span className="font-mono font-semibold text-foreground">{code}</span>
        </p>
        <p className="mt-2 text-xs">{t("platformPayrollBases.hint.codeImmutable")}</p>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5 shadow-sm">
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformPayrollBases.label.name")}</label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            maxLength={255}
          />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformPayrollBases.label.category")}</label>
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
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
          {t("platformPayrollBases.label.active")}
        </label>
        <button
          type="submit"
          disabled={busy}
          className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
        >
          {t("platformPayrollBases.action.save")}
        </button>
      </form>
    </div>
  );
}
