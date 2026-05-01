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
type CurrencyTab = "organization" | "rates" | "available";

export default function TenantCurrenciesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [tab, setTab] = useState<CurrencyTab>("organization");
  const [catalog, setCatalog] = useState<TenantCurrencyItem[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [initialSelected, setInitialSelected] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [search, setSearch] = useState("");
  const [msg, setMsg] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoad("loading");
    setMsg(null);
    const r = await fetchTenantCurrencies();
    if (!r.ok) {
      setLoad(r.status === 403 ? "forbidden" : "error");
      return;
    }
    const assigned = new Set(r.items.filter((i) => i.assigned).map((i) => i.code));
    setCatalog(r.items);
    setSelected(assigned);
    setInitialSelected(new Set(assigned));
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
    setMsg(null);
  }

  function addCurrency(code: string) {
    if (selected.has(code)) return;
    toggle(code);
  }

  function removeCurrency(code: string) {
    if (!selected.has(code)) return;
    toggle(code);
  }

  async function persistSelection() {
    setBusy(true);
    setMsg(null);
    try {
      await replaceTenantCurrencies(Array.from(selected));
      setInitialSelected(new Set(selected));
      setMsg(t("tenantCurrencies.msg.saved"));
    } catch {
      setMsg(t("tenantCurrencies.msg.saveFailed"));
    } finally {
      setBusy(false);
    }
  }

  async function onSave(e: React.FormEvent) {
    e.preventDefault();
    await persistSelection();
  }

  const canEdit = me.privileges.includes("TENANT_CURRENCY_EDIT");
  const hasUnsaved =
    selected.size !== initialSelected.size || Array.from(selected).some((code) => !initialSelected.has(code));
  const assignedItems = catalog.filter((row) => selected.has(row.code));
  const availableItems = catalog
    .filter((row) => !selected.has(row.code))
    .filter((row) => {
      const q = search.trim().toLowerCase();
      if (!q) return true;
      return row.code.toLowerCase().includes(q) || row.displayName.toLowerCase().includes(q);
    });

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
    <div className="mx-auto max-w-6xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">{t("tenantCurrencies.managementTitle")}</h1>
          <p className="mt-1 text-sm text-muted">{t("tenantCurrencies.managementSubtitle")}</p>
        </div>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>

      <div className="flex flex-wrap gap-2 border-b border-border pb-2">
        <button
          type="button"
          onClick={() => setTab("organization")}
          className={`rounded-md px-3 py-1.5 text-sm font-medium ${
            tab === "organization" ? "bg-surface text-foreground" : "text-muted hover:bg-surface"
          }`}
        >
          {t("tenantCurrencies.tab.organization")}
        </button>
        <button
          type="button"
          onClick={() => setTab("rates")}
          className={`rounded-md px-3 py-1.5 text-sm font-medium ${
            tab === "rates" ? "bg-surface text-foreground" : "text-muted hover:bg-surface"
          }`}
        >
          {t("tenantCurrencies.tab.exchangeRates")}
        </button>
        <button
          type="button"
          onClick={() => setTab("available")}
          className={`rounded-md px-3 py-1.5 text-sm font-medium ${
            tab === "available" ? "bg-surface text-foreground" : "text-muted hover:bg-surface"
          }`}
        >
          {t("tenantCurrencies.tab.available")}
        </button>
      </div>

      {msg ? <p className="text-sm font-medium text-primary">{msg}</p> : null}

      {tab === "organization" ? (
        <div className="space-y-4 rounded-md border border-border bg-surface p-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-semibold text-foreground">{t("tenantCurrencies.section.organization")}</h2>
              <p className="mt-1 text-sm text-muted">{t("tenantCurrencies.helper.intro")}</p>
            </div>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setTab("available")}
                className="rounded border border-border px-3 py-2 text-sm font-semibold text-foreground hover:bg-background"
              >
                {t("tenantCurrencies.action.addCurrency")}
              </button>
              {canEdit ? (
                <button
                  type="button"
                  onClick={() => void persistSelection()}
                  disabled={busy || !hasUnsaved}
                  className="rounded bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
                >
                  {busy ? t("tenantCurrencies.state.saving") : t("tenantCurrencies.action.saveChanges")}
                </button>
              ) : null}
            </div>
          </div>

          <div className="overflow-x-auto rounded-md border border-border bg-background">
            <table className="w-full text-sm">
              <thead className="bg-surface text-left text-xs font-medium uppercase text-muted">
                <tr>
                  <th className="px-3 py-2">{t("tenantCurrencies.col.code")}</th>
                  <th className="px-3 py-2">{t("tenantCurrencies.col.name")}</th>
                  <th className="px-3 py-2">{t("tenantCurrencies.col.status")}</th>
                  <th className="px-3 py-2 text-right">{t("tenantCurrencies.col.actions")}</th>
                </tr>
              </thead>
              <tbody>
                {assignedItems.map((row) => (
                  <tr key={row.id} className="border-t border-border">
                    <td className="px-3 py-2 font-mono font-semibold text-foreground">{row.code}</td>
                    <td className="px-3 py-2 text-foreground">{row.displayName}</td>
                    <td className="px-3 py-2 text-sm text-foreground">{t("tenantCurrencies.status.enabled")}</td>
                    <td className="px-3 py-2 text-right">
                      {canEdit ? (
                        <button
                          type="button"
                          onClick={() => removeCurrency(row.code)}
                          disabled={busy}
                          className="text-sm font-medium text-primary hover:underline disabled:opacity-60"
                        >
                          {t("tenantCurrencies.action.removeCurrency")}
                        </button>
                      ) : null}
                    </td>
                  </tr>
                ))}
                {assignedItems.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-3 py-6 text-center text-sm text-muted">
                      {t("tenantCurrencies.state.noneAssigned")}
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}

      {tab === "rates" ? (
        <div className="rounded-md border border-border bg-surface p-4">
          <h2 className="text-lg font-semibold text-foreground">{t("tenantCurrencies.section.exchangeRates")}</h2>
          <p className="mt-2 text-sm text-muted">{t("tenantCurrencies.state.exchangeRatesPending")}</p>
        </div>
      ) : null}

      {tab === "available" ? (
        <div className="space-y-4 rounded-md border border-border bg-surface p-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-lg font-semibold text-foreground">{t("tenantCurrencies.section.available")}</h2>
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={t("tenantCurrencies.search.placeholder")}
              className="w-full max-w-xs rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
            />
          </div>

          <div className="overflow-x-auto rounded-md border border-border bg-background">
            <table className="w-full text-sm">
              <thead className="bg-surface text-left text-xs font-medium uppercase text-muted">
                <tr>
                  <th className="px-3 py-2">{t("tenantCurrencies.col.code")}</th>
                  <th className="px-3 py-2">{t("tenantCurrencies.col.name")}</th>
                  <th className="px-3 py-2 text-right">{t("tenantCurrencies.col.actions")}</th>
                </tr>
              </thead>
              <tbody>
                {availableItems.map((row) => (
                  <tr key={row.id} className="border-t border-border">
                    <td className="px-3 py-2 font-mono font-semibold text-foreground">{row.code}</td>
                    <td className="px-3 py-2 text-foreground">{row.displayName}</td>
                    <td className="px-3 py-2 text-right">
                      {canEdit ? (
                        <button
                          type="button"
                          onClick={() => addCurrency(row.code)}
                          disabled={busy}
                          className="text-sm font-medium text-primary hover:underline disabled:opacity-60"
                        >
                          {t("tenantCurrencies.action.addCurrency")}
                        </button>
                      ) : null}
                    </td>
                  </tr>
                ))}
                {availableItems.length === 0 ? (
                  <tr>
                    <td colSpan={3} className="px-3 py-6 text-center text-sm text-muted">
                      {t("tenantCurrencies.state.noneAvailable")}
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>

          {canEdit ? (
            <div className="flex justify-end">
              <button
                type="button"
                onClick={() => void persistSelection()}
                disabled={busy || !hasUnsaved}
                className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
              >
                {busy ? t("tenantCurrencies.state.saving") : t("tenantCurrencies.action.saveChanges")}
              </button>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
