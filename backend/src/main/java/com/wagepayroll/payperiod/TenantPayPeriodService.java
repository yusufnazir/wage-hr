package com.wagepayroll.payperiod;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.api.dto.CompanyCalendarAdvanceResultDto;
import com.wagepayroll.api.dto.PayPeriodGenerateResultDto;
import com.wagepayroll.api.dto.TenantPayPeriodItemDto;
import com.wagepayroll.api.dto.TenantPayPeriodRunCreateRequest;
import com.wagepayroll.api.dto.TenantPayPeriodRunItemDto;
import com.wagepayroll.api.dto.TenantPayPeriodStatusPatchRequest;
import com.wagepayroll.api.dto.TenantPayPeriodUpsertRequest;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.org.TenantPayPeriodEntity;
import com.wagepayroll.domain.org.TenantPayPeriodRepository;
import com.wagepayroll.domain.org.TenantPayPeriodRunEntity;
import com.wagepayroll.domain.org.TenantPayPeriodRunRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantPayPeriodService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final Set<String> ALLOWED_STATUSES = Set.of("READY", "OPEN", "CLOSED");
	private static final Set<String> ALLOWED_RUN_TYPES = Set.of("INTERIM", "FINAL");

	private final TenantPayPeriodRepository payPeriodRepository;
	private final TenantPayPeriodRunRepository runRepository;
	private final TenantCompanyRepository companyRepository;

	public TenantPayPeriodService(TenantPayPeriodRepository payPeriodRepository,
			TenantPayPeriodRunRepository runRepository,
			TenantCompanyRepository companyRepository) {
		this.payPeriodRepository = payPeriodRepository;
		this.runRepository = runRepository;
		this.companyRepository = companyRepository;
	}

	@Transactional(readOnly = true)
	public Page<TenantPayPeriodItemDto> listPayPeriods(UUID tenantId, UUID companyId, Integer year, int page, int size,
			String sort, String status) {
		Pageable pageable = pageable(page, size, sort);
		Page<TenantPayPeriodEntity> rows;
		if (companyId != null && year != null && status != null) {
			rows = payPeriodRepository.findByTenantIdAndCompanyIdAndYearAndStatus(tenantId, companyId, year,
					normalizeStatus(status), pageable);
		}
		else if (companyId != null && year != null) {
			rows = payPeriodRepository.findByTenantIdAndCompanyIdAndYear(tenantId, companyId, year, pageable);
		}
		else if (companyId != null && status != null) {
			rows = payPeriodRepository.findByTenantIdAndCompanyIdAndStatus(tenantId, companyId,
					normalizeStatus(status), pageable);
		}
		else if (companyId != null) {
			rows = payPeriodRepository.findByTenantIdAndCompanyId(tenantId, companyId, pageable);
		}
		else if (year != null && status != null) {
			rows = payPeriodRepository.findByTenantIdAndYearAndStatus(tenantId, year, normalizeStatus(status),
					pageable);
		}
		else if (year != null) {
			rows = payPeriodRepository.findByTenantIdAndYear(tenantId, year, pageable);
		}
		else if (status != null) {
			rows = payPeriodRepository.findByTenantIdAndStatus(tenantId, normalizeStatus(status), pageable);
		}
		else {
			rows = payPeriodRepository.findByTenantId(tenantId, pageable);
		}
		return rows.map(this::toDto);
	}

	@Transactional(readOnly = true)
	public TenantPayPeriodItemDto getPayPeriod(UUID tenantId, UUID id) {
		return toDto(requirePayPeriod(tenantId, id));
	}

	@Transactional
	public TenantPayPeriodItemDto createPayPeriod(UUID tenantId, TenantPayPeriodUpsertRequest request) {
		if (request == null) {
			throw badRequest("Request body is required");
		}
		UUID companyId = requireUuid(request.companyId(), "companyId is required");
		TenantPayPeriodEntity entity = new TenantPayPeriodEntity();
		entity.setId(UUID.randomUUID());
		entity.setTenantId(tenantId);
		entity.setCompanyId(companyId);
		applyPayPeriod(entity, request);
		Instant now = Instant.now();
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		return toDto(saveWithConflict(entity));
	}

	@Transactional
	public TenantPayPeriodItemDto updatePayPeriod(UUID tenantId, UUID id, TenantPayPeriodUpsertRequest request) {
		if (request == null) {
			throw badRequest("Request body is required");
		}
		TenantPayPeriodEntity entity = requirePayPeriod(tenantId, id);
		UUID companyId = requireUuid(request.companyId(), "companyId is required");
		if (!entity.getCompanyId().equals(companyId)) {
			throw badRequest("companyId cannot be changed");
		}
		applyPayPeriod(entity, request);
		entity.setUpdatedAt(Instant.now());
		return toDto(saveWithConflict(entity));
	}

	@Transactional
	public TenantPayPeriodItemDto patchPayPeriodStatus(UUID tenantId, UUID id,
			TenantPayPeriodStatusPatchRequest request) {
		if (request == null || request.status() == null || request.status().isBlank()) {
			throw badRequest("status is required");
		}
		TenantPayPeriodEntity entity = requirePayPeriod(tenantId, id);
		entity.setStatus(normalizeStatus(request.status()));
		entity.setUpdatedAt(Instant.now());
		return toDto(saveWithConflict(entity));
	}

	@Transactional(readOnly = true)
	public Page<TenantPayPeriodRunItemDto> listRuns(UUID tenantId, UUID payPeriodId, int page, int size) {
		requirePayPeriod(tenantId, payPeriodId);
		Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
				Sort.by(Sort.Order.asc("runNumber")));
		return runRepository.findByTenantIdAndPayPeriodId(tenantId, payPeriodId, pageable).map(this::toRunDto);
	}

	@Transactional
	public TenantPayPeriodRunItemDto createRun(UUID tenantId, TenantPayPeriodRunCreateRequest request) {
		if (request == null) {
			throw badRequest("Request body is required");
		}
		UUID payPeriodId = requireUuid(request.payPeriodId(), "payPeriodId is required");
		requirePayPeriod(tenantId, payPeriodId);
		String runType = normalizeRunType(request.runType());
		long count = runRepository.countByTenantIdAndPayPeriodId(tenantId, payPeriodId);
		int runNumber = (int) (count + 1);
		TenantPayPeriodRunEntity entity = new TenantPayPeriodRunEntity();
		entity.setId(UUID.randomUUID());
		entity.setTenantId(tenantId);
		entity.setPayPeriodId(payPeriodId);
		entity.setRunType(runType);
		entity.setRunNumber(runNumber);
		Instant now = Instant.now();
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		return toRunDto(saveRunWithConflict(entity));
	}

	@Transactional(readOnly = true)
	public TenantPayPeriodRunItemDto getRun(UUID tenantId, UUID id) {
		return toRunDto(requireRun(tenantId, id));
	}

	private void applyPayPeriod(TenantPayPeriodEntity entity, TenantPayPeriodUpsertRequest request) {
		if (request.year() == null || request.year() < 1900 || request.year() > 2200) {
			throw badRequest("year must be a valid calendar year");
		}
		entity.setYear(request.year());
		if (request.startDate() == null) {
			throw badRequest("startDate is required");
		}
		entity.setStartDate(request.startDate());
		if (request.endDate() == null) {
			throw badRequest("endDate is required");
		}
		if (request.endDate().isBefore(request.startDate())) {
			throw badRequest("endDate must not be before startDate");
		}
		entity.setEndDate(request.endDate());
		entity.setStatus(normalizeStatus(request.status()));
	}

	private String normalizeStatus(String value) {
		if (value == null || value.isBlank()) {
			throw badRequest("status is required");
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		if (!ALLOWED_STATUSES.contains(normalized)) {
			throw badRequest("status must be one of: READY, OPEN, CLOSED");
		}
		return normalized;
	}

	private String normalizeRunType(String value) {
		if (value == null || value.isBlank()) {
			throw badRequest("runType is required");
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		if (!ALLOWED_RUN_TYPES.contains(normalized)) {
			throw badRequest("runType must be INTERIM or FINAL");
		}
		return normalized;
	}

	private UUID requireUuid(UUID value, String message) {
		if (value == null) {
			throw badRequest(message);
		}
		return value;
	}

	private TenantPayPeriodEntity requirePayPeriod(UUID tenantId, UUID id) {
		return payPeriodRepository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pay period not found"));
	}

	private TenantPayPeriodRunEntity requireRun(UUID tenantId, UUID id) {
		return runRepository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pay period run not found"));
	}

	private TenantPayPeriodEntity saveWithConflict(TenantPayPeriodEntity entity) {
		try {
			return payPeriodRepository.save(entity);
		}
		catch (DataIntegrityViolationException ex) {
			throw conflict("Request conflicts with existing records");
		}
	}

	private TenantPayPeriodRunEntity saveRunWithConflict(TenantPayPeriodRunEntity entity) {
		try {
			return runRepository.save(entity);
		}
		catch (DataIntegrityViolationException ex) {
			throw conflict("Request conflicts with existing records");
		}
	}

	private Pageable pageable(int page, int size, String sort) {
		int safePage = Math.max(page, 0);
		int safeSize = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
		return PageRequest.of(safePage, safeSize, parseSort(sort));
	}

	private Sort parseSort(String sort) {
		Set<String> allowedFields = Set.of("year", "startDate", "endDate", "status", "updatedAt", "createdAt");
		String defaultField = "startDate";
		if (sort == null || sort.isBlank()) {
			return Sort.by(new Sort.Order(Sort.Direction.DESC, defaultField), Sort.Order.desc("updatedAt"));
		}
		String[] parts = sort.split(",");
		String field = parts[0].trim();
		if (!allowedFields.contains(field)) {
			throw badRequest("Unsupported sort field: " + field);
		}
		Sort.Direction direction = Sort.Direction.ASC;
		if (parts.length > 1) {
			String token = parts[1].trim();
			if ("desc".equalsIgnoreCase(token)) {
				direction = Sort.Direction.DESC;
			}
			else if (!"asc".equalsIgnoreCase(token)) {
				throw badRequest("Sort direction must be asc or desc");
			}
		}
		return Sort.by(new Sort.Order(direction, field), Sort.Order.desc("updatedAt"));
	}

	private TenantPayPeriodItemDto toDto(TenantPayPeriodEntity e) {
		return new TenantPayPeriodItemDto(
				e.getId(),
				e.getCompanyId(),
				e.getYear(),
				e.getStartDate() != null ? e.getStartDate().toString() : null,
				e.getEndDate() != null ? e.getEndDate().toString() : null,
				e.getStatus(),
				e.getCreatedAt(),
				e.getUpdatedAt());
	}

	private TenantPayPeriodRunItemDto toRunDto(TenantPayPeriodRunEntity e) {
		return new TenantPayPeriodRunItemDto(
				e.getId(),
				e.getPayPeriodId(),
				e.getTenantId(),
				e.getRunType(),
				e.getRunNumber(),
				e.getCreatedAt(),
				e.getUpdatedAt());
	}

	private ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	private ResponseStatusException conflict(String message) {
		return new ResponseStatusException(HttpStatus.CONFLICT, message);
	}

	/**
	 * After a successful FINAL payroll run: close the pay period and advance the company calendar when the
	 * finalized period matches {@code currentYear}/{@code currentPeriod}.
	 */
	@Transactional
	public CompanyCalendarAdvanceResultDto advanceCompanyCalendarAfterFinalize(UUID tenantId, UUID payPeriodId,
			String runType) {
		if (runType == null || !"FINAL".equalsIgnoreCase(runType.trim())) {
			return new CompanyCalendarAdvanceResultDto(false, null, null, null, null, null);
		}
		TenantPayPeriodEntity period = requirePayPeriod(tenantId, payPeriodId);
		TenantCompanyEntity company = companyRepository.findByIdAndTenantId(period.getCompanyId(), tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));

		Integer prevYear = company.getCurrentYear();
		Integer prevPeriod = company.getCurrentPeriod();

		period.setStatus("CLOSED");
		period.setUpdatedAt(Instant.now());
		payPeriodRepository.save(period);

		if (prevYear == null || prevPeriod == null || !matchesCompanyPeriod(company, period)) {
			return new CompanyCalendarAdvanceResultDto(false, prevYear, prevPeriod, company.getCurrentYear(),
					company.getCurrentPeriod(),
					company.getPayPeriodEndDate() != null ? company.getPayPeriodEndDate().toString() : null);
		}

		int max = maxPeriodsForFrequency(company.getPayrollFrequency());
		int nextYear = prevYear;
		int nextPeriod = prevPeriod + 1;
		if (nextPeriod > max) {
			nextPeriod = 1;
			nextYear = prevYear + 1;
		}
		company.setCurrentYear(nextYear);
		company.setCurrentPeriod(nextPeriod);
		LocalDate nextEnd = periodEndForCalendarIndex(company, nextYear, nextPeriod);
		if (nextEnd != null) {
			company.setPayPeriodEndDate(nextEnd);
			ensurePayPeriodRowExists(tenantId, company.getId(), nextYear, nextEnd);
		}
		company.setUpdatedAt(Instant.now());
		companyRepository.save(company);

		return new CompanyCalendarAdvanceResultDto(true, prevYear, prevPeriod, nextYear, nextPeriod,
				nextEnd != null ? nextEnd.toString() : null);
	}

	private boolean matchesCompanyPeriod(TenantCompanyEntity company, TenantPayPeriodEntity period) {
		if (company.getCurrentYear() == null || period.getYear() != company.getCurrentYear()) {
			return false;
		}
		LocalDate expectedEnd = periodEndForCalendarIndex(company, company.getCurrentYear(), company.getCurrentPeriod());
		return expectedEnd != null && period.getEndDate().equals(expectedEnd);
	}

	private int maxPeriodsForFrequency(String payrollFrequency) {
		return switch (payrollFrequency) {
			case "WEEKLY" -> 53;
			case "BIWEEKLY" -> 27;
			case "SEMIMONTHLY" -> 24;
			default -> 12;
		};
	}

	private LocalDate periodEndForCalendarIndex(TenantCompanyEntity company, int year, int periodIndex1Based) {
		if (company.getPayPeriodEndDate() == null || periodIndex1Based < 1) {
			return null;
		}
		LocalDate anchor = company.getPayPeriodEndDate();
		String frequency = company.getPayrollFrequency();
		boolean anchorIsEOM = anchor.equals(anchor.withDayOfMonth(anchor.lengthOfMonth()));
		LocalDate end = anchor;
		LocalDate jan1 = LocalDate.of(year, 1, 1);
		while (!end.isBefore(jan1)) {
			end = previousEndBefore(anchor, frequency, anchorIsEOM, end);
		}
		end = nextEnd(end, frequency, anchorIsEOM);
		for (int i = 1; i < periodIndex1Based; i++) {
			end = nextEnd(end, frequency, anchorIsEOM);
		}
		return end;
	}

	private void ensurePayPeriodRowExists(UUID tenantId, UUID companyId, int year, LocalDate periodEnd) {
		TenantCompanyEntity company = companyRepository.findByIdAndTenantId(companyId, tenantId).orElseThrow();
		LocalDate anchor = company.getPayPeriodEndDate();
		boolean anchorIsEOM = anchor != null && anchor.equals(anchor.withDayOfMonth(anchor.lengthOfMonth()));
		LocalDate prev = previousEndBefore(anchor, company.getPayrollFrequency(), anchorIsEOM, periodEnd);
		LocalDate periodStart = computeStartDate(periodEnd, prev, company.getPayrollFrequency());
		if (!payPeriodRepository.existsByTenantIdAndCompanyIdAndStartDateAndEndDate(tenantId, companyId, periodStart,
				periodEnd)) {
			Instant now = Instant.now();
			TenantPayPeriodEntity entity = new TenantPayPeriodEntity();
			entity.setId(UUID.randomUUID());
			entity.setTenantId(tenantId);
			entity.setCompanyId(companyId);
			entity.setYear(year);
			entity.setStartDate(periodStart);
			entity.setEndDate(periodEnd);
			entity.setStatus("OPEN");
			entity.setCreatedAt(now);
			entity.setUpdatedAt(now);
			payPeriodRepository.save(entity);
		}
	}

	// -------------------------------------------------------------------------
	// Pay period generation
	// -------------------------------------------------------------------------

	/**
	 * Generates pay periods for a company based on its payroll frequency and payPeriodEndDate anchor.
	 * Skips periods that already exist (by exact start+end date match).
	 *
	 * @param tenantId   tenant scope
	 * @param companyId  target company
	 * @param fromDate   first period end must be on or after this date; defaults to today when null
	 * @param yearsAhead how many years ahead of today to generate (1–5)
	 * @return count of newly created periods
	 */
	@Transactional
	public PayPeriodGenerateResultDto generatePayPeriodsForCompany(UUID tenantId, UUID companyId,
			LocalDate fromDate, int yearsAhead) {
		int safeYears = Math.min(Math.max(yearsAhead, 1), 5);
		TenantCompanyEntity company = companyRepository.findByIdAndTenantId(companyId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));

		LocalDate anchor = company.getPayPeriodEndDate();
		if (anchor == null) {
			return new PayPeriodGenerateResultDto(0);
		}

		String frequency = company.getPayrollFrequency();
		LocalDate effectiveFrom = fromDate != null ? fromDate : LocalDate.now();
		LocalDate cutoff = LocalDate.now().plusYears(safeYears);
		boolean anchorIsEOM = anchor.equals(anchor.withDayOfMonth(anchor.lengthOfMonth()));

		LocalDate periodEnd = firstEndOnOrAfter(anchor, frequency, anchorIsEOM, effectiveFrom);
		LocalDate previousEnd = periodEnd.equals(anchor) ? null
				: previousEndBefore(anchor, frequency, anchorIsEOM, periodEnd);

		int created = 0;
		Instant now = Instant.now();

		while (!periodEnd.isAfter(cutoff)) {
			LocalDate periodStart = computeStartDate(periodEnd, previousEnd, frequency);
			if (!payPeriodRepository.existsByTenantIdAndCompanyIdAndStartDateAndEndDate(
					tenantId, companyId, periodStart, periodEnd)) {
				TenantPayPeriodEntity entity = new TenantPayPeriodEntity();
				entity.setId(UUID.randomUUID());
				entity.setTenantId(tenantId);
				entity.setCompanyId(companyId);
				entity.setYear(periodEnd.getYear());
				entity.setStartDate(periodStart);
				entity.setEndDate(periodEnd);
				entity.setStatus("READY");
				entity.setCreatedAt(now);
				entity.setUpdatedAt(now);
				payPeriodRepository.save(entity);
				created++;
			}
			previousEnd = periodEnd;
			periodEnd = nextEnd(periodEnd, frequency, anchorIsEOM);
		}

		return new PayPeriodGenerateResultDto(created);
	}

	/** Find the first period-end date on or after {@code target}, anchored at {@code anchor}. */
	private LocalDate firstEndOnOrAfter(LocalDate anchor, String frequency, boolean anchorIsEOM, LocalDate target) {
		if (!anchor.isBefore(target)) {
			// anchor is on or after target: step backward until we find first end >= target
			LocalDate cur = anchor;
			while (true) {
				LocalDate prev = previousEndBefore(anchor, frequency, anchorIsEOM, cur);
				if (prev == null || prev.isBefore(target)) {
					return cur;
				}
				cur = prev;
			}
		}
		// anchor is before target: step forward
		LocalDate cur = anchor;
		while (cur.isBefore(target)) {
			cur = nextEnd(cur, frequency, anchorIsEOM);
		}
		return cur;
	}

	/** Compute the end date immediately before {@code periodEnd} in the same sequence. */
	private LocalDate previousEndBefore(LocalDate anchor, String frequency, boolean anchorIsEOM, LocalDate periodEnd) {
		switch (frequency) {
			case "WEEKLY" -> { return periodEnd.minusDays(7); }
			case "BIWEEKLY" -> { return periodEnd.minusDays(14); }
			case "MONTHLY" -> {
				YearMonth ym = YearMonth.from(periodEnd).minusMonths(1);
				if (anchorIsEOM) return ym.atEndOfMonth();
				int day = anchor.getDayOfMonth();
				return ym.atDay(Math.min(day, ym.lengthOfMonth()));
			}
			case "SEMIMONTHLY" -> {
				int day = periodEnd.getDayOfMonth();
				if (day == 15) {
					// previous = EOM of prior month
					YearMonth ym = YearMonth.from(periodEnd).minusMonths(1);
					return ym.atEndOfMonth();
				} else {
					// EOM -> previous = 15th of same month
					return periodEnd.withDayOfMonth(15);
				}
			}
			default -> throw badRequest("Unknown payroll frequency: " + frequency);
		}
	}

	/** Advance one period forward. */
	private LocalDate nextEnd(LocalDate current, String frequency, boolean anchorIsEOM) {
		switch (frequency) {
			case "WEEKLY" -> { return current.plusDays(7); }
			case "BIWEEKLY" -> { return current.plusDays(14); }
			case "MONTHLY" -> {
				YearMonth ym = YearMonth.from(current).plusMonths(1);
				if (anchorIsEOM) return ym.atEndOfMonth();
				int day = current.getDayOfMonth();
				return ym.atDay(Math.min(day, ym.lengthOfMonth()));
			}
			case "SEMIMONTHLY" -> {
				int day = current.getDayOfMonth();
				if (day <= 15) {
					// 15th -> EOM of same month
					YearMonth ym = YearMonth.from(current);
					return ym.atEndOfMonth();
				} else {
					// EOM -> 15th of next month
					return current.plusMonths(1).withDayOfMonth(15);
				}
			}
			default -> throw badRequest("Unknown payroll frequency: " + frequency);
		}
	}

	/** Compute start date of a period given its end date and the previous period's end date (if any). */
	private LocalDate computeStartDate(LocalDate periodEnd, LocalDate previousEnd, String frequency) {
		if (previousEnd != null) {
			return previousEnd.plusDays(1);
		}
		// First period in sequence: derive start from end based on frequency
		return switch (frequency) {
			case "WEEKLY" -> periodEnd.minusDays(6);
			case "BIWEEKLY" -> periodEnd.minusDays(13);
			case "SEMIMONTHLY" -> {
				int day = periodEnd.getDayOfMonth();
				yield (day == 15) ? periodEnd.withDayOfMonth(1) : periodEnd.withDayOfMonth(16);
			}
			case "MONTHLY" -> periodEnd.withDayOfMonth(1);
			default -> throw badRequest("Unknown payroll frequency: " + frequency);
		};
	}
}
