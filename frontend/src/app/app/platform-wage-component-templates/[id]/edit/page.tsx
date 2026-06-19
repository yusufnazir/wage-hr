"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import {
  baseEffectsFromTemplate,
  toBaseEffectPutPayload,
  WageComponentTemplateBaseEffectsEditor,
  type EditableBaseEffectRow,
} from "@/components/payroll/WageComponentTemplateBaseEffects";
import { PlatformDefinitionDefaultsEditor } from "@/components/payroll/PlatformDefinitionDefaultsEditor";
import {
  dependenciesFromTemplate,
  toDependencyPutPayload,
  WageComponentTemplateDependenciesEditor,
  type EditableDependencyRow,
} from "@/components/payroll/WageComponentTemplateDependencies";
import { detectDependencyIssues } from "@/lib/dependency-graph";
import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { showToast } from "@/components/ui/Toast";
import {
  fetchPlatformCountryTaxRules,
  fetchPlatformLedgerTemplates,
  fetchPlatformPayrollBases,
  fetchPlatformWageComponentTemplate,
  fetchPlatformWageComponentTemplates,
  putPlatformWageComponentTemplate,
  type PlatformCountryTaxRuleRow,
  type PlatformLedgerTemplateRow,
  type PlatformPayrollBaseRow,
  type PlatformWageComponentTemplateRow,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "notFound" | "error";

const TABS = ["general", "definition", "baseEffects", "dependencies", "rules", "ledgers"] as const;
type TabId = (typeof TABS)[number];

const tabButtonClass = (active: boolean) =>
  `rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
    active
      ? "bg-primary/15 text-primary ring-1 ring-primary/40"
      : "text-muted hover:bg-surface-alt hover:text-foreground"
  }`;

export default function PlatformWageComponentTemplateEditPage() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const id = params.id;
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [activeTab, setActiveTab] = useState<TabId>("general");
  const [template, setTemplate] = useState<PlatformWageComponentTemplateRow | null>(null);
  const [ledgers, setLedgers] = useState<PlatformLedgerTemplateRow[]>([]);
  const [taxRules, setTaxRules] = useState<PlatformCountryTaxRuleRow[]>([]);
  const [payrollBases, setPayrollBases] = useState<PlatformPayrollBaseRow[]>([]);
  const [baseEffectRows, setBaseEffectRows] = useState<EditableBaseEffectRow[]>([]);
  const [dependencyRows, setDependencyRows] = useState<EditableDependencyRow[]>([]);
  const [countryTemplates, setCountryTemplates] = useState<PlatformWageComponentTemplateRow[]>([]);

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [definitionDefaultsJson, setDefinitionDefaultsJson] = useState("");
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
      const tr = await fetchPlatformWageComponentTemplate(id);
      if (!tr.ok) {
        setLoad(tr.status === 404 ? "notFound" : "error");
        return;
      }
      const tpl = tr.template;
      setTemplate(tpl);
      setName(tpl.name);
      setDescription(tpl.description ?? "");
      setDefinitionDefaultsJson(tpl.definitionDefaultsJson ?? "{}");
      setProcessingOrderHint(tpl.processingOrderHint != null ? String(tpl.processingOrderHint) : "");
      setPhaseHint(tpl.phaseHint ?? "");
      setDebitId(tpl.debitPlatformLedgerTemplateId ?? "");
      setCreditId(tpl.creditPlatformLedgerTemplateId ?? "");
      setDuplicable(tpl.duplicable);
      setPrintOnPayslip(tpl.printOnPayslip);
      setAuxiliary(tpl.auxiliary);
      setApplyInPayroll(tpl.applyInPayroll);
      setRecurrence(tpl.recurrence ?? "");
      setCountryRuleKey(tpl.countryRuleKey ?? "");
      setPlatformCountryTaxRuleId(tpl.platformCountryTaxRuleId ?? "");
      setActive(tpl.active);
      setBaseEffectRows(baseEffectsFromTemplate(tpl.baseEffects));
      setDependencyRows(dependenciesFromTemplate(tpl.dependencies));

      const [lr, tax, bases, countryTpl] = await Promise.all([
        fetchPlatformLedgerTemplates({ page: 0, size: 200, country: tpl.countryCode, active: true, locale: me.locale }),
        fetchPlatformCountryTaxRules({ page: 0, size: 100, country: tpl.countryCode, active: true }),
        fetchPlatformPayrollBases({ page: 0, size: 200, active: true }),
        fetchPlatformWageComponentTemplates({ country: tpl.countryCode, active: true, page: 0, size: 500 }),
      ]);
      if (lr.ok) setLedgers(lr.items);
      if (tax.ok) setTaxRules(tax.items);
      if (bases.ok) setPayrollBases(bases.items);
      if (countryTpl.ok) setCountryTemplates(countryTpl.items);
      setLoad("ready");
    })();
  }, [id, me.locale, me.platformSuperadmin]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) {
      setError("Name is required.");
      setActiveTab("general");
      return;
    }
    let poh: number | null = null;
    if (processingOrderHint.trim() !== "") {
      const n = Number.parseInt(processingOrderHint.trim(), 10);
      if (Number.isNaN(n)) {
        setError("Processing order hint must be a number.");
        setActiveTab("general");
        return;
      }
      poh = n;
    }
    const depIssue = detectDependencyIssues(id, dependencyRows.map((r) => r.dependsOnTemplateId));
    if (depIssue.hasCycle) {
      setError(depIssue.message ?? t("platformWageComponentTemplates.dependencies.cycleError"));
      setActiveTab("dependencies");
      return;
    }
    try {
      JSON.parse(definitionDefaultsJson.trim());
    } catch {
      setError(t("platformWageComponentTemplates.formula.invalidJson"));
      setActiveTab("definition");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await putPlatformWageComponentTemplate(id, {
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
        baseEffects: toBaseEffectPutPayload(baseEffectRows),
        dependencies: toDependencyPutPayload(dependencyRows),
      });
      showToast(t("platformWageComponentTemplates.msg.saved"));
      router.push("/app/platform-wage-component-templates");
    } catch (err) {
      const msg = err instanceof Error ? err.message : t("platformWageComponentTemplates.msg.saveFailed");
      setError(msg.includes("DEPENDENCY_CYCLE") ? t("platformWageComponentTemplates.dependencies.cycleError") : msg);
      if (msg.includes("DEPENDENCY")) setActiveTab("dependencies");
      setBusy(false);
    }
  }

  if (!me.platformSuperadmin || load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformWageComponentTemplates.title.edit")}</h1>
        <p className="text-sm text-muted">{t("platformWageComponentTemplates.error.notOperator")}</p>
        <Link href="/app/platform-wage-component-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformWageComponentTemplates.action.backToList")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-4xl">
        <p className="text-sm text-muted">{t("platformWageComponentTemplates.state.loading")}</p>
      </div>
    );
  }

  if (load !== "ready" || !template) {
    const msg =
      load === "notFound" ? t("platformWageComponentTemplates.error.notFound") : t("platformWageComponentTemplates.error.load");
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <p className="text-sm text-destructive">{msg}</p>
        <Link href="/app/platform-wage-component-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformWageComponentTemplates.action.backToList")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformWageComponentTemplates.title.edit")}</h1>
        <div className="flex flex-wrap gap-3 text-sm">
          <Link href={`/app/platform-wage-component-templates/${id}/ledger`} className="font-medium text-primary underline-offset-4 hover:underline">
            {t("platformWageComponentTemplates.title.editLedger")}
          </Link>
          <Link href="/app/platform-wage-component-templates" className="font-medium text-primary underline-offset-4 hover:underline">
            {t("platformWageComponentTemplates.action.backToList")}
          </Link>
        </div>
      </div>

      <div className="rounded border border-border bg-surface-alt/40 px-4 py-3 text-sm">
        <span className="text-muted">{t("platformWageComponentTemplates.label.readonlyCountryCode")}: </span>
        <span className="font-mono">{template.countryCode}</span>
        <span className="mx-3 text-muted">|</span>
        <span className="text-muted">{t("platformWageComponentTemplates.label.readonlyTemplateCode")}: </span>
        <span className="font-mono text-xs">{template.templateCode}</span>
        <span className="mx-3 text-muted">|</span>
        <span className="font-medium text-foreground">{name}</span>
      </div>

      <form onSubmit={(e) => void onSubmit(e)} className="rounded-lg border border-border bg-surface">
        <div
          className="flex flex-wrap gap-2 border-b border-border px-4 pt-4 pb-2"
          role="tablist"
          aria-label={t("platformWageComponentTemplates.title.edit")}
        >
          {TABS.map((tid) => (
            <button
              key={tid}
              type="button"
              role="tab"
              aria-selected={activeTab === tid}
              className={tabButtonClass(activeTab === tid)}
              onClick={() => setActiveTab(tid)}
            >
              {tid === "dependencies" ? t("platformWageComponentTemplates.tab.dependencies") : t(`platformWageComponentTemplates.tab.${tid}`)}
            </button>
          ))}
        </div>

        <div className="min-h-[12rem] space-y-4 p-6">
          {activeTab === "general" ? (
            <div className="space-y-4">
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
                  rows={3}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </label>

              <div className="grid gap-4 sm:grid-cols-2">
                <label className="block space-y-1 text-sm">
                  <span className="text-muted">{t("platformWageComponentTemplates.label.processingOrderHint")}</span>
                  <input
                    type="number"
                    className="w-full rounded border border-border bg-background px-3 py-2 text-foreground"
                    value={processingOrderHint}
                    onChange={(e) => setProcessingOrderHint(e.target.value)}
                  />
                  <span className="block text-xs text-muted">{t("platformWageComponentTemplates.helper.processingOrderHint")}</span>
                </label>
                <label className="block space-y-1 text-sm">
                  <span className="text-muted">{t("platformWageComponentTemplates.label.phaseHint")}</span>
                  <input
                    className="w-full rounded border border-border bg-background px-3 py-2 font-mono text-xs text-foreground"
                    value={phaseHint}
                    onChange={(e) => setPhaseHint(e.target.value)}
                  />
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
            </div>
          ) : null}

          {activeTab === "definition" ? (
            <PlatformDefinitionDefaultsEditor
              definitionDefaultsJson={definitionDefaultsJson}
              onDefinitionDefaultsJsonChange={setDefinitionDefaultsJson}
              dependencyComponentCodes={dependencyRows.map((r) => r.dependsOnTemplateCode)}
              t={t}
            />
          ) : null}

          {activeTab === "baseEffects" ? (
            <WageComponentTemplateBaseEffectsEditor
              rows={baseEffectRows}
              onChange={setBaseEffectRows}
              payrollBases={payrollBases}
              t={t}
            />
          ) : null}

          {activeTab === "dependencies" ? (
            <WageComponentTemplateDependenciesEditor
              rows={dependencyRows}
              onChange={setDependencyRows}
              availableTemplates={countryTemplates}
              currentTemplateId={id}
              currentTemplateCode={template.templateCode}
              t={t}
            />
          ) : null}

          {activeTab === "rules" ? (
            <div className="space-y-4">
              <label className="block space-y-1 text-sm">
                <span className="text-muted">{t("platformWageComponentTemplates.label.recurrence")}</span>
                <input
                  className="w-full rounded border border-border bg-background px-3 py-2 text-foreground"
                  value={recurrence}
                  onChange={(e) => setRecurrence(e.target.value)}
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
                >
                  <option value="">—</option>
                  {taxRules.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.ruleCode} — {r.name}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          ) : null}

          {activeTab === "ledgers" ? (
            <div className="space-y-4">
              <p className="text-xs text-muted">{t("platformWageComponentTemplates.helper.ledger")}</p>
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="block space-y-1 text-sm">
                  <span className="text-muted">{t("platformWageComponentTemplates.label.debitLedgerTemplate")}</span>
                  <select
                    className="w-full rounded border border-border bg-background px-3 py-2 text-foreground"
                    value={debitId}
                    onChange={(e) => setDebitId(e.target.value)}
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
            </div>
          ) : null}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border bg-surface-alt/30 px-6 py-4">
          <div>
            {error ? <p className="text-sm text-destructive">{error}</p> : null}
          </div>
          <div className="flex gap-3">
            <button
              type="submit"
              disabled={busy}
              className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90 disabled:opacity-50"
            >
              {t("platformWageComponentTemplates.action.saveForm")}
            </button>
            <Link
              href="/app/platform-wage-component-templates"
              className="rounded border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-surface-alt"
            >
              {t("platformWageComponentTemplates.action.cancel")}
            </Link>
          </div>
        </div>
      </form>
    </div>
  );
}
