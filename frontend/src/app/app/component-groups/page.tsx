"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchTenantCompanies,
  fetchTenantComponentGroups,
  type TenantCompanyItem,
  type TenantComponentGroupRow,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function TenantComponentGroupsPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<TenantComponentGroupRow[]>([]);
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [selectedCompanyId, setSelectedCompanyId] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  const canManage = me.privileges.includes("WAGE_COMPONENT_MANAGE");

  const reload = useCallback(
    async (p = 0, companyId = selectedCompanyId) => {
      if (!companyId) {
        setItems([]);
        setTotalPages(1);
        setPage(0);
        setLoad("ready");
        return;
      }
      setLoad("loading");
      const listR = await fetchTenantComponentGroups({ page: p, size: 20, companyId, locale: me.locale });
      if (!listR.ok) {
        setLoad(listR.status === 403 ? "forbidden" : "error");
        return;
      }
      setItems(listR.items);
      setTotalPages(listR.totalPages);
      setPage(p);
      setLoad("ready");
    },
    [me.locale, selectedCompanyId],
  );

  useEffect(() => {
    void (async () => {
      const cr = await fetchTenantCompanies({ size: 100 });
      if (cr.ok) {
        setCompanies(cr.items);
        if (cr.items.length === 1) {
          setSelectedCompanyId((prev) => prev || cr.items[0].id);
        }
      }
    })();
  }, [me.userId]);

  useEffect(() => {
    void reload(0, selectedCompanyId);
  }, [reload, selectedCompanyId]);

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("nav.component_groups")}</h1>
        <p className="text-sm text-muted">You do not have access to this area.</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}
          {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "error") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("nav.component_groups")}</h1>
        <p className="text-sm text-muted">Could not load component groups.</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}
          {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-lg font-semibold text-foreground">{t("nav.component_groups")}</h1>
          <p className="text-sm text-muted">Company-specific grouping of wage components for payslips and admin.</p>
        </div>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}
          {t("nav.dashboard")}
        </Link>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <label className="text-sm text-muted" htmlFor="company">
          Company
        </label>
        <select
          id="company"
          className="rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
          value={selectedCompanyId}
          onChange={(e) => setSelectedCompanyId(e.target.value)}
        >
          <option value="">Select company…</option>
          {companies.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>

      {selectedCompanyId ? (
        <div className="space-y-3">
          {canManage ? (
            <Link
              href={`/app/component-groups/new?companyId=${encodeURIComponent(selectedCompanyId)}`}
              className="inline-flex rounded border border-border bg-muted/40 px-3 py-2 text-sm font-medium text-foreground hover:bg-muted/60"
            >
              New component group
            </Link>
          ) : null}
          {load === "loading" ? (
            <p className="text-sm text-muted">Loading…</p>
          ) : items.length === 0 ? (
            <p className="text-sm text-muted">No component groups yet.</p>
          ) : (
            <ul className="divide-y divide-border rounded border border-border">
              {items.map((row) => (
                <li key={row.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2">
                  <div>
                    <div className="text-sm font-medium text-foreground">{row.name}</div>
                    <div className="text-xs text-muted">
                      {row.countryCode} · sort {row.sortOrder} · {row.active ? "active" : "inactive"}
                    </div>
                  </div>
                  <Link
                    href={`/app/component-groups/${row.id}/edit?companyId=${encodeURIComponent(selectedCompanyId)}`}
                    className="text-sm font-medium text-primary underline-offset-4 hover:underline"
                  >
                    Edit
                  </Link>
                </li>
              ))}
            </ul>
          )}
          {totalPages > 1 ? (
            <div className="flex gap-2 text-sm">
              <button
                type="button"
                className="rounded border border-border px-3 py-1 disabled:opacity-40"
                disabled={page <= 0}
                onClick={() => void reload(page - 1)}
              >
                Previous
              </button>
              <button
                type="button"
                className="rounded border border-border px-3 py-1 disabled:opacity-40"
                disabled={page >= totalPages - 1}
                onClick={() => void reload(page + 1)}
              >
                Next
              </button>
            </div>
          ) : null}
        </div>
      ) : (
        <p className="text-sm text-muted">Choose a company to view its component groups.</p>
      )}
    </div>
  );
}
