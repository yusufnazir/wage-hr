"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchPlatformLedgerTemplates,
  fetchPlatformCountries,
  patchActivatePlatformLedgerTemplate,
  patchDeactivatePlatformLedgerTemplate,
  type PlatformLedgerTemplateRow,
  type PlatformCountryRow,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function PlatformLedgerTemplatesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<PlatformLedgerTemplateRow[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [countryFilter, setCountryFilter] = useState<string>("");
  const [activeFilter, setActiveFilter] = useState<"all" | "active">("all");
  const [countries, setCountries] = useState<PlatformCountryRow[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<
    { kind: "activate" | "deactivate"; row: PlatformLedgerTemplateRow } | null
  >(null);

  const activeParam = useMemo(() => (activeFilter === "active" ? true : null), [activeFilter]);

  const reload = useCallback(
    async (p: number) => {
      setMsg(null);
      const r = await fetchPlatformLedgerTemplates({
        page: p,
        size: 20,
        country: countryFilter || null,
        active: activeParam,
        locale: me.locale,
      });
      if (!r.ok) {
        setLoad(r.status === 403 ? "forbidden" : "error");
        return;
      }
      setItems(r.items);
      setTotalPages(r.totalPages);
      setLoad("ready");
    },
    [activeParam, countryFilter, me.locale],
  );

  useEffect(() => {
    if (!me.platformSuperadmin) return;
    void (async () => {
      const c = await fetchPlatformCountries({
        page: 0,
        size: 200,
        active: true,
        payrollEnabled: true,
        locale: me.locale,
      });
      if (c.ok) setCountries(c.items);
    })();
  }, [me.locale, me.platformSuperadmin]);

  useEffect(() => {
    if (!me.platformSuperadmin) return;
    void reload(page);
  }, [me.platformSuperadmin, page, reload]);

  async function runConfirmed() {
    if (!confirm) return;
    const row = confirm.row;
    setBusyId(row.id);
    setMsg(null);
    try {
      if (confirm.kind === "deactivate") {
        await patchDeactivatePlatformLedgerTemplate(row.id, { locale: me.locale });
      } else if (confirm.kind === "activate") {
        await patchActivatePlatformLedgerTemplate(row.id, { locale: me.locale });
      }
      setConfirm(null);
      await reload(page);
    } catch {
      setMsg(t("platformLedgerTemplates.error.action"));
    } finally {
      setBusyId(null);
    }
  }

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformLedgerTemplates.title")}</h1>
        <p className="text-sm text-muted">{t("platformLedgerTemplates.error.notOperator")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"\u2190 "}
          {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformLedgerTemplates.title")}</h1>
        <p className="text-sm text-muted">{t("platformLedgerTemplates.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"\u2190 "}
          {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-4xl">
        <p className="text-sm text-muted">{t("platformLedgerTemplates.state.loading")}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      {confirm ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="max-w-md rounded-lg border border-border bg-surface p-5 shadow-lg">
            <p className="text-sm text-foreground">
              {confirm.kind === "deactivate"
                ? t("platformLedgerTemplates.confirm.deactivate")
                    .replace("{code}", confirm.row.code)
                    .replace("{country}", confirm.row.countryCode)
                : t("platformLedgerTemplates.confirm.activate").replace("{code}", confirm.row.code)}
            </p>
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                className="rounded border border-border px-3 py-1.5 text-sm"
                onClick={() => setConfirm(null)}
              >
                {t("platformLedgerTemplates.action.cancel")}
              </button>
              <button
                type="button"
                className="rounded bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground"
                disabled={busyId !== null}
                onClick={() => void runConfirmed()}
              >
                {confirm.kind === "deactivate"
                  ? t("platformLedgerTemplates.action.deactivate")
                  : t("platformLedgerTemplates.action.activate")}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformLedgerTemplates.title")}</h1>
        <div className="flex flex-wrap items-center gap-3">
          <Link
            href="/app/platform-ledger-templates/new"
            className="rounded bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground"
          >
            + {t("platformLedgerTemplates.action.new")}
          </Link>
          <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
            {"\u2190 "}
            {t("nav.dashboard")}
          </Link>
        </div>
      </div>

      <p className="text-sm text-muted">{t("platformLedgerTemplates.helper.intro")}</p>

      <section className="rounded-lg border border-border bg-surface/80 p-4 shadow-sm sm:p-5">
        <div className="flex flex-col gap-5 sm:flex-row sm:flex-wrap sm:items-end sm:justify-between sm:gap-x-10 sm:gap-y-4">
          <div className="flex min-w-0 flex-1 flex-col gap-2 sm:max-w-md">
            <label className="text-xs font-medium uppercase tracking-wide text-muted" htmlFor="platform-ledger-templates-country">
              {t("platformLedgerTemplates.label.country")}
            </label>
            <select
              id="platform-ledger-templates-country"
              className="h-10 w-full min-w-0 rounded border border-border bg-background px-3 text-sm leading-none sm:min-w-[16rem]"
              value={countryFilter}
              onChange={(e) => {
                setPage(0);
                setCountryFilter(e.target.value);
              }}
            >
              <option value="">{t("platformLedgerTemplates.filter.country.all")}</option>
              {countries.map((c) => (
                <option key={c.id} value={c.isoAlpha2}>
                  {c.isoAlpha2} — {c.name}
                </option>
              ))}
            </select>
          </div>
          <div className="flex shrink-0 flex-wrap items-center gap-2 border-t border-border pt-4 sm:border-l sm:border-t-0 sm:pl-8 sm:pt-0">
            <button
              type="button"
              className={`h-10 shrink-0 rounded px-4 text-sm font-medium leading-none ${activeFilter === "all" ? "bg-primary text-primary-foreground" : "border border-border bg-background"}`}
              onClick={() => {
                setPage(0);
                setActiveFilter("all");
              }}
            >
              {t("platformLedgerTemplates.filter.active.all")}
            </button>
            <button
              type="button"
              className={`h-10 shrink-0 rounded px-4 text-sm font-medium leading-none ${activeFilter === "active" ? "bg-primary text-primary-foreground" : "border border-border bg-background"}`}
              onClick={() => {
                setPage(0);
                setActiveFilter("active");
              }}
            >
              {t("platformLedgerTemplates.filter.active.active")}
            </button>
          </div>
        </div>
      </section>

      {msg ? <p className="text-sm font-medium text-destructive">{msg}</p> : null}

      {load === "error" ? (
        <p className="text-sm text-muted">{t("platformLedgerTemplates.error.load")}</p>
      ) : (
        <div className="overflow-x-auto rounded-md border border-border">
          <table className="w-full text-sm">
            <thead className="bg-surface text-left text-xs font-medium uppercase text-muted">
              <tr>
                <th className="px-3 py-2">{t("platformLedgerTemplates.col.country")}</th>
                <th className="px-3 py-2">{t("platformLedgerTemplates.col.code")}</th>
                <th className="px-3 py-2">{t("platformLedgerTemplates.col.description")}</th>
                <th className="px-3 py-2">{t("platformLedgerTemplates.col.status")}</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {items.map((row) => (
                <tr key={row.id} className="border-t border-border">
                  <td className="px-3 py-2 font-mono">{row.countryCode}</td>
                  <td className="px-3 py-2 font-mono text-xs">{row.code}</td>
                  <td className="px-3 py-2 max-w-md truncate" title={row.description}>
                    {row.description}
                  </td>
                  <td className="px-3 py-2">
                    {row.active ? t("platformLedgerTemplates.status.active") : t("platformLedgerTemplates.status.inactive")}
                  </td>
                  <td className="px-3 py-2 text-right">
                    <Link
                      href={`/app/platform-ledger-templates/${row.id}/edit`}
                      className="mr-2 text-primary underline-offset-4 hover:underline"
                    >
                      {t("platformLedgerTemplates.action.edit")}
                    </Link>
                    {row.active ? (
                      <button
                        type="button"
                        className="text-primary underline-offset-4 hover:underline disabled:opacity-50"
                        disabled={busyId === row.id}
                        onClick={() => setConfirm({ kind: "deactivate", row })}
                      >
                        {t("platformLedgerTemplates.action.deactivate")}
                      </button>
                    ) : (
                      <button
                        type="button"
                        className="text-primary underline-offset-4 hover:underline disabled:opacity-50"
                        disabled={busyId === row.id}
                        onClick={() => setConfirm({ kind: "activate", row })}
                      >
                        {t("platformLedgerTemplates.action.activate")}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="flex items-center justify-between text-sm">
        <button
          type="button"
          className="rounded border border-border px-3 py-1 disabled:opacity-40"
          disabled={page <= 0}
          onClick={() => setPage((p) => Math.max(0, p - 1))}
        >
          {t("platformLedgerTemplates.action.prev")}
        </button>
        <span className="text-muted">
          {t("platformLedgerTemplates.pageIndicator").replace("{n}", String(page + 1)).replace("{t}", String(totalPages))}
        </span>
        <button
          type="button"
          className="rounded border border-border px-3 py-1 disabled:opacity-40"
          disabled={page + 1 >= totalPages}
          onClick={() => setPage((p) => p + 1)}
        >
          {t("platformLedgerTemplates.action.next")}
        </button>
      </div>
    </div>
  );
}
