"use client";

import { useCallback, useMemo, useState } from "react";

import { WageComponentCriteriaFormulaEditor } from "@/components/wage-components/WageComponentCriteriaFormulaEditor";
import {
  CALCULATION_METHOD_OPTIONS,
  CRITERIA_RULES_MODE,
  mergeDefinitionDefaultsJson,
  parseDefinitionDefaultsJson,
} from "@/lib/wage-component-definition";
import {
  BASE_SALARY_CRITERIA_RULES,
  DEFAULT_FORMULA_EXPRESSION,
  extractComponentCodesFromRules,
} from "@/lib/wage-component-formula";

type Props = {
  definitionDefaultsJson: string;
  onDefinitionDefaultsJsonChange: (json: string) => void;
  dependencyComponentCodes?: string[];
  t: (key: string) => string;
};

export function PlatformDefinitionDefaultsEditor({
  definitionDefaultsJson,
  onDefinitionDefaultsJsonChange,
  dependencyComponentCodes = [],
  t,
}: Props) {
  const [showAdvancedJson, setShowAdvancedJson] = useState(false);
  const [jsonError, setJsonError] = useState<string | null>(null);

  const def = useMemo(() => parseDefinitionDefaultsJson(definitionDefaultsJson), [definitionDefaultsJson]);

  const depCodes = useMemo(() => {
    const fromRules = extractComponentCodesFromRules(
      def.formulaRules,
      def.defaultFormulaExpression,
      def.formulaExpression,
    );
    return [...new Set([...dependencyComponentCodes, ...fromRules])];
  }, [dependencyComponentCodes, def.defaultFormulaExpression, def.formulaExpression, def.formulaRules]);

  const patchJson = useCallback(
    (patch: Parameters<typeof mergeDefinitionDefaultsJson>[1]) => {
      onDefinitionDefaultsJsonChange(mergeDefinitionDefaultsJson(definitionDefaultsJson, patch));
    },
    [definitionDefaultsJson, onDefinitionDefaultsJsonChange],
  );

  const onRawJsonChange = (raw: string) => {
    onDefinitionDefaultsJsonChange(raw);
    try {
      JSON.parse(raw);
      setJsonError(null);
    } catch {
      setJsonError(t("platformWageComponentTemplates.formula.invalidJson"));
    }
  };

  return (
    <div className="space-y-4">
      <label className="block space-y-1 text-sm">
        <span className="text-muted">{t("wageComponents.label.calculationMethod")}</span>
        <select
          className="w-full max-w-md rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
          value={def.calculationMethod}
          onChange={(e) => {
            const method = e.target.value;
            const patch: Parameters<typeof mergeDefinitionDefaultsJson>[1] = { calculationMethod: method };
            if (method === "FORMULA" && def.formulaMode !== CRITERIA_RULES_MODE && !def.formulaExpression) {
              patch.formulaMode = CRITERIA_RULES_MODE;
              patch.formulaRules = BASE_SALARY_CRITERIA_RULES;
              patch.defaultFormulaExpression = DEFAULT_FORMULA_EXPRESSION;
              patch.formulaExpression = null;
            }
            patchJson(patch);
          }}
        >
          {CALCULATION_METHOD_OPTIONS.map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </select>
      </label>

      <p className="text-xs text-muted">{t("platformWageComponentTemplates.formula.compositeHint")}</p>

      <WageComponentCriteriaFormulaEditor
        validateScope="platform"
        calculationMethod={def.calculationMethod}
        formulaMode={def.formulaMode}
        formulaRules={def.formulaRules}
        defaultFormulaExpression={def.defaultFormulaExpression}
        formulaExpression={def.formulaExpression}
        percentageBase={def.percentageBase}
        roundingStrategy={def.roundingStrategy}
        dependencyComponentCodes={depCodes}
        onPatch={patchJson}
        t={t}
      />

      <div className="rounded border border-border bg-surface-alt">
        <button
          type="button"
          className="flex w-full items-center justify-between px-3 py-2 text-left text-sm font-medium text-foreground hover:bg-surface"
          onClick={() => setShowAdvancedJson((v) => !v)}
        >
          {t("platformWageComponentTemplates.formula.advancedJson")}
          <span className="text-muted">{showAdvancedJson ? "−" : "+"}</span>
        </button>
        {showAdvancedJson ? (
          <div className="border-t border-border px-3 pb-3 pt-2">
            <label className="block space-y-1 text-sm">
              <span className="text-muted">{t("platformWageComponentTemplates.label.definitionDefaultsJson")}</span>
              <textarea
                required
                className="min-h-[240px] w-full rounded border border-border bg-background px-3 py-2 font-mono text-xs text-foreground"
                value={definitionDefaultsJson}
                onChange={(e) => onRawJsonChange(e.target.value)}
                spellCheck={false}
              />
              {jsonError ? <span className="block text-xs text-destructive">{jsonError}</span> : null}
              <span className="block text-xs text-muted">{t("platformWageComponentTemplates.helper.definitionJson")}</span>
            </label>
          </div>
        ) : null}
      </div>
    </div>
  );
}
