package com.wagepayroll.api.dto;

import java.util.UUID;

public record TenantBankTemplateCatalogRowDto(
		UUID id,
		String countryCode,
		String name,
		String bankName,
		String swiftBic,
		String currencyCode) {
}
