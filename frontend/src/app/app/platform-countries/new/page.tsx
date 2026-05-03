"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { createPlatformCountry } from "@/lib/api";
import { navLabel } from "@/messages/nav";

export default function PlatformCountryNewPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [isoAlpha2, setIsoAlpha2] = useState("");
  const [isoAlpha3, setIsoAlpha3] = useState("");
  const [isoNumeric, setIsoNumeric] = useState("");
  const [dialCode, setDialCode] = useState("");
  const [nameEn, setNameEn] = useState("");
  const [nameNl, setNameNl] = useState("");
  const [active, setActive] = useState(true);
  const [payrollEnabled, setPayrollEnabled] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCountries.title.new")}</h1>
        <p className="text-sm text-muted">{t("platformCountries.error.notOperator")}</p>
        <Link href="/app/platform-countries" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformCountries.action.backToList")}
        </Link>
      </div>
    );
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await createPlatformCountry({
        isoAlpha2: isoAlpha2.trim().toUpperCase(),
        isoAlpha3: isoAlpha3.trim().toUpperCase(),
        isoNumeric: isoNumeric.trim(),
        dialCode: dialCode.trim() ? dialCode.trim() : null,
        active,
        payrollEnabled,
        translations: [
          { locale: "en", name: nameEn.trim() },
          { locale: "nl", name: nameNl.trim() },
        ],
      });
      router.push("/app/platform-countries");
    } catch {
      setError(t("platformCountries.msg.createFailed"));
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-xl space-y-6" data-testid="platform-country-form-new">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCountries.title.new")}</h1>
        <Link href="/app/platform-countries" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformCountries.action.backToList")}
        </Link>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5 shadow-sm">
        <div className="grid gap-3 sm:grid-cols-3">
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformCountries.label.alpha2")}</label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground uppercase"
              value={isoAlpha2}
              onChange={(e) => setIsoAlpha2(e.target.value.toUpperCase())}
              maxLength={2}
              required
              pattern="[A-Z]{2}"
              data-testid="platform-country-alpha2"
              autoFocus
            />
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformCountries.label.alpha3")}</label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground uppercase"
              value={isoAlpha3}
              onChange={(e) => setIsoAlpha3(e.target.value.toUpperCase())}
              maxLength={3}
              required
              pattern="[A-Z]{3}"
              data-testid="platform-country-alpha3"
            />
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformCountries.label.numeric")}</label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
              value={isoNumeric}
              onChange={(e) => setIsoNumeric(e.target.value)}
              maxLength={3}
              required
              pattern="[0-9]{1,3}"
              data-testid="platform-country-numeric"
            />
          </div>
        </div>

        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformCountries.label.dialCode")}</label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
            value={dialCode}
            onChange={(e) => setDialCode(e.target.value)}
            maxLength={15}
            placeholder="+31"
            data-testid="platform-country-dial"
          />
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformCountries.label.nameEn")}</label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
              value={nameEn}
              onChange={(e) => setNameEn(e.target.value)}
              maxLength={100}
              required
              data-testid="platform-country-name-en"
            />
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformCountries.label.nameNl")}</label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
              value={nameNl}
              onChange={(e) => setNameNl(e.target.value)}
              maxLength={100}
              required
              data-testid="platform-country-name-nl"
            />
          </div>
        </div>

        <div className="flex items-center gap-2">
          <input
            id="country-active"
            type="checkbox"
            checked={active}
            onChange={(e) => setActive(e.target.checked)}
            data-testid="platform-country-active"
          />
          <label htmlFor="country-active" className="text-sm text-foreground">
            {t("platformCountries.label.active")}
          </label>
        </div>

        <div className="flex items-center gap-2">
          <input
            id="country-payroll-enabled"
            type="checkbox"
            checked={payrollEnabled}
            onChange={(e) => setPayrollEnabled(e.target.checked)}
            data-testid="platform-country-payroll-enabled"
          />
          <label htmlFor="country-payroll-enabled" className="text-sm text-foreground">
            {t("platformCountries.label.payrollEnabled")}
          </label>
        </div>

        <div className="flex gap-3">
          <button
            type="submit"
            disabled={busy}
            className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
            data-testid="platform-country-create"
          >
            {t("platformCountries.action.create")}
          </button>
          <Link
            href="/app/platform-countries"
            className="rounded border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-surface"
          >
            Cancel
          </Link>
        </div>
      </form>
    </div>
  );
}
