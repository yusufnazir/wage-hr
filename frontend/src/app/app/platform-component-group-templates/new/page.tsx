"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  formCardClass,
  formCheckboxRowClass,
  formFieldClass,
  formFieldHelperClass,
  formInputClass,
  formLabelClass,
  formPrimaryButtonClass,
  formSelectClass,
} from "@/components/ui/formStyles";
import { fetchPlatformCountries, postPlatformComponentGroup, type PlatformCountryRow } from "@/lib/api";
import { navLabel } from "@/messages/nav";

export default function PlatformComponentGroupNewPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [countries, setCountries] = useState<PlatformCountryRow[]>([]);
  const [platformCountryId, setPlatformCountryId] = useState("");
  const [defaultName, setDefaultName] = useState("");
  const [sortOrder, setSortOrder] = useState(0);
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!me.platformSuperadmin) return;
    void (async () => {
      const c = await fetchPlatformCountries({
        page: 0,
        size: 200,
        active: true,
        payrollEnabled: true,
        locale: me.locale,
      });
      if (c.ok) {
        setCountries(c.items);
        setPlatformCountryId((prev) => prev || c.items[0]?.id || "");
      }
    })();
  }, [me.locale, me.platformSuperadmin]);

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformComponentGroups.title.new")}</h1>
        <p className="text-sm text-muted">Platform SuperAdmin access required.</p>
        <Link href="/app/platform-component-group-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformComponentGroups.action.backToList")}
        </Link>
      </div>
    );
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const name = defaultName.trim();
    if (!name) {
      setError(t("platformComponentGroups.msg.nameRequired"));
      return;
    }
    setBusy(true);
    setError(null);
    const translations = [
      { locale: "en", name, description: null as string | null },
      { locale: "nl", name, description: null as string | null },
    ];
    try {
      const row = await postPlatformComponentGroup(
        {
          platformCountryId,
          sortOrder,
          active,
          translations,
        },
        { locale: me.locale },
      );
      router.push(`/app/platform-component-group-templates/${row.id}/edit`);
    } catch {
      setError(t("platformComponentGroups.msg.createFailed"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformComponentGroups.title.new")}</h1>
        <Link href="/app/platform-component-group-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformComponentGroups.action.backToList")}
        </Link>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <form onSubmit={(e) => void onSubmit(e)} className={formCardClass}>
        <div className={formFieldClass}>
          <label className={formLabelClass} htmlFor="pcg-new-country">
            {t("platformComponentGroups.label.country")}
          </label>
          <select
            id="pcg-new-country"
            className={formSelectClass}
            value={platformCountryId}
            onChange={(e) => setPlatformCountryId(e.target.value)}
            required
          >
            {countries.map((c) => (
              <option key={c.id} value={c.id}>
                {c.isoAlpha2} — {c.name}
              </option>
            ))}
          </select>
        </div>

        <div className={formFieldClass}>
          <label className={formLabelClass} htmlFor="pcg-new-default-name">
            {t("platformComponentGroups.label.defaultName")}
          </label>
          <input
            id="pcg-new-default-name"
            className={formInputClass}
            value={defaultName}
            onChange={(e) => setDefaultName(e.target.value)}
            maxLength={200}
            required
            autoComplete="off"
          />
          <p className={formFieldHelperClass}>{t("platformComponentGroups.helper.defaultName")}</p>
        </div>

        <div className={formFieldClass}>
          <label className={formLabelClass} htmlFor="pcg-new-sort">
            {t("platformComponentGroups.label.sortOrder")}
          </label>
          <input
            id="pcg-new-sort"
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

        <Link
          href="/app/platform-component-group-templates"
          className="inline-flex w-full items-center justify-center rounded-md border border-border bg-background px-4 py-2 text-sm font-medium text-foreground shadow-sm hover:bg-muted/30"
        >
          {t("platformWageComponentTemplates.action.cancel")}
        </Link>
        <button type="submit" disabled={busy} className={formPrimaryButtonClass}>
          {busy ? "…" : t("platformWageComponentTemplates.action.create")}
        </button>
      </form>
    </div>
  );
}
