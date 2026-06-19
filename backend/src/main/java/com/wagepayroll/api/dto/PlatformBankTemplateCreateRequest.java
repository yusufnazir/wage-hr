package com.wagepayroll.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record PlatformBankTemplateCreateRequest(
		String countryCode,
		String name,
		String bankName,
		String swiftBic,
		String bankCode,
		String accountNumberFormat,
		Boolean active) {
}
