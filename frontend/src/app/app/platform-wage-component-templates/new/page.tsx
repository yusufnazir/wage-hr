"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { PlatformDefinitionDefaultsEditor } from "@/components/payroll/PlatformDefinitionDefaultsEditor";
import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { showToast } from "@/components/ui/Toast";
import {
  createPlatformWageComponentTemplate,
  fetchPlatformCountries,
  fetchPlatformCountryTaxRules,
  fetchPlatformLedgerTemplates,
  type PlatformCountryRow,
  type PlatformCountryTaxRuleRow,
  type PlatformLedgerTemplateRow,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

const DEFAULT_DEFINITION_JSON = `{
  "componentType": "EARNING",
  "category": "GENERAL",
  "netEffect": "ADD_TO_NET",
  "calculationMethod": "FIXED_AMOUNT",
  "phase": "GROSS",
  "processingOrder": 100,
  "taxableWageTax": true,
  "taxableSocialSecurity": true,
  "taxablePension": true,
  "taxableVacationReserve": false
}`;

type LoadState = "loading" | "ready" | "forbidden";

export default function PlatformWageComponentTemplateNewPage() {
  const router = useRouter();
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [countries, setCountries] = useState<PlatformCountryRow[]>([]);
  const [ledgers, setLedgers] = useState<PlatformLedgerTemplateRow[]>([]);
  const [taxRules, setTaxRules] = useState<PlatformCountryTaxRuleRow[]>([]);

  const [countryCode, setCountryCode] = useState("");
  const [templateCode, setTemplateCode] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [definitionDefaultsJson, setDefinitionDefaultsJson] = useState(DEFAULT_DEFINITION_JSON);
  const [processingOrderHint, setProcessingOrderHint] = useState<string>("");
  const [phaseHint, setPhaseHint] = useState("");
  const [debitId, setDebitId] = useState("");
  const [creditId, setCreditId] = useState("");
  const [duplicable, setDuplicable] = useState(true);
  const [printOnPayslip, setPrintOnPayslip] = useState(true);
  const [auxiliary, setAuxiliary] = useState(false);
  const [applyInPayroll, setApplyInPayroll] = useState(true);
  const [recurrence, setRecurrence] = useState("");
  const [countryRuleKey, setCountryRuleKey] = useState("");
  const [platformCountryTaxRuleId, setPlatformCountryTaxRuleId] = useState("");
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!me.platformSuperadmin) {
      setLoad("forbidden");
      return;
    }
    void (async () => {
      const c = await fetchPlatformCountries({
        page: 0,
        size: 200,
        active: true,
        payrollEnabled: true,
        locale: me.locale,
      });
      if (c.ok) setCountries(c.items);
      setLoad("ready");
    })();
  }, [me.locale, me.platformSuperadmin]);

  useEffect(() => {
    if (!countryCode || !me.platformSuperadmin) {
      setLedgers([]);
      setTaxRules([]);
      return;
    }
    void (async () => {
      const [lr, tr] = await Promise.all([
        fetchPlatformLedgerTemplates({ page: 0, size: 200, country: countryCode, active: true, locale: me.locale }),
        fetchPlatformCountryTaxRules({ page: 0, size: 100, country: countryCode, active: true }),
      ]);
      if (lr.ok) setLedgers(lr.items);
      else setLedgers([]);
      if (tr.ok) setTaxRules(tr.items);
      else setTaxRules([]);
    })();
  }, [countryCode, me.locale, me.platformSuperadmin]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!countryCode || !templateCode.trim() || !name.trim()) {
      setError("Country, template code, and name are required.");
      return;
    }
    let poh: number | null = null;
    if (processingOrderHint.trim() !== "") {
      const n = Number.parseInt(processingOrderHint.trim(), 10);
      if (Number.isNaN(n)) {
        setError("Processing order hint must be a number.");
        return;
      }
      poh = n;
    }
    try {
      JSON.parse(definitionDefaultsJson.trim());
    } catch {
      setError(t("platformWageComponentTemplates.formula.invalidJson"));
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await createPlatformWageComponentTemplate({
        countryCode: countryCode.trim().toUpperCase(),
        templateCode: templateCode.trim(),
        name: name.trim(),
        description: description.trim() || null,
        definitionDefaultsJson: definitionDefaultsJson.trim(),
        processingOrderHint: poh,
        phaseHint: phaseHint.trim() || null,
        debitPlatformLedgerTemplateId: debitId.trim() || null,
        creditPlatformLedgerTemplateId: creditId.trim() || null,
        duplicable,
        printOnPayslip,
        auxiliary,
        applyInPayroll,
        recurrence: recurrence.trim() || null,
        countryRuleKey: countryRuleKey.trim() || null,
        platformCountryTaxRuleId: platformCountryTaxRuleId.trim() || null,
        active,
      });
      showToast(t("platformWageComponentTemplates.msg.created"));
      router.push("/app/platform-wage-component-templates");
    } catch (err) {
      setError(err instanceof Error ? err.message : t("platformWageComponentTemplates.msg.createFailed"));
      setBusy(false);
    }
  }

  if (!me.platformSuperadmin || load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformWageComponentTemplates.title.new")}</h1>
        <p className="text-sm text-muted">{t("platformWageComponentTemplates.error.notOperator")}</p>
        <Link href="/app/platform-wage-component-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformWageComponentTemplates.action.backToList")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-3xl">
        <p className="text-sm text-muted">{t("platformWageComponentTemplates.state.loading")}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformWageComponentTemplates.title.new")}</h1>
        <Link href="/app/platform-wage-component-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformWageComponentTemplates.action.backToList")}
        </Link>
      </div>

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-lg border border-border bg-surface p-6">
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block space-y-1 text-sm">
            <span className="text-muted">{t("platformWageComponentTemplates.label.country")}</span>
            <select
              required
              className="w-full rounded border border-border bg-background px-3 py-2 text-foreground"
              value={countryCode}
              onChange={(e) => setCountryCode(e.target.value)}
            >
              <option value="">—</option>
              {countries.map((c) => (
                <option key={c.id} value={c.isoAlpha2}>
                  {c.isoAlpha2} — {c.name}
                </option>
              ))}
            </select>
          </label>
          <label className="block space-y-1 text-sm">
            <span className="text-muted">{t("platformWageComponentTemplates.label.templateCode")}</span>
            <input
              required
              className="w-full rounded border border-border bg-background px-3 py-2 font-mono text-xs text-foreground"
              value={templateCode}
              onChange={(e) => setTemplateCode(e.target.value)}
              placeholder="1100"
            />
          </label>
        </div>

        <label className="block space-y-1 text-sm">
          <span className="text-muted">{t("platformWageComponentTemplates.label.name")}</span>
          <input
            required
            className="w-full rounded border border-border bg-background px-3 py-2 text-foreground"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </label>

        <label className="block space-y-1 text-sm">
          <span className="text-muted">{t("platformWageComponentTemplates.label.description")}</span>
          <textarea
            className="w-full rounded border border-border bg-background px-3 py-2 text-foreground"
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </label>

        <PlatformDefinitionDefaultsEditor
          definitionDefaultsJson={definitionDefaultsJson}
          onDefinitionDefaultsJsonChange={setDefinitionDefaultsJson}
          t={t}
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block space-y-1 text-sm">
            <span className="text-muted">{t("platformWageComponentTemplates.label.processingOrderHint")}</span>
            <input
              type="number"
              className="w-full rounded border border-border bg-background px-3 py-2 text-foreground"
              value={processingOrderHint}
              onChange={(e) => setProcessingOrderHint(e.target.value)}
              placeholder="Optional"
            />
            <span className="block text-xs text-muted">{t("platformWageComponentTemplates.helper.processingOrderHint")}</span>
          </label>
          <label className="block space-y-1 text-sm">
            <span className="text-muted">{t("platformWageComponentTemplates.label.phaseHint")}</span>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 font-mono text-xs text-foreground"
              value={phaseHint}
              onChange={(e) => setPhaseHint(e.target.value)}
              placeholder="GROSS"
            />
          </label>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block space-y-1 text-sm">
            <span className="text-muted">{t("platformWageComponentTemplates.label.debitLedgerTemplate")}</span>
            <select
              className="w-full rounded border border-border bg-background px-3 py-2 text-foreground"
              value={debitId}
              onChange={(e) => setDebitId(e.target.value)}
              disabled={!countryCode}
            >
              <option value="">—</option>
              {ledgers.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.code} — {l.description}
                </option>
              ))}
            </select>
          </label>
          <label className="block space-y-1 text-sm">
            <span className="text-muted">{t("platformWageComponentTemplates.label.creditLedgerTemplate")}</span>
            <select
              className="w-full rounded border border-border bg-background px-3 py-2 text-foreground"
              value={creditId}
              onChange={(e) => setCreditId(e.target.value)}
              disabled={!countryCode}
            >
              <option value="">—</option>
              {ledgers.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.code} — {l.description}
                </option>
              ))}
            </select>
          </label>
        </div>

        <fieldset className="grid gap-2 text-sm sm:grid-cols-2">
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={duplicable} onChange={(e) => setDuplicable(e.target.checked)} />
            {t("platformWageComponentTemplates.label.duplicable")}
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={printOnPayslip} onChange={(e) => setPrintOnPayslip(e.target.checked)} />
            {t("platformWageComponentTemplates.label.printOnPayslip")}
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={auxiliary} onChange={(e) => setAuxiliary(e.target.checked)} />
            {t("platformWageComponentTemplates.label.auxiliary")}
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={applyInPayroll} onChange={(e) => setApplyInPayroll(e.target.checked)} />
            {t("platformWageComponentTemplates.label.applyInPayroll")}
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            {t("platformWageComponentTemplates.label.active")}
          </label>
        </fieldset>

        <label className="block space-y-1 text-sm">
          <span className="text-muted">{t("platformWageComponentTemplates.label.recurrence")}</span>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-foreground"
            value={recurrence}
            onChange={(e) => setRecurrence(e.target.value)}
            placeholder="RECURRENT"
          />
        </label>

        <label className="block space-y-1 text-sm">
          <span className="text-muted">{t("platformWageComponentTemplates.label.countryRuleKey")}</span>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 font-mono text-xs text-foreground"
            value={countryRuleKey}
            onChange={(e) => setCountryRuleKey(e.target.value)}
          />
        </label>

        <label className="block space-y-1 text-sm">
          <span className="text-muted">{t("platformWageComponentTemplates.label.platformCountryTaxRule")}</span>
          <select
            className="w-full rounded border border-border bg-background px-3 py-2 text-foreground"
            value={platformCountryTaxRuleId}
            onChange={(e) => setPlatformCountryTaxRuleId(e.target.value)}
            disabled={!countryCode}
          >
            <option value="">—</option>
            {taxRules.map((r) => (
              <option key={r.id} value={r.id}>
                {r.ruleCode} — {r.name}
              </option>
            ))}
          </select>
        </label>

        {error ? <p className="text-sm text-destructive">{error}</p> : null}

        <div className="flex gap-3 pt-2">
          <button
            type="submit"
            disabled={busy}
            className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90 disabled:opacity-50"
          >
            {t("platformWageComponentTemplates.action.create")}
          </button>
          <Link
            href="/app/platform-wage-component-templates"
            className="rounded border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-surface-alt"
          >
            {t("platformWageComponentTemplates.action.cancel")}
          </Link>
        </div>
      </form>
    </div>
  );
}
