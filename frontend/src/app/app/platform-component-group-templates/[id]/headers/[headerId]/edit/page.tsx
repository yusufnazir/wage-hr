"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  formCardClass,
  formFieldClass,
  formHelperClass,
  formInputClass,
  formLabelClass,
  formPrimaryButtonClass,
  formSelectClass,
  formTextareaClass,
} from "@/components/ui/formStyles";
import { showToast } from "@/components/ui/Toast";
import {
  deletePlatformComponentHeader,
  deletePlatformComponentItem,
  fetchPlatformComponentGroup,
  fetchPlatformComponentHeader,
  fetchPlatformComponentHeaders,
  fetchPlatformComponentItems,
  fetchPlatformWageComponentTemplates,
  postPlatformComponentItem,
  putPlatformComponentHeader,
  type PlatformComponentHeaderRow,
  type PlatformComponentItemRow,
  type PlatformWageComponentTemplateRow,
} from "@/lib/api";
import {
  buildTranslationsPayload,
  draftsFromTranslations,
  itemTranslationsFromCatalogName,
  localeSectionTitleKey,
  LOCALE_ORDER,
  primaryLocale,
  type LocaleDrafts,
  type LocaleKey,
} from "@/lib/platform-component-grouping-locale";
import { navLabel } from "@/messages/nav";

const TABS = ["basics", "i18n", "items"] as const;
type TabId = (typeof TABS)[number];

function parseTab(raw: string | null): TabId {
  if (raw === "i18n" || raw === "items") return raw;
  return "basics";
}

export default function PlatformComponentHeaderEditPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const sp = useSearchParams();
  const params = useParams();
  const groupId = String(params.id ?? "");
  const headerId = String(params.headerId ?? "");
  const tab = parseTab(sp.get("tab"));
  const pageBase = `/app/platform-component-group-templates/${groupId}/headers/${headerId}/edit`;
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<"loading" | "ready" | "error">("loading");
  const [groupName, setGroupName] = useState("");
  const [countryCode, setCountryCode] = useState("");
  const [header, setHeader] = useState<PlatformComponentHeaderRow | null>(null);
  const [localeDrafts, setLocaleDrafts] = useState<LocaleDrafts | null>(null);
  const [sortOrder, setSortOrder] = useState(0);
  const [groupHeaders, setGroupHeaders] = useState<PlatformComponentHeaderRow[]>([]);
  const [items, setItems] = useState<PlatformComponentItemRow[]>([]);
  const [templates, setTemplates] = useState<PlatformWageComponentTemplateRow[]>([]);
  const [addHeaderId, setAddHeaderId] = useState(headerId);
  const [busyBasics, setBusyBasics] = useState(false);
  const [busyI18n, setBusyI18n] = useState(false);
  const [busyItem, setBusyItem] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [addWageId, setAddWageId] = useState("");
  const [addSort, setAddSort] = useState(0);

  const reload = useCallback(async () => {
    const g = await fetchPlatformComponentGroup(groupId, { locale: me.locale });
    if (g.ok) {
      setGroupName(g.group.name);
      setCountryCode(g.group.countryCode);
    }
    const h = await fetchPlatformComponentHeader(groupId, headerId, { locale: me.locale });
    if (!h.ok) {
      setLoad("error");
      return;
    }
    setHeader(h.header);
    setLocaleDrafts(draftsFromTranslations(h.header.translations));
    setSortOrder(h.header.sortOrder);
    const hr = await fetchPlatformComponentHeaders(groupId, { page: 0, size: 200, locale: me.locale });
    if (hr.ok) setGroupHeaders(hr.items);
    const it = await fetchPlatformComponentItems(groupId, headerId, { page: 0, size: 100, locale: me.locale });
    if (it.ok) setItems(it.items);
    setLoad("ready");
  }, [groupId, headerId, me.locale]);

  useEffect(() => {
    if (!me.platformSuperadmin || !groupId || !headerId) return;
    void reload();
  }, [me.platformSuperadmin, groupId, headerId, reload]);

  useEffect(() => {
    setAddHeaderId(headerId);
  }, [headerId]);

  useEffect(() => {
    if (!countryCode || !me.platformSuperadmin) return;
    void (async () => {
      const c = await fetchPlatformWageComponentTemplates({ country: countryCode, active: true, page: 0, size: 500 });
      if (c.ok) {
        setTemplates(c.items);
        setAddWageId((prev) => prev || c.items[0]?.id || "");
      }
    })();
  }, [countryCode, me.platformSuperadmin]);

  function setDraftField(loc: LocaleKey, field: "name" | "description", value: string) {
    setLocaleDrafts((prev) => {
      if (!prev) return prev;
      return { ...prev, [loc]: { ...prev[loc], [field]: value } };
    });
  }

  async function persistHeader(drafts: LocaleDrafts, sort: number) {
    const translations = buildTranslationsPayload(drafts);
    await putPlatformComponentHeader(groupId, headerId, { sortOrder: sort, translations }, { locale: me.locale });
    await reload();
  }

  async function onSaveBasics(e: FormEvent) {
    e.preventDefault();
    if (!localeDrafts) return;
    setBusyBasics(true);
    setError(null);
    try {
      await persistHeader(localeDrafts, sortOrder);
      showToast(t("platformComponentGroups.msg.saved"), "success");
    } catch (err) {
      if (err instanceof Error && err.message === "NAME_REQUIRED") {
        setError(t("platformComponentGroups.msg.nameRequired"));
      } else {
        setError(t("platformComponentGroups.msg.saveFailed"));
      }
    } finally {
      setBusyBasics(false);
    }
  }

  async function onSaveI18n(e: FormEvent) {
    e.preventDefault();
    if (!localeDrafts) return;
    setBusyI18n(true);
    setError(null);
    try {
      await persistHeader(localeDrafts, sortOrder);
      showToast(t("platformComponentGroups.msg.saved"), "success");
    } catch (err) {
      if (err instanceof Error && err.message === "NAME_REQUIRED") {
        setError(t("platformComponentGroups.msg.nameRequired"));
      } else {
        setError(t("platformComponentGroups.msg.saveFailed"));
      }
    } finally {
      setBusyI18n(false);
    }
  }

  async function onAddItem(e: FormEvent) {
    e.preventDefault();
    if (!addWageId || !addHeaderId) return;
    const wage = templates.find((w) => w.id === addWageId);
    if (!wage) return;
    setBusyItem(true);
    setError(null);
    try {
      const translations = itemTranslationsFromCatalogName(wage.name);
      await postPlatformComponentItem(
        groupId,
        addHeaderId,
        { platformWageComponentTemplateId: addWageId, sortOrder: addSort, translations },
        { locale: me.locale },
      );
      setAddSort(0);
      showToast(t("platformComponentGroups.msg.saved"), "success");
      if (addHeaderId === headerId) {
        await reload();
      } else {
        const target = groupHeaders.find((h) => h.id === addHeaderId);
        setError(
          target
            ? `Item added under header "${target.name}". Select that header above to see it in the list.`
            : "Item added under the selected header.",
        );
      }
    } catch {
      setError("Could not add item (duplicate wage component on that header?).");
    } finally {
      setBusyItem(false);
    }
  }

  async function onDeleteHeader() {
    if (!window.confirm("Delete this header and all its items?")) return;
    try {
      await deletePlatformComponentHeader(groupId, headerId);
      router.push(`/app/platform-component-group-templates/${groupId}/edit?tab=headers`);
    } catch {
      setError(t("platformComponentGroups.msg.deleteFailed"));
    }
  }

  async function onDeleteItem(itemId: string) {
    if (!window.confirm("Delete this item?")) return;
    try {
      await deletePlatformComponentItem(groupId, headerId, itemId);
      await reload();
    } catch {
      setError("Item delete failed.");
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
      <div className="mx-auto max-w-2xl space-y-4">
        <p className="text-sm text-muted">Loading…</p>
      </div>
    );
  }

  if (load === "error" || !header || !localeDrafts) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <p className="text-sm text-destructive">Header not found.</p>
        <Link
          href={`/app/platform-component-group-templates/${groupId}/edit?tab=headers`}
          className="text-sm font-medium text-primary underline-offset-4 hover:underline"
        >
          ← {t("nav.platform_component_group_templates")}
        </Link>
      </div>
    );
  }

  const groupBackHref = `/app/platform-component-group-templates/${groupId}/edit?tab=headers`;

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold text-foreground">{header.name}</h1>
          <p className="mt-1 text-sm text-muted">
            {t("platformComponentHeaders.title.edit")}
            {groupName ? (
              <>
                {" "}
                · {t("platformComponentHeaders.label.componentGroup")}:{" "}
                <Link href={groupBackHref} className="font-medium text-primary underline-offset-4 hover:underline">
                  {groupName}
                </Link>
              </>
            ) : null}
            {countryCode ? (
              <>
                {" "}
                · {t("platformComponentGroups.label.countryReadonly")}: <span className="font-mono">{countryCode}</span>
              </>
            ) : null}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Link href={groupBackHref} className="text-sm font-medium text-primary underline-offset-4 hover:underline">
            {groupName ? `← ${groupName}` : t("platformComponentHeaders.action.backToGroup")}
          </Link>
          <button type="button" className="text-sm text-destructive underline-offset-4 hover:underline" onClick={() => void onDeleteHeader()}>
            {t("platformComponentHeaders.action.deleteHeader")}
          </button>
        </div>
      </div>

      <div className="flex flex-wrap gap-2 border-b border-border pb-2">
        {TABS.map((tid) => (
          <Link
            key={tid}
            href={tid === "basics" ? pageBase : `${pageBase}?tab=${tid}`}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${
              tab === tid ? "bg-surface text-foreground" : "text-muted hover:bg-surface"
            }`}
          >
            {tid === "items" ? t("platformComponentGroups.tab.items") : t(`platformComponentGroups.tab.${tid}`)}
          </Link>
        ))}
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      {tab === "basics" ? (
        <>
          <p className={formHelperClass}>{t("platformComponentHeaders.helper.basics")}</p>
          <form onSubmit={(e) => void onSaveBasics(e)} className={formCardClass}>
            <h2 className="text-base font-semibold text-foreground">{t("platformComponentGroups.section.basics")}</h2>
            <div className={formFieldClass}>
              <label className={formLabelClass} htmlFor="pch-basics-name">
                {t("platformComponentGroups.label.name")} ({primaryLocale(me.locale)})
              </label>
              <input
                id="pch-basics-name"
                className={formInputClass}
                value={localeDrafts[primaryLocale(me.locale)].name}
                onChange={(e) => setDraftField(primaryLocale(me.locale), "name", e.target.value)}
                maxLength={200}
                required
                aria-required
              />
            </div>
            <div className={formFieldClass}>
              <label className={formLabelClass} htmlFor="pch-basics-sort">
                {t("platformComponentGroups.label.sortOrder")}
              </label>
              <input
                id="pch-basics-sort"
                type="number"
                className={formInputClass}
                value={sortOrder}
                onChange={(e) => setSortOrder(Number.parseInt(e.target.value, 10) || 0)}
              />
            </div>
            <button type="submit" disabled={busyBasics} className={formPrimaryButtonClass}>
              {busyBasics ? "…" : t("platformComponentHeaders.action.saveBasics")}
            </button>
          </form>
        </>
      ) : null}

      {tab === "i18n" ? (
        <>
          <p className={formHelperClass}>{t("platformComponentGroups.helper.i18n")}</p>
          <form onSubmit={(e) => void onSaveI18n(e)} className={formCardClass}>
            <h2 className="text-base font-semibold text-foreground">{t("platformComponentGroups.section.i18n")}</h2>
            {LOCALE_ORDER.map((loc) => (
              <fieldset key={loc} className="flex flex-col gap-4 rounded-md border border-border/80 bg-background/50 p-4">
                <legend className="px-1 text-sm font-semibold text-foreground">{t(localeSectionTitleKey(loc))}</legend>
                <div className={formFieldClass}>
                  <label className={formLabelClass} htmlFor={`pch-name-${loc}`}>
                    {t("platformComponentGroups.label.name")}
                  </label>
                  <input
                    id={`pch-name-${loc}`}
                    className={formInputClass}
                    value={localeDrafts[loc].name}
                    onChange={(e) => setDraftField(loc, "name", e.target.value)}
                    maxLength={200}
                    required
                    aria-required
                  />
                </div>
                <div className={formFieldClass}>
                  <label className={formLabelClass} htmlFor={`pch-desc-${loc}`}>
                    {t("platformComponentGroups.label.description")}
                  </label>
                  <textarea
                    id={`pch-desc-${loc}`}
                    className={formTextareaClass}
                    value={localeDrafts[loc].description}
                    onChange={(e) => setDraftField(loc, "description", e.target.value)}
                    maxLength={500}
                    rows={3}
                  />
                </div>
              </fieldset>
            ))}
            <button type="submit" disabled={busyI18n} className={formPrimaryButtonClass}>
              {busyI18n ? "…" : t("platformComponentHeaders.action.saveTranslations")}
            </button>
          </form>
        </>
      ) : null}

      {tab === "items" ? (
        <section className="space-y-6">
          <h2 className="text-base font-semibold text-foreground">{t("platformComponentHeaders.section.items")}</h2>
          <p className="text-sm text-muted">
            {t("platformComponentHeaders.label.header")}: <span className="font-medium text-foreground">{header.name}</span>
          </p>
          <div className="overflow-x-auto rounded-lg border border-border">
            <table className="w-full text-sm">
              <thead className="bg-surface text-left text-xs font-medium text-muted">
                <tr>
                  <th className="px-3 py-2">{t("platformComponentGroups.col.wageCode")}</th>
                  <th className="px-3 py-2">{t("platformComponentGroups.label.name")}</th>
                  <th className="px-3 py-2">{t("platformComponentGroups.col.sort")}</th>
                  <th className="px-3 py-2 text-right">{t("platformWageComponentTemplates.col.actions")}</th>
                </tr>
              </thead>
              <tbody>
                {items.map((row) => (
                  <tr key={row.id} className="border-t border-border">
                    <td className="px-3 py-2 font-mono text-xs">
                      {row.wageComponentCode}
                      <div className="text-foreground">{row.wageComponentName}</div>
                    </td>
                    <td className="px-3 py-2">{row.name}</td>
                    <td className="px-3 py-2">{row.sortOrder}</td>
                    <td className="space-x-3 px-3 py-2 text-right">
                      <Link
                        href={`/app/platform-component-group-templates/${groupId}/headers/${headerId}/items/${row.id}/edit`}
                        className="text-primary underline-offset-4 hover:underline"
                      >
                        {t("platformWageComponentTemplates.action.edit")}
                      </Link>
                      <button type="button" className="text-destructive underline-offset-4 hover:underline" onClick={() => void onDeleteItem(row.id)}>
                        {t("platformWageComponentTemplates.action.delete")}
                      </button>
                    </td>
                  </tr>
                ))}
                {items.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-3 py-6 text-center text-muted">
                      {t("platformComponentHeaders.items.empty")}
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>

          <p className={formHelperClass}>{t("platformComponentHeaders.helper.addItem")}</p>
          <form onSubmit={(e) => void onAddItem(e)} className={`${formCardClass} max-w-lg border-dashed`}>
            <h3 className="text-base font-semibold text-foreground">{t("platformComponentHeaders.addItem.title")}</h3>
            <div className={formFieldClass}>
              <label className={formLabelClass} htmlFor="pch-add-header">
                {t("platformComponentHeaders.label.header")}
              </label>
              <select
                id="pch-add-header"
                className={formSelectClass}
                value={addHeaderId}
                onChange={(e) => setAddHeaderId(e.target.value)}
                required
              >
                {groupHeaders.map((h) => (
                  <option key={h.id} value={h.id}>
                    {h.name}
                  </option>
                ))}
              </select>
            </div>
            <div className={formFieldClass}>
              <label className={formLabelClass} htmlFor="pch-add-wage">
                {t("platformComponentGroups.col.wageCode")}
              </label>
              <select id="pch-add-wage" className={formSelectClass} value={addWageId} onChange={(e) => setAddWageId(e.target.value)} required>
                {templates
                  .filter(
                    (w) =>
                      addHeaderId !== headerId ||
                      !items.some((it) => it.platformWageComponentTemplateId === w.id),
                  )
                  .map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.templateCode} — {w.name}
                    </option>
                  ))}
              </select>
            </div>
            <div className={formFieldClass}>
              <label className={formLabelClass} htmlFor="pch-add-sort">
                {t("platformComponentGroups.label.sortOrder")}
              </label>
              <input
                id="pch-add-sort"
                type="number"
                className={formInputClass}
                value={addSort}
                onChange={(e) => setAddSort(Number.parseInt(e.target.value, 10) || 0)}
              />
            </div>
            <button type="submit" disabled={busyItem || !addWageId || !addHeaderId} className={formPrimaryButtonClass}>
              {busyItem ? "…" : t("platformComponentHeaders.action.addItem")}
            </button>
          </form>
        </section>
      ) : null}
    </div>
  );
}
