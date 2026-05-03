"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  createTenantExchangeRate,
  deleteTenantExchangeRate,
  fetchTenantCurrencies,
  fetchTenantExchangeRates,
  patchTenantExchangeRate,
  replaceTenantCurrencies,
  type TenantCurrencyItem,
  type TenantExchangeRateItem,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";
type CurrencyTab = "organization" | "rates" | "available";
type ExchangeLoadState = "idle" | "loading" | "ready" | "forbidden" | "error";
type NoticeTone = "success" | "error";

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

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

  const [ratesLoad, setRatesLoad] = useState<ExchangeLoadState>("idle");
  const [ratesPage, setRatesPage] = useState(0);
  const [ratesTotalPages, setRatesTotalPages] = useState(0);
  const [ratesItems, setRatesItems] = useState<TenantExchangeRateItem[]>([]);
  const [ratesBusy, setRatesBusy] = useState(false);
  const [ratesNotice, setRatesNotice] = useState<{ tone: NoticeTone; text: string } | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [createFromId, setCreateFromId] = useState("");
  const [createToId, setCreateToId] = useState("");
  const [createRate, setCreateRate] = useState("");
  const [createDate, setCreateDate] = useState(todayIsoDate());

  const [editing, setEditing] = useState<TenantExchangeRateItem | null>(null);
  const [editRate, setEditRate] = useState("");
  const [editDate, setEditDate] = useState(todayIsoDate());

  const [deleting, setDeleting] = useState<TenantExchangeRateItem | null>(null);

  const canEdit = me.privileges.includes("TENANT_CURRENCY_EDIT");
  const canViewRates = me.privileges.includes("EXCHANGE_RATE_VIEW") || me.privileges.includes("EXCHANGE_RATE_MANAGE");
  const canManageRates = me.privileges.includes("EXCHANGE_RATE_MANAGE");

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

  const reloadRates = useCallback(
    async (page: number) => {
      if (!canViewRates) {
        setRatesLoad("forbidden");
        return;
      }
      setRatesLoad("loading");
      const r = await fetchTenantExchangeRates(page, 20, "effectiveDate,desc");
      if (!r.ok) {
        setRatesLoad(r.status === 403 ? "forbidden" : "error");
        return;
      }
      setRatesItems(r.items);
      setRatesPage(r.page);
      setRatesTotalPages(r.totalPages);
      setRatesLoad("ready");
    },
    [canViewRates],
  );

  useEffect(() => {
    void reload();
  }, [reload]);

  useEffect(() => {
    if (tab === "rates" && ratesLoad === "idle") {
      void reloadRates(0);
    }
  }, [tab, ratesLoad, reloadRates]);

  function toggle(code: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
    setMsg(null);
  }

  async function addCurrency(code: string) {
    if (selected.has(code) || busy) return;

    const previous = new Set(selected);
    const next = new Set(selected);
    next.add(code);

    setSelected(next);
    setBusy(true);
    setMsg(null);
    try {
      await replaceTenantCurrencies(Array.from(next));
      setInitialSelected(new Set(next));
      setMsg(t("tenantCurrencies.msg.saved"));
    } catch {
      setSelected(previous);
      setMsg(t("tenantCurrencies.msg.saveFailed"));
    } finally {
      setBusy(false);
    }
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

  function mapCreateError(status: number): string {
    if (status === 409) return t("tenantCurrencies.exchangeRates.error.duplicate");
    if (status === 422) return t("tenantCurrencies.exchangeRates.error.inactiveCurrency");
    return t("tenantCurrencies.exchangeRates.error.invalid");
  }

  function mapUpdateError(status: number): string {
    if (status === 409) return t("tenantCurrencies.exchangeRates.error.duplicate");
    if (status === 404) return t("tenantCurrencies.exchangeRates.error.notFoundRefresh");
    return t("tenantCurrencies.exchangeRates.error.invalid");
  }

  function mapDeleteError(status: number): string {
    if (status === 404) return t("tenantCurrencies.exchangeRates.error.notFoundRefresh");
    return t("tenantCurrencies.exchangeRates.error.deleteFailed");
  }

  function openCreateModal() {
    const currencyOptions = catalog.filter((row) => selected.has(row.code));
    const first = currencyOptions[0]?.id ?? "";
    const second = currencyOptions.find((c) => c.id !== first)?.id ?? "";
    setCreateFromId(first);
    setCreateToId(second);
    setCreateRate("");
    setCreateDate(todayIsoDate());
    setCreateOpen(true);
    setRatesNotice(null);
  }

  function openEditModal(item: TenantExchangeRateItem) {
    setEditing(item);
    setEditRate(String(item.rate));
    setEditDate(item.effectiveDate);
    setRatesNotice(null);
  }

  async function submitCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!createFromId || !createToId) {
      setRatesNotice({ tone: "error", text: t("tenantCurrencies.exchangeRates.error.invalid") });
      return;
    }
    setRatesBusy(true);
    const r = await createTenantExchangeRate({
      fromCurrencyId: createFromId,
      toCurrencyId: createToId,
      rate: createRate,
      effectiveDate: createDate,
    });
    if (!r.ok) {
      setRatesNotice({ tone: "error", text: mapCreateError(r.status) });
      setRatesBusy(false);
      return;
    }
    setCreateOpen(false);
    setRatesNotice({ tone: "success", text: t("tenantCurrencies.exchangeRates.msg.created") });
    await reloadRates(0);
    setRatesBusy(false);
  }

  async function submitEdit(e: React.FormEvent) {
    e.preventDefault();
    if (!editing) return;
    setRatesBusy(true);
    const r = await patchTenantExchangeRate(editing.id, {
      rate: editRate,
      effectiveDate: editDate,
    });
    if (!r.ok) {
      setRatesNotice({ tone: "error", text: mapUpdateError(r.status) });
      setRatesBusy(false);
      return;
    }
    setEditing(null);
    setRatesNotice({ tone: "success", text: t("tenantCurrencies.exchangeRates.msg.updated") });
    await reloadRates(ratesPage);
    setRatesBusy(false);
  }

  async function confirmDelete() {
    if (!deleting) return;
    setRatesBusy(true);
    const r = await deleteTenantExchangeRate(deleting.id);
    if (!r.ok) {
      setRatesNotice({ tone: "error", text: mapDeleteError(r.status) });
      setRatesBusy(false);
      return;
    }
    setDeleting(null);
    setRatesNotice({ tone: "success", text: t("tenantCurrencies.exchangeRates.msg.deleted") });
    const targetPage = ratesItems.length === 1 && ratesPage > 0 ? ratesPage - 1 : ratesPage;
    await reloadRates(targetPage);
    setRatesBusy(false);
  }

  async function onSave(e: React.FormEvent) {
    e.preventDefault();
    await persistSelection();
  }

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

  const exchangeCurrencyOptions = useMemo(
    () => catalog.filter((row) => selected.has(row.code)).sort((a, b) => a.code.localeCompare(b.code)),
    [catalog, selected],
  );

  const createToOptions = exchangeCurrencyOptions.filter((row) => row.id !== createFromId);

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("tenantCurrencies.title")}</h1>
        <p className="text-sm text-muted">{t("tenantCurrencies.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}{t("nav.dashboard")}
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
          {"<- "}{t("nav.dashboard")}
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
          {"<- "}{t("nav.dashboard")}
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
        <form onSubmit={onSave} className="space-y-4 rounded-md border border-border bg-surface p-4">
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
                  type="submit"
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
        </form>
      ) : null}

      {tab === "rates" ? (
        <div className="space-y-4 rounded-md border border-border bg-surface p-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-semibold text-foreground">{t("tenantCurrencies.section.exchangeRates")}</h2>
              <p className="mt-1 text-sm text-muted">{t("tenantCurrencies.exchangeRates.helper")}</p>
            </div>
            {canManageRates ? (
              <button
                type="button"
                onClick={openCreateModal}
                disabled={exchangeCurrencyOptions.length < 2 || ratesBusy}
                className="rounded bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
              >
                {t("tenantCurrencies.exchangeRates.action.new")}
              </button>
            ) : null}
          </div>

          {ratesNotice ? (
            <p className={`text-sm font-medium ${ratesNotice.tone === "error" ? "text-red-600" : "text-primary"}`}>
              {ratesNotice.text}
            </p>
          ) : null}

          {ratesLoad === "forbidden" ? (
            <p className="text-sm text-muted">{t("tenantCurrencies.exchangeRates.error.forbidden")}</p>
          ) : null}
          {ratesLoad === "loading" ? <p className="text-sm text-muted">{t("tenantCurrencies.state.loading")}</p> : null}
          {ratesLoad === "error" ? (
            <p className="text-sm text-muted">{t("tenantCurrencies.exchangeRates.error.load")}</p>
          ) : null}

          {ratesLoad === "ready" ? (
            <>
              <div className="overflow-x-auto rounded-md border border-border bg-background">
                <table className="w-full text-sm">
                  <thead className="bg-surface text-left text-xs font-medium uppercase text-muted">
                    <tr>
                      <th className="px-3 py-2">{t("tenantCurrencies.exchangeRates.col.from")}</th>
                      <th className="px-3 py-2">{t("tenantCurrencies.exchangeRates.col.to")}</th>
                      <th className="px-3 py-2">{t("tenantCurrencies.exchangeRates.col.rate")}</th>
                      <th className="px-3 py-2">{t("tenantCurrencies.exchangeRates.col.effectiveDate")}</th>
                      <th className="px-3 py-2 text-right">{t("tenantCurrencies.col.actions")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {ratesItems.map((row) => (
                      <tr key={row.id} className="border-t border-border">
                        <td className="px-3 py-2 text-foreground">{`${row.fromCurrencyCode} - ${row.fromCurrencyDisplayName}`}</td>
                        <td className="px-3 py-2 text-foreground">{`${row.toCurrencyCode} - ${row.toCurrencyDisplayName}`}</td>
                        <td className="px-3 py-2 font-mono text-foreground">{Number(row.rate).toFixed(8)}</td>
                        <td className="px-3 py-2 text-foreground">{row.effectiveDate}</td>
                        <td className="px-3 py-2 text-right">
                          {canManageRates ? (
                            <div className="inline-flex gap-3">
                              <button
                                type="button"
                                onClick={() => openEditModal(row)}
                                className="text-sm font-medium text-primary hover:underline"
                              >
                                {t("tenantCurrencies.exchangeRates.action.edit")}
                              </button>
                              <button
                                type="button"
                                onClick={() => setDeleting(row)}
                                className="text-sm font-medium text-red-600 hover:underline"
                              >
                                {t("tenantCurrencies.exchangeRates.action.delete")}
                              </button>
                            </div>
                          ) : null}
                        </td>
                      </tr>
                    ))}
                    {ratesItems.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="px-3 py-6 text-center text-sm text-muted">
                          {t("tenantCurrencies.exchangeRates.state.none")}
                        </td>
                      </tr>
                    ) : null}
                  </tbody>
                </table>
              </div>

              <div className="flex items-center justify-end gap-2">
                <button
                  type="button"
                  disabled={ratesBusy || ratesPage <= 0}
                  onClick={() => void reloadRates(ratesPage - 1)}
                  className="rounded border border-border px-3 py-1.5 text-sm text-foreground disabled:opacity-50"
                >
                  {t("tenantCurrencies.exchangeRates.action.prev")}
                </button>
                <p className="text-sm text-muted">
                  {t("tenantCurrencies.exchangeRates.page")
                    .replace("{n}", String(ratesPage + 1))
                    .replace("{t}", String(Math.max(1, ratesTotalPages)))}
                </p>
                <button
                  type="button"
                  disabled={ratesBusy || ratesPage + 1 >= ratesTotalPages}
                  onClick={() => void reloadRates(ratesPage + 1)}
                  className="rounded border border-border px-3 py-1.5 text-sm text-foreground disabled:opacity-50"
                >
                  {t("tenantCurrencies.exchangeRates.action.next")}
                </button>
              </div>
            </>
          ) : null}
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
                          onClick={() => void addCurrency(row.code)}
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

      {createOpen ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <form onSubmit={submitCreate} className="w-full max-w-lg space-y-4 rounded-md bg-background p-4 shadow-xl">
            <h3 className="text-lg font-semibold text-foreground">{t("tenantCurrencies.exchangeRates.modal.createTitle")}</h3>

            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("tenantCurrencies.exchangeRates.field.from")}</span>
              <select
                value={createFromId}
                onChange={(e) => {
                  const nextFrom = e.target.value;
                  setCreateFromId(nextFrom);
                  if (nextFrom === createToId) {
                    const nextTo = exchangeCurrencyOptions.find((row) => row.id !== nextFrom)?.id ?? "";
                    setCreateToId(nextTo);
                  }
                }}
                className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
              >
                {exchangeCurrencyOptions.map((row) => (
                  <option key={row.id} value={row.id}>
                    {`${row.code} - ${row.displayName}`}
                  </option>
                ))}
              </select>
            </label>

            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("tenantCurrencies.exchangeRates.field.to")}</span>
              <select
                value={createToId}
                onChange={(e) => setCreateToId(e.target.value)}
                className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
              >
                {createToOptions.map((row) => (
                  <option key={row.id} value={row.id}>
                    {`${row.code} - ${row.displayName}`}
                  </option>
                ))}
              </select>
            </label>

            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("tenantCurrencies.exchangeRates.field.rate")}</span>
              <input
                type="number"
                min="0"
                step="0.00000001"
                value={createRate}
                onChange={(e) => setCreateRate(e.target.value)}
                placeholder={t("tenantCurrencies.exchangeRates.field.ratePlaceholder")}
                className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
                required
              />
            </label>

            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("tenantCurrencies.exchangeRates.field.effectiveDate")}</span>
              <input
                type="date"
                value={createDate}
                onChange={(e) => setCreateDate(e.target.value)}
                className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
                required
              />
            </label>

            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setCreateOpen(false)}
                className="rounded border border-border px-3 py-2 text-sm font-medium text-foreground"
              >
                {t("tenantCurrencies.exchangeRates.action.cancel")}
              </button>
              <button
                type="submit"
                disabled={ratesBusy}
                className="rounded bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
              >
                {ratesBusy ? t("tenantCurrencies.state.saving") : t("tenantCurrencies.exchangeRates.action.create")}
              </button>
            </div>
          </form>
        </div>
      ) : null}

      {editing ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <form onSubmit={submitEdit} className="w-full max-w-lg space-y-4 rounded-md bg-background p-4 shadow-xl">
            <h3 className="text-lg font-semibold text-foreground">{t("tenantCurrencies.exchangeRates.modal.editTitle")}</h3>

            <p className="text-sm text-muted">
              {`${editing.fromCurrencyCode} -> ${editing.toCurrencyCode}`}
            </p>

            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("tenantCurrencies.exchangeRates.field.rate")}</span>
              <input
                type="number"
                min="0"
                step="0.00000001"
                value={editRate}
                onChange={(e) => setEditRate(e.target.value)}
                className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
                required
              />
            </label>

            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("tenantCurrencies.exchangeRates.field.effectiveDate")}</span>
              <input
                type="date"
                value={editDate}
                onChange={(e) => setEditDate(e.target.value)}
                className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
                required
              />
            </label>

            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setEditing(null)}
                className="rounded border border-border px-3 py-2 text-sm font-medium text-foreground"
              >
                {t("tenantCurrencies.exchangeRates.action.cancel")}
              </button>
              <button
                type="submit"
                disabled={ratesBusy}
                className="rounded bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
              >
                {ratesBusy ? t("tenantCurrencies.state.saving") : t("tenantCurrencies.exchangeRates.action.save")}
              </button>
            </div>
          </form>
        </div>
      ) : null}

      {deleting ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-lg space-y-4 rounded-md bg-background p-4 shadow-xl">
            <h3 className="text-lg font-semibold text-foreground">{t("tenantCurrencies.exchangeRates.modal.deleteTitle")}</h3>
            <p className="text-sm text-muted">
              {t("tenantCurrencies.exchangeRates.modal.deleteBody")
                .replace("{from}", deleting.fromCurrencyCode)
                .replace("{to}", deleting.toCurrencyCode)
                .replace("{date}", deleting.effectiveDate)}
            </p>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setDeleting(null)}
                className="rounded border border-border px-3 py-2 text-sm font-medium text-foreground"
              >
                {t("tenantCurrencies.exchangeRates.action.cancel")}
              </button>
              <button
                type="button"
                onClick={() => void confirmDelete()}
                disabled={ratesBusy}
                className="rounded bg-red-600 px-3 py-2 text-sm font-semibold text-white disabled:opacity-50"
              >
                {t("tenantCurrencies.exchangeRates.action.delete")}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
