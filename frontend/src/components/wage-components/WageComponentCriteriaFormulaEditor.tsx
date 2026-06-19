"use client";

import { useCallback, useMemo, useState } from "react";

import { FormulaValidatePanel } from "@/components/payroll/FormulaValidatePanel";
import { WageComponentFormulaEditor } from "@/components/wage-components/WageComponentFormulaEditor";
import {
  CRITERIA_RULES_MODE,
  FORMULA_CRITERIA_TYPE_OPTIONS,
  formatFormulaRuleSummary,
  type DefinitionDefaultsPatch,
  type FormulaCriteriaType,
  type FormulaRuleRow,
  WAGE_TYPE_CRITERIA_OPTIONS,
} from "@/lib/wage-component-definition";
import {
  BASE_SALARY_CRITERIA_RULES,
  DEFAULT_FORMULA_EXPRESSION,
  extractComponentCodesFromRules,
} from "@/lib/wage-component-formula";

type Props = {
  calculationMethod: string;
  formulaMode: string | null;
  formulaRules: FormulaRuleRow[];
  defaultFormulaExpression: string | null;
  formulaExpression: string | null;
  percentageBase: string | null;
  roundingStrategy: string | null;
  dependencyComponentCodes?: string[];
  validateScope?: "tenant" | "platform";
  onPatch: (patch: DefinitionDefaultsPatch) => void;
  t: (key: string) => string;
};

function ruleSummary(rule: FormulaRuleRow, t: (key: string) => string): string {
  return formatFormulaRuleSummary(rule, t);
}

function newRule(): FormulaRuleRow {
  return {
    criteriaType: "WAGE_TYPE",
    itemKey: "PER_HOUR",
    itemLabel: "Per hour",
    formulaExpression: "transaction.quantity * transaction.rate",
  };
}

function CriteriaShell({ t, children }: { t: (key: string) => string; children: React.ReactNode }) {
  return (
    <div className="space-y-3 rounded border border-border bg-surface-alt p-4">
      <div className="text-sm font-medium text-foreground">{t("wageComponents.formula.criteria.title")}</div>
      <p className="text-xs text-muted">{t("wageComponents.formula.criteria.intro")}</p>
      {children}
    </div>
  );
}

