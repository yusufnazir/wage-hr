package com.wagepayroll.api.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record TenantBankTemplateCreateRequest(
		UUID companyId,
		UUID platformBankTemplateId,
		String accountNumber,
		String currencyCode,
		Boolean active) {
}
