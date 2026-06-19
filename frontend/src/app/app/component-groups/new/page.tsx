"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  formCardClass,
  formCheckboxRowClass,
  formFieldClass,
  formInputClass,
  formLabelClass,
  formPrimaryButtonClass,
} from "@/components/ui/formStyles";
import { postTenantComponentGroup } from "@/lib/api";
import { navLabel } from "@/messages/nav";

export default function TenantComponentGroupNewPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const sp = useSearchParams();
  const companyId = (sp.get("companyId") ?? "").trim();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [defaultName, setDefaultName] = useState("");
  const [templateId, setTemplateId] = useState("");
  const [sortOrder, setSortOrder] = useState(0);
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canManage = me.privileges.includes("WAGE_COMPONENT_MANAGE");

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const name = defaultName.trim();
    if (!companyId) {
      setError("Select a company from the list page first.");
      return;
    }
    if (!name) {
      setError("Name is required.");
      return;
    }
    setBusy(true);
    setError(null);
    const translations = [
      { locale: "en", name, description: null as string | null },
      { locale: "nl", name, description: null as string | null },
    ];
    try {
      const tid = templateId.trim();
      const row = await postTenantComponentGroup(
        {
          companyId,
          platformComponentGroupTemplateId: tid ? tid : null,
          sortOrder,
          active,
          translations,
        },
        { locale: me.locale },
      );
      router.push(`/app/component-groups/${row.id}/edit?companyId=${encodeURIComponent(companyId)}`);
    } catch {
      setError("Could not create component group.");
    } finally {
      setBusy(false);
    }
  }

  if (!canManage) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("nav.component_groups")}</h1>
        <p className="text-sm text-muted">You need wage component manage permission.</p>
        <Link href="/app/component-groups" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          Back to list
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-xl space-y-6 px-4 py-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">New {t("nav.component_groups").toLowerCase()}</h1>
        <Link href="/app/component-groups" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          Back to list
        </Link>
      </div>

      {!companyId ? <p className="text-sm text-destructive">Missing companyId. Open this screen from the list page.</p> : null}

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <form onSubmit={(e) => void onSubmit(e)} className={formCardClass}>
        <div className={formFieldClass}>
          <label className={formLabelClass} htmlFor="tcg-template">
            Platform group template id (optional)
          </label>
          <input
            id="tcg-template"
            className={formInputClass}
            value={templateId}
            onChange={(e) => setTemplateId(e.target.value)}
            placeholder="UUID of platform component group template"
          />
        </div>
        <div className={formFieldClass}>
          <label className={formLabelClass} htmlFor="tcg-name">
            Default name (EN + NL)
          </label>
          <input
            id="tcg-name"
            className={formInputClass}
            value={defaultName}
            onChange={(e) => setDefaultName(e.target.value)}
          />
        </div>
        <div className={formFieldClass}>
          <label className={formLabelClass} htmlFor="tcg-sort">
            Sort order
          </label>
          <input
            id="tcg-sort"
            type="number"
            className={formInputClass}
            value={sortOrder}
            onChange={(e) => setSortOrder(Number.parseInt(e.target.value, 10) || 0)}
          />
        </div>
        <div className={formFieldClass}>
          <label className={formCheckboxRowClass}>
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            Active
          </label>
        </div>
        <button type="submit" disabled={busy || !companyId} className={formPrimaryButtonClass}>
          {busy ? "Saving…" : "Create"}
        </button>
      </form>
    </div>
  );
}
