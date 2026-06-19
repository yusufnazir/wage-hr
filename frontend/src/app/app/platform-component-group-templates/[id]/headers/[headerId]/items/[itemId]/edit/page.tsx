"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  formCardClass,
  formFieldClass,
  formHelperClass,
  formInputClass,
  formInputReadOnlyClass,
  formLabelClass,
  formPrimaryButtonClass,
  formSelectClass,
} from "@/components/ui/formStyles";
import { showToast } from "@/components/ui/Toast";
import {
  fetchPlatformComponentGroup,
  fetchPlatformComponentHeader,
  fetchPlatformComponentItem,
  fetchPlatformWageComponentTemplates,
  putPlatformComponentItem,
  type PlatformComponentItemRow,
  type PlatformWageComponentTemplateRow,
} from "@/lib/api";
import { itemTranslationsFromCatalogName } from "@/lib/platform-component-grouping-locale";
import { navLabel } from "@/messages/nav";

export default function PlatformComponentItemEditPage() {
  const { me } = useTenantAppSession();
  const params = useParams();
  const groupId = String(params.id ?? "");
  const headerId = String(params.headerId ?? "");
  const itemId = String(params.itemId ?? "");
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<"loading" | "ready" | "error">("loading");
  const [groupName, setGroupName] = useState("");
  const [headerName, setHeaderName] = useState("");
  const [countryCode, setCountryCode] = useState("");
  const [item, setItem] = useState<PlatformComponentItemRow | null>(null);
  const [templates, setTemplates] = useState<PlatformWageComponentTemplateRow[]>([]);
  const [wageId, setWageId] = useState("");
  const [sortOrder, setSortOrder] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    const g = await fetchPlatformComponentGroup(groupId, { locale: me.locale });
    if (g.ok) {
      setGroupName(g.group.name);
      setCountryCode(g.group.countryCode);
    }
    const h = await fetchPlatformComponentHeader(groupId, headerId, { locale: me.locale });
    if (h.ok) setHeaderName(h.header.name);
    const it = await fetchPlatformComponentItem(groupId, headerId, itemId, { locale: me.locale });
    if (!it.ok) {
      setLoad("error");
      return;
    }
    setItem(it.item);
    setWageId(it.item.platformWageComponentTemplateId);
    setSortOrder(it.item.sortOrder);
    setLoad("ready");
  }, [groupId, headerId, itemId, me.locale]);

  useEffect(() => {
    if (!me.platformSuperadmin || !groupId || !headerId || !itemId) return;
    void reload();
  }, [me.platformSuperadmin, groupId, headerId, itemId, reload]);

  useEffect(() => {
    if (!countryCode || !me.platformSuperadmin) return;
    void (async () => {
      const c = await fetchPlatformWageComponentTemplates({ country: countryCode, active: true, page: 0, size: 500 });
      if (c.ok) setTemplates(c.items);
    })();
  }, [countryCode, me.platformSuperadmin]);

  async function onSave(e: FormEvent) {
    e.preventDefault();
    const wage = templates.find((w) => w.id === wageId);
    if (!wage) return;
    setBusy(true);
    setError(null);
    try {
      const translations = itemTranslationsFromCatalogName(wage.name);
      await putPlatformComponentItem(
        groupId,
        headerId,
        itemId,
        { platformWageComponentTemplateId: wageId, sortOrder, translations },
        { locale: me.locale },
      );
      showToast(t("platformComponentGroups.msg.saved"), "success");
      await reload();
    } catch {
      setError(t("platformComponentGroups.msg.saveFailed"));
    } finally {
      setBusy(false);
    }
  }

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <p className="text-sm text-muted">Platform SuperAdmin access required.</p>
        <Link href="/app/platform-component-group-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("platformComponentGroups.action.backToList")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-2xl">
        <p className="text-sm text-muted">Loading…</p>
      </div>
    );
  }

  if (load === "error" || !item) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <p className="text-sm text-destructive">Item not found.</p>
        <Link
          href={`/app/platform-component-group-templates/${groupId}/headers/${headerId}/edit?tab=items`}
          className="text-sm font-medium text-primary underline-offset-4 hover:underline"
        >
          ← Header
        </Link>
      </div>
    );
  }

  const headerBackHref = `/app/platform-component-group-templates/${groupId}/headers/${headerId}/edit?tab=items`;
  const selectedWage = templates.find((w) => w.id === wageId);

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold text-foreground">{item.name}</h1>
          <p className="mt-1 text-sm text-muted">
            {t("platformWageComponentTemplates.action.edit")} · {item.wageComponentCode}
            {groupName ? (
              <>
                {" "}
                · {t("platformComponentHeaders.label.componentGroup")}:{" "}
                <Link
                  href={`/app/platform-component-group-templates/${groupId}/edit?tab=headers`}
                  className="font-medium text-primary underline-offset-4 hover:underline"
                >
                  {groupName}
                </Link>
              </>
            ) : null}
            {headerName ? (
              <>
                {" "}
                · {t("platformComponentHeaders.label.header")}:{" "}
                <Link href={headerBackHref} className="font-medium text-primary underline-offset-4 hover:underline">
                  {headerName}
                </Link>
              </>
            ) : null}
          </p>
        </div>
        <Link href={headerBackHref} className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {headerName ? `← ${headerName}` : "← Header"}
        </Link>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <p className={formHelperClass}>{t("platformComponentHeaders.helper.itemEdit")}</p>
      <form onSubmit={(e) => void onSave(e)} className={formCardClass}>
        <h2 className="text-base font-semibold text-foreground">{t("platformComponentGroups.section.basics")}</h2>
        <div className={formFieldClass}>
          <span className={formLabelClass}>{t("platformComponentHeaders.label.header")}</span>
          <input readOnly aria-readonly="true" className={formInputReadOnlyClass} value={headerName} />
        </div>
        <div className={formFieldClass}>
          <label className={formLabelClass} htmlFor="pci-wage">
            {t("platformComponentGroups.col.wageCode")}
          </label>
          <select id="pci-wage" className={formSelectClass} value={wageId} onChange={(e) => setWageId(e.target.value)} required>
            {templates.map((w) => (
              <option key={w.id} value={w.id}>
                {w.templateCode} — {w.name}
              </option>
            ))}
          </select>
        </div>
        {selectedWage ? (
          <p className="text-xs text-muted">
            {t("platformComponentGroups.label.name")}: {selectedWage.name}
          </p>
        ) : null}
        <div className={formFieldClass}>
          <label className={formLabelClass} htmlFor="pci-sort">
            {t("platformComponentGroups.label.sortOrder")}
          </label>
          <input
            id="pci-sort"
            type="number"
            className={formInputClass}
            value={sortOrder}
            onChange={(e) => setSortOrder(Number.parseInt(e.target.value, 10) || 0)}
          />
        </div>
        <button type="submit" disabled={busy || !wageId} className={formPrimaryButtonClass}>
          {busy ? "…" : t("platformWageComponentTemplates.action.saveForm")}
        </button>
      </form>
    </div>
  );
}