export function WageComponentCriteriaFormulaEditor({
  calculationMethod,
  formulaMode,
  formulaRules,
  defaultFormulaExpression,
  formulaExpression,
  percentageBase,
  roundingStrategy,
  dependencyComponentCodes = [],
  validateScope = "platform",
  onPatch,
  t,
}: Props) {
  const criteriaActive = formulaMode === CRITERIA_RULES_MODE && formulaRules.length > 0;
  const [selectedIndex, setSelectedIndex] = useState(0);

  const depCodes = useMemo(() => {
    const fromRules = extractComponentCodesFromRules(
      formulaRules,
      defaultFormulaExpression,
      formulaExpression,
    );
    return [...new Set([...dependencyComponentCodes, ...fromRules])];
  }, [dependencyComponentCodes, defaultFormulaExpression, formulaExpression, formulaRules]);

  const safeSelectedIndex =
    formulaRules.length === 0 ? -1 : Math.min(Math.max(selectedIndex, 0), formulaRules.length - 1);
  const selectedRule = safeSelectedIndex >= 0 ? formulaRules[safeSelectedIndex] : null;

  const applyCriteriaRules = useCallback(
    (rules: FormulaRuleRow[], defaultExpr: string) => {
      onPatch({
        formulaMode: CRITERIA_RULES_MODE,
        formulaRules: rules,
        defaultFormulaExpression: defaultExpr,
        formulaExpression: null,
      });
    },
    [onPatch],
  );

  const updateRule = useCallback(
    (index: number, patch: Partial<FormulaRuleRow>) => {
      const next = formulaRules.map((r, i) => (i === index ? { ...r, ...patch } : r));
      onPatch({ formulaRules: next, formulaMode: CRITERIA_RULES_MODE });
    },
    [formulaRules, onPatch],
  );

  const moveRule = useCallback(
    (index: number, direction: -1 | 1) => {
      const target = index + direction;
      if (target < 0 || target >= formulaRules.length) return;
      const next = [...formulaRules];
      [next[index], next[target]] = [next[target], next[index]];
      onPatch({ formulaRules: next, formulaMode: CRITERIA_RULES_MODE });
      setSelectedIndex(target);
    },
    [formulaRules, onPatch],
  );

  const removeRule = useCallback(
    (index: number) => {
      const next = formulaRules.filter((_, i) => i !== index);
      onPatch({
        formulaRules: next,
        formulaMode: next.length > 0 ? CRITERIA_RULES_MODE : null,
      });
      setSelectedIndex(Math.max(0, index - 1));
    },
    [formulaRules, onPatch],
  );

  const addRule = useCallback(() => {
    const next = [...formulaRules, newRule()];
    applyCriteriaRules(next, defaultFormulaExpression ?? DEFAULT_FORMULA_EXPRESSION);
    setSelectedIndex(next.length - 1);
  }, [applyCriteriaRules, defaultFormulaExpression, formulaRules]);

  const applyBaseSalaryPreset = useCallback(() => {
    applyCriteriaRules(BASE_SALARY_CRITERIA_RULES, DEFAULT_FORMULA_EXPRESSION);
    setSelectedIndex(0);
  }, [applyCriteriaRules]);

  if (calculationMethod !== "FORMULA") {
    return (
      <WageComponentFormulaEditor
        validateScope={validateScope}
        calculationMethod={calculationMethod}
        formulaExpression={formulaExpression}
        percentageBase={percentageBase}
        roundingStrategy={roundingStrategy}
        dependencyComponentCodes={depCodes}
        onFormulaChange={(v) => onPatch({ formulaExpression: v })}
        onPercentageBaseChange={(v) => onPatch({ percentageBase: v })}
        t={t}
      />
    );
  }

  if (!criteriaActive) {
    return (
      <CriteriaShell t={t}>
        <WageComponentFormulaEditor
          validateScope={validateScope}
          calculationMethod="FORMULA"
          formulaExpression={formulaExpression}
          percentageBase={percentageBase}
          roundingStrategy={roundingStrategy}
          dependencyComponentCodes={depCodes}
          onFormulaChange={(v) => onPatch({ formulaExpression: v })}
          onPercentageBaseChange={(v) => onPatch({ percentageBase: v })}
          t={t}
        />
        <button
          type="button"
          className="text-xs font-medium text-primary hover:underline"
          onClick={applyBaseSalaryPreset}
        >
          {t("wageComponents.formula.criteria.switchToRules")}
        </button>
      </CriteriaShell>
    );
  }

  const defaultExpr = defaultFormulaExpression ?? "";

  return (
    <CriteriaShell t={t}>
      <p className="text-xs text-muted">{t("wageComponents.formula.criteria.firstMatchHint")}</p>
      <div className="grid gap-4 lg:grid-cols-[minmax(200px,280px)_1fr]">
        <div className="space-y-3">
          <div className="text-xs font-medium text-muted">{t("wageComponents.formula.criteria.rulesList")}</div>
          <ul className="space-y-1">
            {formulaRules.map((rule, index) => (
              <li key={`${rule.criteriaType}-${rule.itemKey}-${index}`}>
                <RuleCard
                  index={index}
                  rule={rule}
                  selected={index === safeSelectedIndex}
                  isFirst={index === 0}
                  isLast={index === formulaRules.length - 1}
                  onSelect={() => setSelectedIndex(index)}
                  onMoveUp={() => moveRule(index, -1)}
                  onMoveDown={() => moveRule(index, 1)}
                  onRemove={() => {
                    if (window.confirm(t("wageComponents.formula.criteria.removeConfirm"))) {
                      removeRule(index);
                    }
                  }}
                  t={t}
                />
              </li>
            ))}
          </ul>
          <button
            type="button"
            className="w-full rounded border border-dashed border-border px-2 py-1.5 text-xs font-medium text-foreground hover:bg-surface"
            onClick={addRule}
          >
            {t("wageComponents.formula.criteria.addRule")}
          </button>

          <label className="block space-y-1 text-sm">
            <span className="text-xs font-medium text-muted">{t("wageComponents.formula.criteria.defaultLabel")}</span>
            <textarea
              className="min-h-[72px] w-full rounded border border-border bg-background px-2 py-1.5 font-mono text-xs text-foreground"
              value={defaultExpr}
              onChange={(e) =>
                onPatch({
                  defaultFormulaExpression: e.target.value === "" ? null : e.target.value,
                  formulaMode: CRITERIA_RULES_MODE,
                })
              }
              spellCheck={false}
            />
          </label>
        </div>

        <div className="space-y-3">
          {selectedRule ? (
            <>
              <RuleCriteriaFields
                rule={selectedRule}
                onChange={(patch) => updateRule(safeSelectedIndex, patch)}
                t={t}
              />
              <WageComponentFormulaEditor
                validateScope={validateScope}
                calculationMethod="FORMULA"
                formulaExpression={selectedRule.formulaExpression}
                percentageBase={percentageBase}
                roundingStrategy={roundingStrategy}
                dependencyComponentCodes={depCodes}
                showValidatePanel={false}
                onFormulaChange={(v) => updateRule(safeSelectedIndex, { formulaExpression: v ?? "" })}
                onPercentageBaseChange={() => {}}
                t={t}
              />
            </>
          ) : (
            <p className="text-sm text-muted">{t("wageComponents.formula.criteria.noRules")}</p>
          )}

          {defaultExpr || selectedRule ? (
            <div className="rounded border border-border bg-background p-3">
              <ValidateDefaultLabel
                t={t}
                label={
                  selectedRule
                    ? formatFormulaRuleSummary(selectedRule, t)
                    : t("wageComponents.formula.criteria.defaultLabel")
                }
              />
              <FormulaValidatePanel
                scope={validateScope}
                calculationMethod="FORMULA"
                formulaExpression={selectedRule?.formulaExpression ?? defaultExpr}
                percentageBase={percentageBase}
                roundingStrategy={roundingStrategy}
                componentCodes={depCodes}
                t={t}
              />
            </div>
          ) : null}
        </div>
      </div>
    </CriteriaShell>
  );
}

