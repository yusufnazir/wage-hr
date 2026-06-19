package com.wagepayroll.payrollstanding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.domain.org.TenantEmployeeEntity;
import com.wagepayroll.domain.payrollstanding.TenantEmployeePayrollStandingInstructionEntity;
import com.wagepayroll.domain.payrollstanding.TenantEmployeePayrollStandingInstructionRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.payroll.model.CalculationMethod;
import com.wagepayroll.payroll.model.StandingInstructionRecurrence;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates default {@link TenantEmployeePayrollStandingInstructionEntity} rows when an employee is onboarded.
 */
@Service
public class TenantEmployeePayrollStandingProvisionService {

	private final TenantWageComponentRepository wageComponentRepository;
	private final TenantEmployeePayrollStandingInstructionRepository standingRepository;
	private final AuditService auditService;

	public TenantEmployeePayrollStandingProvisionService(TenantWageComponentRepository wageComponentRepository,
			TenantEmployeePayrollStandingInstructionRepository standingRepository, AuditService auditService) {
		this.wageComponentRepository = wageComponentRepository;
		this.standingRepository = standingRepository;
		this.auditService = auditService;
	}

	/**
	 * One standing instruction per active company wage component (same set as the wage components UI).
	 * Idempotent: only creates rows for components not yet assigned to the employee.
	 *
	 * @return number of standing instructions created
	 */
	@Transactional
	public int provisionForNewEmployee(UUID tenantId, UUID companyId, UUID employeeId, LocalDate hireDate) {
		LocalDate effectiveFrom = hireDate != null ? hireDate : LocalDate.now();
		return syncActiveWageComponents(tenantId, companyId, employeeId, effectiveFrom);
	}

	/**
	 * Ensures every active wage component for the company has a standing instruction for the employee.
	 * Used after demo catalog seeding for employees inserted outside {@code createEmployee}.
	 */
	@Transactional
	public int syncActiveWageComponentsForCompany(UUID tenantId, UUID companyId, List<TenantEmployeeEntity> employees) {
		int total = 0;
		for (TenantEmployeeEntity employee : employees) {
			LocalDate effectiveFrom = employee.getHireDate() != null ? employee.getHireDate() : LocalDate.now();
			total += syncActiveWageComponents(tenantId, companyId, employee.getId(), effectiveFrom);
		}
		return total;
	}

	private int syncActiveWageComponents(UUID tenantId, UUID companyId, UUID employeeId, LocalDate effectiveFrom) {
		List<TenantWageComponentEntity> components = wageComponentRepository
				.findByTenantIdAndCompanyIdAndActiveIsTrueOrderByProcessingOrderAsc(tenantId, companyId);
		if (components.isEmpty()) {
			return 0;
		}
		Set<UUID> covered = standingRepository
				.findByTenantIdAndCompanyIdAndEmployeeIdOrderByEffectiveFromAsc(tenantId, companyId, employeeId)
				.stream()
				.map(TenantEmployeePayrollStandingInstructionEntity::getTenantWageComponentId)
				.collect(Collectors.toCollection(HashSet::new));

		Instant now = Instant.now();
		int created = 0;
		for (TenantWageComponentEntity component : components) {
			if (covered.contains(component.getId())) {
				continue;
			}
			StandingAmounts amounts = defaultStandingAmounts(component);
			TenantEmployeePayrollStandingInstructionEntity row = new TenantEmployeePayrollStandingInstructionEntity();
			row.setId(UUID.randomUUID());
			row.setTenantId(tenantId);
			row.setCompanyId(companyId);
			row.setEmployeeId(employeeId);
			row.setTenantWageComponentId(component.getId());
			row.setEffectiveFrom(effectiveFrom);
			row.setEffectiveTo(null);
			row.setAmount(amounts.amount());
			row.setQuantity(amounts.quantity());
			row.setRate(amounts.rate());
			row.setRecurrence(StandingInstructionRecurrence.EACH_PAY_PERIOD);
			row.setActive(true);
			row.setAmountOverride(false);
			row.setFactorOverride(false);
			row.setRemarks("Auto-assigned on employee create");
			row.setCreatedAt(now);
			row.setUpdatedAt(now);
			standingRepository.save(row);
			auditService.append(tenantId, null, AuditActionCodes.EMPLOYEE_PAYROLL_STANDING_INSTRUCTION_CREATED,
					AuditResourceTypes.TENANT_EMPLOYEE_PAYROLL_STANDING_INSTRUCTION, row.getId().toString(), null,
					Map.of("autoProvision", true, "employeeId", employeeId.toString(), "tenantWageComponentId",
							component.getId().toString()));
			created++;
		}
		return created;
	}

	/**
	 * Derives initial standing amounts from the wage component definition.
	 */
	static StandingAmounts defaultStandingAmounts(TenantWageComponentEntity component) {
		CalculationMethod method = component.getCalculationMethod();
		if (method == CalculationMethod.FIXED_AMOUNT || method == CalculationMethod.MANUAL_INPUT) {
			BigDecimal def = component.getDefaultAmount();
			if (def != null && def.signum() > 0) {
				return new StandingAmounts(scaleMoney(def), null, null);
			}
		}
		if (method == CalculationMethod.HOURLY) {
			BigDecimal def = component.getDefaultAmount();
			if (def != null && def.signum() > 0) {
				return new StandingAmounts(null, BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP), scaleMoney(def));
			}
		}
		return StandingAmounts.empty();
	}

	private static BigDecimal scaleMoney(BigDecimal v) {
		return v.setScale(4, RoundingMode.HALF_UP);
	}

	record StandingAmounts(BigDecimal amount, BigDecimal quantity, BigDecimal rate) {
		static StandingAmounts empty() {
			return new StandingAmounts(null, null, null);
		}
	}
}
