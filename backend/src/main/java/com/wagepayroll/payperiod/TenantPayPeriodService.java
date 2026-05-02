package com.wagepayroll.payperiod;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.api.dto.TenantPayPeriodItemDto;
import com.wagepayroll.api.dto.TenantPayPeriodRunCreateRequest;
import com.wagepayroll.api.dto.TenantPayPeriodRunItemDto;
import com.wagepayroll.api.dto.TenantPayPeriodStatusPatchRequest;
import com.wagepayroll.api.dto.TenantPayPeriodUpsertRequest;
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

	public TenantPayPeriodService(TenantPayPeriodRepository payPeriodRepository,
			TenantPayPeriodRunRepository runRepository) {
		this.payPeriodRepository = payPeriodRepository;
		this.runRepository = runRepository;
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
}
