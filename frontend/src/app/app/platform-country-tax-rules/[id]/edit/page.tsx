"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { CountryTaxRuleParametersEditor } from "@/components/platform/CountryTaxRuleParametersEditor";
import { fetchPlatformCountryTaxRule, putPlatformCountryTaxRule } from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "notFound" | "error";

export default function PlatformCountryTaxRuleEditPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const id = params.id;
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [countryCode, setCountryCode] = useState("");
  const [ruleCode, setRuleCode] = useState("");
  const [effectiveFrom, setEffectiveFrom] = useState("");
  const [name, setName] = useState("");
  const [effectiveTo, setEffectiveTo] = useState("");
  const [parametersJson, setParametersJson] = useState("");
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!me.platformSuperadmin) return;
    void (async () => {
      const r = await fetchPlatformCountryTaxRule(id);
      if (!r.ok) {
        setLoad(r.status === 403 ? "forbidden" : r.status === 404 ? "notFound" : "error");
        return;
      }
      setCountryCode(r.item.countryCode);
      setRuleCode(r.item.ruleCode);
      setEffectiveFrom(r.item.effectiveFrom);
      setName(r.item.name);
      setEffectiveTo(r.item.effectiveTo ?? "");
      setParametersJson(r.item.parametersJson);
      setActive(r.item.active);
      setLoad("ready");
    })();
  }, [id, me.platformSuperadmin]);

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCountryTaxRules.title.edit")}</h1>
        <p className="text-sm text-muted">{t("platformCountryTaxRules.error.notOperator")}</p>
        <Link href="/app/platform-country-tax-rules" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformCountryTaxRules.action.backToList")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-lg">
        <p className="text-sm text-muted">{t("platformCountryTaxRules.state.loading")}</p>
      </div>
    );
  }

  if (load !== "ready") {
    const key =
      load === "forbidden"
        ? "platformCountryTaxRules.error.forbidden"
        : load === "notFound"
          ? "platformCountryTaxRules.error.notFound"
          : "platformCountryTaxRules.error.load";
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCountryTaxRules.title.edit")}</h1>
        <p className="text-sm text-muted">{t(key)}</p>
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
      await putPlatformCountryTaxRule(id, {
        name: name.trim(),
        parametersJson: parametersJson.trim(),
        effectiveTo: effectiveTo.trim() ? effectiveTo.trim() : null,
        active,
      });
      router.push("/app/platform-country-tax-rules");
    } catch {
      setError(t("platformCountryTaxRules.msg.saveFailed"));
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6" data-testid="platform-country-tax-rule-form-edit">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCountryTaxRules.title.edit")}</h1>
        <Link href="/app/platform-country-tax-rules" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformCountryTaxRules.action.backToList")}
        </Link>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <div className="rounded-md border border-border bg-surface p-4 text-sm text-muted">
        <p>
          <span className="font-medium text-foreground">{countryCode}</span> ·{" "}
          <span className="font-mono text-foreground">{ruleCode}</span> · {t("platformCountryTaxRules.col.effectiveFrom")}{" "}
          <span className="font-mono text-foreground">{effectiveFrom}</span>
        </p>
        <p className="mt-2 text-xs">{t("platformCountryTaxRules.helper.intro")}</p>
      </div>

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5 shadow-sm">
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
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformCountryTaxRules.label.effectiveTo")}</label>
          <input
            type="date"
            className="w-full max-w-xs rounded border border-border bg-background px-3 py-2 text-sm"
            value={effectiveTo}
            onChange={(e) => setEffectiveTo(e.target.value)}
          />
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
          {t("platformCountryTaxRules.action.save")}
        </button>
      </form>
    </div>
  );
}
