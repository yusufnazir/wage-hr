"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { fetchPlatformCountries, postPlatformLedgerTemplate, type PlatformCountryRow } from "@/lib/api";
import { navLabel } from "@/messages/nav";

export default function PlatformLedgerTemplateNewPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [countries, setCountries] = useState<PlatformCountryRow[]>([]);
  const [countryCode, setCountryCode] = useState("");
  const [code, setCode] = useState("");
  const [descriptionEn, setDescriptionEn] = useState("");
  const [descriptionNl, setDescriptionNl] = useState("");
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
        setCountryCode((prev) => prev || (c.items[0]?.isoAlpha2 ?? ""));
      }
    })();
  }, [me.locale, me.platformSuperadmin]);

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformLedgerTemplates.title.new")}</h1>
        <p className="text-sm text-muted">{t("platformLedgerTemplates.error.notOperator")}</p>
        <Link href="/app/platform-ledger-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformLedgerTemplates.action.backToList")}
        </Link>
      </div>
    );
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await postPlatformLedgerTemplate(
        {
          countryCode: countryCode.trim().toUpperCase(),
          code: code.trim(),
          translations: [
            { locale: "en", description: descriptionEn.trim() },
            { locale: "nl", description: descriptionNl.trim() },
          ],
          active,
        },
        { locale: me.locale },
      );
      router.push("/app/platform-ledger-templates");
    } catch {
      setError(t("platformLedgerTemplates.msg.createFailed"));
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformLedgerTemplates.title.new")}</h1>
        <Link href="/app/platform-ledger-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformLedgerTemplates.action.backToList")}
        </Link>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5 shadow-sm">
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformLedgerTemplates.label.country")}</label>
          <select
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={countryCode}
            onChange={(e) => setCountryCode(e.target.value)}
            required
          >
            {countries.map((c) => (
              <option key={c.id} value={c.isoAlpha2}>
                {c.isoAlpha2} — {c.name}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformLedgerTemplates.label.code")}</label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            maxLength={64}
            required
          />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformLedgerTemplates.label.descriptionEn")}</label>
          <textarea
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={descriptionEn}
            onChange={(e) => setDescriptionEn(e.target.value)}
            maxLength={500}
            rows={3}
            required
          />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformLedgerTemplates.label.descriptionNl")}</label>
          <textarea
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={descriptionNl}
            onChange={(e) => setDescriptionNl(e.target.value)}
            maxLength={500}
            rows={3}
            required
          />
        </div>
        <div className="flex items-end gap-2 pb-1">
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            {t("platformLedgerTemplates.label.active")}
          </label>
        </div>
        <button
          type="submit"
          disabled={busy}
          className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
        >
          {t("platformLedgerTemplates.action.create")}
        </button>
      </form>
    </div>
  );
}
