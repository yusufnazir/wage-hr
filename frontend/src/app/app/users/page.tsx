"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import { FilterBar, FilterChip } from "@/components/filters/FilterBar";
import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { fetchTenantUserRoleOptions, fetchTenantUsersPage, type TenantRoleOption, type TenantUsersPageResult } from "@/lib/api";
import { nextSearchParams, toQueryString } from "@/lib/filter-url";
import { navLabel } from "@/messages/nav";

function formatInstant(iso: string | null, locale: string): string {
  if (!iso) {
    return "—";
  }
  try {
    return new Date(iso).toLocaleString(locale);
  } catch {
    return iso;
  }
}

function nextSort(current: string, column: string): string {
  const asc = `${column}_ASC`;
  const desc = `${column}_DESC`;
  if (current === asc) {
    return desc;
  }
  return asc;
}

export default function TenantUsersPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const sp = useSearchParams();
  const isDev = process.env.NODE_ENV === "development";
  const page = Math.max(0, Number.parseInt(sp.get("page") ?? "0", 10) || 0);
  const size = 20;
  const sort = sp.get("sort") ?? "EMAIL_ASC";
  const emailQ = sp.get("email") ?? "";
  const statusQ = sp.get("status") ?? "";
  const roleQ = sp.get("role") ?? "";

  const [draftEmail, setDraftEmail] = useState(emailQ);
  const [data, setData] = useState<TenantUsersPageResult | undefined>(undefined);
  const [roleOptions, setRoleOptions] = useState<TenantRoleOption[]>([]);
  const [roleOptionsLoaded, setRoleOptionsLoaded] = useState(false);

  useEffect(() => {
    setDraftEmail(emailQ);
  }, [emailQ]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const res = await fetchTenantUserRoleOptions();
      if (cancelled) return;
      setRoleOptionsLoaded(true);
      if (res.ok) {
        setRoleOptions(res.roles);
      } else {
        setRoleOptions([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const canEdit = me.privileges.includes("USER_EDIT");

  const reload = useCallback(async () => {
    setData(undefined);
    const r = await fetchTenantUsersPage({
      page,
      size,
      sort,
      email: emailQ || undefined,
      status: statusQ || undefined,
      role: roleQ || undefined,
    });
    setData(r);
  }, [page, size, sort, emailQ, statusQ, roleQ]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const sortHref = useMemo(() => {
    return (column: string) => {
      const q = nextSearchParams(sp, { sort: nextSort(sort, column), page: "0" });
      return `/app/users${toQueryString(q)}`;
    };
  }, [sp, sort]);

  const anyFilterActive = emailQ.trim() !== "" || statusQ.trim() !== "" || roleQ.trim() !== "";

  function pushWithPatch(patch: Record<string, string | undefined>) {
    const q = nextSearchParams(sp, patch);
    router.push(`/app/users${toQueryString(q)}`);
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{navLabel(me.locale, "nav.users")}</h1>
        <div className="flex flex-wrap gap-3 text-sm">
          <Link href="/app" className="font-medium text-primary underline-offset-4 hover:underline">
            ← {navLabel(me.locale, "nav.dashboard")}
          </Link>
          {canEdit ? (
            <Link href="/app/users/new" className="text-muted underline-offset-4 hover:underline">
              Add user
            </Link>
          ) : null}
        </div>
      </div>

      <div className="rounded-md border border-border bg-surface p-4">
        <FilterBar
          showClearAll={anyFilterActive}
          onClearAll={() => {
            pushWithPatch({ email: undefined, status: undefined, role: undefined, page: "0" });
          }}
        >
          <FilterChip
            label="Email"
            value={emailQ}
            valueLabel={emailQ ? `contains “${emailQ}”` : ""}
            onClear={() => pushWithPatch({ email: undefined, page: "0" })}
          >
            {({ close }) => (
              <form
                className="space-y-2"
                onSubmit={(e) => {
                  e.preventDefault();
                  pushWithPatch({ email: draftEmail.trim() || undefined, page: "0" });
                  close();
                }}
              >
                <label className="flex flex-col gap-1 text-xs font-medium text-foreground">
                  Email contains
                  <input
                    className="rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    value={draftEmail}
                    onChange={(e) => setDraftEmail(e.target.value)}
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
                      setDraftEmail(emailQ);
                      close();
                    }}
                  >
                    Cancel
                  </button>
                </div>
              </form>
            )}
          </FilterChip>

          <FilterChip
            label="Status"
            value={statusQ}
            onClear={() => pushWithPatch({ status: undefined, page: "0" })}
          >
            {({ close }) => (
              <div className="space-y-2">
                <p className="text-xs font-medium text-foreground">Status</p>
                <div className="flex flex-col gap-1">
                  {["ACTIVE"].map((opt) => (
                    <button
                      key={opt}
                      type="button"
                      className={`w-full rounded-md border px-3 py-2 text-left text-sm ${
                        statusQ === opt
                          ? "border-primary bg-primary/10 text-foreground"
                          : "border-border bg-background text-foreground hover:bg-muted/30"
                      }`}
                      onClick={() => {
                        pushWithPatch({ status: opt, page: "0" });
                        close();
                      }}
                    >
                      {opt}
                    </button>
                  ))}
                </div>
                <p className="text-xs text-muted">Status is read-only in v1; more options may be added later.</p>
              </div>
            )}
          </FilterChip>

          <FilterChip
            label="Role"
            value={roleQ}
            valueLabel={roleQ ? `is “${roleQ}”` : ""}
            onClear={() => pushWithPatch({ role: undefined, page: "0" })}
          >
            {({ close }) => (
              <div className="space-y-2">
                <p className="text-xs font-medium text-foreground">Role</p>
                {!roleOptionsLoaded ? <p className="text-xs text-muted">Loading roles…</p> : null}
                {roleOptionsLoaded && roleOptions.length === 0 ? (
                  <p className="text-xs text-muted">No role options available.</p>
                ) : null}
                {roleOptions.length ? (
                  <div className="flex max-h-56 flex-col gap-1 overflow-auto pr-1">
                    {roleOptions.map((r) => (
                      <button
                        key={r.id}
                        type="button"
                        className={`w-full rounded-md border px-3 py-2 text-left text-sm ${
                          roleQ === r.name
                            ? "border-primary bg-primary/10 text-foreground"
                            : "border-border bg-background text-foreground hover:bg-muted/30"
                        }`}
                        onClick={() => {
                          pushWithPatch({ role: r.name, page: "0" });
                          close();
                        }}
                      >
                        {r.name}
                      </button>
                    ))}
                  </div>
                ) : null}
              </div>
            )}
          </FilterChip>
        </FilterBar>
        {isDev ? (
          <div className="mt-2 flex items-start gap-2 rounded-md border border-border bg-background/50 px-3 py-2 text-xs text-muted">
            <span
              className="mt-0.5 inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-md border border-border bg-surface"
              aria-hidden
              title="Development note"
            >
              <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="1.75">
                <path
                  d="M10.5 3.75h3M12 3.75V7M7 7h10l-4.2 7.2v2.3a2 2 0 0 1-2 2H9.8a.8.8 0 0 1-.7-1.2l.9-1.5V14.2L7 7Z"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </span>
            <div className="min-w-0">
              <span className="mr-2 inline-flex items-center rounded-full border border-border bg-surface px-2 py-0.5 font-semibold text-foreground">
                Dev
              </span>
              Filters are applied server-side via URL query params (shareable links). Sorting remains available on the
              column headers below.
            </div>
          </div>
        ) : null}
      </div>

      {data === undefined ? (
        <p className="text-sm text-muted">Loading…</p>
      ) : !data.ok ? (
        <p className="text-sm text-muted">Could not load users (HTTP {data.status}).</p>
      ) : (
        <>
          <div className="overflow-x-auto rounded-md border border-border">
            <table className="w-full min-w-[36rem] border-collapse text-sm">
              <thead className="bg-muted/30 text-left text-xs font-semibold uppercase tracking-wide text-muted">
                <tr>
                  <th className="px-3 py-2">
                    <Link href={sortHref("EMAIL")} className="text-primary hover:underline">
                      Email
                    </Link>
                  </th>
                  <th className="px-3 py-2">
                    <Link href={sortHref("LAST_ACTIVE")} className="text-primary hover:underline">
                      Last active
                    </Link>
                  </th>
                  <th className="px-3 py-2">
                    <Link href={sortHref("STATUS")} className="text-primary hover:underline">
                      Status
                    </Link>
                  </th>
                  <th className="px-3 py-2">
                    <Link href={sortHref("ROLES")} className="text-primary hover:underline">
                      Roles
                    </Link>
                  </th>
                  {canEdit ? <th className="px-3 py-2"> </th> : null}
                </tr>
              </thead>
              <tbody>
                {data.items.map((row) => (
                  <tr key={row.userId} className="border-t border-border">
                    <td className="px-3 py-2 font-medium text-foreground">{row.email}</td>
                    <td className="px-3 py-2 text-muted">{formatInstant(row.lastActiveAt, me.locale)}</td>
                    <td className="px-3 py-2 text-muted">{row.status}</td>
                    <td className="px-3 py-2 text-muted">{row.roleNames.length ? row.roleNames.join(", ") : "—"}</td>
                    {canEdit ? (
                      <td className="px-3 py-2">
                        <Link
                          href={`/app/users/${row.userId}`}
                          className="font-medium text-primary underline-offset-4 hover:underline"
                        >
                          Edit
                        </Link>
                      </td>
                    ) : null}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-muted">
            <span>
              {data.totalElements === 0
                ? "No users match these filters."
                : `Page ${data.page + 1} of ${data.totalPages} · ${data.totalElements} users`}
            </span>
            <div className="flex gap-2">
              <Link
                className={`rounded-md border border-border px-3 py-1.5 font-medium ${
                  data.totalElements === 0 || data.page <= 0
                    ? "pointer-events-none opacity-40"
                    : "text-primary hover:bg-muted/30"
                }`}
                href={`/app/users${toQueryString(nextSearchParams(sp, { page: String(Math.max(0, data.page - 1)) }))}`}
              >
                Previous
              </Link>
              <Link
                className={`rounded-md border border-border px-3 py-1.5 font-medium ${
                  data.totalElements === 0 || data.page + 1 >= data.totalPages
                    ? "pointer-events-none opacity-40"
                    : "text-primary hover:bg-muted/30"
                }`}
                href={`/app/users${toQueryString(nextSearchParams(sp, { page: String(data.page + 1) }))}`}
              >
                Next
              </Link>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
