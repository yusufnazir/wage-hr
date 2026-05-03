package com.wagepayroll.api.dto;

import java.util.List;

public record PlatformCountryUpsertRequest(
		String isoAlpha2,
		String isoAlpha3,
		String isoNumeric,
		String dialCode,
		Boolean active,
		Boolean payrollEnabled,
		List<PlatformCountryTranslationRequest> translations) {
}
