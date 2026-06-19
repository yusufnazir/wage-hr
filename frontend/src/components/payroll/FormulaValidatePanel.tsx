"use client";

import { useMemo, useState } from "react";

import {
  formatFormulaRuleSummary,
  isStoredCriteriaRules,
  parseStoredFormulaExpression,
} from "@/lib/wage-component-definition";
import {
  postPlatformWageComponentTemplateValidateFormula,
  postTenantWageComponentValidateFormula,
  type FormulaMockContext,
  type FormulaValidateRequest,
} from "@/lib/api";
import { WageComponentFormulaRulesReadOnly } from "@/components/wage-components/WageComponentFormulaRulesReadOnly";

type Props = {
  scope: "tenant" | "platform";
  calculationMethod: string;
  formulaExpression: string | null;
  percentageBase?: string | null;
  roundingStrategy?: string | null;
  componentCodes?: string[];
  readOnlyFormula?: boolean;
  /** When false, parent renders rules summary (tenant edit page). */
  showRulesSummary?: boolean;
  embedded?: boolean;
  t: (key: string) => string;
};

const EMPTY_MOCK: FormulaMockContext = {
  compensationPeriodicRate: "0",
  compensationIsHourly: "0",
  compensationHourlyRate: "0",
  transactionQuantity: "0",
  transactionRate: "0",
  transactionAmount: "0",
  definitionDefaultAmount: "0",
  componentAmounts: {},
};

type ValidateTarget = { id: string; label: string; expression: string };

