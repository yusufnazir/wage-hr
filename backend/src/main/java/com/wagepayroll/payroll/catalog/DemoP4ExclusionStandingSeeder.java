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

import com.wagepayroll.domain.org.TenantEmployeeEntity;
import com.wagepayroll.domain.payrollstanding.TenantEmployeePayrollStandingInstructionEntity;
import com.wagepayroll.domain.payrollstanding.TenantEmployeePayrollStandingInstructionRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.payroll.model.StandingInstructionRecurrence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds Andre (demo employee) with Art. 10 P4 exclusion payout standing inputs so payroll preview
 * exercises templates 1058–1065 (see {@code suriname-wage-tax-rules.md} §5.3 AC-P4-*).
 */
@Component
public class DemoP4ExclusionStandingSeeder {

	static final UUID ANDRE_EMPLOYEE_ID = UUID.fromString("5fa00000-0000-4000-8000-000000000006");

	static final Set<String> P4_PAYOUT_TEMPLATE_CODES = Set.of("1058", "1060", "1062", "1064");

	private static final BigDecimal COST_ALLOWANCE_AMOUNT = new BigDecimal("425.0000");
	private static final BigDecimal COMMUTE_TRANSPORT_AMOUNT = new BigDecimal("1200.0000");
	private static final BigDecimal TRAINING_AMOUNT = new BigDecimal("3500.0000");
	private static final BigDecimal PENSION_PAYOUT_AMOUNT = new BigDecimal("3000.0000");

	private final TenantWageComponentRepository wageComponentRepository;
	private final TenantEmployeePayrollStandingInstructionRepository standingRepository;

	public DemoP4ExclusionStandingSeeder(TenantWageComponentRepository wageComponentRepository,
			TenantEmployeePayrollStandingInstructionRepository standingRepository) {
		this.wageComponentRepository = wageComponentRepository;
		this.standingRepository = standingRepository;
	}

	@Transactional
	public int seedAndreP4Exclusions(UUID tenantId, UUID companyId, List<TenantEmployeeEntity> employees) {
		TenantEmployeeEntity andre = employees.stream()
				.filter(e -> ANDRE_EMPLOYEE_ID.equals(e.getId()))
				.findFirst()
				.orElse(null);
		if (andre == null || !andre.isActive() || "TERMINATED".equalsIgnoreCase(andre.getStatus())) {
			return 0;
		}
		Map<String, TenantWageComponentEntity> componentByCode = wageComponentRepository
				.findByTenantIdAndCompanyIdAndActiveIsTrueOrderByProcessingOrderAsc(tenantId, companyId)
				.stream()
				.filter(c -> P4_PAYOUT_TEMPLATE_CODES.contains(c.getCode()))
				.collect(Collectors.toMap(TenantWageComponentEntity::getCode, Function.identity(), (a, b) -> a));
		if (componentByCode.isEmpty()) {
			return 0;
		}

		int updated = 0;
		updated += upsertAmount(andre, componentByCode.get("1058"), COST_ALLOWANCE_AMOUNT, tenantId, companyId);
		updated += upsertAmount(andre, componentByCode.get("1060"), COMMUTE_TRANSPORT_AMOUNT, tenantId, companyId);
		updated += upsertAmount(andre, componentByCode.get("1062"), TRAINING_AMOUNT, tenantId, companyId);
		updated += upsertAmount(andre, componentByCode.get("1064"), PENSION_PAYOUT_AMOUNT, tenantId, companyId);
		return updated;
	}

	private int upsertAmount(TenantEmployeeEntity employee, TenantWageComponentEntity component, BigDecimal amount,
			UUID tenantId, UUID companyId) {
		if (component == null || amount == null || amount.signum() <= 0) {
			return 0;
		}
		return upsertStanding(employee, component, scaleMoney(amount), tenantId, companyId);
	}

	private int upsertStanding(TenantEmployeeEntity employee, TenantWageComponentEntity component, BigDecimal amount,
			UUID tenantId, UUID companyId) {
		LocalDate effectiveFrom = employee.getHireDate() != null ? employee.getHireDate() : LocalDate.of(2020, 1, 1);
		List<TenantEmployeePayrollStandingInstructionEntity> existing = standingRepository
				.findByTenantIdAndCompanyIdAndEmployeeIdOrderByEffectiveFromAsc(tenantId, companyId, employee.getId())
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
			row.setQuantity(null);
			row.setRate(null);
			row.setRecurrence(StandingInstructionRecurrence.EACH_PAY_PERIOD);
			row.setActive(true);
			row.setAmountOverride(true);
			row.setFactorOverride(false);
			row.setRemarks("Demo P4 Art. 10 exclusion payout seed");
			row.setCreatedAt(now);
			row.setUpdatedAt(now);
			standingRepository.save(row);
			return 1;
		}
		boolean changed = false;
		for (TenantEmployeePayrollStandingInstructionEntity row : existing) {
			if (row.getAmount() == null || row.getAmount().compareTo(amount) != 0) {
				row.setAmount(amount);
				row.setAmountOverride(true);
				changed = true;
			}
			if (!row.isActive()) {
				row.setActive(true);
				changed = true;
			}
			if (changed) {
				row.setRemarks("Demo P4 Art. 10 exclusion payout seed");
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
