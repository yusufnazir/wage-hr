package com.wagepayroll.payroll.formula;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.wagepayroll.domain.compensation.TenantEmployeeCompensationEntity;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantWorkTimeEntity;

/**
 * Shared base-salary formula and compensation bindings for payroll formula evaluation.
 */
public final class CompensationFormulaSupport {

	/** Platform / tenant base salary (template code 1001) default formula. */
	public static final String BASE_SALARY_FORMULA =
			"if(compensation.is_hourly, transaction.quantity * transaction.rate, compensation.periodic_rate)";

	private CompensationFormulaSupport() {
	}

	public record CompensationBindings(BigDecimal periodicRate, BigDecimal isHourly, BigDecimal hourlyRate) {
	}

	public static CompensationBindings bindings(TenantEmployeeCompensationEntity compensation,
			TenantCompanyEntity company, TenantWorkTimeEntity workTime) {
		if (compensation == null || compensation.getWageAmount() == null) {
			return new CompensationBindings(zero(), zero(), zero());
		}
		if ("PER_HOUR".equals(compensation.getWageType())) {
			BigDecimal hourly = compensation.getWageAmount().setScale(4, ROUND);
			return new CompensationBindings(zero(), BigDecimal.ONE, hourly);
		}
		BigDecimal period = periodAmount(compensation, company);
		BigDecimal periodRate = period != null ? period.setScale(4, ROUND) : zero();
		BigDecimal hours = hoursPerPayPeriod(workTime, company);
		BigDecimal hourly = hours.signum() > 0
				? periodRate.divide(hours, 10, ROUND).setScale(4, ROUND)
				: zero();
		return new CompensationBindings(periodRate, zero(), hourly);
	}

	/**
	 * Contract hours in one pay period from work-time pattern (hours/day × days/week × 52 weeks ÷ periods/year).
	 */
	public static BigDecimal hoursPerPayPeriod(TenantWorkTimeEntity workTime, TenantCompanyEntity company) {
		if (workTime == null || workTime.getHoursPerDay() == null) {
			return null;
		}
		BigDecimal weekly = workTime.getHoursPerDay()
				.multiply(BigDecimal.valueOf(workTime.getWorkDaysPerWeek()));
		int periods = periodsPerYear(company != null ? company.getPayrollFrequency() : null);
		return weekly.multiply(BigDecimal.valueOf(52)).divide(BigDecimal.valueOf(periods), 10, ROUND);
	}

	/**
	 * Pay-period amount for non-hourly compensation (matches {@code TenantEmployeeCompensationDto} period column).
	 */
	public static BigDecimal periodAmount(TenantEmployeeCompensationEntity compensation, TenantCompanyEntity company) {
		if (compensation == null || compensation.getWageAmount() == null || company == null) {
			return null;
		}
		BigDecimal amount = compensation.getWageAmount();
		int periods = periodsPerYear(company.getPayrollFrequency());
		BigDecimal yearly = switch (compensation.getWageType()) {
			case "PER_PERIOD" -> amount.multiply(BigDecimal.valueOf(periods));
			case "PER_MONTH" -> amount.multiply(BigDecimal.valueOf(12));
			case "PER_YEAR" -> amount;
			default -> null;
		};
		if (yearly == null) {
			return null;
		}
		return yearly.divide(BigDecimal.valueOf(periods), 4, RoundingMode.HALF_UP);
	}

	private static final RoundingMode ROUND = RoundingMode.HALF_UP;

	private static BigDecimal zero() {
		return BigDecimal.ZERO.setScale(4, ROUND);
	}

	private static int periodsPerYear(String payrollFrequency) {
		if (payrollFrequency == null) {
			return 12;
		}
		return switch (payrollFrequency) {
			case "WEEKLY" -> 52;
			case "BIWEEKLY" -> 26;
			case "SEMIMONTHLY" -> 24;
			default -> 12;
		};
	}
}
