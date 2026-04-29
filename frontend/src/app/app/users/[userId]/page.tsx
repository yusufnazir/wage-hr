"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { fetchTenantUserDetail, patchTenantUser, type TenantUserDetail, type TenantUserDetailResult } from "@/lib/api";
import { navLabel } from "@/messages/nav";

export default function TenantUserEditPage() {
  const { me } = useTenantAppSession();
  const params = useParams();
  const router = useRouter();
  const userId = typeof params.userId === "string" ? params.userId : "";

  const canEdit = me.privileges.includes("USER_EDIT");
  const isSelf = me.userId === userId;

  const [load, setLoad] = useState<TenantUserDetailResult | "loading">("loading");
  const [email, setEmail] = useState("");
  const [selectedRoleIds, setSelectedRoleIds] = useState<Set<string>>(new Set());
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const r = await fetchTenantUserDetail(userId);
      if (cancelled) return;
      setLoad(r);
      if (r.ok) {
        setEmail(r.user.email);
        setSelectedRoleIds(new Set(r.user.roleAssignments.map((x) => x.roleId)));
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [userId]);

  async function onSave(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    setBusy(true);
    try {
      if (canEdit && !isSelf) {
        await patchTenantUser(userId, {
          email,
          roleIds: Array.from(selectedRoleIds),
        });
      } else if (canEdit && isSelf) {
        await patchTenantUser(userId, { email });
      }
      router.push("/app/users");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Save failed");
    } finally {
      setBusy(false);
    }
  }

  if (load === "loading") {
    return <p className="mx-auto max-w-lg p-6 text-sm text-muted">Loading…</p>;
  }

  if (!load.ok) {
    return (
      <div className="mx-auto max-w-lg space-y-4 p-6">
        <h1 className="text-lg font-semibold text-foreground">User</h1>
        <p className="text-sm text-muted">
          {load.status === 403
            ? "You do not have access to this user."
            : load.status === 404
              ? "User not found in this tenant."
              : `Could not load user (HTTP ${load.status}).`}
        </p>
        <Link href="/app/users" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← Back to users
        </Link>
      </div>
    );
  }

  const u: TenantUserDetail = load.user;
  const readOnly = !canEdit;

  return (
    <div className="mx-auto max-w-lg space-y-6 p-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{readOnly ? "User" : "Edit user"}</h1>
        <Link href="/app/users" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {navLabel(me.locale, "nav.users")}
        </Link>
      </div>

      {readOnly ? (
        <dl className="space-y-3 text-sm">
          <div>
            <dt className="text-xs font-medium uppercase text-muted">Email</dt>
            <dd className="text-foreground">{u.email}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium uppercase text-muted">Status</dt>
            <dd className="text-muted">{u.status}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium uppercase text-muted">Roles</dt>
            <dd className="text-muted">{u.roleNames.length ? u.roleNames.join(", ") : "—"}</dd>
          </div>
        </dl>
      ) : (
        <form className="space-y-4" onSubmit={(e) => void onSave(e)}>
          <label className="flex flex-col gap-1 text-sm font-medium text-foreground">
            Email
            <input
              className="rounded-md border border-border bg-background px-3 py-2"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
            />
          </label>
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium text-foreground">Roles in this tenant</legend>
            {isSelf ? (
              <p className="text-xs text-muted">You cannot change your own roles.</p>
            ) : null}
            <ul className="space-y-2">
              {u.assignableRoles.map((r) => (
                <li key={r.id}>
                  <label className="flex items-center gap-2 text-sm text-foreground">
                    <input
                      type="checkbox"
                      checked={selectedRoleIds.has(r.id)}
                      disabled={isSelf}
                      onChange={(ev) => {
                        setSelectedRoleIds((prev) => {
                          const n = new Set(prev);
                          if (ev.target.checked) {
                            n.add(r.id);
                          } else {
                            n.delete(r.id);
                          }
                          return n;
                        });
                      }}
                    />
                    {r.name}
                  </label>
                </li>
              ))}
            </ul>
          </fieldset>
          {msg ? <p className="text-sm text-destructive">{msg}</p> : null}
          <button
            type="submit"
            disabled={busy}
            className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
          >
            Save
          </button>
        </form>
      )}
    </div>
  );
}
