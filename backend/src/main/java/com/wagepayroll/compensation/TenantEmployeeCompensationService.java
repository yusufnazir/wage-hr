package com.wagepayroll.compensation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.api.dto.TenantEmployeeCompensationDto;
import com.wagepayroll.api.dto.TenantEmployeeCompensationPutRequest;
import com.wagepayroll.domain.compensation.TenantEmployeeCompensationEntity;
import com.wagepayroll.domain.compensation.TenantEmployeeCompensationRepository;
import com.wagepayroll.domain.currency.PlatformCurrencyRepository;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.org.TenantEmployeeEntity;
import com.wagepayroll.domain.org.TenantEmployeeRepository;
import com.wagepayroll.domain.org.TenantWorkTimeEntity;
import com.wagepayroll.domain.org.TenantWorkTimeRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Per-employee compensation: stores the current pay setup (currency, wage type,
 * primary amount and tax/premium toggles) used by the payroll engine.
 *
 * <p>One row per employee. GET returns a 404 envelope when the employee has no
 * compensation yet so the UI can show a fresh form; PUT is idempotent and
 * creates or replaces the row in a single call.
 */
@Service
public class TenantEmployeeCompensationService {

	private static final Set<String> WAGE_TYPES = Set.of("PER_HOUR", "PER_PERIOD");

	private final TenantEmployeeCompensationRepository compensationRepository;
	private final TenantEmployeeRepository employeeRepository;
	private final TenantCompanyRepository companyRepository;
	private final TenantWorkTimeRepository workTimeRepository;
	private final PlatformCurrencyRepository platformCurrencyRepository;

	public TenantEmployeeCompensationService(TenantEmployeeCompensationRepository compensationRepository,
			TenantEmployeeRepository employeeRepository, TenantCompanyRepository companyRepository,
			TenantWorkTimeRepository workTimeRepository, PlatformCurrencyRepository platformCurrencyRepository) {
		this.compensationRepository = compensationRepository;
		this.employeeRepository = employeeRepository;
		this.companyRepository = companyRepository;
		this.workTimeRepository = workTimeRepository;
		this.platformCurrencyRepository = platformCurrencyRepository;
	}

	@Transactional(readOnly = true)
	public TenantEmployeeCompensationDto getForEmployee(UUID tenantId, UUID employeeId) {
		TenantEmployeeEntity employee = requireEmployee(tenantId, employeeId);
		TenantEmployeeCompensationEntity compensation = compensationRepository
				.findByEmployeeIdAndTenantId(employee.getId(), tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compensation not set"));
		TenantCompanyEntity company = companyRepository.findByIdAndTenantId(employee.getCompanyId(), tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
		TenantWorkTimeEntity workTime = compensation.getWorkTimeId() == null ? null
				: workTimeRepository.findByIdAndTenantId(compensation.getWorkTimeId(), tenantId).orElse(null);
		return toDto(compensation, workTime, company);
	}

	@Transactional
	public TenantEmployeeCompensationDto putForEmployee(UUID tenantId, UUID employeeId,
			TenantEmployeeCompensationPutRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		TenantEmployeeEntity employee = requireEmployee(tenantId, employeeId);
		TenantCompanyEntity company = companyRepository.findByIdAndTenantId(employee.getCompanyId(), tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));

		TenantEmployeeCompensationEntity entity = compensationRepository
				.findByEmployeeIdAndTenantId(employee.getId(), tenantId)
				.orElseGet(TenantEmployeeCompensationEntity::new);
		boolean creating = entity.getId() == null;
		if (creating) {
			entity.setId(UUID.randomUUID());
			entity.setTenantId(tenantId);
			entity.setCompanyId(employee.getCompanyId());
			entity.setEmployeeId(employee.getId());
			entity.setCreatedAt(Instant.now());
		}

		entity.setCurrencyCode(normalizeCurrencyCode(request.currencyCode()));
		entity.setWageType(normalizeWageType(request.wageType()));
		entity.setWageAmount(requirePositiveAmount(request.wageAmount(), "wageAmount"));
		TenantWorkTimeEntity workTime = resolveWorkTime(tenantId, employee.getCompanyId(), request.workTimeId());
		entity.setWorkTimeId(workTime == null ? null : workTime.getId());
		entity.setApplyTaxes(request.applyTaxes() == null ? true : request.applyTaxes());
		entity.setApplyTaxExempt(request.applyTaxExempt() == null ? true : request.applyTaxExempt());
		entity.setApplyAov(request.applyAov() == null ? true : request.applyAov());
		entity.setNotes(trimToNull(request.notes(), 500, "notes"));
		entity.setUpdatedAt(Instant.now());

		TenantEmployeeCompensationEntity saved;
		try {
			saved = compensationRepository.save(entity);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Compensation conflicts with existing data");
		}
		return toDto(saved, workTime, company);
	}

	// ------------------------------------------------------------------ helpers

	private TenantEmployeeEntity requireEmployee(UUID tenantId, UUID employeeId) {
		return employeeRepository.findByIdAndTenantId(employeeId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
	}

	private String normalizeCurrencyCode(String value) {
		if (value == null || value.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currencyCode is required");
		}
		String code = value.trim().toUpperCase(Locale.ROOT);
		if (!code.matches("^[A-Z]{3}$")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currencyCode must be ISO-4217");
		}
		if (!platformCurrencyRepository.existsByCodeIgnoreCase(code)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown currency code");
		}
		return code;
	}

	private String normalizeWageType(String value) {
		if (value == null || value.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "wageType is required");
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		if (!WAGE_TYPES.contains(normalized)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "wageType must be one of " + WAGE_TYPES);
		}
		return normalized;
	}

	private BigDecimal requirePositiveAmount(BigDecimal value, String field) {
		if (value == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
		}
		if (value.signum() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be greater than zero");
		}
		return value.setScale(4, RoundingMode.HALF_UP);
	}

	private TenantWorkTimeEntity resolveWorkTime(UUID tenantId, UUID companyId, UUID workTimeId) {
		if (workTimeId == null) {
			return null;
		}
		TenantWorkTimeEntity workTime = workTimeRepository.findByIdAndTenantId(workTimeId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown workTimeId"));
		if (!workTime.getCompanyId().equals(companyId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work time does not belong to the employee's company");
		}
		return workTime;
	}

	private String trimToNull(String value, int maxLen, String field) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		if (trimmed.length() > maxLen) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " exceeds max length " + maxLen);
		}
		return trimmed;
	}

