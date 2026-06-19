package com.wagepayroll.payroll.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wagepayroll.domain.payrollbase.PlatformPayrollBaseEntity;
import com.wagepayroll.domain.payrollbase.PlatformPayrollBaseRepository;
import com.wagepayroll.domain.payrollbase.TenantWageComponentBaseEffectEntity;
import com.wagepayroll.domain.payrollbase.TenantWageComponentBaseEffectRepository;
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;
import com.wagepayroll.payroll.model.PayrollBaseEffectCalculationType;
import com.wagepayroll.payroll.model.PayrollBaseEffectDirection;

@ExtendWith(MockitoExtension.class)
class PayrollBaseAccumulatorTest {

	private static final UUID TENANT_ID = UUID.randomUUID();
	private static final UUID EMPLOYEE_ID = UUID.randomUUID();
	private static final UUID COMPONENT_1001 = UUID.randomUUID();
	private static final UUID COMPONENT_1006 = UUID.randomUUID();
	private static final UUID LOON_BASE_ID = UUID.randomUUID();

	@Mock
	private TenantWageComponentBaseEffectRepository tenantEffectRepository;

	@Mock
	private PlatformPayrollBaseRepository payrollBaseRepository;

	private PayrollBaseAccumulator accumulator;

	@BeforeEach
	void setUp() {
		accumulator = new PayrollBaseAccumulator(tenantEffectRepository, payrollBaseRepository);
		PlatformPayrollBaseEntity loonBase = new PlatformPayrollBaseEntity();
		loonBase.setId(LOON_BASE_ID);
		loonBase.setCode("LOONBELASTING");
		when(payrollBaseRepository.findByActiveIsTrueOrderByCodeAsc()).thenReturn(List.of(loonBase));
	}

	@Test
	void accumulateDetailedRecordsPerComponentContributions() {
		when(tenantEffectRepository.findByTenantIdAndTenantWageComponentIdInAndActiveIsTrue(eq(TENANT_ID), any()))
				.thenReturn(List.of(effect(COMPONENT_1001, new BigDecimal("25000")),
						effect(COMPONENT_1006, new BigDecimal("575"))));

		List<EvaluatedComponentAmount> evaluated = List.of(
				EvaluatedComponentAmount.tenant(EMPLOYEE_ID, COMPONENT_1001, "1001", "FIXED_AMOUNT",
						new BigDecimal("25000"), null),
				EvaluatedComponentAmount.tenant(EMPLOYEE_ID, COMPONENT_1006, "1006", "FIXED_AMOUNT", new BigDecimal("575"),
						null));

		PayrollBaseAccumulationResult result = accumulator.accumulateDetailed(TENANT_ID, evaluated);

		assertThat(result.totalsByEmployee().get(EMPLOYEE_ID).get("LOONBELASTING"))
				.isEqualByComparingTo(new BigDecimal("25575.0000"));
		List<PayrollBaseContribution> contributions = result.contributionsFor(EMPLOYEE_ID, "LOONBELASTING");
		assertThat(contributions).hasSize(2);
		assertThat(contributions.get(0).componentCode()).isEqualTo("1001");
		assertThat(contributions.get(0).baseDelta()).isEqualByComparingTo("25000");
		assertThat(contributions.get(1).componentCode()).isEqualTo("1006");
	}

	private static TenantWageComponentBaseEffectEntity effect(UUID componentId, BigDecimal ignored) {
		TenantWageComponentBaseEffectEntity effect = new TenantWageComponentBaseEffectEntity();
		effect.setTenantWageComponentId(componentId);
		effect.setPlatformPayrollBaseId(LOON_BASE_ID);
		effect.setEffectDirection(PayrollBaseEffectDirection.INCREASE);
		effect.setEffectCalculationType(PayrollBaseEffectCalculationType.FULL);
		effect.setEffectValue(new BigDecimal("100"));
		effect.setActive(true);
		return effect;
	}
}
