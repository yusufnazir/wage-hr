"use client";

import { useCallback, useMemo } from "react";

import { FormulaValidatePanel } from "@/components/payroll/FormulaValidatePanel";
import { WageComponentFormulaTokenBar } from "@/components/payroll/WageComponentFormulaTokenBar";
import { WageComponentFormulaMonaco } from "@/components/wage-components/WageComponentFormulaMonaco";
import { extractComponentCodesFromFormula } from "@/lib/wage-component-formula";

type Props = {
  calculationMethod: string;
  formulaExpression: string | null | undefined;
  percentageBase: string | null | undefined;
  roundingStrategy?: string | null;
  onFormulaChange: (v: string | null) => void;
  onPercentageBaseChange: (v: string | null) => void;
  validateScope?: "tenant" | "platform";
  dependencyComponentCodes?: string[];
  showValidatePanel?: boolean;
  t: (key: string) => string;
};

export function WageComponentFormulaEditor({
  calculationMethod,
  formulaExpression,
  percentageBase,
  roundingStrategy,
  onFormulaChange,
  onPercentageBaseChange,
  validateScope = "tenant",
  dependencyComponentCodes = [],
  showValidatePanel = true,
  t,
}: Props) {
  const fe = formulaExpression ?? "";
  const pb = percentageBase ?? "";

  const componentCodes = useMemo(() => {
    const fromFormula = extractComponentCodesFromFormula(fe);
    const merged = new Set([...dependencyComponentCodes, ...fromFormula]);
    return [...merged];
  }, [fe, dependencyComponentCodes]);

  const appendRef = useCallback(
    (ref: string) => {
      const spacer = fe.length > 0 && !/\s$/.test(fe) ? " " : "";
      onFormulaChange((fe + spacer + ref).trim() === "" ? null : (fe + spacer + ref).trim());
    },
    [fe, onFormulaChange],
  );

  const applyPreset = useCallback(
    (expression: string) => {
      onFormulaChange(expression);
    },
    [onFormulaChange],
  );

  if (calculationMethod === "HOURLY") {
    return (
      <div className="space-y-3">
        <div className="rounded border border-border bg-surface-alt px-3 py-2 text-sm text-muted">
          {t("wageComponents.formula.hintHourly")}
        </div>
        {showValidatePanel ? (
          <FormulaValidatePanel
            scope={validateScope}
            calculationMethod={calculationMethod}
            formulaExpression={null}
            roundingStrategy={roundingStrategy}
            t={t}
          />
        ) : null}
      </div>
    );
  }

  if (calculationMethod === "PERCENTAGE") {
    return (
      <div className="space-y-3">
        <label className="block space-y-1 text-sm">
          <span className="text-muted">{t("wageComponents.label.percentageBase")}</span>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 font-mono text-xs text-foreground"
            value={pb}
            onChange={(e) => onPercentageBaseChange(e.target.value === "" ? null : e.target.value)}
            placeholder="GROSS_TAXABLE"
            maxLength={40}
            spellCheck={false}
          />
        </label>
        <p className="text-xs text-muted">{t("wageComponents.formula.hintPercentage")}</p>
      </div>
    );
  }

  if (calculationMethod !== "FORMULA") {
    return showValidatePanel ? (
      <FormulaValidatePanel
        scope={validateScope}
        calculationMethod={calculationMethod}
        formulaExpression={formulaExpression ?? null}
        roundingStrategy={roundingStrategy}
        componentCodes={componentCodes}
        t={t}
      />
    ) : null;
  }

  return (
    <div className="space-y-3 rounded border border-border bg-surface-alt p-4">
      <div>
        <div className="text-sm font-medium text-foreground">{t("wageComponents.formula.title")}</div>
        <p className="mt-1 text-xs text-muted">{t("wageComponents.formula.intro")}</p>
      </div>

      <WageComponentFormulaTokenBar
        componentCodes={dependencyComponentCodes}
        onAppendRef={appendRef}
        onApplyPreset={applyPreset}
        t={t}
      />

      <label className="block space-y-1 text-sm">
        <span className="text-muted">{t("wageComponents.label.formulaExpression")}</span>
        <WageComponentFormulaMonaco
          value={fe}
          onChange={(v) => {
            if (v == null || v.trim() === "") {
              onFormulaChange(null);
              return;
            }
            onFormulaChange(v.length > 500 ? v.slice(0, 500) : v);
          }}
        />
      </label>
      <p className="text-xs text-muted">{t("wageComponents.formula.operatorsHint")}</p>

      {showValidatePanel ? (
        <FormulaValidatePanel
          scope={validateScope}
          calculationMethod={calculationMethod}
          formulaExpression={formulaExpression ?? null}
          percentageBase={percentageBase ?? null}
          roundingStrategy={roundingStrategy}
          componentCodes={componentCodes}
          t={t}
        />
      ) : null}
    </div>
  );
}
