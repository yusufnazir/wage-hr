package com.wagepayroll.payroll.country;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class CountryRuleProviderRegistry {

	private final Map<String, CountryRuleProvider> byCountry;

	public CountryRuleProviderRegistry(List<CountryRuleProvider> providers) {
		this.byCountry = providers.stream()
			.collect(Collectors.toUnmodifiableMap(p -> p.isoCountryCode().toUpperCase(Locale.ROOT),
					Function.identity(), (a, b) -> {
						throw new IllegalStateException("Duplicate CountryRuleProvider for country: " + a.isoCountryCode());
					}));
	}

	public Optional<CountryRuleProvider> forCountry(String isoAlpha2) {
		if (isoAlpha2 == null || isoAlpha2.length() != 2) {
			return Optional.empty();
		}
		return Optional.ofNullable(byCountry.get(isoAlpha2.toUpperCase(Locale.ROOT)));
	}
}
