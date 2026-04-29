"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import { FilterBar, FilterChip } from "@/components/filters/FilterBar";
import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { createTenantRole, fetchTenantRoles, type TenantRolesListResult } from "@/lib/api";
import { nextSearchParams, toQueryString } from "@/lib/filter-url";
import { navLabel } from "@/messages/nav";

function nextSort(current: string): string {
  const asc = "NAME_ASC";
  const desc = "NAME_DESC";
  if (current === asc) return desc;
  return asc;
}

export default function TenantRolesPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const sp = useSearchParams();

  const q = sp.get("q") ?? "";
  const sort = sp.get("sort") ?? "NAME_ASC";
  const pageTitle = navLabel(me.locale, "nav.roles");

  const [draftQ, setDraftQ] = useState(q);
  const [data, setData] = useState<TenantRolesListResult | undefined>(undefined);

  const [createName, setCreateName] = useState("");
  const [breakGlassReason, setBreakGlassReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const canEdit = me.privileges.includes("ROLE_EDIT");
  const isAdminHost =
    typeof window !== "undefined" ? window.location.hostname.toLowerCase().startsWith("admin.") : false;
  const needsBreakGlass = canEdit && isAdminHost && me.platformSuperadmin;

  useEffect(() => {
    setDraftQ(q);
  }, [q]);

  const reload = useCallback(async () => {
    setData(undefined);
    const r = await fetchTenantRoles({ q: q || undefined, sort });
    setData(r);
  }, [q, sort]);

  useEffect(() => {
    void reload();
  }, [reload]);

  function pushWithPatch(patch: Record<string, string | undefined>) {
    const next = nextSearchParams(sp, patch);
    router.push(`/app/roles${toQueryString(next)}`);
  }

  const sortHref = useMemo(() => {
    const next = nextSearchParams(sp, { sort: nextSort(sort), page: "0" });
    return `/app/roles${toQueryString(next)}`;
  }, [sp, sort]);

  const anyFilterActive = q.trim() !== "";

  async function onCreate() {
    setBusy(true);
    setMsg(null);
    try {
      if (!createName.trim()) {
        setMsg("Role name is required.");
        return;
      }
      if (needsBreakGlass) {
        const r = breakGlassReason.trim();
        if (r.length < 3) {
          setMsg("Break-glass reason is required for platform operator changes (min 3 chars).");
          return;
        }
        if (r.length > 500) {
          setMsg("Break-glass reason is too long (max 500 chars).");
          return;
        }
      }
      const created = await createTenantRole({
        name: createName.trim(),
        privilegeCodes: [],
        breakGlassReason: needsBreakGlass ? breakGlassReason.trim() : undefined,
      });
      router.push(`/app/roles/${created.id}`);
    } catch (e) {
      setMsg(e instanceof Error ? e.message : "Create failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6" data-testid="tenant-roles-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{pageTitle}</h1>
        <div className="flex flex-wrap gap-3 text-sm">
          <Link href="/app" className="font-medium text-primary underline-offset-4 hover:underline">
            ← {navLabel(me.locale, "nav.dashboard")}
          </Link>
        </div>
      </div>

      <div className="rounded-md border border-border bg-surface p-4">
        <FilterBar
          showClearAll={anyFilterActive}
          onClearAll={() => pushWithPatch({ q: undefined, page: "0" })}
        >
          <FilterChip
            label="Name"
            value={q}
            valueLabel={q ? `contains “${q}”` : ""}
            onClear={() => pushWithPatch({ q: undefined, page: "0" })}
          >
            {({ close }) => (
              <form
                className="space-y-2"
                onSubmit={(e) => {
                  e.preventDefault();
                  pushWithPatch({ q: draftQ.trim() || undefined, page: "0" });
                  close();
                }}
              >
                <label className="flex flex-col gap-1 text-xs font-medium text-foreground">
                  Role name contains
                  <input
                    className="rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    value={draftQ}
                    onChange={(e) => setDraftQ(e.target.value)}
                    autoComplete="off"
                  />
                </label>
                <div className="flex gap-2">
                  <button
                    type="submit"
                    className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90"
                  >
                    Apply
                  </button>
                  <button
                    type="button"
                    className="rounded-md border border-border bg-background px-3 py-1.5 text-sm font-medium text-foreground hover:bg-muted/30"
                    onClick={() => {
                      setDraftQ(q);
                      close();
                    }}
                  >
                    Cancel
                  </button>
                </div>
              </form>
            )}
          </FilterChip>
        </FilterBar>
      </div>

      {canEdit ? (
        <section className="rounded-md border border-border bg-surface p-6 shadow-sm" data-testid="roles-create">
          <h2 className="text-sm font-medium text-foreground">New role</h2>
          <p className="mt-1 text-xs text-muted">
            Create the role first, then assign privileges in the role detail view. Assignable privileges are limited to the
            tenant’s effective ceiling.
          </p>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <label className="flex flex-col gap-1 text-sm font-medium text-foreground">
              Name
              <input
                className="rounded-md border border-border bg-background px-3 py-2"
                value={createName}
                onChange={(e) => setCreateName(e.target.value)}
                disabled={busy}
                required
              />
            </label>
            {needsBreakGlass ? (
              <label className="flex flex-col gap-1 text-sm font-medium text-foreground">
                Break-glass reason (platform operator)
                <input
                  className="rounded-md border border-border bg-background px-3 py-2"
                  value={breakGlassReason}
                  onChange={(e) => setBreakGlassReason(e.target.value)}
                  disabled={busy}
                  placeholder="Why is this change needed?"
                />
              </label>
            ) : null}
          </div>
          {msg ? (
            <p className="mt-2 text-sm text-destructive" data-testid="roles-create-error">
              {msg}
            </p>
          ) : null}
          <button
            type="button"
            disabled={busy}
            onClick={() => void onCreate()}
            className="mt-4 inline-flex w-fit items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
            data-testid="roles-create-btn"
          >
            {busy ? "Creating…" : "Create role"}
          </button>
        </section>
      ) : null}

      {data === undefined ? (
        <p className="text-sm text-muted">Loading…</p>
      ) : !data.ok ? (
        <p className="text-sm text-muted">Could not load roles (HTTP {data.status}).</p>
      ) : (
        <div className="overflow-x-auto rounded-md border border-border">
          <table className="w-full min-w-[36rem] border-collapse text-sm">
            <thead className="bg-muted/30 text-left text-xs font-semibold uppercase tracking-wide text-muted">
              <tr>
                <th className="px-3 py-2">
                  <Link href={sortHref} className="text-primary hover:underline" data-testid="roles-sort-name">
                    Name
                  </Link>
                </th>
                <th className="px-3 py-2">Privileges</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((row) => (
                <tr key={row.id} className="border-t border-border">
                  <td className="px-3 py-2 font-medium text-foreground">{row.name}</td>
                  <td className="px-3 py-2 text-muted">
                    {row.privilegeCodes.length === 0 ? (
                      "—"
                    ) : (
                      <span title={row.privilegeCodes.join(", ")}>{row.privilegeCodes.length} privileges</span>
                    )}
                  </td>
                  <td className="px-3 py-2">
                    <Link
                      href={`/app/roles/${row.id}`}
                      className="font-medium text-primary underline-offset-4 hover:underline"
                    >
                      {canEdit ? "Edit" : "View"}
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

