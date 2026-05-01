"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  createPlatformRoleTemplate,
  fetchPlatformPrivilegeCatalog,
  type PlatformPrivilegeCatalogEntry,
} from "@/lib/api";
import { RoleTemplatePrivilegeGroupList } from "@/components/platform/RoleTemplatePrivilegeGroupList";
import { groupPrivilegeCodesByResource } from "@/lib/privilegeCatalogGroups";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function PlatformRoleTemplateCreatePage() {
  const router = useRouter();
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [catalog, setCatalog] = useState<PlatformPrivilegeCatalogEntry[]>([]);
  const [code, setCode] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoad("loading");
    setMsg(null);
    const c = await fetchPlatformPrivilegeCatalog();
    if (!c.ok) {
      setLoad(c.status === 403 ? "forbidden" : "error");
      return;
    }
    setCatalog(c.entries);
    setLoad("ready");
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const codes = useMemo(() => catalog.map((e) => e.code).sort(), [catalog]);

  const privilegeGroups = useMemo(() => groupPrivilegeCodesByResource(codes, catalog), [codes, catalog]);

  async function onCreate(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      const created = await createPlatformRoleTemplate({
        code: code.trim(),
        displayName: displayName.trim(),
        privilegeCodes: Array.from(selected).sort(),
      });
      router.replace(`/app/platform-role-templates/${created.id}`);
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Create failed");
    } finally {
      setBusy(false);
    }
  }

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("nav.platform_role_templates")}</h1>
        <p className="text-sm text-muted">Only a platform operator (platform superadmin) can create role templates.</p>
        <Link href="/app/platform-role-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← Back to list
        </Link>
      </div>
    );
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("nav.platform_role_templates")}</h1>
        <p className="text-sm text-muted">Access denied (403). Your session may not be a platform operator.</p>
        <Link href="/app/platform-role-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← Back to list
        </Link>
      </div>
    );
  }

  if (load === "error") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("nav.platform_role_templates")}</h1>
        <p className="text-sm text-muted">Could not load privilege catalog.</p>
        <button type="button" className="text-sm font-medium text-primary underline-offset-4 hover:underline" onClick={() => void reload()}>
          Retry
        </button>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-3xl">
        <p className="text-sm text-muted">Loading…</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6" data-testid="platform-role-template-create">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">New template</h1>
        <Link href="/app/platform-role-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← Back to list
        </Link>
      </div>

      <p className="text-sm text-muted">This affects future registrations only (Option A).</p>

      <form className="space-y-6" onSubmit={(e) => void onCreate(e)}>
        <section className="rounded-lg border border-border bg-surface p-6 shadow-sm">
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="flex flex-col gap-1 text-sm font-medium text-foreground">
              Code
              <input
                className="rounded-md border border-border bg-background px-3 py-2 font-mono text-sm"
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                disabled={busy}
                placeholder="MANAGER"
                required
              />
            </label>
            <label className="flex flex-col gap-1 text-sm font-medium text-foreground">
              Display name
              <input
                className="rounded-md border border-border bg-background px-3 py-2 text-sm"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                disabled={busy}
                placeholder="Manager"
                required
              />
            </label>
          </div>
        </section>

        <section className="rounded-lg border border-border bg-surface p-6 shadow-sm">
          <div className="text-xs font-medium uppercase text-muted">Privileges</div>
          {codes.length === 0 ? (
            <p className="mt-2 text-sm text-muted">No privileges available.</p>
          ) : (
            <RoleTemplatePrivilegeGroupList groups={privilegeGroups} selected={selected} setSelected={setSelected} busy={busy} />
          )}
        </section>

        {msg ? <p className="text-sm text-destructive">{msg}</p> : null}

        <div className="flex flex-wrap gap-2">
          <button
            type="submit"
            disabled={busy}
            className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
          >
            {busy ? "Creating…" : "Create"}
          </button>
          <Link
            href="/app/platform-role-templates"
            className="rounded-md border border-border bg-background px-4 py-2 text-sm font-medium text-foreground hover:bg-muted/30"
          >
            Cancel
          </Link>
        </div>
      </form>
    </div>
  );
}