type RuleCardProps = {
  index: number;
  rule: FormulaRuleRow;
  selected: boolean;
  isFirst: boolean;
  isLast: boolean;
  onSelect: () => void;
  onMoveUp: () => void;
  onMoveDown: () => void;
  onRemove: () => void;
  t: (key: string) => string;
};

function RuleCard(props: RuleCardProps) {
  const { index, rule, selected, isFirst, isLast, onSelect, onMoveUp, onMoveDown, onRemove, t } = props;
  return (
    <div
      className={`flex items-start gap-1 rounded border px-2 py-1.5 text-xs ${
        selected ? "border-primary bg-primary/5 text-foreground" : "border-border bg-background text-foreground"
      }`}
    >
      <button type="button" className="min-w-0 flex-1 text-left" onClick={onSelect}>
        <span className="text-muted">{index + 1}.</span> {ruleSummary(rule, t)}
      </button>
      <div className="flex shrink-0 flex-col gap-0.5">
        <button
          type="button"
          className="rounded px-1 text-muted hover:bg-surface hover:text-foreground disabled:opacity-30"
          disabled={isFirst}
          onClick={onMoveUp}
          aria-label={t("wageComponents.formula.criteria.moveUp")}
        >
          ↑
        </button>
        <button
          type="button"
          className="rounded px-1 text-muted hover:bg-surface hover:text-foreground disabled:opacity-30"
          disabled={isLast}
          onClick={onMoveDown}
          aria-label={t("wageComponents.formula.criteria.moveDown")}
        >
          ↓
        </button>
        <button
          type="button"
          className="rounded px-1 text-destructive hover:bg-destructive/10"
          onClick={onRemove}
          aria-label={t("wageComponents.formula.criteria.remove")}
        >
          ×
        </button>
      </div>
    </div>
  );
}

function RuleCriteriaFields({
  rule,
  onChange,
  t,
}: {
  rule: FormulaRuleRow;
  onChange: (patch: Partial<FormulaRuleRow>) => void;
  t: (key: string) => string;
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <label className="block space-y-1 text-sm">
        <span className="text-muted">{t("wageComponents.formula.criteria.when")}</span>
        <select
          className="w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
          value={rule.criteriaType}
          onChange={(e) => {
            const criteriaType = e.target.value as FormulaCriteriaType;
            const patch: Partial<FormulaRuleRow> = { criteriaType };
            if (criteriaType === "WAGE_TYPE") {
              patch.itemKey = "PER_HOUR";
              patch.itemLabel = "Per hour";
            } else {
              patch.itemKey = "";
              patch.itemLabel = null;
            }
            onChange(patch);
          }}
        >
          {FORMULA_CRITERIA_TYPE_OPTIONS.map((o) => (
            <option key={o.key} value={o.key}>
              {t(o.labelKey)}
            </option>
          ))}
        </select>
      </label>

      <label className="block space-y-1 text-sm">
        <span className="text-muted">{t("wageComponents.formula.criteria.value")}</span>
        {rule.criteriaType === "WAGE_TYPE" ? (
          <select
            className="w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
            value={rule.itemKey}
            onChange={(e) => {
              const itemKey = e.target.value;
              const labelKey = WAGE_TYPE_CRITERIA_OPTIONS.find((o) => o.key === itemKey)?.labelKey;
              onChange({
                itemKey,
                itemLabel: labelKey ? t(labelKey) : itemKey,
              });
            }}
          >
            {WAGE_TYPE_CRITERIA_OPTIONS.map((o) => (
              <option key={o.key} value={o.key}>
                {t(o.labelKey)}
              </option>
            ))}
          </select>
        ) : (
          <>
            <input
              className="w-full rounded border border-border bg-background px-2 py-1.5 font-mono text-sm uppercase"
              value={rule.itemKey}
              onChange={(e) =>
                onChange({
                  itemKey: e.target.value.trim().toUpperCase(),
                  itemLabel: e.target.value.trim().toUpperCase(),
                })
              }
              placeholder="OPS"
              spellCheck={false}
            />
            <span className="block text-xs text-muted">{t("wageComponents.formula.criteria.codeHint")}</span>
          </>
        )}
      </label>
    </div>
  );
}

function ValidateDefaultLabel({ t, label }: { t: (key: string) => string; label: string }) {
  return (
    <div className="mb-2 text-xs font-medium text-muted">
      {t("wageComponents.formula.validate.title")}: {label}
    </div>
  );
}
