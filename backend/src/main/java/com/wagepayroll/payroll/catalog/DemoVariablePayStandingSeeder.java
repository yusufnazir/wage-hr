package com.wagepayroll.payroll.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.wagepayroll.domain.compensation.TenantEmployeeCompensationEntity;
import com.wagepayroll.domain.compensation.TenantEmployeeCompensationRepository;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.org.TenantEmployeeEntity;
import com.wagepayroll.domain.payrollstanding.TenantEmployeePayrollStandingInstructionEntity;
import com.wagepayroll.domain.payrollstanding.TenantEmployeePayrollStandingInstructionRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.payroll.formula.CompensationFormulaSupport;
import com.wagepayroll.payroll.model.StandingInstructionRecurrence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds demo standing-instruction amounts for variable and supplemental pay (vacation, bonus,
 * child allowance, lump sum, extra earnings, overtime tiers) on the demo company after catalog provisioning.
 */
@Component
public class DemoVariablePayStandingSeeder {

	static final Set<String> VARIABLE_PAY_TEMPLATE_CODES = Set.of("1006", "1007", "1008", "1009", "1011", "1045",
			"1046", "1047");

	private static final BigDecimal VACATION_MONTHLY_FRACTION = BigDecimal.ONE.divide(BigDecimal.valueOf(12), 10,
			RoundingMode.HALF_UP);
	private static final BigDecimal BONUS_MONTHLY_FRACTION = new BigDecimal("0.10");
	private static final BigDecimal LUMP_SUM_MONTHLY_FRACTION = new BigDecimal("0.05");
	private static final BigDecimal EXTRA_EARNINGS_MONTHLY_FRACTION = new BigDecimal("0.06");

	private final TenantCompanyRepository companyRepository;
	private final TenantWageComponentRepository wageComponentRepository;
	private final TenantEmployeeCompensationRepository compensationRepository;
	private final TenantEmployeePayrollStandingInstructionRepository standingRepository;

	public DemoVariablePayStandingSeeder(TenantCompanyRepository companyRepository,
			TenantWageComponentRepository wageComponentRepository,
			TenantEmployeeCompensationRepository compensationRepository,
			TenantEmployeePayrollStandingInstructionRepository standingRepository) {
		this.companyRepository = companyRepository;
		this.wageComponentRepository = wageComponentRepository;
		this.compensationRepository = compensationRepository;
		this.standingRepository = standingRepository;
	}

	@Transactional
	public int seedDemoCompany(UUID tenantId, UUID companyId, List<TenantEmployeeEntity> employees) {
		if (employees.isEmpty()) {
			return 0;
		}
		TenantCompanyEntity company = companyRepository.findByIdAndTenantId(companyId, tenantId).orElse(null);
		if (company == null) {
			return 0;
		}
		Map<String, TenantWageComponentEntity> componentByCode = wageComponentRepository
				.findByTenantIdAndCompanyIdAndActiveIsTrueOrderByProcessingOrderAsc(tenantId, companyId)
				.stream()
				.filter(c -> VARIABLE_PAY_TEMPLATE_CODES.contains(c.getCode()))
				.collect(Collectors.toMap(TenantWageComponentEntity::getCode, Function.identity(), (a, b) -> a));
		if (componentByCode.isEmpty()) {
			return 0;
		}

		int updated = 0;
		int index = 0;
		for (TenantEmployeeEntity employee : employees) {
			if (!employee.isActive() || "TERMINATED".equalsIgnoreCase(employee.getStatus())) {
				continue;
			}
			BigDecimal periodBase = resolvePeriodBase(tenantId, employee.getId(), company);
			if (periodBase.signum() <= 0) {
				index++;
				continue;
			}
			int slot = index % 5;
			updated += upsertAmount(employee, componentByCode.get("1006"), periodBase.multiply(VACATION_MONTHLY_FRACTION),
					null, null, tenantId, companyId);
			updated += upsertAmount(employee, componentByCode.get("1007"), periodBase.multiply(BONUS_MONTHLY_FRACTION),
					null, null, tenantId, companyId);
			updated += upsertChildren(employee, componentByCode.get("1008"), childrenCount(slot), tenantId, companyId);
			updated += upsertAmount(employee, componentByCode.get("1009"),
					periodBase.multiply(LUMP_SUM_MONTHLY_FRACTION), null, null, tenantId, companyId);
			updated += upsertAmount(employee, componentByCode.get("1011"),
					periodBase.multiply(EXTRA_EARNINGS_MONTHLY_FRACTION), null, null, tenantId, companyId);
			updated += upsertHours(employee, componentByCode.get("1045"), overtimeHours(slot, 4, 8), tenantId,
					companyId);
			updated += upsertHours(employee, componentByCode.get("1046"), overtimeHours(slot, 2, 4), tenantId,
					companyId);
			updated += upsertHours(employee, componentByCode.get("1047"), overtimeHours(slot, 1, 2), tenantId,
					companyId);
			index++;
		}
		return updated;
	}

