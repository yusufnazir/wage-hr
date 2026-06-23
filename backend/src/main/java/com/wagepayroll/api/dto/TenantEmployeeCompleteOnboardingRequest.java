package com.wagepayroll.api.dto;

public record TenantEmployeeCompleteOnboardingRequest(
		TenantEmployeeUpsertRequest employee,
		String targetStatus) {
}
