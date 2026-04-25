package com.wagepayroll.billing;

import java.util.Set;

import com.wagepayroll.api.dto.SettingEntryDto;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class BillingIntegrationSettingsValidator {

	private static final Set<String> KNOWN_KEYS = Set.of("billing.stripe.enabled", "billing.paypal.enabled");

	private BillingIntegrationSettingsValidator() {
	}

	public static void validateIfBillingKey(SettingEntryDto entry) {
		if (entry == null || entry.key() == null || !entry.key().startsWith("billing.")) {
			return;
		}
		if (!KNOWN_KEYS.contains(entry.key())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_BILLING_SETTINGS_KEY");
		}
		if (!"0".equals(entry.value()) && !"1".equals(entry.value())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BILLING_SETTINGS_VALUE");
		}
	}
}
