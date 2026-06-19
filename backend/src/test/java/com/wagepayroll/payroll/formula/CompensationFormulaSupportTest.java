package com.wagepayroll.payroll.formula;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.wagepayroll.domain.compensation.TenantEmployeeCompensationEntity;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantWorkTimeEntity;

class CompensationFormulaSupportTest {

	@Test
	void hoursPerPayPeriodFromStandard40hWorkTime() {
		TenantWorkTimeEntity workTime = new TenantWorkTimeEntity();
		workTime.setHoursPerDay(new BigDecimal("8"));
		workTime.setWorkDaysPerWeek(5);
		TenantCompanyEntity company = new TenantCompanyEntity();
		company.setPayrollFrequency("MONTHLY");
		assertThat(CompensationFormulaSupport.hoursPerPayPeriod(workTime, company))
				.isEqualByComparingTo("173.3333333333");
	}

	@Test
	void bindingsDeriveHourlyFromPeriodicAndWorkTime() {
		TenantEmployeeCompensationEntity compensation = new TenantEmployeeCompensationEntity();
		compensation.setWageType("PER_PERIOD");
		compensation.setWageAmount(new BigDecimal("6000"));
		TenantCompanyEntity company = new TenantCompanyEntity();
		company.setPayrollFrequency("MONTHLY");
		TenantWorkTimeEntity workTime = new TenantWorkTimeEntity();
		workTime.setHoursPerDay(new BigDecimal("8"));
		workTime.setWorkDaysPerWeek(5);
		var bindings = CompensationFormulaSupport.bindings(compensation, company, workTime);
		assertThat(bindings.periodicRate()).isEqualByComparingTo("6000.0000");
		assertThat(bindings.isHourly()).isEqualByComparingTo("0.0000");
		assertThat(bindings.hourlyRate()).isEqualByComparingTo("34.6154");
	}

	@Test
	void bindingsUseWageAmountForPerHour() {
		TenantEmployeeCompensationEntity compensation = new TenantEmployeeCompensationEntity();
		compensation.setWageType("PER_HOUR");
		compensation.setWageAmount(new BigDecimal("125.50"));
		var bindings = CompensationFormulaSupport.bindings(compensation, new TenantCompanyEntity(), null);
		assertThat(bindings.hourlyRate()).isEqualByComparingTo("125.5000");
		assertThat(bindings.isHourly()).isEqualByComparingTo("1.0000");
	}
}
