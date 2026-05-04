package com.wagepayroll.api.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record TenantBankTemplateCreateRequest(
		UUID companyId,
		String name,
		String bankName,
		String swiftBic,
		String bankCode,
		String accountNumberFormat,
		String currencyCode,
		Boolean active) {
}
