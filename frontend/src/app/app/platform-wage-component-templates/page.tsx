"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { FilterChip } from "@/components/ui/FilterChip";
import { showToast } from "@/components/ui/Toast";
import { WageComponentTemplateBaseEffectsSummary } from "@/components/payroll/WageComponentTemplateBaseEffects";
import {
  deletePlatformWageComponentTemplate,
  fetchPlatformCountries,
  fetchPlatformWageComponentTemplates,
  type PlatformCountryRow,
  type PlatformWageComponentTemplateRow,
} from "@/lib/api";
import { nextSearchParams, toQueryString } from "@/lib/filter-url";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

/** URL: unset = all templates; `"active"` = active only (maps to `active=true` query). */
type TemplateActiveFilter = "active" | null;

const LIST_PATH = "/app/platform-wage-component-templates";
const PAGE_SIZE = 20;

export default function PlatformWageComponentTemplatesPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const sp = useSearchParams();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const page = Math.max(0, Number.parseInt(sp.get("page") ?? "0", 10) || 0);
  const countryQ = (sp.get("country") ?? "").trim();
  const activeOnly = sp.get("active") === "true";
  const activeFilterValue: TemplateActiveFilter = activeOnly ? "active" : null;

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<PlatformWageComponentTemplateRow[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [countries, setCountries] = useState<PlatformCountryRow[]>([]);

  const pushWithPatch = useCallback(
    (patch: Record<string, string | undefined>) => {
      const q = nextSearchParams(sp, patch);
      router.push(`${LIST_PATH}${toQueryString(q)}`);
    },
    [router, sp],
  );

  const countryDisplay = useCallback(
    (code: string) => {
      const c = countries.find((x) => x.isoAlpha2 === code);
      return c ? `${c.isoAlpha2} — ${c.name}` : code;
    },
    [countries],
  );

  const reload = useCallback(async () => {
    const r = await fetchPlatformWageComponentTemplates({
      page,
      size: PAGE_SIZE,
      country: countryQ || null,
      active: activeOnly ? true : null,
    });
    if (!r.ok) {
      setLoad(r.status === 403 ? "forbidden" : "error");
      return;
    }
    setItems(r.items);
    setTotalPages(r.totalPages);
    setTotalElements(r.totalElements);
    setLoad("ready");
  }, [page, countryQ, activeOnly]);

  const hasAnyFilter = countryQ.length > 0 || activeOnly;

  const pageWindow = useMemo(() => {
    const start = page * PAGE_SIZE;
    const end = start + items.length;
    return items.length ? `${start + 1}-${end}` : "0";
  }, [items.length, page]);

  function clearAllFilters() {
    pushWithPatch({ country: undefined, active: undefined, page: "0" });
  }

  function applyCountry(next: string | null) {
    pushWithPatch({
      country: next?.trim() || undefined,
      page: "0",
    });
  }

  function applyActiveFilter(next: TemplateActiveFilter) {
    pushWithPatch({
      active: next === "active" ? "true" : undefined,
      page: "0",
    });
  }

  async function onDelete(row: PlatformWageComponentTemplateRow) {
    if (!window.confirm(t("platformWageComponentTemplates.msg.deleteConfirm"))) return;
    try {
      await deletePlatformWageComponentTemplate(row.id);
      showToast(t("platformWageComponentTemplates.msg.deleted"));
      void reload();
    } catch {
      showToast(t("platformWageComponentTemplates.msg.deleteFailed"));
    }
  }

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
    void reload();
  }, [me.platformSuperadmin, reload]);

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformWageComponentTemplates.title")}</h1>
        <p className="text-sm text-muted">{t("platformWageComponentTemplates.error.notOperator")}</p>
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
        <p className="text-sm text-muted">{t("platformWageComponentTemplates.state.loading")}</p>
      </div>
    );
  }

  if (load === "error" || load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <p className="text-sm text-muted">
          {load === "forbidden" ? t("platformWageComponentTemplates.error.forbidden") : t("platformWageComponentTemplates.error.load")}
        </p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"\u2190 "}
          {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformWageComponentTemplates.title")}</h1>
        <div className="flex flex-wrap items-center gap-3">
          <Link
            href="/app/platform-wage-component-templates/new"
            className="rounded bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground hover:opacity-90"
          >
            {t("platformWageComponentTemplates.action.new")}
          </Link>
          <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
            {"\u2190 "}
            {t("nav.dashboard")}
          </Link>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <FilterChip<string | null>
          label={t("platformWageComponentTemplates.label.country")}
          value={countryQ || null}
          formatValue={(v) => (v ? countryDisplay(v) : t("platformLedgerTemplates.filter.country.all"))}
          onApply={(v) => applyCountry(v)}
          renderInput={(draft, setDraft) => (
            <label className="flex flex-col gap-1 text-xs font-medium text-foreground">
              {t("platformWageComponentTemplates.label.country")}
              <select
                className="w-full rounded border border-border bg-background px-2 py-1.5 text-sm text-foreground focus:border-primary focus:outline-none"
                value={draft ?? ""}
                onChange={(e) => setDraft(e.target.value || null)}
              >
                <option value="">{t("platformLedgerTemplates.filter.country.all")}</option>
                {countries.map((c) => (
                  <option key={c.id} value={c.isoAlpha2}>
                    {c.isoAlpha2} — {c.name}
                  </option>
                ))}
              </select>
            </label>
          )}
        />

        <FilterChip<TemplateActiveFilter>
          label={t("platformLedgerTemplates.col.status")}
          value={activeFilterValue}
          formatValue={() => t("platformLedgerTemplates.filter.active.active")}
          onApply={(v) => applyActiveFilter(v)}
          renderInput={(draft, setDraft) => (
            <div className="flex flex-col gap-2">
              <p className="text-xs font-medium text-foreground">{t("platformLedgerTemplates.col.status")}</p>
              <ul className="rounded border border-border bg-background">
                <li>
                  <label className="flex cursor-pointer items-center gap-2 px-3 py-2 text-sm text-foreground hover:bg-surface-alt">
                    <input
                      type="radio"
                      name="pwct-active-filter"
                      className="h-4 w-4"
                      checked={draft === null}
                      onChange={() => setDraft(null)}
                    />
                    <span>{t("platformLedgerTemplates.filter.active.all")}</span>
                  </label>
                </li>
                <li>
                  <label className="flex cursor-pointer items-center gap-2 px-3 py-2 text-sm text-foreground hover:bg-surface-alt">
                    <input
                      type="radio"
                      name="pwct-active-filter"
                      className="h-4 w-4"
                      checked={draft === "active"}
                      onChange={() => setDraft("active")}
                    />
                    <span>{t("platformLedgerTemplates.filter.active.active")}</span>
                  </label>
                </li>
              </ul>
            </div>
          )}
        />

        {hasAnyFilter ? (
          <button
            type="button"
            onClick={clearAllFilters}
            className="text-xs font-medium text-muted underline-offset-4 hover:text-foreground hover:underline"
          >
            {t("payPeriods.filter.clear")}
          </button>
        ) : null}

        <div className="ml-auto flex items-center gap-2 text-xs text-muted">
          <span>
            {pageWindow} / {totalElements}
          </span>
          <Link
            aria-label="First page"
            className={`rounded border border-border px-2 py-1 hover:bg-surface-alt ${
              totalElements === 0 || page <= 0 ? "pointer-events-none opacity-40" : "text-foreground"
            }`}
            href={`${LIST_PATH}${toQueryString(nextSearchParams(sp, { page: "0" }))}`}
          >
            «
          </Link>
          <Link
            aria-label="Previous page"
            className={`rounded border border-border px-2 py-1 hover:bg-surface-alt ${
              totalElements === 0 || page <= 0 ? "pointer-events-none opacity-40" : "text-foreground"
            }`}
            href={`${LIST_PATH}${toQueryString(nextSearchParams(sp, { page: String(Math.max(0, page - 1)) }))}`}
          >
            ‹
          </Link>
          <Link
            aria-label="Next page"
            className={`rounded border border-border px-2 py-1 hover:bg-surface-alt ${
              totalElements === 0 || page + 1 >= totalPages ? "pointer-events-none opacity-40" : "text-foreground"
            }`}
            href={`${LIST_PATH}${toQueryString(nextSearchParams(sp, { page: String(page + 1) }))}`}
          >
            ›
          </Link>
          <Link
            aria-label="Last page"
            className={`rounded border border-border px-2 py-1 hover:bg-surface-alt ${
              totalElements === 0 || page + 1 >= totalPages ? "pointer-events-none opacity-40" : "text-foreground"
            }`}
            href={`${LIST_PATH}${toQueryString(nextSearchParams(sp, { page: String(Math.max(0, totalPages - 1)) }))}`}
          >
            »
          </Link>
        </div>
      </div>

      <div className="overflow-x-auto rounded-md border border-border">
        <table className="w-full text-sm">
          <thead className="bg-surface text-left text-xs font-medium uppercase text-muted">
            <tr>
              <th className="px-3 py-2">{t("platformLedgerTemplates.col.country")}</th>
              <th className="px-3 py-2">Code</th>
              <th className="px-3 py-2">Name</th>
              <th className="px-3 py-2">Duplicable</th>
              <th className="px-3 py-2">{t("platformWageComponentTemplates.col.baseEffects")}</th>
              <th className="px-3 py-2">{t("platformLedgerTemplates.col.status")}</th>
              <th className="px-3 py-2 text-right">{t("platformWageComponentTemplates.col.actions")}</th>
            </tr>
          </thead>
          <tbody>
            {items.map((row) => (
              <tr key={row.id} className="border-t border-border">
                <td className="px-3 py-2 font-mono">{row.countryCode}</td>
                <td className="px-3 py-2 font-mono text-xs">{row.templateCode}</td>
                <td className="px-3 py-2">{row.name}</td>
                <td className="px-3 py-2 text-muted">{row.duplicable ? "Yes" : "No"}</td>
                <td className="px-3 py-2">
                  <WageComponentTemplateBaseEffectsSummary effects={row.baseEffects} />
                </td>
                <td className="px-3 py-2">
                  {row.active ? t("platformLedgerTemplates.status.active") : t("platformLedgerTemplates.status.inactive")}
                </td>
                <td className="px-3 py-2 text-right text-sm">
                  <div className="flex flex-wrap justify-end gap-x-3 gap-y-1">
                    <Link
                      href={`/app/platform-wage-component-templates/${row.id}/edit`}
                      className="text-primary underline-offset-4 hover:underline"
                    >
                      {t("platformWageComponentTemplates.action.edit")}
                    </Link>
                    <Link
                      href={`/app/platform-wage-component-templates/${row.id}/ledger`}
                      className="text-primary underline-offset-4 hover:underline"
                    >
                      {t("platformWageComponentTemplates.action.editLedgerShort")}
                    </Link>
                    <button
                      type="button"
                      className="text-destructive underline-offset-4 hover:underline"
                      onClick={() => void onDelete(row)}
                    >
                      {t("platformWageComponentTemplates.action.delete")}
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {totalElements === 0 ? <p className="text-sm text-muted">{t("platformWageComponentTemplates.state.empty")}</p> : null}
    </div>
  );
}
