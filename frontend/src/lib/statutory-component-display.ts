import type { TenantFormulaPreviewLine, TenantWageComponentItem } from "@/lib/api";

/**
 * Suriname platform statutory slots (engine) map to tenant template codes (payslip rows).
 * @see backend SurinameStatutoryContributor (WAGE_TAX, SOCIAL_PREMIUM_EE)
 */
export const SURINAME_PLATFORM_STATUTORY_TEMPLATE_CODES: Record<string, string> = {
  WAGE_TAX: "1019",
  SOCIAL_PREMIUM_EE: "1012",
};

export function resolveTenantWageComponentIdForPreviewLine(
  line: TenantFormulaPreviewLine,
  wageComponents: TenantWageComponentItem[],
): string | null {
  if (line.tenantWageComponentId) {
    return line.tenantWageComponentId;
  }
  if (line.componentSource !== "PLATFORM") {
    return null;
  }
  const templateCode = SURINAME_PLATFORM_STATUTORY_TEMPLATE_CODES[line.tenantWageComponentCode];
  if (!templateCode) {
    return null;
  }
  const match = wageComponents.find(
    (c) => c.templateCode === templateCode || c.code === templateCode,
  );
  return match?.id ?? null;
}
