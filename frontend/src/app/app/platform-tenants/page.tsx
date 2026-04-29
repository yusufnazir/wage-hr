"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchPlatformTenants,
  postPlatformTenant,
  type PlatformTenantRow,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function PlatformTenantsPage() {
  const router = useRouter();
  const { me } = useTenantAppSession();
  const [load, setLoad] = useState<LoadState>("loading");
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<PlatformTenantRow[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [newHandle, setNewHandle] = useState("");
  const [newName, setNewName] = useState("");

  const reload = useCallback(async () => {
    setLoad("loading");
    setMsg(null);
    const r = await fetchPlatformTenants(page, 20);
    if (!r.ok) {
      setLoad(r.status === 403 ? "forbidden" : "error");
      return;
    }
    setRows(r.items);
    setTotalPages(r.totalPages);
    setLoad("ready");
  }, [page]);

  useEffect(() => {
    void reload();
  }, [reload]);

  async function onCreate(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      const t = await postPlatformTenant(newHandle.trim(), newName.trim());
      setNewHandle("");
      setNewName("");
      router.push(`/app/platform-tenants/${t.id}`);
    } catch (err) {
      setMsg(err instanceof Error ? err.message : navLabel(me.locale, "platformTenants.msg.createFailed"));
    } finally {
      setBusy(false);
    }
  }

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{navLabel(me.locale, "nav.platform_tenants")}</h1>
        <p className="text-sm text-muted">{navLabel(me.locale, "platformTenants.error.notOperator")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {navLabel(me.locale, "nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{navLabel(me.locale, "nav.platform_tenants")}</h1>
        <p className="text-sm text-muted">{navLabel(me.locale, "platformTenants.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {navLabel(me.locale, "nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "error") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{navLabel(me.locale, "nav.platform_tenants")}</h1>
        <p className="text-sm text-muted">{navLabel(me.locale, "platformTenants.error.load")}</p>
        <button
          type="button"
          className="text-sm font-medium text-primary underline-offset-4 hover:underline"
          onClick={() => void reload()}
        >
          {navLabel(me.locale, "platformTenants.action.retry")}
        </button>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-4xl">
        <p className="text-sm text-muted">{navLabel(me.locale, "platformTenants.state.loading")}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{navLabel(me.locale, "nav.platform_tenants")}</h1>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {navLabel(me.locale, "nav.dashboard")}
        </Link>
      </div>
      <p className="text-sm text-muted">{navLabel(me.locale, "platformTenants.helper.intro")}</p>

      {msg ? (
        <p className="text-sm text-red-600 dark:text-red-400" data-testid="platform-tenants-msg">
          {msg}
        </p>
      ) : null}

      <section className="overflow-x-auto rounded-lg border border-border bg-surface shadow-sm">
        <table className="min-w-full divide-y divide-border text-sm">
          <thead className="bg-muted/30">
            <tr>
              <th className="px-4 py-2 text-left font-medium text-foreground">
                {navLabel(me.locale, "platformTenants.col.handle")}
              </th>
              <th className="px-4 py-2 text-left font-medium text-foreground">
                {navLabel(me.locale, "platformTenants.col.name")}
              </th>
              <th className="px-4 py-2 text-left font-medium text-foreground">
                {navLabel(me.locale, "platformTenants.col.created")}
              </th>
              <th className="px-4 py-2 text-right font-medium text-foreground">
                {navLabel(me.locale, "platformTenants.col.actions")}
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {rows.map((row) => (
              <tr key={row.id}>
                <td className="px-4 py-2 font-mono text-foreground">{row.handle}</td>
                <td className="px-4 py-2 text-foreground">{row.name}</td>
                <td className="px-4 py-2 text-muted">{row.createdAt?.slice(0, 10) ?? "—"}</td>
                <td className="px-4 py-2 text-right">
                  <Link
                    href={`/app/platform-tenants/${row.id}`}
                    className="font-medium text-primary underline-offset-4 hover:underline"
                    data-testid={`platform-tenants-edit-${row.handle}`}
                  >
                    {navLabel(me.locale, "platformTenants.action.edit")}
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {totalPages > 1 ? (
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <button
            type="button"
            disabled={page <= 0 || busy}
            className="rounded-md border border-border px-3 py-1.5 hover:bg-muted/40 disabled:opacity-50"
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            {navLabel(me.locale, "platformTenants.action.prev")}
          </button>
          <span className="text-muted">
            {navLabel(me.locale, "platformTenants.pageIndicator")
              .replace("{n}", String(page + 1))
              .replace("{t}", String(totalPages))}
          </span>
          <button
            type="button"
            disabled={page >= totalPages - 1 || busy}
            className="rounded-md border border-border px-3 py-1.5 hover:bg-muted/40 disabled:opacity-50"
            onClick={() => setPage((p) => p + 1)}
          >
            {navLabel(me.locale, "platformTenants.action.next")}
          </button>
        </div>
      ) : null}

      <section className="space-y-4 rounded-lg border border-border bg-surface p-6 shadow-sm">
        <h2 className="text-sm font-medium text-foreground">{navLabel(me.locale, "platformTenants.section.create")}</h2>
        <form className="flex w-full max-w-lg flex-col gap-4" onSubmit={(e) => void onCreate(e)}>
          <div className="flex w-full min-w-0 flex-col gap-1.5">
            <label htmlFor="platform-tenants-new-handle" className="text-xs font-medium text-muted">
              {navLabel(me.locale, "platformTenants.label.handle")}
            </label>
            <input
              id="platform-tenants-new-handle"
              required
              className="w-full min-w-0 rounded-md border border-border bg-background px-3 py-2 font-mono text-sm shadow-sm"
              value={newHandle}
              onChange={(e) => setNewHandle(e.target.value)}
              autoComplete="off"
              data-testid="platform-tenants-new-handle"
            />
          </div>
          <div className="flex w-full min-w-0 flex-col gap-1.5">
            <label htmlFor="platform-tenants-new-name" className="text-xs font-medium text-muted">
              {navLabel(me.locale, "platformTenants.label.name")}
            </label>
            <input
              id="platform-tenants-new-name"
              required
              className="w-full min-w-0 rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              autoComplete="off"
              data-testid="platform-tenants-new-name"
            />
          </div>
          <div>
            <button
              type="submit"
              disabled={busy}
              className="inline-flex items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
              data-testid="platform-tenants-create"
            >
              {busy ? navLabel(me.locale, "platformTenants.state.saving") : navLabel(me.locale, "platformTenants.action.create")}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
