"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchTenantCurrencies,
  replaceTenantCurrencies,
  type TenantCurrencyItem,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function TenantCurrenciesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [catalog, setCatalog] = useState<TenantCurrencyItem[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoad("loading");
    setMsg(null);
    const r = await fetchTenantCurrencies();
    if (!r.ok) {
      setLoad(r.status === 403 ? "forbidden" : "error");
      return;
    }
    setCatalog(r.items);
    setSelected(new Set(r.items.filter((i) => i.assigned).map((i) => i.code)));
    setLoad("ready");
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  function toggle(code: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
  }

  async function onSave(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      await replaceTenantCurrencies(Array.from(selected));
      setMsg(t("tenantCurrencies.msg.saved"));
    } catch {
      setMsg(t("tenantCurrencies.msg.saveFailed"));
    } finally {
      setBusy(false);
    }
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("tenantCurrencies.title")}</h1>
        <p className="text-sm text-muted">{t("tenantCurrencies.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-2xl">
        <p className="text-sm text-muted">{t("tenantCurrencies.state.loading")}</p>
      </div>
    );
  }

  if (load === "error") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("tenantCurrencies.title")}</h1>
        <p className="text-sm text-muted">{t("tenantCurrencies.error.load")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("tenantCurrencies.title")}</h1>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>

      <p className="text-sm text-muted">{t("tenantCurrencies.helper.intro")}</p>

      {msg ? <p className="text-sm font-medium text-primary">{msg}</p> : null}

      <form onSubmit={(e) => void onSave(e)} className="space-y-4">
        <div className="overflow-x-auto rounded-md border border-border">
          <table className="w-full text-sm">
            <thead className="bg-surface text-left text-xs font-medium uppercase text-muted">
              <tr>
                <th className="px-3 py-2">{t("tenantCurrencies.col.assigned")}</th>
                <th className="px-3 py-2">{t("tenantCurrencies.col.code")}</th>
                <th className="px-3 py-2">{t("tenantCurrencies.col.name")}</th>
              </tr>
            </thead>
            <tbody>
              {catalog.map((row) => (
                <tr key={row.id} className="border-t border-border">
                  <td className="px-3 py-2">
                    <input
                      type="checkbox"
                      checked={selected.has(row.code)}
                      onChange={() => toggle(row.code)}
                      aria-label={row.code}
                      disabled={busy || !me.privileges.includes("TENANT_CURRENCY_EDIT")}
                    />
                  </td>
                  <td className="px-3 py-2 font-mono font-semibold text-foreground">{row.code}</td>
                  <td className="px-3 py-2 text-foreground">{row.displayName}</td>
                </tr>
              ))}
              {catalog.length === 0 && (
                <tr>
                  <td colSpan={3} className="px-3 py-4 text-center text-sm text-muted">
                    No platform currencies available.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {me.privileges.includes("TENANT_CURRENCY_EDIT") ? (
          <div>
            <button
              type="submit"
              disabled={busy}
              className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
            >
              {busy ? t("tenantCurrencies.state.saving") : t("tenantCurrencies.action.save")}
            </button>
          </div>
        ) : null}
      </form>
    </div>
  );
}
