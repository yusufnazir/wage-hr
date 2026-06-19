/** Parse platform template {@code definitionDefaultsJson} for formula validate UI. */
export type FormulaCriteriaType = "WAGE_TYPE" | "DEPARTMENT" | "JOB";

export type FormulaRuleRow = {
  criteriaType: FormulaCriteriaType;
  itemKey: string;
  itemLabel?: string | null;
  formulaExpression: string;
};

export type ParsedDefinitionDefaults = {
  calculationMethod: string;
  formulaMode: string | null;
  formulaRules: FormulaRuleRow[];
  defaultFormulaExpression: string | null;
  formulaExpression: string | null;
  percentageBase: string | null;
  roundingStrategy: string | null;
};

export const CRITERIA_RULES_MODE = "CRITERIA_RULES";

export const CALCULATION_METHOD_OPTIONS = [
  "FIXED_AMOUNT",
  "MANUAL_INPUT",
  "HOURLY",
  "PERCENTAGE",
  "FORMULA",
] as const;

export const WAGE_TYPE_CRITERIA_OPTIONS: { key: string; labelKey: string }[] = [
  { key: "PER_HOUR", labelKey: "wageComponents.formula.criteria.wageType.perHour" },
  { key: "PER_PERIOD", labelKey: "wageComponents.formula.criteria.wageType.perPeriod" },
];

export const FORMULA_CRITERIA_TYPE_OPTIONS: { key: FormulaCriteriaType; labelKey: string }[] = [
  { key: "WAGE_TYPE", labelKey: "wageComponents.formula.criteria.type.wageType" },
  { key: "DEPARTMENT", labelKey: "wageComponents.formula.criteria.type.department" },
  { key: "JOB", labelKey: "wageComponents.formula.criteria.type.job" },
];

/** Example composite formula from product docs (codes 1001 / 1002). */
export const COMPOSITE_FORMULA_EXAMPLE =
  'component("1001").amount * 0.10 + (component("1002").amount * 0.20) * (compensation.periodic_rate * 0.10) + transaction.rate * transaction.quantity';

function parseFormulaRules(raw: unknown): FormulaRuleRow[] {
  if (!Array.isArray(raw)) return [];
  const out: FormulaRuleRow[] = [];
  for (const item of raw) {
    if (!item || typeof item !== "object") continue;
    const o = item as Record<string, unknown>;
    const criteriaType = o.criteriaType;
    const itemKey = o.itemKey;
    const formulaExpression = o.formulaExpression;
    if (typeof criteriaType !== "string" || typeof itemKey !== "string" || typeof formulaExpression !== "string") {
      continue;
    }
    if (!["WAGE_TYPE", "DEPARTMENT", "JOB"].includes(criteriaType)) continue;
    out.push({
      criteriaType: criteriaType as FormulaCriteriaType,
      itemKey,
      itemLabel: typeof o.itemLabel === "string" ? o.itemLabel : null,
      formulaExpression,
    });
  }
  return out;
}

export function parseDefinitionDefaultsJson(raw: string | null | undefined): ParsedDefinitionDefaults {
  const fallback: ParsedDefinitionDefaults = {
    calculationMethod: "FIXED_AMOUNT",
    formulaMode: null,
    formulaRules: [],
    defaultFormulaExpression: null,
    formulaExpression: null,
    percentageBase: null,
    roundingStrategy: "HALF_UP",
  };
  if (!raw?.trim()) return fallback;
  try {
    const o = JSON.parse(raw) as Record<string, unknown>;
    return {
      calculationMethod: typeof o.calculationMethod === "string" ? o.calculationMethod : fallback.calculationMethod,
      formulaMode: typeof o.formulaMode === "string" ? o.formulaMode : null,
      formulaRules: parseFormulaRules(o.formulaRules),
      defaultFormulaExpression:
        typeof o.defaultFormulaExpression === "string" ? o.defaultFormulaExpression : null,
      formulaExpression: typeof o.formulaExpression === "string" ? o.formulaExpression : null,
      percentageBase: typeof o.percentageBase === "string" ? o.percentageBase : null,
      roundingStrategy: typeof o.roundingStrategy === "string" ? o.roundingStrategy : fallback.roundingStrategy,
    };
  } catch {
    return fallback;
  }
}

