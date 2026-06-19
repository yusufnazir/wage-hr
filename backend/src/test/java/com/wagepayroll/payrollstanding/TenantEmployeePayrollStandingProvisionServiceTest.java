package com.wagepayroll.payrollstanding;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.payroll.model.CalculationMethod;

import org.junit.jupiter.api.Test;

class TenantEmployeePayrollStandingProvisionServiceTest {

	@Test
	void fixedAmountUsesDefaultAmount() {
		TenantWageComponentEntity c = component(CalculationMethod.FIXED_AMOUNT, "500.00", null);
		var amounts = TenantEmployeePayrollStandingProvisionService.defaultStandingAmounts(c);
		assertThat(amounts.amount()).isEqualByComparingTo("500.0000");
		assertThat(amounts.quantity()).isNull();
		assertThat(amounts.rate()).isNull();
	}

	@Test
	void manualInputWithoutDefaultLeavesAmountNull() {
		TenantWageComponentEntity c = component(CalculationMethod.MANUAL_INPUT, null, null);
		var amounts = TenantEmployeePayrollStandingProvisionService.defaultStandingAmounts(c);
		assertThat(amounts.amount()).isNull();
	}

	@Test
	void hourlyUsesDefaultAsRateWithQuantityOne() {
		TenantWageComponentEntity c = component(CalculationMethod.HOURLY, "45.50", null);
		var amounts = TenantEmployeePayrollStandingProvisionService.defaultStandingAmounts(c);
		assertThat(amounts.amount()).isNull();
		assertThat(amounts.quantity()).isEqualByComparingTo("1.0000");
		assertThat(amounts.rate()).isEqualByComparingTo("45.5000");
	}

	@Test
	void formulaLeavesAmountsNull() {
		TenantWageComponentEntity c = component(CalculationMethod.FORMULA, "100", null);
		var amounts = TenantEmployeePayrollStandingProvisionService.defaultStandingAmounts(c);
		assertThat(amounts.amount()).isNull();
		assertThat(amounts.quantity()).isNull();
		assertThat(amounts.rate()).isNull();
	}

	private static TenantWageComponentEntity component(CalculationMethod method, String defaultAmount,
			String unused) {
		TenantWageComponentEntity e = new TenantWageComponentEntity();
		e.setCalculationMethod(method);
		e.setDefaultAmount(defaultAmount == null ? null : new BigDecimal(defaultAmount));
		return e;
	}
}
