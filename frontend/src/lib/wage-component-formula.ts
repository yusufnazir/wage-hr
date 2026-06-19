import type { FormulaRuleRow } from "@/lib/wage-component-definition";

/** Allowlisted identifiers — must match {@code WageComponentFormulaValidator} in backend. */
export const WAGE_COMPONENT_FORMULA_REFS = [
  "compensation.periodic_rate",
  "compensation.hourly_rate",
  "compensation.is_hourly",
  "transaction.quantity",
  "transaction.rate",
  "transaction.amount",
  "definition.default_amount",
] as const;

export type WageComponentFormulaRef = (typeof WAGE_COMPONENT_FORMULA_REFS)[number];

/** Default for template 1001 (base salary): periodic pay vs hours × hourly rate. */
export const BASE_SALARY_FORMULA =
  "if(compensation.is_hourly, transaction.quantity * transaction.rate, compensation.periodic_rate)";

export const DEFAULT_FORMULA_EXPRESSION = "compensation.periodic_rate";

/** Base salary as criteria rules (wage type → formula). */
export const BASE_SALARY_CRITERIA_RULES: FormulaRuleRow[] = [
  {
    criteriaType: "WAGE_TYPE",
    itemKey: "PER_HOUR",
    itemLabel: "Per hour",
    formulaExpression: "transaction.quantity * transaction.rate",
  },
  {
    criteriaType: "WAGE_TYPE",
    itemKey: "PER_PERIOD",
    itemLabel: "Per period",
    formulaExpression: "compensation.periodic_rate",
  },
];

export const WAGE_COMPONENT_FORMULA_PRESETS: { key: string; expression: string }[] = [
  { key: "presetBaseSalaryRules", expression: DEFAULT_FORMULA_EXPRESSION },
  { key: "presetPeriodicRate", expression: "compensation.periodic_rate" },
  { key: "presetHoursTimesRate", expression: "transaction.quantity * transaction.rate" },
  { key: "presetOvertime150", expression: "transaction.quantity * compensation.hourly_rate * 1.5" },
  { key: "presetOvertime200", expression: "transaction.quantity * compensation.hourly_rate * 2.0" },
  { key: "presetOvertime300", expression: "transaction.quantity * compensation.hourly_rate * 3.0" },
  { key: "presetDefaultAmount", expression: "definition.default_amount" },
  {
    key: "presetCompositeExample",
    expression:
      'component("1001").amount * 0.10 + (component("1002").amount * 0.20) * (compensation.periodic_rate * 0.10) + transaction.rate * transaction.quantity',
  },
];

const COMPONENT_REF = /component\s*\(\s*"([^"]+)"\s*\)\s*\.amount/g;

export function extractComponentCodesFromFormula(expression: string | null | undefined): string[] {
  if (!expression) return [];
  const codes = new Set<string>();
  let m: RegExpExecArray | null;
  const re = new RegExp(COMPONENT_REF.source, "g");
  while ((m = re.exec(expression)) !== null) {
    const code = m[1]?.trim();
    if (code) codes.add(code);
  }
  return [...codes];
}

export function extractComponentCodesFromRules(
  rules: FormulaRuleRow[],
  defaultFormulaExpression: string | null | undefined,
  legacyFormulaExpression?: string | null,
): string[] {
  const codes = new Set<string>();
  for (const expr of [
    defaultFormulaExpression,
    legacyFormulaExpression,
    ...rules.map((r) => r.formulaExpression),
  ]) {
    for (const c of extractComponentCodesFromFormula(expr)) {
      codes.add(c);
    }
  }
  return [...codes];
}