export function FormulaValidatePanel({
  scope,
  calculationMethod,
  formulaExpression,
  percentageBase,
  roundingStrategy,
  componentCodes = [],
  readOnlyFormula = false,
  showRulesSummary = true,
  embedded = false,
  t,
}: Props) {
  const parsed = useMemo(() => parseStoredFormulaExpression(formulaExpression), [formulaExpression]);
  const criteriaRules = isStoredCriteriaRules(parsed);

  const validateTargets = useMemo((): ValidateTarget[] => {
    if (!criteriaRules) {
      const expr = parsed.legacyFormulaExpression ?? formulaExpression?.trim() ?? "";
      return expr ? [{ id: "formula", label: t("wageComponents.label.formulaExpression"), expression: expr }] : [];
    }
    const targets: ValidateTarget[] = parsed.formulaRules.map((rule, index) => ({
      id: `rule-${index}`,
      label: `${index + 1}. ${formatFormulaRuleSummary(rule, t)}`,
      expression: rule.formulaExpression,
    }));
    if (parsed.defaultFormulaExpression) {
      targets.push({
        id: "default",
        label: t("wageComponents.formula.criteria.defaultLabel"),
        expression: parsed.defaultFormulaExpression,
      });
    }
    return targets;
  }, [criteriaRules, formulaExpression, parsed, t]);

  const [validateTargetId, setValidateTargetId] = useState(
    () => validateTargets[validateTargets.length - 1]?.id ?? validateTargets[0]?.id ?? "formula",
  );

  const activeTarget =
    validateTargets.find((x) => x.id === validateTargetId) ?? validateTargets[0] ?? null;

  const [mock, setMock] = useState<FormulaMockContext>({
    ...EMPTY_MOCK,
    definitionDefaultAmount: "18500",
    componentAmounts: Object.fromEntries(componentCodes.map((c) => [c, "18500"])),
  });
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function updateMockField(field: keyof FormulaMockContext, value: string) {
    setMock((m) => ({ ...m, [field]: value }));
  }

  function updateComponentAmount(code: string, value: string) {
    setMock((m) => ({
      ...m,
      componentAmounts: { ...(m.componentAmounts ?? {}), [code]: value },
    }));
  }

  async function runValidate() {
    if (!activeTarget?.expression) return;
    setBusy(true);
    setError(null);
    setResult(null);
    const body: FormulaValidateRequest = {
      calculationMethod,
      formulaExpression: activeTarget.expression,
      percentageBase: percentageBase ?? undefined,
      roundingStrategy: roundingStrategy ?? undefined,
      mockContext: {
        compensationPeriodicRate: mock.compensationPeriodicRate || "0",
        compensationIsHourly: mock.compensationIsHourly ?? "0",
        compensationHourlyRate: mock.compensationHourlyRate || "0",
        transactionQuantity: mock.transactionQuantity || "0",
        transactionRate: mock.transactionRate || "0",
        transactionAmount: mock.transactionAmount || "0",
        definitionDefaultAmount: mock.definitionDefaultAmount || "0",
        componentAmounts: mock.componentAmounts ?? {},
      },
    };
    const r =
      scope === "platform"
        ? await postPlatformWageComponentTemplateValidateFormula(body)
        : await postTenantWageComponentValidateFormula(body);
    setBusy(false);
    if (!r.ok) {
      setError(r.message ?? t("wageComponents.formula.validate.failed"));
      return;
    }
    setResult(String(r.amount));
  }

  if (calculationMethod === "PERCENTAGE") {
    return (
      <p className="text-xs text-muted">{t("wageComponents.formula.validate.percentageNotSupported")}</p>
    );
  }

  if (calculationMethod !== "FORMULA" && calculationMethod !== "HOURLY" && calculationMethod !== "FIXED_AMOUNT") {
    return null;
  }

  const shellClass = embedded
    ? "space-y-3"
    : "space-y-3 rounded border border-dashed border-border bg-surface-alt/40 p-4";

  return (
    <div className={shellClass}>
      <div>
        <div className="text-sm font-medium text-foreground">{t("wageComponents.formula.validate.title")}</div>
        <p className="mt-1 text-xs text-muted">{t("wageComponents.formula.validate.intro")}</p>
      </div>
      {readOnlyFormula && showRulesSummary && formulaExpression ? (
        <WageComponentFormulaRulesReadOnly formulaExpression={formulaExpression} t={t} fromTemplate={scope === "tenant"} />
      ) : null}
      {readOnlyFormula && !criteriaRules && formulaExpression && !showRulesSummary ? (
        <code className="block overflow-x-auto rounded border border-border bg-background px-2 py-1.5 font-mono text-[11px] text-foreground">
          {parsed.legacyFormulaExpression ?? formulaExpression}
        </code>
      ) : null}
      {criteriaRules && validateTargets.length > 0 ? (
        <label className="block space-y-1 text-xs">
          <span className="text-muted">{t("wageComponents.formula.criteria.validateWhich")}</span>
          <select
            className="w-full max-w-md rounded border border-border bg-background px-2 py-1.5 text-sm text-foreground"
            value={validateTargetId}
            onChange={(e) => setValidateTargetId(e.target.value)}
          >
            {validateTargets.map((target) => (
              <option key={target.id} value={target.id}>
                {target.label}
              </option>
            ))}
          </select>
          {activeTarget ? (
            <code className="mt-1 block overflow-x-auto rounded border border-border bg-background px-2 py-1 font-mono text-[11px] text-muted">
              {activeTarget.expression}
            </code>
          ) : null}
        </label>
      ) : null}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <label className="block text-xs">
          <span className="text-muted">{t("wageComponents.formula.validate.compensationPeriodicRate")}</span>
          <input
            className="mt-0.5 w-full rounded border border-border bg-background px-2 py-1 font-mono text-xs"
            value={mock.compensationPeriodicRate ?? ""}
            onChange={(e) => updateMockField("compensationPeriodicRate", e.target.value)}
          />
        </label>
        <label className="block text-xs">
          <span className="text-muted">{t("wageComponents.formula.validate.compensationHourlyRate")}</span>
          <input
            className="mt-0.5 w-full rounded border border-border bg-background px-2 py-1 font-mono text-xs"
            value={mock.compensationHourlyRate ?? ""}
            onChange={(e) => updateMockField("compensationHourlyRate", e.target.value)}
          />
        </label>
        <label className="block text-xs">
          <span className="text-muted">{t("wageComponents.formula.validate.compensationIsHourly")}</span>
          <input
            className="mt-0.5 w-full rounded border border-border bg-background px-2 py-1 font-mono text-xs"
            value={mock.compensationIsHourly ?? ""}
            onChange={(e) => updateMockField("compensationIsHourly", e.target.value)}
          />
        </label>
        <label className="block text-xs">
          <span className="text-muted">{t("wageComponents.formula.validate.definitionDefaultAmount")}</span>
          <input
            className="mt-0.5 w-full rounded border border-border bg-background px-2 py-1 font-mono text-xs"
            value={mock.definitionDefaultAmount ?? ""}
            onChange={(e) => updateMockField("definitionDefaultAmount", e.target.value)}
          />
        </label>
        <label className="block text-xs">
          <span className="text-muted">{t("wageComponents.formula.validate.transactionQuantity")}</span>
          <input
            className="mt-0.5 w-full rounded border border-border bg-background px-2 py-1 font-mono text-xs"
            value={mock.transactionQuantity ?? ""}
            onChange={(e) => updateMockField("transactionQuantity", e.target.value)}
          />
        </label>
        <label className="block text-xs">
          <span className="text-muted">{t("wageComponents.formula.validate.transactionRate")}</span>
          <input
            className="mt-0.5 w-full rounded border border-border bg-background px-2 py-1 font-mono text-xs"
            value={mock.transactionRate ?? ""}
            onChange={(e) => updateMockField("transactionRate", e.target.value)}
          />
        </label>
        <label className="block text-xs sm:col-span-2 lg:col-span-1">
          <span className="text-muted">{t("wageComponents.formula.validate.transactionAmount")}</span>
          <input
            className="mt-0.5 w-full rounded border border-border bg-background px-2 py-1 font-mono text-xs"
            value={mock.transactionAmount ?? ""}
            onChange={(e) => updateMockField("transactionAmount", e.target.value)}
          />
        </label>
      </div>
      {componentCodes.length > 0 ? (
        <ComponentAmountsSection codes={componentCodes} mock={mock} t={t} onUpdate={updateComponentAmount} />
      ) : null}
      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          disabled={busy || !activeTarget?.expression}
          className="rounded bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50"
          onClick={() => void runValidate()}
        >
          {t("wageComponents.formula.validate.run")}
        </button>
        {result != null ? (
          <span className="font-mono text-sm text-foreground">
            {t("wageComponents.formula.validate.result")}: {result}
          </span>
        ) : null}
      </div>
      {error ? <p className="text-xs text-destructive">{error}</p> : null}
    </div>
  );
}

function ComponentAmountsSection({
  codes,
  mock,
  t,
  onUpdate,
}: {
  codes: string[];
  mock: FormulaMockContext;
  t: (key: string) => string;
  onUpdate: (code: string, value: string) => void;
}) {
  return (
    <div className="space-y-1">
      <div className="text-xs text-muted">{t("wageComponents.formula.validate.componentAmounts")}</div>
      <div className="grid gap-2 sm:grid-cols-2">
        {codes.map((code) => (
          <label key={code} className="flex items-center gap-2 text-xs">
            <span className="w-20 shrink-0 font-mono text-foreground">{code}</span>
            <input
              className="min-w-0 flex-1 rounded border border-border bg-background px-2 py-1 font-mono text-xs"
              value={mock.componentAmounts?.[code] ?? ""}
              onChange={(e) => onUpdate(code, e.target.value)}
            />
          </label>
        ))}
      </div>
    </div>
  );
}
