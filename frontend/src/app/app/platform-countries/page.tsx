"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchPlatformCountries,
  patchActivatePlatformCountry,
  patchDeactivatePlatformCountry,
  type PlatformCountryRow,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function PlatformCountriesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<PlatformCountryRow[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [search, setSearch] = useState("");
  const [activeFilter, setActiveFilter] = useState<"all" | "active">("all");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  const active = useMemo(() => (activeFilter === "active" ? true : null), [activeFilter]);

  const reload = useCallback(async (p: number, s: string) => {
    setMsg(null);
    const r = await fetchPlatformCountries({
      page: p,
      size: 50,
      search: s,
      active,
      locale: me.locale,
    });
    if (!r.ok) {
      setLoad(r.status === 403 ? "forbidden" : "error");
      return;
    }
    setItems(r.items);
    setTotalPages(r.totalPages);
    setLoad("ready");
  }, [active, me.locale]);

  useEffect(() => {
    void reload(page, search);
  }, [reload, page, search]);

  async function toggleActive(row: PlatformCountryRow) {
    const deactivate = row.active;
    const confirmed = window.confirm(
      deactivate
        ? t("platformCountries.confirm.deactivate").replace("{name}", row.name)
        : t("platformCountries.confirm.activate").replace("{name}", row.name),
    );
    if (!confirmed) return;
    setBusyId(row.id);
    setMsg(null);
    try {
      if (deactivate) {
        await patchDeactivatePlatformCountry(row.id);
      } else {
        await patchActivatePlatformCountry(row.id);
      }
      await reload(page, search);
    } catch {
      setMsg(t("platformCountries.error.action"));
    } finally {
      setBusyId(null);
    }
  }

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCountries.title")}</h1>
        <p className="text-sm text-muted">{t("platformCountries.error.notOperator")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"\u2190 "}{t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCountries.title")}</h1>
        <p className="text-sm text-muted">{t("platformCountries.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"\u2190 "}{t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-2xl">
        <p className="text-sm text-muted">{t("platformCountries.state.loading")}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6" data-testid="platform-countries-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCountries.title")}</h1>
        <div className="flex items-center gap-3">
          <Link
            href="/app/platform-countries/new"
            className="rounded bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground"
            data-testid="platform-countries-new"
          >
            + {t("platformCountries.action.new")}
          </Link>
          <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
            {"\u2190 "}{t("nav.dashboard")}
          </Link>
        </div>
      </div>

      <p className="text-sm text-muted">{t("platformCountries.helper.intro")}</p>

      <div className="flex flex-wrap items-center gap-3">
        <input
          value={search}
          onChange={(e) => {
            setPage(0);
            setSearch(e.target.value);
          }}
          placeholder={t("platformCountries.search.placeholder")}
          className="w-full max-w-sm rounded-md border border-border bg-background px-3 py-2 text-sm"
          data-testid="platform-countries-search"
        />
        <div className="flex items-center gap-2">
          <button
            type="button"
            className={`rounded border px-3 py-1.5 text-sm ${activeFilter === "all" ? "bg-surface border-border" : "border-border"}`}
            onClick={() => {
              setPage(0);
              setActiveFilter("all");
            }}
            data-testid="platform-countries-filter-all"
          >
            {t("platformCountries.filter.all")}
          </button>
          <button
            type="button"
            className={`rounded border px-3 py-1.5 text-sm ${activeFilter === "active" ? "bg-surface border-border" : "border-border"}`}
            onClick={() => {
              setPage(0);
              setActiveFilter("active");
            }}
            data-testid="platform-countries-filter-active"
          >
            {t("platformCountries.filter.active")}
          </button>
        </div>
      </div>

      {load === "error" ? <p className="text-sm text-muted">{t("platformCountries.error.load")}</p> : null}
      {msg ? <p className="text-sm text-destructive" data-testid="platform-countries-msg">{msg}</p> : null}

      <div className="overflow-x-auto rounded-md border border-border">
        <table className="w-full text-sm">
          <thead className="bg-surface text-left text-xs font-medium uppercase text-muted">
            <tr>
              <th className="px-3 py-2">{t("platformCountries.col.name")}</th>
              <th className="px-3 py-2">{t("platformCountries.col.alpha2")}</th>
              <th className="px-3 py-2">{t("platformCountries.col.alpha3")}</th>
              <th className="px-3 py-2">{t("platformCountries.col.numeric")}</th>
              <th className="px-3 py-2">{t("platformCountries.col.dialCode")}</th>
              <th className="px-3 py-2">{t("platformCountries.col.payrollEnabled")}</th>
              <th className="px-3 py-2">{t("platformCountries.col.status")}</th>
              <th className="px-3 py-2" />
            </tr>
          </thead>
          <tbody>
            {items.map((row) => (
              <tr key={row.id} className="border-t border-border" data-testid={`platform-country-row-${row.isoAlpha2}`}>
                <td className="px-3 py-2 text-foreground">{row.name}</td>
                <td className="px-3 py-2 font-mono font-semibold text-foreground">{row.isoAlpha2}</td>
                <td className="px-3 py-2 font-mono text-foreground">{row.isoAlpha3}</td>
                <td className="px-3 py-2 font-mono text-foreground">{row.isoNumeric}</td>
                <td className="px-3 py-2 text-foreground">{row.dialCode ?? "\u2014"}</td>
                <td className="px-3 py-2 text-foreground">
                  {row.payrollEnabled ? t("platformCountries.status.payrollEnabled") : t("platformCountries.status.payrollDisabled")}
                </td>
                <td className="px-3 py-2 text-foreground">
                  {row.active ? t("platformCountries.status.active") : t("platformCountries.status.inactive")}
                </td>
                <td className="px-3 py-2">
                  <div className="flex items-center justify-end gap-3">
                    <Link
                      href={`/app/platform-countries/${row.id}/edit`}
                      className="text-sm font-medium text-primary underline-offset-4 hover:underline"
                      data-testid={`platform-country-edit-${row.isoAlpha2}`}
                    >
                      {t("platformCountries.action.edit")}
                    </Link>
                    <button
                      type="button"
                      disabled={busyId === row.id}
                      onClick={() => void toggleActive(row)}
                      className="text-sm font-medium text-primary underline-offset-4 hover:underline disabled:opacity-40"
                      data-testid={`platform-country-toggle-${row.isoAlpha2}`}
                    >
                      {row.active ? t("platformCountries.action.deactivate") : t("platformCountries.action.activate")}
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 ? (
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <button
            type="button"
            className="rounded border border-border px-3 py-1 text-sm disabled:opacity-40"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            {"\u2190 "}{t("platformCountries.action.prev")}
          </button>
          <span className="text-muted">
            {t("platformCountries.pageIndicator").replace("{n}", String(page + 1)).replace("{t}", String(totalPages))}
          </span>
          <button
            type="button"
            className="rounded border border-border px-3 py-1 text-sm disabled:opacity-40"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            {t("platformCountries.action.next")} {"\u2192"}
          </button>
        </div>
      ) : null}
    </div>
  );
}
