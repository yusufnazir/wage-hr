"use client";

import { useCallback, useMemo } from "react";

import { WageComponentCriteriaFormulaEditor } from "@/components/wage-components/WageComponentCriteriaFormulaEditor";
import {
  patchFormulaEditorState,
  type DefinitionDefaultsPatch,
  type FormulaEditorState,
} from "@/lib/wage-component-definition";
import { extractComponentCodesFromRules } from "@/lib/wage-component-formula";

type Props = {
  calculationMethod: string;
  percentageBase: string | null;
  roundingStrategy: string | null;
  formulaState: FormulaEditorState;
  onFormulaStateChange: (state: FormulaEditorState) => void;
  t: (key: string) => string;
};

export function TenantWageComponentFormulaEditor({
  calculationMethod,
  percentageBase,
  roundingStrategy,
  formulaState,
  onFormulaStateChange,
  t,
}: Props) {
  const depCodes = useMemo(() => {
    const fromRules = extractComponentCodesFromRules(
      formulaState.formulaRules,
      formulaState.defaultFormulaExpression,
      formulaState.formulaExpression,
    );
    return fromRules;
  }, [formulaState.defaultFormulaExpression, formulaState.formulaExpression, formulaState.formulaRules]);

  const onPatch = useCallback(
    (patch: DefinitionDefaultsPatch) => {
      onFormulaStateChange(patchFormulaEditorState(formulaState, patch));
    },
    [formulaState, onFormulaStateChange],
  );

  return (
    <WageComponentCriteriaFormulaEditor
      validateScope="tenant"
      calculationMethod={calculationMethod}
      formulaMode={formulaState.formulaMode}
      formulaRules={formulaState.formulaRules}
      defaultFormulaExpression={formulaState.defaultFormulaExpression}
      formulaExpression={formulaState.formulaExpression}
      percentageBase={percentageBase}
      roundingStrategy={roundingStrategy}
      dependencyComponentCodes={depCodes}
      onPatch={onPatch}
      t={t}
    />
  );
}
