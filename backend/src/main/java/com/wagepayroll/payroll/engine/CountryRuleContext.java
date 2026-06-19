package com.wagepayroll.payroll.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Mutable scratch space for {@link com.wagepayroll.payroll.country.CountryRuleProvider} contributions.
 */
public final class CountryRuleContext {

	private final PayrollContext payroll;

	private final Map<String, String> hints = new LinkedHashMap<>();

	private final Map<String, Object> attributes = new LinkedHashMap<>();

	public CountryRuleContext(PayrollContext payroll) {
		this.payroll = payroll;
	}

	public PayrollContext payroll() {
		return payroll;
	}

	public void putHint(String key, String value) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("hint key required");
		}
		hints.put(key, value == null ? "" : value);
	}

	public Map<String, String> hintsView() {
		return Collections.unmodifiableMap(hints);
	}

	public void putAttribute(String key, Object value) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("attribute key required");
		}
		if (value == null) {
			attributes.remove(key);
		}
		else {
			attributes.put(key, value);
		}
	}

	public <T> Optional<T> findAttribute(String key, Class<T> type) {
		Object raw = attributes.get(key);
		if (raw == null) {
			return Optional.empty();
		}
		if (!type.isInstance(raw)) {
			return Optional.empty();
		}
		return Optional.of(type.cast(raw));
	}
}
