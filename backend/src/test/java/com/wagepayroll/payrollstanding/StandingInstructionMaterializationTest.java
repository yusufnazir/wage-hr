package com.wagepayroll.payrollstanding;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.wagepayroll.domain.payrollstanding.TenantEmployeePayrollStandingInstructionEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.payroll.model.CalculationMethod;

import org.junit.jupiter.api.Test;

class StandingInstructionMaterializationTest {

	@Test
	void formulaFactorZero_materializesZeroQuantityAndZeroAmount() throws Exception {
		var si = buildStanding(true, false, "0", "298.5578");
		var component = component(CalculationMethod.FORMULA);

		assertMaterialized(invokeResolve(si, component), "0", "0");
	}

	@Test
	void formulaIgnoresStaleAmountWhenOnlyFactorOverride() throws Exception {
		var si = buildStanding(true, false, "4", "100");
		var component = component(CalculationMethod.FORMULA);

		assertMaterialized(invokeResolve(si, component), "4", "0");
	}

	@Test
	void formulaAmountOverride_usesStandingAmount() throws Exception {
		var si = buildStanding(false, true, null, "500");
		var component = component(CalculationMethod.FORMULA);

		assertMaterialized(invokeResolve(si, component), null, "500");
	}

	@Test
	void fixedAmountOverride_usesStandingAmount() throws Exception {
		var si = buildStanding(false, true, null, "575");
		var component = component(CalculationMethod.FIXED_AMOUNT);

		assertMaterialized(invokeResolve(si, component), null, "575");
	}

	@SuppressWarnings("unchecked")
	private static Optional<Object> invokeResolve(TenantEmployeePayrollStandingInstructionEntity si,
			TenantWageComponentEntity component) throws Exception {
		Method m = TenantPayrollPeriodInputService.class.getDeclaredMethod("resolveMaterializedValues",
				TenantEmployeePayrollStandingInstructionEntity.class, TenantWageComponentEntity.class);
		m.setAccessible(true);
		return (Optional<Object>) m.invoke(null, si, component);
	}

	private static void assertMaterialized(Optional<Object> values, String expectedQty, String expectedAmt)
			throws Exception {
		assertThat(values).isPresent();
		Object row = values.get();
		if (expectedQty != null) {
			assertThat((BigDecimal) row.getClass().getDeclaredMethod("quantity").invoke(row))
					.isEqualByComparingTo(expectedQty);
		}
		assertThat((BigDecimal) row.getClass().getDeclaredMethod("amount").invoke(row))
				.isEqualByComparingTo(expectedAmt);
	}

	private static TenantEmployeePayrollStandingInstructionEntity buildStanding(boolean factorOverride,
			boolean amountOverride, String qty, String amount) {
		var si = new TenantEmployeePayrollStandingInstructionEntity();
		si.setId(UUID.randomUUID());
		si.setFactorOverride(factorOverride);
		si.setAmountOverride(amountOverride);
		si.setQuantity(qty != null ? new BigDecimal(qty) : null);
		si.setAmount(amount != null ? new BigDecimal(amount) : null);
		return si;
	}

	private static TenantWageComponentEntity component(CalculationMethod method) {
		var c = new TenantWageComponentEntity();
		c.setId(UUID.randomUUID());
		c.setCalculationMethod(method);
		return c;
	}
}
