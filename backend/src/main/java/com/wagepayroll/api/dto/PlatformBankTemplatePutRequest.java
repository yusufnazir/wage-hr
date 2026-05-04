package com.wagepayroll.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record PlatformBankTemplatePutRequest(
		String name,
		String bankName,
		String swiftBic,
		String bankCode,
		String accountNumberFormat,
		String currencyCode,
		Boolean active) {
}
