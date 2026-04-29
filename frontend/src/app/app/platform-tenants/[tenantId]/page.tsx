"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { fetchPlatformTenant, patchPlatformTenantName, type PlatformTenantRow } from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error" | "notfound";

export default function PlatformTenantEditorPage() {
  const params = useParams();
  const router = useRouter();
  const tenantId = typeof params.tenantId === "string" ? params.tenantId : "";
  const { me } = useTenantAppSession();
  const [load, setLoad] = useState<LoadState>("loading");
  const [tenant, setTenant] = useState<PlatformTenantRow | null>(null);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const reload = useCallback(async () => {
    if (!tenantId) {
      setLoad("error");
      return;
    }
    setLoad("loading");
    setMsg(null);
    const r = await fetchPlatformTenant(tenantId);
    if (!r.ok) {
      setLoad(r.status === 403 ? "forbidden" : r.status === 404 ? "notfound" : "error");
      return;
    }
    setTenant(r.tenant);
    setName(r.tenant.name);
    setLoad("ready");
  }, [tenantId]);

  useEffect(() => {
    void reload();
  }, [reload]);

  async function onSave() {
    if (!tenantId) return;
    setBusy(true);
    setMsg(null);
    try {
      const updated = await patchPlatformTenantName(tenantId, name.trim());
      setTenant(updated);
      setMsg(navLabel(me.locale, "platformTenants.msg.saved"));
    } catch (e) {
      setMsg(e instanceof Error ? e.message : navLabel(me.locale, "platformTenants.msg.saveFailed"));
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
        <Link href="/app/platform-tenants" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {navLabel(me.locale, "platformTenants.action.backList")}
        </Link>
      </div>
    );
  }

  if (load === "notfound") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{navLabel(me.locale, "nav.platform_tenants")}</h1>
        <p className="text-sm text-muted">{navLabel(me.locale, "platformTenants.error.notFound")}</p>
        <Link href="/app/platform-tenants" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {navLabel(me.locale, "platformTenants.action.backList")}
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

  if (load === "loading" || !tenant) {
    return (
      <div className="mx-auto max-w-2xl">
        <p className="text-sm text-muted">{navLabel(me.locale, "platformTenants.state.loadingTenant")}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{navLabel(me.locale, "platformTenants.title.edit")}</h1>
        <button
          type="button"
          className="text-sm font-medium text-primary underline-offset-4 hover:underline"
          onClick={() => router.push("/app/platform-tenants")}
        >
          ← {navLabel(me.locale, "platformTenants.action.backList")}
        </button>
      </div>

      <p className="text-sm text-muted">{navLabel(me.locale, "platformTenants.helper.editIntro")}</p>

      {msg ? (
        <p
          className={`text-sm ${msg === navLabel(me.locale, "platformTenants.msg.saved") ? "text-foreground" : "text-red-600 dark:text-red-400"}`}
          data-testid="platform-tenant-editor-msg"
        >
          {msg}
        </p>
      ) : null}

      <section className="flex max-w-lg flex-col gap-4 rounded-lg border border-border bg-surface p-6 shadow-sm">
        <div className="flex w-full min-w-0 flex-col gap-1.5">
          <span className="text-xs font-medium text-muted">{navLabel(me.locale, "platformTenants.label.handle")}</span>
          <input
            readOnly
            aria-readonly="true"
            className="w-full min-w-0 rounded-md border border-border bg-muted/20 px-3 py-2 font-mono text-sm text-foreground"
            value={tenant.handle}
          />
        </div>
        <div className="flex w-full min-w-0 flex-col gap-1.5">
          <label htmlFor="platform-tenant-editor-name" className="text-xs font-medium text-muted">
            {navLabel(me.locale, "platformTenants.label.name")}
          </label>
          <input
            id="platform-tenant-editor-name"
            className="w-full min-w-0 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground shadow-sm"
            value={name}
            onChange={(e) => setName(e.target.value)}
            data-testid="platform-tenant-editor-name"
          />
        </div>
        <button
          type="button"
          disabled={busy || !name.trim()}
          className="inline-flex items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
          data-testid="platform-tenant-editor-save"
          onClick={() => void onSave()}
        >
          {busy ? navLabel(me.locale, "platformTenants.state.saving") : navLabel(me.locale, "platformTenants.action.save")}
        </button>
      </section>
    </div>
  );
}
