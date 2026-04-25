package com.wagepayroll.plans;

/**
 * Predefined commercial / product feature codes (M3). Each constant must have a matching {@code plan_feature.code}
 * row seeded via Liquibase — see {@code PlanFeatureCatalogSyncIT}.
 */
public enum PlanFeatureCode {

	TENANT_CORE,
	HR_ESSENTIALS,
	PAYROLL_COUNTRY,
	DOCUMENT_STORAGE,
	COMMERCIAL_BILLING;

	public String code() {
		return name();
	}

	public static PlanFeatureCode fromCode(String code) {
		return PlanFeatureCode.valueOf(code);
	}
}
