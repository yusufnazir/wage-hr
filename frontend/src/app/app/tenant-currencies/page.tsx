"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
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
import { PlatformDateInput } from "@/components/ui/PlatformDateInput";
import { formatUserFacingDate } from "@/lib/user-date-format";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";
type CurrencyTab = "organization" | "rates" | "available";
type ExchangeLoadState = "idle" | "loading" | "ready" | "forbidden" | "error";
type NoticeTone = "success" | "error";
type RatesMode = { kind: "list" } | { kind: "create" } | { kind: "edit"; item: TenantExchangeRateItem };

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function TenantCurrenciesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const searchParams = useSearchParams();
  const router = useRouter();
  const rawTab = searchParams.get("tab") as CurrencyTab | null;
  const tab: CurrencyTab = rawTab === "rates" || rawTab === "available" ? rawTab : "organization";
  const setTab = (t: CurrencyTab) => router.push(`/app/tenant-currencies?tab=${t}`);

  const [load, setLoad] = useState<LoadState>("loading");
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

  const rawMode = searchParams.get("mode");
  const rawRateId = searchParams.get("rateId");
  const ratesMode: RatesMode = useMemo(() => {
    if (tab !== "rates") return { kind: "list" };
    if (rawMode === "create") return { kind: "create" };
    if (rawMode === "edit" && rawRateId) {
      const item = ratesItems.find((i) => i.id === rawRateId);
      if (item) return { kind: "edit", item };
    }
    return { kind: "list" };
  }, [tab, rawMode, rawRateId, ratesItems]);

  const [rateForm, setRateForm] = useState({ fromId: "", toId: "", rate: "", date: todayIsoDate() });
  const [ratesFormMsg, setRatesFormMsg] = useState<string | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);

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

  function openCreate() {
    const options = catalog.filter((row) => selected.has(row.code)).sort((a, b) => a.code.localeCompare(b.code));
    const first = options[0]?.id ?? "";
    const second = options.find((c) => c.id !== first)?.id ?? "";
    setRateForm({ fromId: first, toId: second, rate: "", date: todayIsoDate() });
    setRatesFormMsg(null);
    setRatesNotice(null);
    router.push("/app/tenant-currencies?tab=rates&mode=create");
  }

  function openEdit(item: TenantExchangeRateItem) {
    setRateForm({ fromId: "", toId: "", rate: String(item.rate), date: item.effectiveDate });
    setRatesFormMsg(null);
    setRatesNotice(null);
    router.push(`/app/tenant-currencies?tab=rates&mode=edit&rateId=${item.id}`);
  }

  function cancelRateForm() {
    setRatesFormMsg(null);
    router.push("/app/tenant-currencies?tab=rates");
  }

  async function submitRateForm(e: React.FormEvent) {
    e.preventDefault();
    setRatesBusy(true);
    setRatesFormMsg(null);
    if (ratesMode.kind === "create") {
      if (!rateForm.fromId || !rateForm.toId) {
        setRatesFormMsg(t("tenantCurrencies.exchangeRates.error.invalid"));
        setRatesBusy(false);
        return;
      }
      const r = await createTenantExchangeRate({
        fromCurrencyId: rateForm.fromId,
        toCurrencyId: rateForm.toId,
        rate: rateForm.rate,
        effectiveDate: rateForm.date,
      });
      if (!r.ok) {
        setRatesFormMsg(mapCreateError(r.status));
        setRatesBusy(false);
        return;
      }
      setRatesNotice({ tone: "success", text: t("tenantCurrencies.exchangeRates.msg.created") });
      router.push("/app/tenant-currencies?tab=rates");
      await reloadRates(0);
    } else if (ratesMode.kind === "edit") {
      const r = await patchTenantExchangeRate(ratesMode.item.id, {
        rate: rateForm.rate,
        effectiveDate: rateForm.date,
      });
      if (!r.ok) {
        setRatesFormMsg(mapUpdateError(r.status));
        setRatesBusy(false);
        return;
      }
      setRatesNotice({ tone: "success", text: t("tenantCurrencies.exchangeRates.msg.updated") });
      router.push("/app/tenant-currencies?tab=rates");
      await reloadRates(ratesPage);
    }
    setRatesBusy(false);
  }

  async function deleteRate(id: string) {
    setRatesBusy(true);
    const r = await deleteTenantExchangeRate(id);
    if (!r.ok) {
      setRatesNotice({ tone: "error", text: mapDeleteError(r.status) });
      setRatesBusy(false);
      setConfirmDeleteId(null);
      return;
    }
    setConfirmDeleteId(null);
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

  const rateFormToOptions = exchangeCurrencyOptions.filter((row) => row.id !== rateForm.fromId);

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
        <Link
          href="/app/tenant-currencies?tab=organization"
          className={`rounded-md px-3 py-1.5 text-sm font-medium ${
            tab === "organization" ? "bg-surface text-foreground" : "text-muted hover:bg-surface"
          }`}
        >
          {t("tenantCurrencies.tab.organization")}
        </Link>
        <Link
          href="/app/tenant-currencies?tab=rates"
          className={`rounded-md px-3 py-1.5 text-sm font-medium ${
            tab === "rates" ? "bg-surface text-foreground" : "text-muted hover:bg-surface"
          }`}
        >
          {t("tenantCurrencies.tab.exchangeRates")}
        </Link>
        <Link
          href="/app/tenant-currencies?tab=available"
          className={`rounded-md px-3 py-1.5 text-sm font-medium ${
            tab === "available" ? "bg-surface text-foreground" : "text-muted hover:bg-surface"
          }`}
        >
          {t("tenantCurrencies.tab.available")}
        </Link>
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
          {ratesMode.kind !== "list" ? (
            <>
              <div className="flex items-center justify-between">
                <h2 className="text-base font-semibold text-foreground">
                  {ratesMode.kind === "create"
                    ? t("tenantCurrencies.exchangeRates.modal.createTitle")
                    : t("tenantCurrencies.exchangeRates.modal.editTitle")}
                </h2>
                <button
                  type="button"
                  onClick={cancelRateForm}
                  disabled={ratesBusy}
                  className="text-sm text-primary underline-offset-4 hover:underline disabled:opacity-40"
                >
                  ← {t("tenantCurrencies.exchangeRates.action.cancel")}
                </button>
              </div>

              {ratesMode.kind === "edit" ? (
                <p className="text-sm text-muted">
                  {`${ratesMode.item.fromCurrencyCode} → ${ratesMode.item.toCurrencyCode}`}
                </p>
              ) : null}

              <form onSubmit={submitRateForm} className="max-w-md space-y-4">
                {ratesMode.kind === "create" ? (
                  <>
                    <label className="block">
                      <span className="text-sm text-muted">{t("tenantCurrencies.exchangeRates.field.from")}</span>
                      <select
                        value={rateForm.fromId}
                        onChange={(e) => {
                          const nextFrom = e.target.value;
                          setRateForm((f) => ({
                            ...f,
                            fromId: nextFrom,
                            toId: f.toId === nextFrom
                              ? (exchangeCurrencyOptions.find((r) => r.id !== nextFrom)?.id ?? "")
                              : f.toId,
                          }));
                        }}
                        className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-sm text-foreground"
                      >
                        {exchangeCurrencyOptions.map((row) => (
                          <option key={row.id} value={row.id}>{`${row.code} - ${row.displayName}`}</option>
                        ))}
                      </select>
                    </label>
                    <label className="block">
                      <span className="text-sm text-muted">{t("tenantCurrencies.exchangeRates.field.to")}</span>
                      <select
                        value={rateForm.toId}
                        onChange={(e) => setRateForm((f) => ({ ...f, toId: e.target.value }))}
                        className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-sm text-foreground"
                      >
                        {rateFormToOptions.map((row) => (
                          <option key={row.id} value={row.id}>{`${row.code} - ${row.displayName}`}</option>
                        ))}
                      </select>
                    </label>
                  </>
                ) : null}

                <label className="block">
                  <span className="text-sm text-muted">{t("tenantCurrencies.exchangeRates.field.rate")}</span>
                  <input
                    type="number"
                    min="0"
                    step="0.00000001"
                    value={rateForm.rate}
                    onChange={(e) => setRateForm((f) => ({ ...f, rate: e.target.value }))}
                    placeholder={t("tenantCurrencies.exchangeRates.field.ratePlaceholder")}
                    className="mt-1 w-full rounded border border-border bg-surface px-2 py-1.5 text-sm text-foreground"
                    required
                  />
                </label>

                <label className="block">
                  <span className="text-sm text-muted">{t("tenantCurrencies.exchangeRates.field.effectiveDate")}</span>
                  <PlatformDateInput
                    value={rateForm.date}
                    dateFormat={me.dateFormat}
                    onChange={(v) => setRateForm((f) => ({ ...f, date: v }))}
                    className="mt-1"
                  />
                </label>

                {ratesFormMsg ? <p className="text-sm text-destructive">{ratesFormMsg}</p> : null}

                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={cancelRateForm}
                    disabled={ratesBusy}
                    className="rounded border border-border px-3 py-1.5 text-sm hover:bg-surface-alt disabled:opacity-40"
                  >
                    {t("tenantCurrencies.exchangeRates.action.cancel")}
                  </button>
                  <button
                    type="submit"
                    disabled={ratesBusy}
                    className="rounded bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40"
                  >
                    {ratesBusy
                      ? t("tenantCurrencies.state.saving")
                      : ratesMode.kind === "create"
                        ? t("tenantCurrencies.exchangeRates.action.create")
                        : t("tenantCurrencies.exchangeRates.action.save")}
                  </button>
                </div>
              </form>
            </>
          ) : (
            <>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h2 className="text-lg font-semibold text-foreground">{t("tenantCurrencies.section.exchangeRates")}</h2>
                  <p className="mt-1 text-sm text-muted">{t("tenantCurrencies.exchangeRates.helper")}</p>
                </div>
                {canManageRates ? (
                  <button
                    type="button"
                    onClick={openCreate}
                    disabled={exchangeCurrencyOptions.length < 2 || ratesBusy}
                    className="rounded bg-primary px-3 py-1 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-40"
                  >
                    {t("tenantCurrencies.exchangeRates.action.new")}
                  </button>
                ) : null}
              </div>

              {ratesNotice ? (
                <p className={`text-sm font-medium ${ratesNotice.tone === "error" ? "text-destructive" : "text-foreground"}`}>
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
                  <div className="overflow-x-auto rounded-md border border-border">
                    <table className="min-w-full divide-y divide-border text-sm">
                      <thead className="bg-surface-alt">
                        <tr>
                          <th className="px-4 py-2 text-left font-medium text-muted">{t("tenantCurrencies.exchangeRates.col.from")}</th>
                          <th className="px-4 py-2 text-left font-medium text-muted">{t("tenantCurrencies.exchangeRates.col.to")}</th>
                          <th className="px-4 py-2 text-left font-medium text-muted">{t("tenantCurrencies.exchangeRates.col.rate")}</th>
                          <th className="px-4 py-2 text-left font-medium text-muted">{t("tenantCurrencies.exchangeRates.col.effectiveDate")}</th>
                          {canManageRates ? <th className="px-4 py-2" /> : null}
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border bg-surface">
                        {ratesItems.map((row) => (
                          <tr key={row.id}>
                            <td className="px-4 py-2 text-foreground">{`${row.fromCurrencyCode} - ${row.fromCurrencyDisplayName}`}</td>
                            <td className="px-4 py-2 text-foreground">{`${row.toCurrencyCode} - ${row.toCurrencyDisplayName}`}</td>
                            <td className="px-4 py-2 font-mono text-foreground">{Number(row.rate).toFixed(8)}</td>
                            <td className="px-4 py-2 text-muted">{formatUserFacingDate(row.effectiveDate, me.dateFormat)}</td>
                            {canManageRates ? (
                              <td className="px-4 py-2 text-right">
                                {confirmDeleteId === row.id ? (
                                  <div className="inline-flex items-center gap-3">
                                    <span className="text-sm text-muted">Delete this rate?</span>
                                    <button
                                      type="button"
                                      onClick={() => void deleteRate(row.id)}
                                      disabled={ratesBusy}
                                      className="text-sm text-red-600 underline-offset-4 hover:underline disabled:opacity-40"
                                    >
                                      {t("tenantCurrencies.exchangeRates.action.delete")}
                                    </button>
                                    <button
                                      type="button"
                                      onClick={() => setConfirmDeleteId(null)}
                                      disabled={ratesBusy}
                                      className="text-sm text-muted underline-offset-4 hover:underline disabled:opacity-40"
                                    >
                                      {t("tenantCurrencies.exchangeRates.action.cancel")}
                                    </button>
                                  </div>
                                ) : (
                                  <div className="inline-flex gap-3">
                                    <button
                                      type="button"
                                      onClick={() => openEdit(row)}
                                      className="text-sm text-primary underline-offset-4 hover:underline"
                                    >
                                      {t("tenantCurrencies.exchangeRates.action.edit")}
                                    </button>
                                    <button
                                      type="button"
                                      onClick={() => setConfirmDeleteId(row.id)}
                                      className="text-sm text-red-600 underline-offset-4 hover:underline"
                                    >
                                      {t("tenantCurrencies.exchangeRates.action.delete")}
                                    </button>
                                  </div>
                                )}
                              </td>
                            ) : null}
                          </tr>
                        ))}
                        {ratesItems.length === 0 ? (
                          <tr>
                            <td colSpan={canManageRates ? 5 : 4} className="px-4 py-6 text-center text-sm text-muted">
                              {t("tenantCurrencies.exchangeRates.state.none")}
                            </td>
                          </tr>
                        ) : null}
                      </tbody>
                    </table>
                  </div>

                  <div className="flex items-center gap-2 text-sm">
                    <button
                      type="button"
                      disabled={ratesBusy || ratesPage <= 0}
                      onClick={() => void reloadRates(ratesPage - 1)}
                      className="rounded border border-border px-3 py-1 disabled:opacity-40"
                    >
                      {t("tenantCurrencies.exchangeRates.action.prev")}
                    </button>
                    <span className="py-1 text-muted">
                      {t("tenantCurrencies.exchangeRates.page")
                        .replace("{n}", String(ratesPage + 1))
                        .replace("{t}", String(Math.max(1, ratesTotalPages)))}
                    </span>
                    <button
                      type="button"
                      disabled={ratesBusy || ratesPage + 1 >= ratesTotalPages}
                      onClick={() => void reloadRates(ratesPage + 1)}
                      className="rounded border border-border px-3 py-1 disabled:opacity-40"
                    >
                      {t("tenantCurrencies.exchangeRates.action.next")}
                    </button>
                  </div>
                </>
              ) : null}
            </>
          )}
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

    </div>
  );
}
