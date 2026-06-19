package com.wagepayroll.payroll.country;

import java.util.Map;
import java.util.UUID;

import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;

/**
 * Maps platform statutory engine codes to tenant payslip template codes (SR).
 *
 * @see com.wagepayroll.payroll.country.SurinameStatutoryContributor
 * @see frontend/src/lib/statutory-component-display.ts
 */
public final class SurinamePlatformStatutoryComponentMapping {

	public static final Map<String, String> TENANT_TEMPLATE_CODE_BY_PLATFORM_CODE = Map.of(
			"WAGE_TAX", "1019",
			"SOCIAL_PREMIUM_EE", "1012");

	private SurinamePlatformStatutoryComponentMapping() {
	}

	public static UUID resolveTenantWageComponentId(String platformCode,
			Map<String, TenantWageComponentEntity> tenantComponentByCode) {
		String tenantCode = TENANT_TEMPLATE_CODE_BY_PLATFORM_CODE.get(platformCode);
		if (tenantCode == null || tenantComponentByCode == null) {
			return null;
		}
		TenantWageComponentEntity component = tenantComponentByCode.get(tenantCode);
		return component != null ? component.getId() : null;
	}
}
