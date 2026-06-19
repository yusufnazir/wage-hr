package com.wagepayroll.payroll.engine.phase;

import java.util.List;

import org.springframework.stereotype.Component;

import com.wagepayroll.payroll.country.CountryStatutoryContributor;
import com.wagepayroll.payroll.engine.PayrollRunPhase;
import com.wagepayroll.payroll.engine.PayrollRunState;

/**
 * Phase 3 macro step: statutory deductions via country-specific contributors (Phase 2+).
 */
@Component
public class StatutoryPhaseHandler implements PayrollPhaseHandler {

	private final List<CountryStatutoryContributor> statutoryContributors;

	public StatutoryPhaseHandler(List<CountryStatutoryContributor> statutoryContributors) {
		this.statutoryContributors = List.copyOf(statutoryContributors);
	}

	@Override
	public PayrollRunPhase phase() {
		return PayrollRunPhase.STATUTORY;
	}

	@Override
	public void execute(PayrollRunState state) {
		String country = state.context().payrollCountryIso2();
		for (CountryStatutoryContributor contributor : statutoryContributors) {
			if (contributor.supports(country)) {
				contributor.contribute(state);
			}
		}
	}
}
