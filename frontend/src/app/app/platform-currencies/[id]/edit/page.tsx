"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { fetchPlatformCurrency, patchPlatformCurrency } from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "notFound" | "error";

export default function PlatformCurrencyEditPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const id = params.id;
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [code, setCode] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [sortOrder, setSortOrder] = useState("100");
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!me.platformSuperadmin) return;
    void (async () => {
      const r = await fetchPlatformCurrency(id);
      if (!r.ok) {
        setLoad(r.status === 403 ? "forbidden" : r.status === 404 ? "notFound" : "error");
        return;
      }
      setCode(r.item.code);
      setDisplayName(r.item.displayName);
      setSortOrder(String(r.item.sortOrder));
      setActive(r.item.active);
      setLoad("ready");
    })();
  }, [id, me.platformSuperadmin]);

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCurrencies.title.edit")}</h1>
        <p className="text-sm text-muted">{t("platformCurrencies.error.notOperator")}</p>
        <Link href="/app/platform-currencies" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformCurrencies.action.backToList")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-lg">
        <p className="text-sm text-muted">{t("platformCurrencies.state.loading")}</p>
      </div>
    );
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCurrencies.title.edit")}</h1>
        <p className="text-sm text-muted">{t("platformCurrencies.error.forbidden")}</p>
        <Link href="/app/platform-currencies" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformCurrencies.action.backToList")}
        </Link>
      </div>
    );
  }

  if (load === "notFound" || load === "error") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCurrencies.title.edit")}</h1>
        <p className="text-sm text-muted">
          {load === "notFound" ? t("platformCurrencies.error.notFound") : t("platformCurrencies.error.load")}
        </p>
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
      await patchPlatformCurrency(id, {
        displayName: displayName.trim(),
        sortOrder: parseInt(sortOrder, 10),
        active,
      });
      router.push("/app/platform-currencies");
    } catch {
      setError(t("platformCurrencies.msg.saveFailed"));
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">
          {t("platformCurrencies.title.edit")} — <span className="font-mono">{code}</span>
        </h1>
        <Link href="/app/platform-currencies" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformCurrencies.action.backToList")}
        </Link>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5 shadow-sm">
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformCurrencies.label.code")}</label>
          <p className="rounded border border-border bg-background px-3 py-2 text-sm font-mono font-semibold text-foreground opacity-60">
            {code}
          </p>
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformCurrencies.label.name")}</label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            maxLength={128}
            required
            autoFocus
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
            id="edit-currency-active"
            type="checkbox"
            checked={active}
            onChange={(e) => setActive(e.target.checked)}
          />
          <label htmlFor="edit-currency-active" className="text-sm text-foreground">
            {t("platformCurrencies.label.active")}
          </label>
        </div>
        <div className="flex gap-3">
          <button
            type="submit"
            disabled={busy}
            className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
          >
            {t("platformCurrencies.action.save")}
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
