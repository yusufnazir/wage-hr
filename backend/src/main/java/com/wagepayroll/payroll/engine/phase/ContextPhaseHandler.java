package com.wagepayroll.payroll.engine.phase;

import org.springframework.stereotype.Component;

import com.wagepayroll.domain.wagecomponent.PlatformWageComponentRepository;
import com.wagepayroll.payroll.country.CountryRuleProviderRegistry;
import com.wagepayroll.payroll.engine.PayrollRunPhase;
import com.wagepayroll.payroll.engine.PayrollRunState;

@Component
public class ContextPhaseHandler implements PayrollPhaseHandler {

	private final PlatformWageComponentRepository platformWageComponentRepository;

	private final CountryRuleProviderRegistry countryRuleProviderRegistry;

	public ContextPhaseHandler(PlatformWageComponentRepository platformWageComponentRepository,
			CountryRuleProviderRegistry countryRuleProviderRegistry) {
		this.platformWageComponentRepository = platformWageComponentRepository;
		this.countryRuleProviderRegistry = countryRuleProviderRegistry;
	}

	@Override
	public PayrollRunPhase phase() {
		return PayrollRunPhase.CONTEXT;
	}

	@Override
	public void execute(PayrollRunState state) {
		state.seedContextVariables();
		countryRuleProviderRegistry.forCountry(state.context().payrollCountryIso2())
				.ifPresent(p -> p.contribute(state.countryRuleContext()));
		int statutoryCount = platformWageComponentRepository
				.findByCountryCodeAndActiveIsTrueOrderByProcessingOrderAsc(state.context().payrollCountryIso2()).size();
		state.setResolvedStatutoryComponentCount(statutoryCount);
	}
}
