"use client";

import { useState } from "react";

import {
  formatFormulaRuleSummary,
  isStoredCriteriaRules,
  parseStoredFormulaExpression,
  type FormulaRuleRow,
} from "@/lib/wage-component-definition";

type Props = {
  formulaExpression: string | null | undefined;
  t: (key: string) => string;
  fromTemplate?: boolean;
};

function FormulaMono({ children }: { children: string }) {
  return (
    <code className="block overflow-x-auto rounded border border-border bg-background px-2 py-1.5 font-mono text-[11px] leading-relaxed text-foreground">
      {children}
    </code>
  );
}

function ReadOnlyRuleCard({
  index,
  rule,
  selected,
  onSelect,
  t,
}: {
  index: number;
  rule: FormulaRuleRow;
  selected: boolean;
  onSelect: () => void;
  t: (key: string) => string;
}) {
  return (
    <button
      type="button"
      className={`w-full rounded border px-2 py-1.5 text-left text-xs transition-colors ${
        selected ? "border-primary bg-primary/5 text-foreground" : "border-border bg-background text-foreground hover:bg-surface-alt"
      }`}
      onClick={onSelect}
    >
      <span className="text-muted">{index + 1}.</span> {formatFormulaRuleSummary(rule, t)}
    </button>
  );
}

export function WageComponentFormulaRulesReadOnly({ formulaExpression, t, fromTemplate = false }: Props) {
  const parsed = parseStoredFormulaExpression(formulaExpression);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [showDefault, setShowDefault] = useState(false);

  if (!formulaExpression?.trim()) {
    return null;
  }

  if (isStoredCriteriaRules(parsed)) {
    const rules = parsed.formulaRules;
    const defaultExpr = parsed.defaultFormulaExpression ?? "";
    const safeIndex = rules.length === 0 ? -1 : Math.min(Math.max(selectedIndex, 0), rules.length - 1);
    const selectedRule = !showDefault && safeIndex >= 0 ? rules[safeIndex] : null;

    return (
      <div className="space-y-3 rounded border border-border bg-surface-alt p-4">
        <div>
          <div className="text-sm font-medium text-foreground">{t("wageComponents.formula.criteria.title")}</div>
          <p className="mt-0.5 text-xs text-muted">
            {fromTemplate
              ? t("wageComponents.formula.criteria.readOnlyFromTemplate")
              : t("wageComponents.formula.criteria.firstMatchHint")}
          </p>
        </div>

        <div className="grid gap-4 lg:grid-cols-[minmax(200px,280px)_1fr]">
          <div className="space-y-3">
            <div className="text-xs font-medium text-muted">{t("wageComponents.formula.criteria.rulesList")}</div>
            <ul className="space-y-1">
              {rules.map((rule, index) => (
                <li key={`${rule.criteriaType}-${rule.itemKey}-${index}`}>
                  <ReadOnlyRuleCard
                    index={index}
                    rule={rule}
                    selected={!showDefault && index === safeIndex}
                    onSelect={() => {
                      setShowDefault(false);
                      setSelectedIndex(index);
                    }}
                    t={t}
                  />
                </li>
              ))}
            </ul>

            {defaultExpr ? (
              <div className="space-y-1 border-t border-border pt-3">
                <button
                  type="button"
                  className={`w-full rounded border px-2 py-1.5 text-left text-xs transition-colors ${
                    showDefault
                      ? "border-primary bg-primary/5 text-foreground"
                      : "border-border bg-background text-muted hover:bg-surface-alt hover:text-foreground"
                  }`}
                  onClick={() => setShowDefault(true)}
                >
                  {t("wageComponents.formula.criteria.defaultLabel")}
                </button>
              </div>
            ) : null}
          </div>

          <div className="space-y-3">
            {showDefault && defaultExpr ? (
              <div className="space-y-2">
                <div className="text-xs font-medium text-muted">{t("wageComponents.formula.criteria.defaultLabel")}</div>
                <FormulaMono>{defaultExpr}</FormulaMono>
              </div>
            ) : selectedRule ? (
              <div className="space-y-2">
                <div className="text-xs font-medium text-foreground">{formatFormulaRuleSummary(selectedRule, t)}</div>
                <FormulaMono>{selectedRule.formulaExpression}</FormulaMono>
              </div>
            ) : (
              <p className="text-sm text-muted">{t("wageComponents.formula.criteria.noRules")}</p>
            )}
          </div>
        </div>
      </div>
    );
  }

  const expr = parsed.legacyFormulaExpression ?? formulaExpression.trim();
  return (
    <div className="space-y-1 rounded border border-border bg-surface-alt p-4">
      <div className="text-xs font-medium text-muted">{t("wageComponents.label.formulaExpression")}</div>
      <FormulaMono>{expr}</FormulaMono>
    </div>
  );
}
