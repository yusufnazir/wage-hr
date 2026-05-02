package com.wagepayroll.api.dto;

public record TenantCompanyUpsertRequest(
		String name,
		String legalName,
		String registrationNumber,
		String taxId,
		String payrollCountry,
		String currency,
		String payrollFrequency,
		String timezone,
		String dateFormat,
		String contactEmail,
		String contactPhone,
		String addressLine1,
		String addressLine2,
		String city,
		String stateRegion,
		String postalCode,
		String country,
		Boolean active) {
}
