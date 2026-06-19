"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchPlatformCountryTaxRules,
  patchActivatePlatformCountryTaxRule,
  patchDeactivatePlatformCountryTaxRule,
  type PlatformCountryTaxRuleRow,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function PlatformCountryTaxRulesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<PlatformCountryTaxRuleRow[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [search, setSearch] = useState("");
  const [country, setCountry] = useState("");
  const [activeFilter, setActiveFilter] = useState<"all" | "active">("all");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  const active = useMemo(() => (activeFilter === "active" ? true : null), [activeFilter]);

  const reload = useCallback(
    async (p: number, s: string, cc: string) => {
      setMsg(null);
      const r = await fetchPlatformCountryTaxRules({
        page: p,
        size: 50,
        search: s,
        country: cc.trim().length === 2 ? cc.trim() : undefined,
        active,
      });
      if (!r.ok) {
        setLoad(r.status === 403 ? "forbidden" : "error");
        return;
      }
      setItems(r.items);
      setTotalPages(r.totalPages);
      setLoad("ready");
    },
    [active],
  );

  useEffect(() => {
    void reload(page, search, country);
  }, [reload, page, search, country]);

  async function toggleActive(row: PlatformCountryTaxRuleRow) {
    const deactivate = row.active;
    const confirmed = window.confirm(
      deactivate
        ? t("platformCountryTaxRules.confirm.deactivate").replace("{name}", row.name)
        : t("platformCountryTaxRules.confirm.activate").replace("{name}", row.name),
    );
    if (!confirmed) return;
    setBusyId(row.id);
    setMsg(null);
    try {
      if (deactivate) {
        await patchDeactivatePlatformCountryTaxRule(row.id);
      } else {
        await patchActivatePlatformCountryTaxRule(row.id);
      }
      await reload(page, search, country);
    } catch {
      setMsg(t("platformCountryTaxRules.error.action"));
    } finally {
      setBusyId(null);
    }
  }

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCountryTaxRules.title")}</h1>
        <p className="text-sm text-muted">{t("platformCountryTaxRules.error.notOperator")}</p>
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
        <h1 className="text-lg font-semibold text-foreground">{t("platformCountryTaxRules.title")}</h1>
        <p className="text-sm text-muted">{t("platformCountryTaxRules.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"\u2190 "}
          {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-2xl">
        <p className="text-sm text-muted">{t("platformCountryTaxRules.state.loading")}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6" data-testid="platform-country-tax-rules-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCountryTaxRules.title")}</h1>
        <div className="flex items-center gap-3">
          <Link
            href="/app/platform-country-tax-rules/new"
            className="rounded bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground"
            data-testid="platform-country-tax-rules-new"
          >
            + {t("platformCountryTaxRules.action.new")}
          </Link>
          <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
            {"\u2190 "}
            {t("nav.dashboard")}
          </Link>
        </div>
      </div>

      <p className="text-sm text-muted">{t("platformCountryTaxRules.helper.intro")}</p>

      <div className="flex flex-wrap items-center gap-3">
        <input
          value={search}
          onChange={(e) => {
            setPage(0);
            setSearch(e.target.value);
          }}
          placeholder={t("platformCountryTaxRules.search.placeholder")}
          className="w-full max-w-sm rounded-md border border-border bg-background px-3 py-2 text-sm"
          data-testid="platform-country-tax-rules-search"
        />
        <input
          value={country}
          onChange={(e) => {
            setPage(0);
            setCountry(e.target.value.toUpperCase().slice(0, 2));
          }}
          placeholder={t("platformCountryTaxRules.label.country")}
          maxLength={2}
          className="w-24 rounded-md border border-border bg-background px-3 py-2 text-sm font-mono uppercase"
          data-testid="platform-country-tax-rules-country"
        />
        <div className="flex items-center gap-2">
          <button
            type="button"
            className={`rounded border px-3 py-1.5 text-sm ${activeFilter === "all" ? "bg-surface border-border" : "border-border"}`}
            onClick={() => {
              setPage(0);
              setActiveFilter("all");
            }}
            data-testid="platform-country-tax-rules-filter-all"
          >
            {t("platformCountryTaxRules.filter.all")}
          </button>
          <button
            type="button"
            className={`rounded border px-3 py-1.5 text-sm ${activeFilter === "active" ? "bg-surface border-border" : "border-border"}`}
            onClick={() => {
              setPage(0);
              setActiveFilter("active");
            }}
            data-testid="platform-country-tax-rules-filter-active"
          >
            {t("platformCountryTaxRules.filter.active")}
          </button>
        </div>
      </div>

      {load === "error" ? <p className="text-sm text-muted">{t("platformCountryTaxRules.error.load")}</p> : null}
      {msg ? <p className="text-sm text-destructive">{msg}</p> : null}

      <div className="overflow-x-auto rounded-md border border-border">
        <table className="w-full text-sm">
          <thead className="bg-surface text-left text-xs font-medium uppercase text-muted">
            <tr>
              <th className="px-3 py-2">{t("platformCountryTaxRules.col.country")}</th>
              <th className="px-3 py-2">{t("platformCountryTaxRules.col.ruleCode")}</th>
              <th className="px-3 py-2">{t("platformCountryTaxRules.col.name")}</th>
              <th className="px-3 py-2">{t("platformCountryTaxRules.col.effectiveFrom")}</th>
              <th className="px-3 py-2">{t("platformCountryTaxRules.col.effectiveTo")}</th>
              <th className="px-3 py-2">{t("platformCountryTaxRules.col.status")}</th>
              <th className="px-3 py-2" />
            </tr>
          </thead>
          <tbody>
            {items.map((row) => (
              <tr key={row.id} className="border-t border-border">
                <td className="px-3 py-2 font-mono font-semibold text-foreground">{row.countryCode}</td>
                <td className="px-3 py-2 font-mono text-foreground">{row.ruleCode}</td>
                <td className="px-3 py-2 text-foreground">{row.name}</td>
                <td className="px-3 py-2 font-mono text-foreground">{row.effectiveFrom}</td>
                <td className="px-3 py-2 font-mono text-foreground">{row.effectiveTo ?? "\u2014"}</td>
                <td className="px-3 py-2 text-foreground">
                  {row.active ? t("platformCountryTaxRules.status.active") : t("platformCountryTaxRules.status.inactive")}
                </td>
                <td className="px-3 py-2">
                  <div className="flex items-center justify-end gap-3">
                    <Link
                      href={`/app/platform-country-tax-rules/${row.id}/edit`}
                      className="text-sm font-medium text-primary underline-offset-4 hover:underline"
                    >
                      {t("platformCountryTaxRules.action.edit")}
                    </Link>
                    <button
                      type="button"
                      disabled={busyId === row.id}
                      onClick={() => void toggleActive(row)}
                      className="text-sm font-medium text-primary underline-offset-4 hover:underline disabled:opacity-40"
                    >
                      {row.active ? t("platformCountryTaxRules.action.deactivate") : t("platformCountryTaxRules.action.activate")}
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
            {"\u2190 "}
            {t("platformCountryTaxRules.action.prev")}
          </button>
          <span className="text-muted">
            {t("platformCountryTaxRules.pageIndicator").replace("{n}", String(page + 1)).replace("{t}", String(totalPages))}
          </span>
          <button
            type="button"
            className="rounded border border-border px-3 py-1 text-sm disabled:opacity-40"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            {t("platformCountryTaxRules.action.next")} {"\u2192"}
          </button>
        </div>
      ) : null}
    </div>
  );
}
