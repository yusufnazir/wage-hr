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
  formTextareaClass,
} from "@/components/ui/formStyles";
import { fetchPlatformComponentGroup, postPlatformComponentHeader } from "@/lib/api";
import {
  buildTranslationsPayload,
  coalesceCreateLocaleDrafts,
  emptyLocaleDrafts,
  localeSectionTitleKey,
  LOCALE_ORDER,
  primaryLocale,
  type LocaleDrafts,
  type LocaleKey,
} from "@/lib/platform-component-grouping-locale";
import { navLabel } from "@/messages/nav";

const TABS = ["basics", "i18n"] as const;
type TabId = (typeof TABS)[number];

function parseTab(raw: string | null): TabId {
  return raw === "i18n" ? "i18n" : "basics";
}

export default function PlatformComponentHeaderNewPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const sp = useSearchParams();
  const params = useParams();
  const groupId = String(params.id ?? "");
  const tab = parseTab(sp.get("tab"));
  const pageBase = `/app/platform-component-group-templates/${groupId}/headers/new`;
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [groupName, setGroupName] = useState("");
  const [countryCode, setCountryCode] = useState("");
  const [localeDrafts, setLocaleDrafts] = useState<LocaleDrafts>(emptyLocaleDrafts);
  const [sortOrder, setSortOrder] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!me.platformSuperadmin || !groupId) return;
    void (async () => {
      const g = await fetchPlatformComponentGroup(groupId, { locale: me.locale });
      if (g.ok) {
        setGroupName(g.group.name);
        setCountryCode(g.group.countryCode);
      }
    })();
  }, [groupId, me.locale, me.platformSuperadmin]);

  function setDraftField(loc: LocaleKey, field: "name" | "description", value: string) {
    setLocaleDrafts((prev) => ({ ...prev, [loc]: { ...prev[loc], [field]: value } }));
  }

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const translations = buildTranslationsPayload(coalesceCreateLocaleDrafts(localeDrafts));
      const row = await postPlatformComponentHeader(groupId, { sortOrder, translations }, { locale: me.locale });
      router.push(`/app/platform-component-group-templates/${groupId}/headers/${row.id}/edit?tab=basics`);
    } catch (err) {
      if (err instanceof Error && err.message === "NAME_REQUIRED") {
        setError(t("platformComponentHeaders.msg.nameRequired"));
      } else {
        setError(t("platformComponentGroups.msg.createFailed"));
      }
      setBusy(false);
    }
  }

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <p className="text-sm text-muted">Platform SuperAdmin access required.</p>
        <Link href={`/app/platform-component-group-templates/${groupId}/edit?tab=headers`} className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("platformComponentGroups.action.backToList")}
        </Link>
      </div>
    );
  }

  const cancelHref = `/app/platform-component-group-templates/${groupId}/edit?tab=headers`;

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold text-foreground">{t("platformComponentHeaders.title.new")}</h1>
          <p className="mt-1 text-sm text-muted">
            {groupName ? (
              <>
                {t("platformComponentHeaders.label.componentGroup")}:{" "}
                <Link href={cancelHref} className="font-medium text-primary underline-offset-4 hover:underline">
                  {groupName}
                </Link>
              </>
            ) : null}
            {groupName && countryCode ? " · " : null}
            {countryCode ? (
              <>
                {t("platformComponentGroups.label.countryReadonly")}: <span className="font-mono">{countryCode}</span>
              </>
            ) : null}
          </p>
        </div>
        <Link href={cancelHref} className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {groupName ? `← ${groupName}` : t("platformComponentHeaders.action.backToGroup")}
        </Link>
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
            {t(`platformComponentGroups.tab.${tid}`)}
          </Link>
        ))}
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      {tab === "basics" ? (
        <>
          <p className={formHelperClass}>{t("platformComponentHeaders.helper.create")}</p>
          <form onSubmit={(e) => void onCreate(e)} className={formCardClass}>
            <h2 className="text-base font-semibold text-foreground">{t("platformComponentGroups.section.basics")}</h2>
            <div className={formFieldClass}>
              <label className={formLabelClass} htmlFor="pch-new-name">
                {t("platformComponentGroups.label.name")} ({primaryLocale(me.locale)})
              </label>
              <input
                id="pch-new-name"
                className={formInputClass}
                value={localeDrafts[primaryLocale(me.locale)].name}
                onChange={(e) => setDraftField(primaryLocale(me.locale), "name", e.target.value)}
                maxLength={200}
                required
                aria-required
              />
            </div>
            <div className={formFieldClass}>
              <label className={formLabelClass} htmlFor="pch-new-sort">
                {t("platformComponentGroups.label.sortOrder")}
              </label>
              <input
                id="pch-new-sort"
                type="number"
                className={formInputClass}
                value={sortOrder}
                onChange={(e) => setSortOrder(Number.parseInt(e.target.value, 10) || 0)}
              />
            </div>
            <Link
              href={cancelHref}
              className="inline-flex w-full items-center justify-center rounded-md border border-border bg-background px-4 py-2 text-sm font-medium text-foreground shadow-sm hover:bg-muted/30"
            >
              {t("platformWageComponentTemplates.action.cancel")}
            </Link>
            <button type="submit" disabled={busy} className={formPrimaryButtonClass}>
              {busy ? "…" : t("platformComponentHeaders.action.create")}
            </button>
          </form>
        </>
      ) : null}

      {tab === "i18n" ? (
        <>
          <p className={formHelperClass}>{t("platformComponentGroups.helper.i18n")}</p>
          <form onSubmit={(e) => void onCreate(e)} className={formCardClass}>
            <h2 className="text-base font-semibold text-foreground">{t("platformComponentGroups.section.i18n")}</h2>
            {LOCALE_ORDER.map((loc) => (
              <fieldset key={loc} className="flex flex-col gap-4 rounded-md border border-border/80 bg-background/50 p-4">
                <legend className="px-1 text-sm font-semibold text-foreground">{t(localeSectionTitleKey(loc))}</legend>
                <div className={formFieldClass}>
                  <label className={formLabelClass} htmlFor={`pch-new-name-${loc}`}>
                    {t("platformComponentGroups.label.name")}
                  </label>
                  <input
                    id={`pch-new-name-${loc}`}
                    className={formInputClass}
                    value={localeDrafts[loc].name}
                    onChange={(e) => setDraftField(loc, "name", e.target.value)}
                    maxLength={200}
                    required
                    aria-required
                  />
                </div>
                <div className={formFieldClass}>
                  <label className={formLabelClass} htmlFor={`pch-new-desc-${loc}`}>
                    {t("platformComponentGroups.label.description")}
                  </label>
                  <textarea
                    id={`pch-new-desc-${loc}`}
                    className={formTextareaClass}
                    value={localeDrafts[loc].description}
                    onChange={(e) => setDraftField(loc, "description", e.target.value)}
                    maxLength={500}
                    rows={3}
                  />
                </div>
              </fieldset>
            ))}
            <Link
              href={cancelHref}
              className="inline-flex w-full items-center justify-center rounded-md border border-border bg-background px-4 py-2 text-sm font-medium text-foreground shadow-sm hover:bg-muted/30"
            >
              {t("platformWageComponentTemplates.action.cancel")}
            </Link>
            <button type="submit" disabled={busy} className={formPrimaryButtonClass}>
              {busy ? "…" : t("platformComponentHeaders.action.create")}
            </button>
          </form>
        </>
      ) : null}
    </div>
  );
}
