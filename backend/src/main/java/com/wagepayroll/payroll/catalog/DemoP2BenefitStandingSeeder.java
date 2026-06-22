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
 * Seeds Andre (demo employee) with Art. 10 P2 benefit-in-kind standing inputs so payroll preview
 * exercises templates 1049–1054 and 1057 (see {@code suriname-wage-tax-rules.md} §5.1 AC-P2-*).
 */
@Component
public class DemoP2BenefitStandingSeeder {

	static final UUID ANDRE_EMPLOYEE_ID = UUID.fromString("5fa00000-0000-4000-8000-000000000006");

	static final Set<String> P2_BENEFIT_TEMPLATE_CODES = Set.of("1049", "1050", "1051", "1052", "1053", "1054",
			"1057");

	private static final BigDecimal COMPANY_CAR_LIST_PRICE = new BigDecimal("180000.0000");
	private static final BigDecimal FREE_UTILITIES_AMOUNT = new BigDecimal("275.5000");

	private final TenantWageComponentRepository wageComponentRepository;
	private final TenantEmployeePayrollStandingInstructionRepository standingRepository;

	public DemoP2BenefitStandingSeeder(TenantWageComponentRepository wageComponentRepository,
			TenantEmployeePayrollStandingInstructionRepository standingRepository) {
		this.wageComponentRepository = wageComponentRepository;
		this.standingRepository = standingRepository;
	}

	@Transactional
	public int seedAndreP2Benefits(UUID tenantId, UUID companyId, List<TenantEmployeeEntity> employees) {
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
				.filter(c -> P2_BENEFIT_TEMPLATE_CODES.contains(c.getCode()))
				.collect(Collectors.toMap(TenantWageComponentEntity::getCode, Function.identity(), (a, b) -> a));
		if (componentByCode.isEmpty()) {
			return 0;
		}

		int updated = 0;
		updated += upsertAmount(andre, componentByCode.get("1049"), COMPANY_CAR_LIST_PRICE, tenantId, companyId);
		updated += upsertActiveFlag(andre, componentByCode.get("1050"), true, tenantId, companyId);
		updated += upsertQuantity(andre, componentByCode.get("1051"), new BigDecimal("15"), tenantId, companyId);
		updated += upsertQuantity(andre, componentByCode.get("1052"), new BigDecimal("20"), tenantId, companyId);
		updated += upsertQuantity(andre, componentByCode.get("1053"), new BigDecimal("22"), tenantId, companyId);
		updated += upsertQuantity(andre, componentByCode.get("1054"), new BigDecimal("20"), tenantId, companyId);
		updated += upsertAmount(andre, componentByCode.get("1057"), FREE_UTILITIES_AMOUNT, tenantId, companyId);
		return updated;
	}

	private int upsertAmount(TenantEmployeeEntity employee, TenantWageComponentEntity component, BigDecimal amount,
			UUID tenantId, UUID companyId) {
		if (component == null || amount == null || amount.signum() <= 0) {
			return 0;
		}
		return upsertStanding(employee, component, scaleMoney(amount), null, null, true, tenantId, companyId);
	}

	private int upsertQuantity(TenantEmployeeEntity employee, TenantWageComponentEntity component, BigDecimal quantity,
			UUID tenantId, UUID companyId) {
		if (component == null || quantity == null || quantity.signum() <= 0) {
			return 0;
		}
		return upsertStanding(employee, component, null, quantity, null, false, tenantId, companyId);
	}

	private int upsertActiveFlag(TenantEmployeeEntity employee, TenantWageComponentEntity component, boolean active,
			UUID tenantId, UUID companyId) {
		if (component == null) {
			return 0;
		}
		return upsertStanding(employee, component, null, null, null, false, tenantId, companyId, active);
	}

	private int upsertStanding(TenantEmployeeEntity employee, TenantWageComponentEntity component, BigDecimal amount,
			BigDecimal quantity, BigDecimal rate, boolean amountOverride, UUID tenantId, UUID companyId) {
		return upsertStanding(employee, component, amount, quantity, rate, amountOverride, tenantId, companyId, true);
	}

	private int upsertStanding(TenantEmployeeEntity employee, TenantWageComponentEntity component, BigDecimal amount,
			BigDecimal quantity, BigDecimal rate, boolean amountOverride, UUID tenantId, UUID companyId,
			boolean active) {
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
			row.setQuantity(quantity);
			row.setRate(rate);
			row.setRecurrence(StandingInstructionRecurrence.EACH_PAY_PERIOD);
			row.setActive(active);
			row.setAmountOverride(amountOverride && amount != null);
			row.setFactorOverride(quantity != null);
			row.setRemarks("Demo P2 Art. 10 benefits seed");
			row.setCreatedAt(now);
			row.setUpdatedAt(now);
			standingRepository.save(row);
			return 1;
		}
		boolean changed = false;
		for (TenantEmployeePayrollStandingInstructionEntity row : existing) {
			if (amount != null && (row.getAmount() == null || row.getAmount().compareTo(amount) != 0)) {
				row.setAmount(amount);
				row.setAmountOverride(amountOverride);
				changed = true;
			}
			if (quantity != null && (row.getQuantity() == null || row.getQuantity().compareTo(quantity) != 0)) {
				row.setQuantity(quantity);
				row.setFactorOverride(true);
				changed = true;
			}
			if (row.isActive() != active) {
				row.setActive(active);
				changed = true;
			}
			if (changed) {
				row.setRemarks("Demo P2 Art. 10 benefits seed");
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