	private TenantEmployeeCompensationDto toDto(TenantEmployeeCompensationEntity entity, TenantWorkTimeEntity workTime,
			TenantCompanyEntity company) {
		BigDecimal hoursPerWeek = workTime == null ? null
				: workTime.getHoursPerDay().multiply(BigDecimal.valueOf(workTime.getWorkDaysPerWeek()));
		BigDecimal yearly = computeYearly(entity, hoursPerWeek, company.getPayrollFrequency());
		BigDecimal monthly = yearly == null ? null : yearly.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
		BigDecimal period = yearly == null ? null
				: yearly.divide(BigDecimal.valueOf(periodsPerYear(company.getPayrollFrequency())), 2,
						RoundingMode.HALF_UP);
		BigDecimal hourly = (yearly == null || hoursPerWeek == null || hoursPerWeek.signum() <= 0) ? null
				: yearly.divide(hoursPerWeek.multiply(BigDecimal.valueOf(52)), 4, RoundingMode.HALF_UP);

		return new TenantEmployeeCompensationDto(
				entity.getId(),
				entity.getEmployeeId(),
				entity.getCompanyId(),
				entity.getCurrencyCode(),
				entity.getWageType(),
				entity.getWageAmount().setScale(4, RoundingMode.HALF_UP),
				entity.getWorkTimeId(),
				workTime == null ? null : workTime.getName(),
				workTime == null ? null : workTime.getHoursPerDay(),
				workTime == null ? null : workTime.getWorkDaysPerWeek(),
				entity.isApplyTaxes(),
				entity.isApplyTaxExempt(),
				entity.isApplyAov(),
				entity.getNotes(),
				yearly == null ? null : yearly.setScale(2, RoundingMode.HALF_UP),
				period,
				monthly,
				hourly,
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	private BigDecimal computeYearly(TenantEmployeeCompensationEntity entity, BigDecimal hoursPerWeek,
			String payrollFrequency) {
		BigDecimal amount = entity.getWageAmount();
		return switch (entity.getWageType()) {
			case "PER_HOUR" -> hoursPerWeek == null ? null
					: amount.multiply(hoursPerWeek).multiply(BigDecimal.valueOf(52));
			case "PER_PERIOD" -> amount.multiply(BigDecimal.valueOf(periodsPerYear(payrollFrequency)));
			case "PER_MONTH" -> amount.multiply(BigDecimal.valueOf(12));
			case "PER_YEAR" -> amount;
			default -> null;
		};
	}

	private int periodsPerYear(String payrollFrequency) {
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
