package com.wagepayroll.payroll.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.wagepayroll.domain.wagecomponent.TenantWageComponentDependencyEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.payroll.model.CalculationMethod;
import com.wagepayroll.payroll.model.ComponentType;
import com.wagepayroll.payroll.model.NetEffect;
import com.wagepayroll.payroll.model.PayrollPhase;
import com.wagepayroll.payroll.model.RoundingStrategy;

class ComponentExecutionOrderServiceTest {

	private final ComponentExecutionOrderService service = new ComponentExecutionOrderService();

	@Test
	void usesProcessingOrderWhenNoDependencies() {
		TenantWageComponentEntity a = component("A", 20);
		TenantWageComponentEntity b = component("B", 10);
		List<TenantWageComponentEntity> sorted = service.sortForExecution(List.of(a, b), List.of());
		assertThat(sorted).containsExactly(b, a);
	}

	@Test
	void dependencyOverridesProcessingOrder() {
		TenantWageComponentEntity prereq = component("1001", 50);
		TenantWageComponentEntity dependent = component("2001", 5);
		TenantWageComponentDependencyEntity edge = new TenantWageComponentDependencyEntity();
		edge.setTenantWageComponentId(dependent.getId());
		edge.setDependsOnTenantWageComponentId(prereq.getId());
		List<TenantWageComponentEntity> sorted = service.sortForExecution(List.of(dependent, prereq), List.of(edge));
		assertThat(sorted).containsExactly(prereq, dependent);
	}

	private static TenantWageComponentEntity component(String code, int processingOrder) {
		TenantWageComponentEntity e = new TenantWageComponentEntity();
		e.setId(UUID.randomUUID());
		e.setCode(code);
		e.setProcessingOrder(processingOrder);
		e.setComponentType(ComponentType.EARNING);
		e.setCategory("SALARY");
		e.setNetEffect(NetEffect.ADD_TO_NET);
		e.setCalculationMethod(CalculationMethod.FIXED_AMOUNT);
		e.setRoundingStrategy(RoundingStrategy.HALF_UP);
		e.setPhase(PayrollPhase.GROSS);
		e.setActive(true);
		return e;
	}
}
