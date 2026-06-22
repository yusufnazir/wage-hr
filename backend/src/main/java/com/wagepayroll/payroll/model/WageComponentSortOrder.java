package com.wagepayroll.payroll.model;

import java.util.Map;

/**
 * Canonical {@code processing_order} values for SR platform templates and tenant wage components.
 * Bands: {@link WageComponentSortBand}.
 */
public final class WageComponentSortOrder {

	private WageComponentSortOrder() {
	}

	/** Template code → processing order (band base + offset). */
	private static final Map<String, Integer> BY_TEMPLATE_CODE = Map.ofEntries(
			// Gross earnings (1000)
			Map.entry("1001", 1010),
			Map.entry("1002", 1020),
			Map.entry("1006", 1050),
			Map.entry("1007", 1060),
			Map.entry("1008", 1070),
			Map.entry("1009", 1080),
			Map.entry("1028", 1090),
			Map.entry("1030", 1100),
			// Gross deductions (2000)
			Map.entry("1032", 2010),
			Map.entry("1040", 2110),
			// Non-taxable earnings (3000)
			Map.entry("1031", 3010),
			Map.entry("1042", 3020),
			Map.entry("1049", 3030),
			Map.entry("1050", 3040),
			Map.entry("1051", 3050),
			Map.entry("1052", 3060),
			Map.entry("1053", 3070),
			Map.entry("1054", 3080),
			Map.entry("1057", 3090),
			Map.entry("1055", 1110),
			Map.entry("1058", 1130),
			Map.entry("1064", 1120),
			// Tax adjustments (4000)
			Map.entry("1004", 4010),
			Map.entry("1005", 4020),
			Map.entry("1037", 3980),
			Map.entry("1038", 3985),
			Map.entry("1043", 3990),
			Map.entry("1044", 3995),
			Map.entry("1034", 4030),
			Map.entry("1036", 4040),
			Map.entry("1035", 4050),
			// Statutory deductions (5000) — AOV then wage tax
			Map.entry("1010", 5010),
			Map.entry("1011", 5020),
			Map.entry("1012", 5030),
			Map.entry("1013", 5040),
			Map.entry("1014", 5050),
			Map.entry("1015", 5060),
			Map.entry("1016", 5070),
			Map.entry("1017", 5080),
			Map.entry("1018", 5090),
			Map.entry("1019", 5210),
			Map.entry("1020", 5220),
			Map.entry("1021", 5230),
			Map.entry("1022", 5240),
			Map.entry("1023", 5250),
			Map.entry("1024", 5260),
			Map.entry("1025", 5270),
			Map.entry("1048", 5280),
			Map.entry("1056", 5290),
			Map.entry("1059", 5310),
			Map.entry("1065", 5300),
			// Net deductions (6000)
			Map.entry("1003", 6010),
			Map.entry("1029", 6030),
			Map.entry("1033", 6040),
			Map.entry("1041", 6120),
			// Employer contributions (7000)
			Map.entry("1039", 7020),
			// System calculations (8000) — net wage last
			Map.entry("1026", 8010),
			Map.entry("1027", 8020));

	public static int forTemplateCode(String templateCode) {
		if (templateCode == null || templateCode.isBlank()) {
			return WageComponentSortBand.GROSS_EARNINGS.base() + 50;
		}
		return BY_TEMPLATE_CODE.getOrDefault(templateCode.trim(),
				classifyBand(ComponentType.EARNING, PayrollPhase.GROSS, "GENERAL", true).base() + 50);
	}

	public static int resolve(ComponentType componentType, PayrollPhase phase, String category, boolean taxableWageTax,
			String templateCode, Integer legacyOrder) {
		if (templateCode != null && BY_TEMPLATE_CODE.containsKey(templateCode.trim())) {
			return BY_TEMPLATE_CODE.get(templateCode.trim());
		}
		WageComponentSortBand band = classifyBand(componentType, phase, category, taxableWageTax);
		if (legacyOrder != null && band.contains(legacyOrder)) {
			return legacyOrder;
		}
		return band.base() + 50;
	}

	public static WageComponentSortBand classifyBand(ComponentType componentType, PayrollPhase phase, String category,
			boolean taxableWageTax) {
		String cat = category == null ? "" : category.trim().toUpperCase();
		if (componentType == ComponentType.EMPLOYER_CONTRIBUTION) {
			return WageComponentSortBand.EMPLOYER_CONTRIBUTIONS;
		}
		if (phase == PayrollPhase.NET || "NET".equals(cat) || "ROUNDING".equals(cat)) {
			return WageComponentSortBand.SYSTEM_CALCULATIONS;
		}
		if (componentType == ComponentType.INFORMATIONAL) {
			if (phase == PayrollPhase.PRE_TAX || "TAX".equals(cat)) {
				return WageComponentSortBand.TAX_ADJUSTMENTS;
			}
			if (phase == PayrollPhase.TAX) {
				return WageComponentSortBand.TAX_ADJUSTMENTS;
			}
			return WageComponentSortBand.GROSS_EARNINGS;
		}
		if (componentType == ComponentType.DEDUCTION) {
			if (phase == PayrollPhase.POST_TAX) {
				return WageComponentSortBand.NET_DEDUCTIONS;
			}
			if (phase == PayrollPhase.GROSS) {
				return WageComponentSortBand.GROSS_DEDUCTIONS;
			}
			if (phase == PayrollPhase.PRE_TAX) {
				return WageComponentSortBand.GROSS_DEDUCTIONS;
			}
			if ("SOCIAL_SECURITY".equals(cat) || "TAX".equals(cat) || phase == PayrollPhase.TAX) {
				return WageComponentSortBand.STATUTORY_DEDUCTIONS;
			}
			return WageComponentSortBand.STATUTORY_DEDUCTIONS;
		}
		if (componentType == ComponentType.EARNING && phase == PayrollPhase.GROSS) {
			if (!taxableWageTax) {
				return WageComponentSortBand.NON_TAXABLE_EARNINGS;
			}
			return WageComponentSortBand.GROSS_EARNINGS;
		}
		return WageComponentSortBand.GROSS_EARNINGS;
	}
}