export function isCriteriaRulesMode(def: ParsedDefinitionDefaults): boolean {
  return def.formulaMode === CRITERIA_RULES_MODE && def.formulaRules.length > 0;
}

export type ParsedStoredFormula = {
  formulaMode: string | null;
  formulaRules: FormulaRuleRow[];
  defaultFormulaExpression: string | null;
  legacyFormulaExpression: string | null;
};

export function parseStoredFormulaExpression(raw: string | null | undefined): ParsedStoredFormula {
  const empty: ParsedStoredFormula = {
    formulaMode: null,
    formulaRules: [],
    defaultFormulaExpression: null,
    legacyFormulaExpression: null,
  };
  if (!raw?.trim()) return empty;
  const trimmed = raw.trim();
  if (trimmed.startsWith("{")) {
    try {
      const o = JSON.parse(trimmed) as Record<string, unknown>;
      if (o.formulaMode === CRITERIA_RULES_MODE || o.formulaRules) {
        return {
          formulaMode: typeof o.formulaMode === "string" ? o.formulaMode : CRITERIA_RULES_MODE,
          formulaRules: parseFormulaRules(o.formulaRules),
          defaultFormulaExpression:
            typeof o.defaultFormulaExpression === "string" ? o.defaultFormulaExpression : null,
          legacyFormulaExpression: typeof o.formulaExpression === "string" ? o.formulaExpression : null,
        };
      }
    } catch {
      /* legacy string */
    }
  }
  return { ...empty, legacyFormulaExpression: trimmed };
}

export function isStoredCriteriaRules(parsed: ParsedStoredFormula): boolean {
  return parsed.formulaMode === CRITERIA_RULES_MODE && parsed.formulaRules.length > 0;
}

export function formatFormulaRuleSummary(rule: FormulaRuleRow, t: (key: string) => string): string {
  const typeLabel = t(
    FORMULA_CRITERIA_TYPE_OPTIONS.find((o) => o.key === rule.criteriaType)?.labelKey ??
      "wageComponents.formula.criteria.type.wageType",
  );
  const valueLabel =
    rule.criteriaType === "WAGE_TYPE"
      ? t(
          WAGE_TYPE_CRITERIA_OPTIONS.find((o) => o.key === rule.itemKey)?.labelKey ??
            "wageComponents.formula.criteria.wageType.perHour",
        )
      : rule.itemLabel || rule.itemKey;
  return `${typeLabel} · ${valueLabel}`;
}

