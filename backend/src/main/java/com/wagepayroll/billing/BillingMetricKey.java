package com.wagepayroll.billing;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stable catalog of billable usage metrics (extend as product adds PAYG dimensions).
 */
public enum BillingMetricKey {

	/** Count of payroll runs (integer quantity recommended). */
	PAYROLL_RUN,

	/** Commercial seat-days or seat count snapshot (product-defined unit). */
	COMMERCIAL_SEAT_DAY,

	/** Document storage consumed in gigabytes (fractional allowed). */
	DOCUMENT_STORAGE_GB;

	public String wireValue() {
		return name();
	}

	public static BillingMetricKey parse(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BILLING_METRIC_KEY_REQUIRED");
		}
		try {
			return BillingMetricKey.valueOf(raw.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_BILLING_METRIC_KEY");
		}
	}
}
