"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchPlatformPrivilegeCatalog,
  fetchPlatformRoleTemplate,
  patchPlatformRoleTemplate,
  type PlatformPrivilegeCatalogEntry,
  type PlatformRoleTemplate,
} from "@/lib/api";
import { RoleTemplatePrivilegeGroupList } from "@/components/platform/RoleTemplatePrivilegeGroupList";
import { groupPrivilegeCodesByResource } from "@/lib/privilegeCatalogGroups";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "not_found" | "error";

function asString(v: unknown): string {
  return typeof v === "string" ? v : "";
}

export default function PlatformRoleTemplateEditPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);
  const params = useParams();
  const templateId = asString(params.templateId);

  const [load, setLoad] = useState<LoadState>("loading");
  const [item, setItem] = useState<PlatformRoleTemplate | null>(null);
  const [catalog, setCatalog] = useState<PlatformPrivilegeCatalogEntry[]>([]);
  const [displayName, setDisplayName] = useState("");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoad("loading");
    setMsg(null);
    const r = await fetchPlatformRoleTemplate(templateId);
    if (!r.ok) {
      setLoad(r.status === 403 ? "forbidden" : r.status === 404 ? "not_found" : "error");
      return;
    }
    setItem(r.item);
    setDisplayName(r.item.displayName);
    setSelected(new Set(r.item.privilegeCodes));

    const c = await fetchPlatformPrivilegeCatalog();
    if (c.ok) {
      setCatalog(c.entries);
    } else {
      setCatalog([]);
    }
    setLoad("ready");
  }, [templateId]);

  useEffect(() => {
    if (!templateId) return;
    void reload();
  }, [reload, templateId]);

  const codes = useMemo(() => catalog.map((e) => e.code).sort(), [catalog]);

  const privilegeGroups = useMemo(() => groupPrivilegeCodesByResource(codes, catalog), [codes, catalog]);

  const dirty = useMemo(() => {
    if (!item) return false;
    if (displayName.trim() !== item.displayName) return true;
    const o = new Set(item.privilegeCodes);
    if (o.size !== selected.size) return true;
    for (const c of selected) if (!o.has(c)) return true;
    return false;
  }, [item, displayName, selected]);

  async function onSave(e: React.FormEvent) {
    e.preventDefault();
    if (!item) return;
    setBusy(true);
    setMsg(null);
    try {
      const updated = await patchPlatformRoleTemplate({
        id: item.id,
        displayName: displayName.trim(),
        privilegeCodes: Array.from(selected).sort(),
      });
      setItem(updated);
      setMsg("Saved.");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Save failed");
    } finally {
      setBusy(false);
    }
  }

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("nav.platform_role_templates")}</h1>
        <p className="text-sm text-muted">Only a platform operator (platform superadmin) can edit role templates.</p>
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

  if (load === "not_found") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("nav.platform_role_templates")}</h1>
        <p className="text-sm text-muted">Template not found.</p>
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
        <p className="text-sm text-muted">Could not load template.</p>
        <button type="button" className="text-sm font-medium text-primary underline-offset-4 hover:underline" onClick={() => void reload()}>
          Retry
        </button>
      </div>
    );
  }

  if (load === "loading" || !item) {
    return (
      <div className="mx-auto max-w-3xl">
        <p className="text-sm text-muted">Loading…</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6" data-testid="platform-role-template-edit">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">Edit template</h1>
        <Link href="/app/platform-role-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← Back to list
        </Link>
      </div>

      <p className="text-sm text-muted">Changes affect future registrations only (Option A).</p>

      <form className="space-y-6" onSubmit={(e) => void onSave(e)}>
        <section className="rounded-lg border border-border bg-surface p-6 shadow-sm">
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="flex flex-col gap-1 text-sm font-medium text-foreground">
              Code
              <input
                className="rounded-md border border-border bg-background px-3 py-2 font-mono text-sm"
                value={item.code}
                readOnly
                disabled
              />
            </label>
            <label className="flex flex-col gap-1 text-sm font-medium text-foreground">
              Display name
              <input
                className="rounded-md border border-border bg-background px-3 py-2 text-sm"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                disabled={busy}
              />
            </label>
          </div>
        </section>

        <section className="rounded-lg border border-border bg-surface p-6 shadow-sm">
          <div className="text-xs font-medium uppercase text-muted">Privileges</div>
          {codes.length === 0 ? (
            <p className="mt-2 text-sm text-muted">No privilege catalog available.</p>
          ) : (
            <RoleTemplatePrivilegeGroupList groups={privilegeGroups} selected={selected} setSelected={setSelected} busy={busy} />
          )}
        </section>

        {msg ? (
          <p className={msg === "Saved." ? "text-sm text-foreground" : "text-sm text-destructive"} data-testid="platform-role-template-msg">
            {msg}
          </p>
        ) : null}

        <div className="flex flex-wrap gap-2">
          <button
            type="submit"
            disabled={busy || !dirty}
            className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
          >
            {busy ? "Saving…" : "Save"}
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

