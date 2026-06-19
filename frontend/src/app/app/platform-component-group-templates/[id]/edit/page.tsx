"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  formCardClass,
  formCheckboxRowClass,
  formFieldClass,
  formHelperClass,
  formInputClass,
  formInputReadOnlyClass,
  formLabelClass,
  formPrimaryButtonClass,
  formTextareaClass,
} from "@/components/ui/formStyles";
import { showToast } from "@/components/ui/Toast";
import {
  deletePlatformComponentGroup,
  fetchPlatformComponentGroup,
  fetchPlatformComponentHeaders,
  fetchPlatformComponentItems,
  putPlatformComponentGroup,
  type PlatformComponentGroupRow,
  type PlatformComponentHeaderRow,
  type PlatformComponentItemRow,
  type PlatformComponentTranslation,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "error";

const TABS = ["basics", "i18n", "headers", "items"] as const;
type TabId = (typeof TABS)[number];

// Allowed write locales — keep in sync with backend ComponentGroupingValidation when adding languages.
const LOCALE_ORDER = ["en", "nl"] as const;
type LocaleKey = (typeof LOCALE_ORDER)[number];

function parseTab(raw: string | null): TabId {
  if (raw === "i18n" || raw === "headers" || raw === "items" || raw === "basics") return raw;
  return "basics";
}

function draftsFromTranslations(tr: PlatformComponentTranslation[]): Record<LocaleKey, { name: string; description: string }> {
  const map = new Map(tr.map((x) => [x.locale.toLowerCase(), x]));
  const out = {} as Record<LocaleKey, { name: string; description: string }>;
  for (const loc of LOCALE_ORDER) {
    const row = map.get(loc);
    out[loc] = { name: row?.name ?? "", description: row?.description ?? "" };
  }
  return out;
}

function localeSectionTitleKey(loc: LocaleKey): string {
  if (loc === "en") return "platformComponentGroups.locale.en";
  return "platformComponentGroups.locale.nl";
}

function primaryLocale(uiLocale: string): LocaleKey {
  const v = uiLocale.trim().toLowerCase().replace("_", "-");
  return v === "nl" ? "nl" : "en";
}

function buildTranslationsPayload(drafts: Record<LocaleKey, { name: string; description: string }>): PlatformComponentTranslation[] {
  const en = drafts.en.name.trim();
  const nl = drafts.nl.name.trim();
  if (!en || !nl) {
    throw new Error("NAME_REQUIRED");
  }
  return [
    { locale: "en", name: en, description: drafts.en.description.trim() || null },
    { locale: "nl", name: nl, description: drafts.nl.description.trim() || null },
  ];
}

export default function PlatformComponentGroupEditPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const sp = useSearchParams();
  const params = useParams();
  const id = String(params.id ?? "");
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);
  const tab = parseTab(sp.get("tab"));
  const editBase = `/app/platform-component-group-templates/${id}/edit`;

  const [load, setLoad] = useState<LoadState>("loading");
  const [group, setGroup] = useState<PlatformComponentGroupRow | null>(null);
  const [headers, setHeaders] = useState<PlatformComponentHeaderRow[]>([]);
  const [localeDrafts, setLocaleDrafts] = useState<Record<LocaleKey, { name: string; description: string }> | null>(null);
  const [sortOrder, setSortOrder] = useState(0);
  const [active, setActive] = useState(true);
  const [busyBasics, setBusyBasics] = useState(false);
  const [busyI18n, setBusyI18n] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [flatItems, setFlatItems] = useState<{ headerId: string; headerName: string; row: PlatformComponentItemRow }[]>([]);
  const [itemsLoad, setItemsLoad] = useState<"idle" | "loading" | "ready">("idle");

  const reload = useCallback(async () => {
    const g = await fetchPlatformComponentGroup(id, { locale: me.locale });
    if (!g.ok) {
      setLoad("error");
      return;
    }
    setGroup(g.group);
    setLocaleDrafts(draftsFromTranslations(g.group.translations));
    setSortOrder(g.group.sortOrder);
    setActive(g.group.active);
    const h = await fetchPlatformComponentHeaders(id, { page: 0, size: 100, locale: me.locale });
    if (h.ok) setHeaders(h.items);
    setLoad("ready");
  }, [id, me.locale]);

  const loadFlatItems = useCallback(async () => {
    setItemsLoad("loading");
    const hRes = await fetchPlatformComponentHeaders(id, { page: 0, size: 200, locale: me.locale });
    if (!hRes.ok) {
      setFlatItems([]);
      setItemsLoad("ready");
      return;
    }
    const acc: { headerId: string; headerName: string; row: PlatformComponentItemRow }[] = [];
    for (const h of hRes.items) {
      const ir = await fetchPlatformComponentItems(id, h.id, { page: 0, size: 200, locale: me.locale });
      if (ir.ok) {
        for (const row of ir.items) {
          acc.push({ headerId: h.id, headerName: h.name, row });
        }
      }
    }
    setFlatItems(acc);
    setItemsLoad("ready");
  }, [id, me.locale]);

  useEffect(() => {
    if (!me.platformSuperadmin || !id) return;
    void reload();
  }, [me.platformSuperadmin, id, reload]);

  useEffect(() => {
    if (tab !== "items" || load !== "ready") return;
    void loadFlatItems();
  }, [tab, load, loadFlatItems]);

  async function persistGroup(nextSort: number, nextActive: boolean, drafts: Record<LocaleKey, { name: string; description: string }>) {
    const translations = buildTranslationsPayload(drafts);
    await putPlatformComponentGroup(id, { sortOrder: nextSort, active: nextActive, translations }, { locale: me.locale });
    await reload();
  }

  async function onSaveBasics(e: FormEvent) {
    e.preventDefault();
    if (!localeDrafts) return;
    setBusyBasics(true);
    setError(null);
    try {
      await persistGroup(sortOrder, active, localeDrafts);
      showToast(t("platformComponentGroups.msg.saved"), "success");
    } catch (err) {
      if (err instanceof Error && err.message === "NAME_REQUIRED") {
        setError(t("platformComponentGroups.msg.saveFailed"));
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
      await persistGroup(sortOrder, active, localeDrafts);
      showToast(t("platformComponentGroups.msg.saved"), "success");
    } catch {
      setError(t("platformComponentGroups.msg.saveFailed"));
    } finally {
      setBusyI18n(false);
    }
  }

  async function onDeleteGroup() {
    if (!window.confirm(t("platformComponentGroups.confirm.deleteGroup"))) return;
    try {
      await deletePlatformComponentGroup(id);
      router.push("/app/platform-component-group-templates");
    } catch {
      setError(t("platformComponentGroups.msg.deleteFailed"));
    }
  }

  function setDraftField(loc: LocaleKey, field: "name" | "description", value: string) {
    setLocaleDrafts((prev) => {
      if (!prev) return prev;
      return { ...prev, [loc]: { ...prev[loc], [field]: value } };
    });
  }

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <p className="text-sm text-muted">Platform SuperAdmin access required.</p>
        <Link href="/app/platform-component-group-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformComponentGroups.action.backToList")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-5xl space-y-4">
        <p className="text-sm text-muted">Loading…</p>
      </div>
    );
  }

  if (load === "error" || !group || !localeDrafts) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <p className="text-sm text-destructive">Group not found.</p>
        <Link href="/app/platform-component-group-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformComponentGroups.action.backToList")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold text-foreground">{group.name}</h1>
          <p className="mt-1 text-sm text-muted">
            {t("platformComponentGroups.title.edit")} · {t("platformComponentGroups.label.countryReadonly")}:{" "}
            <span className="font-mono">{group.countryCode}</span>
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Link href="/app/platform-component-group-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
            {t("platformComponentGroups.action.backToList")}
          </Link>
          <button type="button" className="text-sm text-destructive underline-offset-4 hover:underline" onClick={() => void onDeleteGroup()}>
            {t("platformComponentGroups.action.deleteGroup")}
          </button>
        </div>
      </div>

      <div className="flex flex-wrap gap-2 border-b border-border pb-2">
        {TABS.map((tid) => (
          <Link
            key={tid}
            href={tid === "basics" ? editBase : `${editBase}?tab=${tid}`}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${
              tab === tid ? "bg-surface text-foreground" : "text-muted hover:bg-surface"
            }`}
          >
            {t(`platformComponentGroups.tab.${tid}`)}
          </Link>
        ))}
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      {tab === "basics" ? (
        <>
          <p className={formHelperClass}>{t("platformComponentGroups.helper.basics")}</p>
          <form onSubmit={(e) => void onSaveBasics(e)} className={formCardClass}>
            <h2 className="text-base font-semibold text-foreground">{t("platformComponentGroups.section.basics")}</h2>
            <div className={formFieldClass}>
              <label className={formLabelClass} htmlFor="pcg-basics-name">
                {t("platformComponentGroups.label.name")} ({primaryLocale(me.locale)})
              </label>
              <input
                id="pcg-basics-name"
                className={formInputClass}
                value={localeDrafts[primaryLocale(me.locale)].name}
                onChange={(e) => setDraftField(primaryLocale(me.locale), "name", e.target.value)}
                maxLength={200}
                required
                aria-required
              />
            </div>
            <div className={formFieldClass}>
              <span className={formLabelClass}>{t("platformComponentGroups.label.countryReadonly")}</span>
              <input readOnly aria-readonly="true" className={formInputReadOnlyClass} value={group.countryCode} />
            </div>
            <div className={formFieldClass}>
              <label className={formLabelClass} htmlFor="pcg-sort">
                {t("platformComponentGroups.label.sortOrder")}
              </label>
              <input
                id="pcg-sort"
                type="number"
                className={formInputClass}
                value={sortOrder}
                onChange={(e) => setSortOrder(Number.parseInt(e.target.value, 10) || 0)}
              />
            </div>
            <div className={formFieldClass}>
              <label className={formCheckboxRowClass}>
                <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
                {t("platformComponentGroups.label.active")}
              </label>
            </div>
            <button type="submit" disabled={busyBasics} className={formPrimaryButtonClass}>
              {busyBasics ? "…" : t("platformComponentGroups.action.saveBasics")}
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
            <fieldset key={loc} className="space-y-3 rounded-md border border-border/80 bg-background/50 p-4">
              <legend className="px-1 text-sm font-semibold text-foreground">{t(localeSectionTitleKey(loc))}</legend>
              <div className={formFieldClass}>
                <label className={formLabelClass} htmlFor={`pcg-name-${loc}`}>
                  {t("platformComponentGroups.label.name")}
                </label>
                <input
                  id={`pcg-name-${loc}`}
                  className={formInputClass}
                  value={localeDrafts[loc].name}
                  onChange={(e) => setDraftField(loc, "name", e.target.value)}
                  maxLength={200}
                  required
                  aria-required
                />
              </div>
              <div className={formFieldClass}>
                <label className={formLabelClass} htmlFor={`pcg-desc-${loc}`}>
                  {t("platformComponentGroups.label.description")}
                </label>
                <textarea
                  id={`pcg-desc-${loc}`}
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
              {busyI18n ? "…" : t("platformComponentGroups.action.saveTranslations")}
            </button>
          </form>
        </>
      ) : null}

      {tab === "headers" ? (
        <section className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-semibold text-foreground">{t("platformComponentGroups.section.headers")}</h2>
            <Link
              href={`/app/platform-component-group-templates/${id}/headers/new`}
              className="rounded bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground hover:opacity-90"
            >
              {t("platformComponentGroups.action.addHeader")}
            </Link>
          </div>
          <div className="overflow-x-auto rounded-md border border-border">
            <table className="w-full text-sm">
              <thead className="bg-surface text-left text-xs font-medium uppercase text-muted">
                <tr>
                  <th className="px-3 py-2">{t("platformComponentGroups.label.name")}</th>
                  <th className="px-3 py-2">{t("platformComponentGroups.col.sort")}</th>
                  <th className="px-3 py-2 text-right">{t("platformWageComponentTemplates.col.actions")}</th>
                </tr>
              </thead>
              <tbody>
                {headers.map((h) => (
                  <tr key={h.id} className="border-t border-border">
                    <td className="px-3 py-2 font-medium text-foreground">{h.name}</td>
                    <td className="px-3 py-2">{h.sortOrder}</td>
                    <td className="px-3 py-2 text-right">
                      <Link
                        href={`/app/platform-component-group-templates/${id}/headers/${h.id}/edit`}
                        className="text-primary underline-offset-4 hover:underline"
                      >
                        {t("platformWageComponentTemplates.action.edit")}
                      </Link>
                    </td>
                  </tr>
                ))}
                {headers.length === 0 ? (
                  <tr>
                    <td colSpan={3} className="px-3 py-6 text-center text-muted">
                      {t("platformComponentGroups.headers.empty")}
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}

      {tab === "items" ? (
        <section className="space-y-3">
          <h2 className="text-base font-semibold text-foreground">{t("platformComponentGroups.section.items")}</h2>
          <p className="text-sm text-muted">{t("platformComponentGroups.items.helper")}</p>
          {itemsLoad === "loading" ? <p className="text-sm text-muted">{t("platformComponentGroups.items.loading")}</p> : null}
          {itemsLoad === "ready" && flatItems.length === 0 ? (
            <p className="text-sm text-muted">{t("platformComponentGroups.items.empty")}</p>
          ) : null}
          {itemsLoad === "ready" && flatItems.length > 0 ? (
            <div className="overflow-x-auto rounded-md border border-border">
              <table className="w-full text-sm">
                <thead className="bg-surface text-left text-xs font-medium uppercase text-muted">
                  <tr>
                    <th className="px-3 py-2">{t("platformComponentGroups.col.header")}</th>
                    <th className="px-3 py-2">{t("platformComponentGroups.col.item")}</th>
                    <th className="px-3 py-2">{t("platformComponentGroups.col.wageCode")}</th>
                    <th className="px-3 py-2">{t("platformComponentGroups.col.sort")}</th>
                    <th className="px-3 py-2 text-right">{t("platformWageComponentTemplates.col.actions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {flatItems.map(({ headerId, headerName, row }) => (
                    <tr key={row.id} className="border-t border-border">
                      <td className="px-3 py-2 text-foreground">{headerName}</td>
                      <td className="px-3 py-2 font-medium text-foreground">{row.name}</td>
                      <td className="px-3 py-2 font-mono text-xs">{row.wageComponentCode}</td>
                      <td className="px-3 py-2">{row.sortOrder}</td>
                      <td className="px-3 py-2 text-right">
                        <Link
                          href={`/app/platform-component-group-templates/${id}/headers/${headerId}/items/${row.id}/edit`}
                          className="text-primary underline-offset-4 hover:underline"
                        >
                          {t("platformWageComponentTemplates.action.edit")}
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}
