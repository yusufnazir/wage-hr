"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { CountryTaxRuleParametersEditor } from "@/components/platform/CountryTaxRuleParametersEditor";
import { createPlatformCountryTaxRule } from "@/lib/api";
import { defaultTaxRuleParametersForm, serializeTaxRuleParameters } from "@/lib/country-tax-rule-parameters";
import { navLabel } from "@/messages/nav";

export default function PlatformCountryTaxRuleNewPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [countryCode, setCountryCode] = useState("SR");
  const [ruleCode, setRuleCode] = useState("");
  const [name, setName] = useState("");
  const [effectiveFrom, setEffectiveFrom] = useState("");
  const [effectiveTo, setEffectiveTo] = useState("");
  const [parametersJson, setParametersJson] = useState(() =>
    serializeTaxRuleParameters(defaultTaxRuleParametersForm()),
  );
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCountryTaxRules.title.new")}</h1>
        <p className="text-sm text-muted">{t("platformCountryTaxRules.error.notOperator")}</p>
        <Link href="/app/platform-country-tax-rules" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformCountryTaxRules.action.backToList")}
        </Link>
      </div>
    );
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await createPlatformCountryTaxRule({
        countryCode: countryCode.trim().toUpperCase(),
        ruleCode: ruleCode.trim(),
        name: name.trim(),
        effectiveFrom: effectiveFrom.trim(),
        effectiveTo: effectiveTo.trim() ? effectiveTo.trim() : null,
        parametersJson: parametersJson.trim(),
        active,
      });
      router.push("/app/platform-country-tax-rules");
    } catch {
      setError(t("platformCountryTaxRules.msg.createFailed"));
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6" data-testid="platform-country-tax-rule-form-new">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCountryTaxRules.title.new")}</h1>
        <Link href="/app/platform-country-tax-rules" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformCountryTaxRules.action.backToList")}
        </Link>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5 shadow-sm">
        <p className="text-xs text-muted">{t("platformCountryTaxRules.params.intro")}</p>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformCountryTaxRules.label.country")}</label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono uppercase"
              value={countryCode}
              onChange={(e) => setCountryCode(e.target.value.toUpperCase().slice(0, 2))}
              maxLength={2}
              required
              data-testid="tax-rule-country"
            />
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformCountryTaxRules.label.ruleCode")}</label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono"
              value={ruleCode}
              onChange={(e) => setRuleCode(e.target.value)}
              required
              maxLength={64}
              data-testid="tax-rule-code"
            />
          </div>
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformCountryTaxRules.label.name")}</label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            maxLength={200}
          />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformCountryTaxRules.label.effectiveFrom")}</label>
            <input
              type="date"
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
              value={effectiveFrom}
              onChange={(e) => setEffectiveFrom(e.target.value)}
              required
            />
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformCountryTaxRules.label.effectiveTo")}</label>
            <input
              type="date"
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
              value={effectiveTo}
              onChange={(e) => setEffectiveTo(e.target.value)}
            />
          </div>
        </div>
        <CountryTaxRuleParametersEditor
          locale={me.locale}
          value={parametersJson}
          onChange={setParametersJson}
        />
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
          {t("platformCountryTaxRules.label.active")}
        </label>
        <button
          type="submit"
          disabled={busy}
          className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
        >
          {t("platformCountryTaxRules.action.create")}
        </button>
      </form>
    </div>
  );
}
