"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { createPlatformCurrency } from "@/lib/api";
import { navLabel } from "@/messages/nav";

export default function PlatformCurrencyNewPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [code, setCode] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [sortOrder, setSortOrder] = useState("100");
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCurrencies.title.new")}</h1>
        <p className="text-sm text-muted">{t("platformCurrencies.error.notOperator")}</p>
        <Link href="/app/platform-currencies" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformCurrencies.action.backToList")}
        </Link>
      </div>
    );
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await createPlatformCurrency({
        code: code.trim().toUpperCase(),
        displayName: displayName.trim(),
        sortOrder: parseInt(sortOrder, 10) || 100,
        active,
      });
      router.push("/app/platform-currencies");
    } catch {
      setError(t("platformCurrencies.msg.createFailed"));
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCurrencies.title.new")}</h1>
        <Link href="/app/platform-currencies" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformCurrencies.action.backToList")}
        </Link>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5 shadow-sm">
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformCurrencies.label.code")}</label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground uppercase placeholder:normal-case"
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            maxLength={3}
            required
            pattern="[A-Z]{3}"
            placeholder="e.g. USD"
            autoFocus
          />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformCurrencies.label.name")}</label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            maxLength={128}
            required
            placeholder="e.g. US Dollar"
          />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformCurrencies.label.sortOrder")}</label>
          <input
            type="number"
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
            value={sortOrder}
            onChange={(e) => setSortOrder(e.target.value)}
            min={0}
            max={10000}
          />
        </div>
        <div className="flex items-center gap-2">
          <input
            id="currency-active"
            type="checkbox"
            checked={active}
            onChange={(e) => setActive(e.target.checked)}
          />
          <label htmlFor="currency-active" className="text-sm text-foreground">
            {t("platformCurrencies.label.active")}
          </label>
        </div>
        <div className="flex gap-3">
          <button
            type="submit"
            disabled={busy}
            className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
          >
            {t("platformCurrencies.action.create")}
          </button>
          <Link
            href="/app/platform-currencies"
            className="rounded border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-surface"
          >
            Cancel
          </Link>
        </div>
      </form>
    </div>
  );
}
