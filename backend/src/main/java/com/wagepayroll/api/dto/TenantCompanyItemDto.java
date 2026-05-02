package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantCompanyItemDto(
		UUID id,
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
		boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
