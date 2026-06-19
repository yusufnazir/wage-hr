"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchPlatformPayrollBases,
  patchActivatePlatformPayrollBase,
  patchDeactivatePlatformPayrollBase,
  type PlatformPayrollBaseRow,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

const CATEGORIES = ["TAX", "CONTRIBUTION", "ACCRUAL", "NET", "GROSS", "STATUTORY"] as const;

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function PlatformPayrollBasesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<PlatformPayrollBaseRow[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [activeFilter, setActiveFilter] = useState<"all" | "active">("all");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  const active = useMemo(() => (activeFilter === "active" ? true : null), [activeFilter]);

  const reload = useCallback(
    async (p: number, s: string, cat: string) => {
      setMsg(null);
      const r = await fetchPlatformPayrollBases({
        page: p,
        size: 50,
        search: s,
        category: cat || undefined,
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
    void reload(page, search, category);
  }, [reload, page, search, category]);

  async function toggleActive(row: PlatformPayrollBaseRow) {
    const deactivate = row.active;
    const confirmed = window.confirm(
      deactivate
        ? t("platformPayrollBases.confirm.deactivate").replace("{name}", row.name)
        : t("platformPayrollBases.confirm.activate").replace("{name}", row.name),
    );
    if (!confirmed) return;
    setBusyId(row.id);
    setMsg(null);
    try {
      if (deactivate) {
        await patchDeactivatePlatformPayrollBase(row.id);
      } else {
        await patchActivatePlatformPayrollBase(row.id);
      }
      await reload(page, search, category);
    } catch {
      setMsg(t("platformPayrollBases.error.action"));
    } finally {
      setBusyId(null);
    }
  }

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformPayrollBases.title")}</h1>
        <p className="text-sm text-muted">{t("platformPayrollBases.error.notOperator")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"\u2190 "}
          {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "forbidden") {
    return (
      <ForbiddenNotice t={t} messageKey="platformPayrollBases.error.forbidden" />
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-2xl">
        <p className="text-sm text-muted">{t("platformPayrollBases.state.loading")}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6" data-testid="platform-payroll-bases-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformPayrollBases.title")}</h1>
        <div className="flex items-center gap-3">
          <Link
            href="/app/platform-payroll-bases/new"
            className="rounded bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground"
            data-testid="platform-payroll-bases-new"
          >
            + {t("platformPayrollBases.action.new")}
          </Link>
          <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
            {"\u2190 "}
            {t("nav.dashboard")}
          </Link>
        </div>
      </div>

      <p className="text-sm text-muted">{t("platformPayrollBases.helper.intro")}</p>

      <div className="flex flex-wrap items-center gap-3">
        <input
          value={search}
          onChange={(e) => {
            setPage(0);
            setSearch(e.target.value);
          }}
          placeholder={t("platformPayrollBases.search.placeholder")}
          className="w-full max-w-sm rounded-md border border-border bg-background px-3 py-2 text-sm"
          data-testid="platform-payroll-bases-search"
        />
        <select
          value={category}
          onChange={(e) => {
            setPage(0);
            setCategory(e.target.value);
          }}
          className="rounded-md border border-border bg-background px-3 py-2 text-sm"
          data-testid="platform-payroll-bases-category"
          aria-label={t("platformPayrollBases.label.category")}
        >
          <option value="">{t("platformPayrollBases.filter.categoryAll")}</option>
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        <div className="flex items-center gap-2">
          <button
            type="button"
            className={`rounded border px-3 py-1.5 text-sm ${activeFilter === "all" ? "bg-surface border-border" : "border-border"}`}
            onClick={() => {
              setPage(0);
              setActiveFilter("all");
            }}
            data-testid="platform-payroll-bases-filter-all"
          >
            {t("platformPayrollBases.filter.all")}
          </button>
          <button
            type="button"
            className={`rounded border px-3 py-1.5 text-sm ${activeFilter === "active" ? "bg-surface border-border" : "border-border"}`}
            onClick={() => {
              setPage(0);
              setActiveFilter("active");
            }}
            data-testid="platform-payroll-bases-filter-active"
          >
            {t("platformPayrollBases.filter.active")}
          </button>
        </div>
      </div>

      {load === "error" ? <p className="text-sm text-muted">{t("platformPayrollBases.error.load")}</p> : null}
      {msg ? <p className="text-sm text-destructive">{msg}</p> : null}

      <div className="overflow-x-auto rounded-md border border-border">
        <table className="w-full text-sm">
          <thead className="bg-surface text-left text-xs font-medium uppercase text-muted">
            <tr>
              <th className="px-3 py-2">{t("platformPayrollBases.col.code")}</th>
              <th className="px-3 py-2">{t("platformPayrollBases.col.name")}</th>
              <th className="px-3 py-2">{t("platformPayrollBases.col.category")}</th>
              <th className="px-3 py-2">{t("platformPayrollBases.col.status")}</th>
              <th className="px-3 py-2" />
            </tr>
          </thead>
          <tbody>
            {items.map((row) => (
              <tr key={row.id} className="border-t border-border">
                <td className="px-3 py-2 font-mono font-semibold text-foreground">{row.code}</td>
                <td className="px-3 py-2 text-foreground">{row.name}</td>
                <td className="px-3 py-2 font-mono text-foreground">{row.category ?? "\u2014"}</td>
                <td className="px-3 py-2 text-foreground">
                  {row.active ? t("platformPayrollBases.status.active") : t("platformPayrollBases.status.inactive")}
                </td>
                <td className="px-3 py-2">
                  <BaseRowActions row={row} busyId={busyId} t={t} toggleActive={toggleActive} />
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
            {t("platformPayrollBases.action.prev")}
          </button>
          <span className="text-muted">
            {t("platformPayrollBases.pageIndicator").replace("{n}", String(page + 1)).replace("{t}", String(totalPages))}
          </span>
          <button
            type="button"
            className="rounded border border-border px-3 py-1 text-sm disabled:opacity-40"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            {t("platformPayrollBases.action.next")} {"\u2192"}
          </button>
        </div>
      ) : null}
    </div>
  );
}

function ForbiddenNotice({ t, messageKey }: { t: (key: string) => string; messageKey: string }) {
  return (
    <div className="mx-auto max-w-lg space-y-4">
      <h1 className="text-lg font-semibold text-foreground">{t("platformPayrollBases.title")}</h1>
      <p className="text-sm text-muted">{t(messageKey)}</p>
      <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
        {"\u2190 "}
        {t("nav.dashboard")}
      </Link>
    </div>
  );
}

function BaseRowActions({
  row,
  busyId,
  t,
  toggleActive,
}: {
  row: PlatformPayrollBaseRow;
  busyId: string | null;
  t: (key: string) => string;
  toggleActive: (row: PlatformPayrollBaseRow) => Promise<void>;
}) {
  return (
    <div className="flex items-center justify-end gap-3">
      <Link
        href={`/app/platform-payroll-bases/${row.id}/edit`}
        className="text-sm font-medium text-primary underline-offset-4 hover:underline"
      >
        {t("platformPayrollBases.action.edit")}
      </Link>
      <button
        type="button"
        disabled={busyId === row.id}
        onClick={() => void toggleActive(row)}
        className="text-sm font-medium text-primary underline-offset-4 hover:underline disabled:opacity-40"
      >
        {row.active ? t("platformPayrollBases.action.deactivate") : t("platformPayrollBases.action.activate")}
      </button>
    </div>
  );
}
