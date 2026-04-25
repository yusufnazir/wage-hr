package com.wagepayroll.billing;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum BillingProvider {

	STRIPE,
	PAYPAL;

	public String code() {
		return name();
	}

	public static BillingProvider parsePathSegment(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BILLING_PROVIDER");
		}
		try {
			return BillingProvider.valueOf(raw.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BILLING_PROVIDER");
		}
	}
}