	private BigDecimal resolvePeriodBase(UUID tenantId, UUID employeeId, TenantCompanyEntity company) {
		return compensationRepository.findByEmployeeIdAndTenantId(employeeId, tenantId)
				.map(c -> CompensationFormulaSupport.periodAmount(c, company))
				.filter(a -> a.signum() > 0)
				.orElse(BigDecimal.ZERO);
	}

	/** Demo children count 1–4 (standing factor for 1008 child allowance algorithm). */
	private static BigDecimal childrenCount(int employeeIndex) {
		int children = 1 + (employeeIndex % 4);
		return BigDecimal.valueOf(children).setScale(4, RoundingMode.HALF_UP);
	}

	private int upsertChildren(TenantEmployeeEntity employee, TenantWageComponentEntity component,
			BigDecimal children, UUID tenantId, UUID companyId) {
		if (component == null || children == null || children.signum() <= 0) {
			return 0;
		}
		return upsertStanding(employee, component, null, children, null, tenantId, companyId);
	}

	private static BigDecimal overtimeHours(int employeeIndex, int minHours, int maxHours) {
		int span = maxHours - minHours + 1;
		int hours = minHours + (employeeIndex % span);
		return BigDecimal.valueOf(hours).setScale(4, RoundingMode.HALF_UP);
	}

	private int upsertAmount(TenantEmployeeEntity employee, TenantWageComponentEntity component, BigDecimal amount,
			BigDecimal quantity, BigDecimal rate, UUID tenantId, UUID companyId) {
		if (component == null || amount == null || amount.signum() <= 0) {
			return 0;
		}
		return upsertStanding(employee, component, scaleMoney(amount), quantity, rate, tenantId, companyId);
	}

	private int upsertHours(TenantEmployeeEntity employee, TenantWageComponentEntity component, BigDecimal hours,
			UUID tenantId, UUID companyId) {
		if (component == null || hours == null || hours.signum() <= 0) {
			return 0;
		}
		return upsertStanding(employee, component, null, hours, null, tenantId, companyId);
	}

	private int upsertStanding(TenantEmployeeEntity employee, TenantWageComponentEntity component, BigDecimal amount,
			BigDecimal quantity, BigDecimal rate, UUID tenantId, UUID companyId) {
		LocalDate effectiveFrom = employee.getHireDate() != null ? employee.getHireDate() : LocalDate.of(2020, 1, 1);
		List<TenantEmployeePayrollStandingInstructionEntity> existing = standingRepository
				.findByTenantIdAndCompanyIdAndEmployeeIdOrderByEffectiveFromAsc(tenantId, companyId,
						employee.getId())
				.stream()
				.filter(s -> s.getTenantWageComponentId().equals(component.getId()))
				.toList();
		Instant now = Instant.now();
		if (existing.isEmpty()) {
			TenantEmployeePayrollStandingInstructionEntity row = new TenantEmployeePayrollStandingInstructionEntity();
			row.setId(UUID.randomUUID());
			row.setTenantId(tenantId);
			row.setCompanyId(companyId);
			row.setEmployeeId(employee.getId());
			row.setTenantWageComponentId(component.getId());
			row.setEffectiveFrom(effectiveFrom);
			row.setEffectiveTo(null);
			row.setAmount(amount);
			row.setQuantity(quantity);
			row.setRate(rate);
			row.setRecurrence(StandingInstructionRecurrence.EACH_PAY_PERIOD);
			row.setActive(true);
			row.setAmountOverride(amount != null);
			row.setFactorOverride(quantity != null);
			row.setRemarks("Demo payroll inputs seed");
			row.setCreatedAt(now);
			row.setUpdatedAt(now);
			standingRepository.save(row);
			return 1;
		}
		boolean changed = false;
		for (TenantEmployeePayrollStandingInstructionEntity row : existing) {
			if (amount != null && (row.getAmount() == null || row.getAmount().compareTo(amount) != 0)) {
				row.setAmount(amount);
				row.setAmountOverride(true);
				changed = true;
			}
			if (quantity != null && (row.getQuantity() == null || row.getQuantity().compareTo(quantity) != 0)) {
				row.setQuantity(quantity);
				row.setFactorOverride(true);
				changed = true;
			}
			if (changed) {
				row.setRemarks("Demo payroll inputs seed");
				row.setUpdatedAt(now);
				standingRepository.save(row);
			}
		}
		return changed ? 1 : 0;
	}

	private static BigDecimal scaleMoney(BigDecimal v) {
		return v.setScale(4, RoundingMode.HALF_UP);
	}
}
