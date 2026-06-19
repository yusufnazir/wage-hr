import type { TenantWageComponentItem } from "@/lib/api";

/** Matches {@code SurinameSpecialRemunerationSupport.DEFAULT_ATTRIBUTION_PERIODS}. */
export const SURINAME_DEFAULT_ART17_ATTRIBUTION_PERIODS = 12;

/** Tenant components whose factor column shows art. 17 N (aantal loontijdvakken) — gross earners only, not wage-tax lines. */
export const SURINAME_ART17_ATTRIBUTION_RULE_KEYS = new Set([
  "SUR_VACATION_ALLOWANCE",
  "SUR_BONUS",
  "SUR_EXTRA_EARNINGS",
]);

export function usesArt17AttributionFactor(countryRuleKey: string | null | undefined): boolean {
  return countryRuleKey != null && SURINAME_ART17_ATTRIBUTION_RULE_KEYS.has(countryRuleKey);
}

/** Component 1008: standing factor = number of children; gross = children × rate (Art. 10(h) cap applies on 1023). */
export function usesChildAllowanceQuantityFactor(countryRuleKey: string | null | undefined): boolean {
  return countryRuleKey === "SUR_CHILD_ALLOWANCE";
}

export function resolveArt17AttributionPeriods(
  payrollCountry: string | null | undefined,
  employeeId: string | null | undefined,
  fromPreview: Record<string, number> | undefined,
): number | null {
  if (payrollCountry?.toUpperCase() !== "SR") return null;
  if (employeeId && fromPreview?.[employeeId] != null) {
    return fromPreview[employeeId];
  }
  return SURINAME_DEFAULT_ART17_ATTRIBUTION_PERIODS;
}

export type ComponentCalcFactorAmount = {
  factor: string | null;
  amount: string | null;
};

export function applyArt17AttributionToCalcDisplay(
  byComponent: Map<string, ComponentCalcFactorAmount>,
  wageComponents: TenantWageComponentItem[],
  attributionPeriods: number,
): void {
  const factor = String(attributionPeriods);
  for (const wc of wageComponents) {
    if (!usesArt17AttributionFactor(wc.countryRuleKey)) continue;
    const existing = byComponent.get(wc.id) ?? { factor: null, amount: null };
    existing.factor = factor;
    byComponent.set(wc.id, existing);
  }
}
