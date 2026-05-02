package com.wagepayroll.plans;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Maps commercial {@link PlanFeatureCode} values (from {@code plan_feature} / subscriptions) to named
 * {@code privilege.code} rows that the active subscription contributes to tenant-scoped effective access.
 * Extend the switch when new product areas ship privileges.
 */
public final class PlanFeaturePrivilegeWiring {

	private PlanFeaturePrivilegeWiring() {
	}

	public static Set<String> privilegeCodesForPlanFeatures(List<String> planFeatureCodes) {
		Set<String> out = new LinkedHashSet<>();
		if (planFeatureCodes == null) {
			return out;
		}
		for (String raw : planFeatureCodes) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			out.addAll(privilegeCodesForOnePlanFeature(raw.trim()));
		}
		return out;
	}

	private static Set<String> privilegeCodesForOnePlanFeature(String planFeatureCode) {
		try {
			return switch (PlanFeatureCode.fromCode(planFeatureCode)) {
				case HR_ESSENTIALS -> Set.of("USER_INVITE");
				case TENANT_CORE, PAYROLL_COUNTRY, DOCUMENT_STORAGE, COMMERCIAL_BILLING -> Set.of();
			};
		}
		catch (IllegalArgumentException ex) {
			return Set.of();
		}
	}
}
