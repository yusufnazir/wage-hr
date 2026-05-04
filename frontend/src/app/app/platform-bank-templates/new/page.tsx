"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { fetchPlatformCountries, postPlatformBankTemplate, type PlatformCountryRow } from "@/lib/api";
import { navLabel } from "@/messages/nav";

export default function PlatformBankTemplateNewPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [countries, setCountries] = useState<PlatformCountryRow[]>([]);
  const [countryCode, setCountryCode] = useState("");
  const [name, setName] = useState("");
  const [bankName, setBankName] = useState("");
  const [swiftBic, setSwiftBic] = useState("");
  const [bankCode, setBankCode] = useState("");
  const [accountNumberFormat, setAccountNumberFormat] = useState("");
  const [currencyCode, setCurrencyCode] = useState("");
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
        <h1 className="text-lg font-semibold text-foreground">{t("platformBankTemplates.title.new")}</h1>
        <p className="text-sm text-muted">{t("platformBankTemplates.error.notOperator")}</p>
        <Link href="/app/platform-bank-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformBankTemplates.action.backToList")}
        </Link>
      </div>
    );
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await postPlatformBankTemplate({
        countryCode: countryCode.trim().toUpperCase(),
        name: name.trim(),
        bankName: bankName.trim() || null,
        swiftBic: swiftBic.trim() || null,
        bankCode: bankCode.trim() || null,
        accountNumberFormat: accountNumberFormat.trim() || null,
        currencyCode: currencyCode.trim() ? currencyCode.trim().toUpperCase() : null,
        active,
      });
      router.push("/app/platform-bank-templates");
    } catch {
      setError(t("platformBankTemplates.msg.createFailed"));
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformBankTemplates.title.new")}</h1>
        <Link href="/app/platform-bank-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformBankTemplates.action.backToList")}
        </Link>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5 shadow-sm">
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformBankTemplates.label.country")}</label>
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
          <label className="text-xs font-medium uppercase text-muted">{t("platformBankTemplates.label.name")}</label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={150}
            required
          />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformBankTemplates.label.bankName")}</label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={bankName}
            onChange={(e) => setBankName(e.target.value)}
            maxLength={150}
          />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformBankTemplates.label.swiftBic")}</label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono uppercase"
              value={swiftBic}
              onChange={(e) => setSwiftBic(e.target.value.toUpperCase())}
              maxLength={11}
            />
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformBankTemplates.label.bankCode")}</label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
              value={bankCode}
              onChange={(e) => setBankCode(e.target.value)}
              maxLength={30}
            />
          </div>
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">
            {t("platformBankTemplates.label.accountNumberFormat")}
          </label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono"
            value={accountNumberFormat}
            onChange={(e) => setAccountNumberFormat(e.target.value)}
            maxLength={100}
          />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">
              {t("platformBankTemplates.label.currencyCode")}
            </label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono uppercase"
              value={currencyCode}
              onChange={(e) => setCurrencyCode(e.target.value.toUpperCase())}
              maxLength={3}
            />
          </div>
          <div className="flex items-end gap-2 pb-1">
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              {t("platformBankTemplates.label.active")}
            </label>
          </div>
        </div>
        <button
          type="submit"
          disabled={busy}
          className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
        >
          {t("platformBankTemplates.action.create")}
        </button>
      </form>
    </div>
  );
}