function parseDefinitionObject(raw: string): Record<string, unknown> {
  if (!raw?.trim()) return {};
  try {
    const o = JSON.parse(raw) as unknown;
    return o && typeof o === "object" && !Array.isArray(o) ? (o as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

export type FormulaEditorState = {
  formulaMode: string | null;
  formulaRules: FormulaRuleRow[];
  defaultFormulaExpression: string | null;
  formulaExpression: string | null;
};

export function formulaEditorStateFromStored(raw: string | null | undefined): FormulaEditorState {
  const parsed = parseStoredFormulaExpression(raw);
  if (isStoredCriteriaRules(parsed)) {
    return {
      formulaMode: CRITERIA_RULES_MODE,
      formulaRules: parsed.formulaRules,
      defaultFormulaExpression: parsed.defaultFormulaExpression,
      formulaExpression: null,
    };
  }
  const legacy = parsed.legacyFormulaExpression ?? raw?.trim() ?? null;
  return {
    formulaMode: null,
    formulaRules: [],
    defaultFormulaExpression: null,
    formulaExpression: legacy,
  };
}

export function patchFormulaEditorState(
  state: FormulaEditorState,
  patch: DefinitionDefaultsPatch,
): FormulaEditorState {
  return {
    formulaMode: patch.formulaMode !== undefined ? patch.formulaMode : state.formulaMode,
    formulaRules: patch.formulaRules !== undefined ? patch.formulaRules : state.formulaRules,
    defaultFormulaExpression:
      patch.defaultFormulaExpression !== undefined
        ? patch.defaultFormulaExpression
        : state.defaultFormulaExpression,
    formulaExpression:
      patch.formulaExpression !== undefined ? patch.formulaExpression : state.formulaExpression,
  };
}

/** Serialize tenant formula for API save (compact criteria JSON or legacy string). */
export function buildStoredFormulaExpression(state: FormulaEditorState): string | null {
  if (state.formulaMode === CRITERIA_RULES_MODE && state.formulaRules.length > 0) {
    const defaultExpr = state.defaultFormulaExpression?.trim() ?? "";
    const rules = state.formulaRules
      .filter((r) => r.formulaExpression?.trim())
      .filter((r) => !defaultExpr || r.formulaExpression.trim() !== defaultExpr)
      .map((r) => ({
        criteriaType: r.criteriaType,
        itemKey: r.itemKey,
        formulaExpression: r.formulaExpression.trim(),
      }));
    const payload: Record<string, unknown> = {
      formulaMode: CRITERIA_RULES_MODE,
      formulaRules: rules,
    };
    if (defaultExpr) {
      payload.defaultFormulaExpression = defaultExpr;
    }
    return JSON.stringify(payload);
  }
  const legacy = state.formulaExpression?.trim() ?? state.defaultFormulaExpression?.trim() ?? "";
  return legacy === "" ? null : legacy;
}

export type DefinitionDefaultsPatch = {
  calculationMethod?: string;
  formulaMode?: string | null;
  formulaRules?: FormulaRuleRow[];
  defaultFormulaExpression?: string | null;
  formulaExpression?: string | null;
  percentageBase?: string | null;
  roundingStrategy?: string | null;
};

/** Merge formula-related fields into definition JSON; preserves other keys; pretty-prints. */
export function mergeDefinitionDefaultsJson(raw: string, patch: DefinitionDefaultsPatch): string {
  const obj = parseDefinitionObject(raw);
  if (patch.calculationMethod !== undefined) {
    obj.calculationMethod = patch.calculationMethod;
  }
  if (patch.formulaMode !== undefined) {
    if (patch.formulaMode == null || patch.formulaMode === "") {
      delete obj.formulaMode;
    } else {
      obj.formulaMode = patch.formulaMode;
    }
  }
  if (patch.formulaRules !== undefined) {
    if (patch.formulaRules.length === 0) {
      delete obj.formulaRules;
    } else {
      obj.formulaRules = patch.formulaRules;
    }
  }
  if (patch.defaultFormulaExpression !== undefined) {
    if (patch.defaultFormulaExpression == null || patch.defaultFormulaExpression === "") {
      delete obj.defaultFormulaExpression;
    } else {
      obj.defaultFormulaExpression = patch.defaultFormulaExpression;
    }
  }
  if (patch.formulaExpression !== undefined) {
    if (patch.formulaExpression == null || patch.formulaExpression === "") {
      delete obj.formulaExpression;
    } else {
      obj.formulaExpression = patch.formulaExpression;
    }
  }
  if (patch.percentageBase !== undefined) {
    if (patch.percentageBase == null || patch.percentageBase === "") {
      delete obj.percentageBase;
    } else {
      obj.percentageBase = patch.percentageBase;
    }
  }
  if (patch.roundingStrategy !== undefined) {
    obj.roundingStrategy = patch.roundingStrategy;
  }
  return JSON.stringify(obj, null, 2);
}
