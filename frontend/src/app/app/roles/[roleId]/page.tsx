"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchTenantRoleDetail,
  patchTenantRole,
  type TenantRoleDetail,
  type TenantRoleDetailResult,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

function asString(v: unknown): string {
  return typeof v === "string" ? v : "";
}

export default function TenantRoleDetailPage() {
  const { me } = useTenantAppSession();
  const params = useParams();
  const router = useRouter();
  const roleId = asString(params.roleId);

  const canEdit = me.privileges.includes("ROLE_EDIT");
  const isAdminHost =
    typeof window !== "undefined" ? window.location.hostname.toLowerCase().startsWith("admin.") : false;
  const needsBreakGlass = canEdit && isAdminHost && me.platformSuperadmin;

  const [load, setLoad] = useState<TenantRoleDetailResult | "loading">("loading");
  const [name, setName] = useState("");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [breakGlassReason, setBreakGlassReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const r = await fetchTenantRoleDetail(roleId);
      if (cancelled) return;
      setLoad(r);
      if (r.ok) {
        setName(r.data.role.name);
        setSelected(new Set(r.data.role.privilegeCodes));
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [roleId]);

  const readOnly = !canEdit;

  const dirty = useMemo(() => {
    if (load === "loading" || !load.ok) return false;
    const original = load.data.role;
    if (name.trim() !== original.name) return true;
    const o = new Set(original.privilegeCodes);
    if (o.size !== selected.size) return true;
    for (const c of selected) {
      if (!o.has(c)) return true;
    }
    return false;
  }, [load, name, selected]);

  async function onSave(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
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
      const updated = await patchTenantRole({
        roleId,
        name: name.trim(),
        privilegeCodes: Array.from(selected).sort(),
        breakGlassReason: needsBreakGlass ? breakGlassReason.trim() : undefined,
      });
      setMsg("Saved.");
      setLoad({ ok: true, data: { ...(load as { ok: true; data: TenantRoleDetail }).data, role: updated } });
      router.refresh();
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Save failed");
    } finally {
      setBusy(false);
    }
  }

  if (load === "loading") {
    return <p className="mx-auto max-w-3xl p-6 text-sm text-muted">Loading…</p>;
  }

  if (!load.ok) {
    return (
      <div className="mx-auto max-w-3xl space-y-4 p-6">
        <h1 className="text-lg font-semibold text-foreground">Role</h1>
        <p className="text-sm text-muted">
          {load.status === 403
            ? "You do not have access to roles in this tenant."
            : load.status === 404
              ? "Role not found in this tenant."
              : `Could not load role (HTTP ${load.status}).`}
        </p>
        <Link href="/app/roles" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← Back to {navLabel(me.locale, "nav.roles")}
        </Link>
      </div>
    );
  }

  const d: TenantRoleDetail = load.data;

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-6" data-testid="tenant-role-detail">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{readOnly ? d.role.name : "Edit role"}</h1>
        <Link href="/app/roles" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {navLabel(me.locale, "nav.roles")}
        </Link>
      </div>

      {readOnly ? (
        <section className="rounded-md border border-border bg-surface p-6 shadow-sm">
          <dl className="space-y-3 text-sm">
            <div>
              <dt className="text-xs font-medium uppercase text-muted">Name</dt>
              <dd className="text-foreground">{d.role.name}</dd>
            </div>
            <div>
              <dt className="text-xs font-medium uppercase text-muted">Privileges</dt>
              <dd className="text-muted">
                {d.role.privilegeCodes.length === 0 ? "—" : d.role.privilegeCodes.join(", ")}
              </dd>
            </div>
          </dl>
        </section>
      ) : (
        <form className="space-y-6" onSubmit={(e) => void onSave(e)}>
          <section className="rounded-md border border-border bg-surface p-6 shadow-sm">
            <label className="flex flex-col gap-1 text-sm font-medium text-foreground">
              Name
              <input
                className="rounded-md border border-border bg-background px-3 py-2"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                disabled={busy}
              />
            </label>
            {needsBreakGlass ? (
              <label className="mt-4 flex flex-col gap-1 text-sm font-medium text-foreground">
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
          </section>

          <section className="rounded-md border border-border bg-surface p-6 shadow-sm">
            <h2 className="text-sm font-medium text-foreground">Assignable privileges</h2>
            <p className="mt-1 text-xs text-muted">
              Based on tenant plan features and operator allowances. Codes outside this ceiling cannot be assigned.
            </p>
            {d.assignablePrivilegeCodes.length === 0 ? (
              <p className="mt-3 text-sm text-muted">No privileges are assignable in this tenant.</p>
            ) : (
              <ul className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2" data-testid="role-privilege-picker">
                {d.assignablePrivilegeCodes.map((code) => (
                  <li key={code}>
                    <label className="flex items-center gap-2 text-sm text-foreground">
                      <input
                        type="checkbox"
                        checked={selected.has(code)}
                        disabled={busy}
                        onChange={(ev) => {
                          setSelected((prev) => {
                            const n = new Set(prev);
                            if (ev.target.checked) n.add(code);
                            else n.delete(code);
                            return n;
                          });
                        }}
                      />
                      <span className="font-mono text-xs">{code}</span>
                    </label>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {msg ? (
            <p className={msg === "Saved." ? "text-sm text-foreground" : "text-sm text-destructive"} data-testid="role-msg">
              {msg}
            </p>
          ) : null}

          <div className="flex flex-wrap gap-3">
            <button
              type="submit"
              disabled={busy || !dirty}
              className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
              data-testid="role-save-btn"
            >
              {busy ? "Saving…" : "Save"}
            </button>
            <Link
              href="/app/roles"
              className="rounded-md border border-border bg-background px-4 py-2 text-sm font-medium text-foreground hover:bg-muted/30"
            >
              Cancel
            </Link>
          </div>
        </form>
      )}
    </div>
  );
}

