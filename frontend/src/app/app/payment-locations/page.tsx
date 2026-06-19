"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { NoCompanyEmptyState } from "@/components/onboarding/NoCompanyEmptyState";
import {
  fetchTenantCompanies,
  fetchTenantPaymentLocations,
  patchActivateTenantPaymentLocation,
  patchDeactivateTenantPaymentLocation,
  type TenantCompanyItem,
  type TenantPaymentLocationRow,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";
type TypeFilter = "all" | "CASH" | "BANK_ACCOUNT";

export default function TenantPaymentLocationsPage() {
  const { me, hasCompany } = useTenantAppSession();
  const router = useRouter();
  const searchParams = useSearchParams();
  const paramCompanyId = searchParams.get("companyId");
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const canView = me.privileges.includes("PAYMENT_LOCATION_VIEW");
  const canManage = me.privileges.includes("PAYMENT_LOCATION_MANAGE");

  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [companyId, setCompanyId] = useState<string>(paramCompanyId ?? "");
  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<TenantPaymentLocationRow[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [activeFilter, setActiveFilter] = useState<"all" | "active">("all");
  const [typeFilter, setTypeFilter] = useState<TypeFilter>("all");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<{ kind: "activate" | "deactivate"; row: TenantPaymentLocationRow } | null>(null);

  const activeParam = useMemo(() => (activeFilter === "active" ? true : undefined), [activeFilter]);

  useEffect(() => {
    if (!canView) {
      setLoad("forbidden");
      return;
    }
    void (async () => {
      const c = await fetchTenantCompanies({ page: 0, size: 100, active: true });
      if (!c.ok) {
        setLoad("error");
        return;
      }
      setCompanies(c.items);
      setCompanyId((prev) => {
        if (prev) return prev;
        if (paramCompanyId) return paramCompanyId;
        return c.items[0]?.id ?? "";
      });
    })();
  }, [canView, paramCompanyId]);

  const reload = useCallback(
    async (p: number, cid: string) => {
      if (!cid) {
        setItems([]);
        setLoad("ready");
        return;
      }
      setMsg(null);
      const r = await fetchTenantPaymentLocations({ companyId: cid, page: p, size: 20, active: activeParam });
      if (!r.ok) {
        setLoad(r.status === 403 ? "forbidden" : "error");
        return;
      }
      setItems(r.items);
      setTotalPages(r.totalPages);
      setLoad("ready");
    },
    [activeParam],
  );

  useEffect(() => {
    if (!canView) return;
    void reload(page, companyId);
  }, [activeParam, canView, companyId, page, reload]);

  function syncCompanyToUrl(cid: string) {
    setPage(0);
    setCompanyId(cid);
    const q = new URLSearchParams(searchParams.toString());
    if (cid) q.set("companyId", cid);
    else q.delete("companyId");
    router.replace(`/app/payment-locations${q.toString() ? `?${q}` : ""}`);
  }

  async function runConfirmed() {
    if (!confirm || !canManage) return;
    const row = confirm.row;
    setBusyId(row.id);
    setMsg(null);
    try {
      if (confirm.kind === "deactivate") await patchDeactivateTenantPaymentLocation(row.id);
      else await patchActivateTenantPaymentLocation(row.id);
      setConfirm(null);
      await reload(page, companyId);
    } catch {
      setMsg(t("paymentLocations.error.action"));
    } finally {
      setBusyId(null);
    }
  }

  const displayedItems = useMemo(
    () => (typeFilter === "all" ? items : items.filter((i) => i.paymentType === typeFilter)),
    [items, typeFilter],
  );

  const typeLabel = (pt: string) =>
    pt === "CASH" ? t("paymentLocations.type.cash") : t("paymentLocations.type.bankAccount");

  if (hasCompany === false) {
    const returnTo =
      typeof window !== "undefined"
        ? `${window.location.pathname}${window.location.search}${window.location.hash}`
        : "/app/payment-locations";
    return (
      <div className="mx-auto max-w-5xl">
        <NoCompanyEmptyState
          title="Payment locations need a company"
          body="Create a company first, then add payment locations to define how payroll disbursement happens."
          returnTo={returnTo}
          showViewCompanies={me.privileges.includes("COMPANY_VIEW") || me.privileges.includes("COMPANY_MANAGE")}
        />
      </div>
    );
  }

  if (!canView || load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("paymentLocations.title")}</h1>
        <p className="text-sm text-muted">{t("paymentLocations.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"← "}
          {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "loading" && !companyId) {
    return (
      <div className="mx-auto max-w-4xl">
        <p className="text-sm text-muted">{t("paymentLocations.state.loading")}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      {confirm && canManage ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="max-w-md rounded-lg border border-border bg-surface p-5 shadow-lg">
            <p className="text-sm text-foreground">
              {confirm.kind === "deactivate"
                ? t("paymentLocations.confirm.deactivate").replace("{name}", confirm.row.name)
                : t("paymentLocations.confirm.activate").replace("{name}", confirm.row.name)}
            </p>
            <div className="mt-4 flex justify-end gap-2">
              <button type="button" className="rounded border border-border px-3 py-1.5 text-sm" onClick={() => setConfirm(null)}>
                {t("paymentLocations.action.cancel")}
              </button>
              <button
                type="button"
                className="rounded bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground"
                disabled={busyId !== null}
                onClick={() => void runConfirmed()}
              >
                {confirm.kind === "deactivate"
                  ? t("paymentLocations.action.deactivate")
                  : t("paymentLocations.action.activate")}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("paymentLocations.title")}</h1>
        {canManage && companyId ? (
          <Link
            href={`/app/payment-locations/new?companyId=${encodeURIComponent(companyId)}`}
            className="rounded bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground"
          >
            + {t("paymentLocations.action.new")}
          </Link>
        ) : null}
      </div>

      {msg ? <p className="text-sm text-destructive">{msg}</p> : null}

      <div className="flex flex-wrap items-end gap-4">
        <div>
          <label className="block text-xs font-medium text-muted mb-1">{t("paymentLocations.label.company")}</label>
          <select
            className="rounded border border-border bg-surface px-3 py-1.5 text-sm"
            value={companyId}
            onChange={(e) => syncCompanyToUrl(e.target.value)}
          >
            {companies.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-xs font-medium text-muted mb-1">Status</label>
          <select
            className="rounded border border-border bg-surface px-3 py-1.5 text-sm"
            value={activeFilter}
            onChange={(e) => {
              setActiveFilter(e.target.value as "all" | "active");
              setPage(0);
            }}
          >
            <option value="all">{t("paymentLocations.filter.active.all")}</option>
            <option value="active">{t("paymentLocations.filter.active.active")}</option>
          </select>
        </div>

        <div>
          <label className="block text-xs font-medium text-muted mb-1">{t("paymentLocations.label.paymentType")}</label>
          <select
            className="rounded border border-border bg-surface px-3 py-1.5 text-sm"
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value as TypeFilter)}
          >
            <option value="all">{t("paymentLocations.filter.type.all")}</option>
            <option value="CASH">{t("paymentLocations.filter.type.cash")}</option>
            <option value="BANK_ACCOUNT">{t("paymentLocations.filter.type.bankAccount")}</option>
          </select>
        </div>
      </div>

      {load === "loading" ? (
        <p className="text-sm text-muted">{t("paymentLocations.state.loading")}</p>
      ) : load === "error" ? (
        <p className="text-sm text-destructive">{t("paymentLocations.error.load")}</p>
      ) : displayedItems.length === 0 ? (
        <p className="text-sm text-muted">{t("paymentLocations.state.empty")}</p>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border">
          <table className="min-w-full text-sm">
            <thead className="bg-surface-raised text-left text-xs font-semibold uppercase tracking-wide text-muted">
              <tr>
                <th className="px-4 py-3">{t("paymentLocations.col.name")}</th>
                <th className="px-4 py-3">{t("paymentLocations.col.paymentType")}</th>
                <th className="px-4 py-3">{t("paymentLocations.col.currency")}</th>
                <th className="px-4 py-3">{t("paymentLocations.col.bankTemplate")}</th>
                <th className="px-4 py-3">{t("paymentLocations.col.accountNumber")}</th>
                <th className="px-4 py-3">{t("paymentLocations.col.status")}</th>
                {canManage ? <th className="px-4 py-3"></th> : null}
              </tr>
            </thead>
            <tbody className="divide-y divide-border bg-surface">
              {displayedItems.map((row) => (
                <tr key={row.id} className="hover:bg-surface-raised/50">
                  <td className="px-4 py-3 font-medium text-foreground">
                    {canManage ? (
                      <Link
                        href={`/app/payment-locations/${encodeURIComponent(row.id)}/edit?companyId=${encodeURIComponent(companyId)}`}
                        className="text-primary underline-offset-2 hover:underline"
                      >
                        {row.name}
                      </Link>
                    ) : (
                      row.name
                    )}
                  </td>
                  <td className="px-4 py-3 text-muted">{typeLabel(row.paymentType)}</td>
                  <td className="px-4 py-3 text-muted font-mono">{row.currency}</td>
                  <td className="px-4 py-3 text-muted">{row.bankTemplateName ?? "—"}</td>
                  <td className="px-4 py-3 font-mono text-muted">{row.accountNumberMasked ?? "—"}</td>
                  <td className="px-4 py-3">
                    <span
                      className={
                        row.active
                          ? "rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-800"
                          : "rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600"
                      }
                    >
                      {row.active ? t("paymentLocations.status.active") : t("paymentLocations.status.inactive")}
                    </span>
                  </td>
                  {canManage ? (
                    <td className="px-4 py-3 text-right">
                      <button
                        type="button"
                        disabled={busyId === row.id}
                        onClick={() => setConfirm({ kind: row.active ? "deactivate" : "activate", row })}
                        className="text-xs font-medium text-primary underline-offset-2 hover:underline disabled:opacity-50"
                      >
                        {row.active ? t("paymentLocations.action.deactivate") : t("paymentLocations.action.activate")}
                      </button>
                    </td>
                  ) : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 ? (
        <div className="flex items-center gap-3">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
            className="rounded border border-border px-3 py-1.5 text-sm disabled:opacity-40"
          >
            {t("paymentLocations.action.prev")}
          </button>
          <span className="text-sm text-muted">
            {t("paymentLocations.pageIndicator").replace("{n}", String(page + 1)).replace("{t}", String(totalPages))}
          </span>
          <button
            type="button"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
            className="rounded border border-border px-3 py-1.5 text-sm disabled:opacity-40"
          >
            {t("paymentLocations.action.next")}
          </button>
        </div>
      ) : null}
    </div>
  );
}
