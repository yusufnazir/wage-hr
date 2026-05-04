"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  deleteTenantBankTemplate,
  fetchTenantBankTemplates,
  fetchTenantCompanies,
  patchActivateTenantBankTemplate,
  patchDeactivateTenantBankTemplate,
  type TenantBankTemplateRow,
  type TenantCompanyItem,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function TenantBankTemplatesPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const searchParams = useSearchParams();
  const paramCompanyId = searchParams.get("companyId");
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const canView = me.privileges.includes("BANK_TEMPLATE_VIEW");
  const canManage = me.privileges.includes("BANK_TEMPLATE_MANAGE");
  const canManageCatalog = me.platformSuperadmin;

  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [companyId, setCompanyId] = useState<string>(paramCompanyId ?? "");
  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<TenantBankTemplateRow[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [activeFilter, setActiveFilter] = useState<"all" | "active">("all");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<{ kind: "activate" | "deactivate" | "delete"; row: TenantBankTemplateRow } | null>(
    null,
  );

  const activeParam = useMemo(() => (activeFilter === "active" ? true : null), [activeFilter]);

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
      const r = await fetchTenantBankTemplates({ companyId: cid, page: p, size: 20, active: activeParam });
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
    router.replace(`/app/bank-templates${q.toString() ? `?${q}` : ""}`);
  }

  async function runConfirmed() {
    if (!confirm || !canManage) return;
    const row = confirm.row;
    setBusyId(row.id);
    setMsg(null);
    try {
      if (confirm.kind === "deactivate") await patchDeactivateTenantBankTemplate(row.id);
      else if (confirm.kind === "activate") await patchActivateTenantBankTemplate(row.id);
      else await deleteTenantBankTemplate(row.id);
      setConfirm(null);
      await reload(page, companyId);
    } catch {
      setMsg(confirm.kind === "delete" ? t("bankTemplates.msg.deleteFailed") : t("bankTemplates.error.action"));
    } finally {
      setBusyId(null);
    }
  }

  if (!canView) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("bankTemplates.title")}</h1>
        <p className="text-sm text-muted">{t("bankTemplates.error.forbidden")}</p>
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
        <h1 className="text-lg font-semibold text-foreground">{t("bankTemplates.title")}</h1>
        <p className="text-sm text-muted">{t("bankTemplates.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"\u2190 "}
          {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "loading" && !companyId) {
    return (
      <div className="mx-auto max-w-4xl">
        <p className="text-sm text-muted">{t("bankTemplates.state.loading")}</p>
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
                ? t("bankTemplates.confirm.deactivate").replace("{name}", confirm.row.platformTemplateName)
                : confirm.kind === "activate"
                  ? t("bankTemplates.confirm.activate").replace("{name}", confirm.row.platformTemplateName)
                  : t("bankTemplates.confirm.delete").replace("{name}", confirm.row.platformTemplateName)}
            </p>
            <div className="mt-4 flex justify-end gap-2">
              <button type="button" className="rounded border border-border px-3 py-1.5 text-sm" onClick={() => setConfirm(null)}>
                {t("bankTemplates.action.cancel")}
              </button>
              <button
                type="button"
                className="rounded bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground"
                disabled={busyId !== null}
                onClick={() => void runConfirmed()}
              >
                {confirm.kind === "deactivate"
                  ? t("bankTemplates.action.deactivate")
                  : confirm.kind === "activate"
                    ? t("bankTemplates.action.activate")
                    : t("bankTemplates.action.delete")}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("bankTemplates.title")}</h1>
        <div className="flex items-center gap-3">
          {canManage && companyId ? (
            <Link
              href={`/app/bank-templates/new?companyId=${encodeURIComponent(companyId)}`}
              className="rounded bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground"
            >
              + {t("bankTemplates.action.new")}
            </Link>
          ) : null}
          {canManageCatalog ? (
            <Link
              href="/app/platform-bank-templates"
              className="rounded bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground"
            >
              {t("bankTemplates.action.manageCatalog")}
            </Link>
          ) : null}
          <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
            {"\u2190 "}
            {t("nav.dashboard")}
          </Link>
        </div>
      </div>

      <p className="text-sm text-muted">{t("bankTemplates.helper.intro")}</p>

      <section className="rounded-lg border border-border bg-surface/80 p-4 shadow-sm sm:p-5">
        <div className="flex flex-col gap-5 sm:flex-row sm:flex-wrap sm:items-end sm:justify-between sm:gap-x-10 sm:gap-y-4">
          <div className="flex min-w-0 flex-1 flex-col gap-2 sm:max-w-md">
            <label className="text-xs font-medium uppercase tracking-wide text-muted" htmlFor="bank-templates-company">
              {t("bankTemplates.label.company")}
            </label>
            <select
              id="bank-templates-company"
              className="h-10 w-full min-w-0 rounded border border-border bg-background px-3 text-sm leading-none sm:min-w-[16rem]"
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
          <div className="flex shrink-0 flex-wrap items-center gap-2 border-t border-border pt-4 sm:border-l sm:border-t-0 sm:pl-8 sm:pt-0">
            <button
              type="button"
              className={`h-10 shrink-0 rounded px-4 text-sm font-medium leading-none ${activeFilter === "all" ? "bg-primary text-primary-foreground" : "border border-border bg-background"}`}
              onClick={() => {
                setPage(0);
                setActiveFilter("all");
              }}
            >
              {t("bankTemplates.filter.active.all")}
            </button>
            <button
              type="button"
              className={`h-10 shrink-0 rounded px-4 text-sm font-medium leading-none ${activeFilter === "active" ? "bg-primary text-primary-foreground" : "border border-border bg-background"}`}
              onClick={() => {
                setPage(0);
                setActiveFilter("active");
              }}
            >
              {t("bankTemplates.filter.active.active")}
            </button>
          </div>
        </div>
      </section>

      {msg ? <p className="text-sm font-medium text-destructive">{msg}</p> : null}

      {load === "error" ? (
        <p className="text-sm text-muted">{t("bankTemplates.error.load")}</p>
      ) : !companyId ? (
        <p className="text-sm text-muted">{t("bankTemplates.state.empty")}</p>
      ) : load === "loading" ? (
        <p className="text-sm text-muted">{t("bankTemplates.state.loading")}</p>
      ) : items.length === 0 ? (
        <section className="rounded-md border border-border bg-surface p-5">
          <p className="text-sm text-muted">{t("bankTemplates.state.empty")}</p>
          {canManageCatalog ? (
            <div className="mt-3 flex flex-wrap items-center gap-3">
              <Link
                href="/app/platform-bank-templates"
                className="rounded border border-border px-3 py-1.5 text-sm font-medium text-foreground hover:bg-surface-alt"
              >
                {t("bankTemplates.action.openPlatformCatalog")}
              </Link>
              <Link
                href="/app/platform-bank-templates/new"
                className="rounded bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground"
              >
                {t("bankTemplates.action.addPlatformTemplate")}
              </Link>
            </div>
          ) : null}
        </section>
      ) : (
        <div className="overflow-x-auto rounded-md border border-border">
          <table className="w-full text-sm">
            <thead className="bg-surface text-left text-xs font-medium uppercase text-muted">
              <tr>
                <th className="px-3 py-2">{t("bankTemplates.col.name")}</th>
                <th className="px-3 py-2">{t("bankTemplates.col.bankName")}</th>
                <th className="px-3 py-2">{t("bankTemplates.col.swift")}</th>
                <th className="px-3 py-2">{t("bankTemplates.col.accountNumber")}</th>
                <th className="px-3 py-2">{t("bankTemplates.col.currency")}</th>
                <th className="px-3 py-2">{t("bankTemplates.col.status")}</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {items.map((row) => (
                <tr key={row.id} className="border-t border-border">
                  <td className="px-3 py-2">{row.platformTemplateName}</td>
                  <td className="px-3 py-2">{row.bankName ?? "—"}</td>
                  <td className="px-3 py-2 font-mono">{row.swiftBic ?? "—"}</td>
                  <td className="px-3 py-2 font-mono">{row.accountNumber ?? "—"}</td>
                  <td className="px-3 py-2 font-mono">{row.currencyCode ?? "—"}</td>
                  <td className="px-3 py-2">{row.active ? t("bankTemplates.status.active") : t("bankTemplates.status.inactive")}</td>
                  <td className="px-3 py-2 text-right">
                    {canManage ? (
                      <>
                        <Link
                          href={`/app/bank-templates/${row.id}/edit?companyId=${encodeURIComponent(companyId)}`}
                          className="mr-2 text-primary underline-offset-4 hover:underline"
                        >
                          {t("bankTemplates.action.edit")}
                        </Link>
                        {row.active ? (
                          <button
                            type="button"
                            className="text-primary underline-offset-4 hover:underline disabled:opacity-50"
                            disabled={busyId === row.id}
                            onClick={() => setConfirm({ kind: "deactivate", row })}
                          >
                            {t("bankTemplates.action.deactivate")}
                          </button>
                        ) : (
                          <button
                            type="button"
                            className="text-primary underline-offset-4 hover:underline disabled:opacity-50"
                            disabled={busyId === row.id}
                            onClick={() => setConfirm({ kind: "activate", row })}
                          >
                            {t("bankTemplates.action.activate")}
                          </button>
                        )}
                        <button
                          type="button"
                          className="ml-2 text-destructive underline-offset-4 hover:underline disabled:opacity-50"
                          disabled={busyId === row.id}
                          onClick={() => setConfirm({ kind: "delete", row })}
                        >
                          {t("bankTemplates.action.delete")}
                        </button>
                      </>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {companyId && items.length > 0 ? (
        <div className="flex items-center justify-between text-sm">
          <button
            type="button"
            className="rounded border border-border px-3 py-1 disabled:opacity-40"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            {t("bankTemplates.action.prev")}
          </button>
          <span className="text-muted">
            {t("bankTemplates.pageIndicator").replace("{n}", String(page + 1)).replace("{t}", String(totalPages))}
          </span>
          <button
            type="button"
            className="rounded border border-border px-3 py-1 disabled:opacity-40"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            {t("bankTemplates.action.next")}
          </button>
        </div>
      ) : null}
    </div>
  );
}
