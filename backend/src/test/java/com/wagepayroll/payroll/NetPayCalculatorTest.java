package com.wagepayroll.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wagepayroll.domain.wagecomponent.PlatformWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;
import com.wagepayroll.payroll.engine.PayrollContext;
import com.wagepayroll.payroll.engine.PayrollRunState;
import com.wagepayroll.payroll.model.NetEffect;

@ExtendWith(MockitoExtension.class)
class NetPayCalculatorTest {

	private static final UUID EMPLOYEE = UUID.randomUUID();
	private static final UUID TENANT_COMP = UUID.randomUUID();
	private static final UUID WAGE_TAX = UUID.fromString("50000000-0000-0000-0000-000000000001");
	private static final UUID AOV = UUID.fromString("50000000-0000-0000-0000-000000000002");

	@Mock
	private TenantWageComponentRepository tenantWageComponentRepository;

	@Mock
	private PlatformWageComponentRepository platformWageComponentRepository;

	@InjectMocks
	private NetPayCalculator calculator;

	@Test
	void goldenScenarioNetFromSignedLines() {
		var ctx = PayrollContext.withoutPinnedCountryRules(UUID.randomUUID(), UUID.randomUUID(), "SR", "SRD", null,
				null, List.of(EMPLOYEE));
		PayrollRunState state = new PayrollRunState(ctx);
		state.evaluatedComponentAmounts().add(EvaluatedComponentAmount.tenant(EMPLOYEE, TENANT_COMP, "1001",
				"FIXED_AMOUNT", new BigDecimal("18500.0000"), null));
		state.statutoryEvaluatedAmounts().add(EvaluatedComponentAmount.platform(EMPLOYEE, WAGE_TAX, "WAGE_TAX",
				"STATUTORY", new BigDecimal("4930.0000")));
		state.statutoryEvaluatedAmounts().add(EvaluatedComponentAmount.platform(EMPLOYEE, AOV, "SOCIAL_PREMIUM_EE",
				"STATUTORY", new BigDecimal("740.0000")));

		TenantWageComponentEntity tenant = new TenantWageComponentEntity();
		tenant.setId(TENANT_COMP);
		tenant.setNetEffect(NetEffect.ADD_TO_NET);
		when(tenantWageComponentRepository.findAllById(anyIterable())).thenReturn(List.of(tenant));

		PlatformWageComponentEntity tax = new PlatformWageComponentEntity();
		tax.setId(WAGE_TAX);
		tax.setNetEffect(NetEffect.SUBTRACT_FROM_NET);
		PlatformWageComponentEntity aov = new PlatformWageComponentEntity();
		aov.setId(AOV);
		aov.setNetEffect(NetEffect.SUBTRACT_FROM_NET);
		when(platformWageComponentRepository.findAllById(anyIterable())).thenReturn(List.of(tax, aov));

		Map<UUID, BigDecimal> net = calculator.compute(state);
		assertThat(net.get(EMPLOYEE)).isEqualByComparingTo("12830.0000");
	}
}
