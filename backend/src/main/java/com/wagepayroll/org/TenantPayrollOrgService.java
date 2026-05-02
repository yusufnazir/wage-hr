package com.wagepayroll.org;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.api.dto.TenantActivePatchRequest;
import com.wagepayroll.api.dto.TenantCompanyItemDto;
import com.wagepayroll.api.dto.TenantCompanyUpsertRequest;
import com.wagepayroll.api.dto.TenantDepartmentItemDto;
import com.wagepayroll.api.dto.TenantDepartmentUpsertRequest;
import com.wagepayroll.api.dto.TenantEmployeeGroupItemDto;
import com.wagepayroll.api.dto.TenantEmployeeGroupUpsertRequest;
import com.wagepayroll.api.dto.TenantEmployeeItemDto;
import com.wagepayroll.api.dto.TenantEmployeeStatusPatchRequest;
import com.wagepayroll.api.dto.TenantEmployeeUpsertRequest;
import com.wagepayroll.api.dto.TenantJobItemDto;
import com.wagepayroll.api.dto.TenantJobUpsertRequest;
import com.wagepayroll.api.dto.TenantWorkTimeItemDto;
import com.wagepayroll.api.dto.TenantWorkTimeUpsertRequest;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.org.TenantDepartmentEntity;
import com.wagepayroll.domain.org.TenantDepartmentRepository;
import com.wagepayroll.domain.org.TenantEmployeeEntity;
import com.wagepayroll.domain.org.TenantEmployeeGroupEntity;
import com.wagepayroll.domain.org.TenantEmployeeGroupRepository;
import com.wagepayroll.domain.org.TenantEmployeeRepository;
import com.wagepayroll.domain.org.TenantJobEntity;
import com.wagepayroll.domain.org.TenantJobRepository;
import com.wagepayroll.domain.org.TenantWorkTimeEntity;
import com.wagepayroll.domain.org.TenantWorkTimeRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantPayrollOrgService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final Set<String> ALLOWED_PAYROLL_FREQUENCIES = Set.of("WEEKLY", "BIWEEKLY", "SEMIMONTHLY",
			"MONTHLY");
	private static final Set<String> ALLOWED_SALARY_TYPES = Set.of("HOURLY", "MONTHLY");
	private static final Set<String> ALLOWED_EMPLOYEE_STATUS = Set.of("ACTIVE", "ON_LEAVE", "SUSPENDED",
			"TERMINATED");

	private final TenantCompanyRepository companyRepository;
	private final TenantDepartmentRepository departmentRepository;
	private final TenantJobRepository jobRepository;
	private final TenantEmployeeGroupRepository employeeGroupRepository;
	private final TenantEmployeeRepository employeeRepository;
	private final TenantWorkTimeRepository workTimeRepository;

	public TenantPayrollOrgService(TenantCompanyRepository companyRepository, TenantDepartmentRepository departmentRepository,
			TenantJobRepository jobRepository, TenantEmployeeGroupRepository employeeGroupRepository,
			TenantEmployeeRepository employeeRepository, TenantWorkTimeRepository workTimeRepository) {
		this.companyRepository = companyRepository;
		this.departmentRepository = departmentRepository;
		this.jobRepository = jobRepository;
		this.employeeGroupRepository = employeeGroupRepository;
		this.employeeRepository = employeeRepository;
		this.workTimeRepository = workTimeRepository;
	}

	@Transactional(readOnly = true)
	public Page<TenantCompanyItemDto> listCompanies(UUID tenantId, int page, int size, String sort, Boolean active) {
		Pageable pageable = pageable(page, size, sort, Set.of("name", "legalName", "updatedAt", "createdAt"), "name");
		Page<TenantCompanyEntity> rows = active == null
				? companyRepository.findByTenantId(tenantId, pageable)
				: companyRepository.findByTenantIdAndActive(tenantId, active.booleanValue(), pageable);
		return rows.map(this::toCompanyDto);
	}

	@Transactional(readOnly = true)
	public TenantCompanyItemDto getCompany(UUID tenantId, UUID id) {
		return toCompanyDto(requireCompany(tenantId, id));
	}

	@Transactional
	public TenantCompanyItemDto createCompany(UUID tenantId, TenantCompanyUpsertRequest request) {
		TenantCompanyEntity entity = new TenantCompanyEntity();
		entity.setId(UUID.randomUUID());
		entity.setTenantId(tenantId);
		applyCompany(entity, request, true);
		Instant now = Instant.now();
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		return toCompanyDto(saveWithConflict(entity));
	}

	@Transactional
	public TenantCompanyItemDto updateCompany(UUID tenantId, UUID id, TenantCompanyUpsertRequest request) {
		TenantCompanyEntity entity = requireCompany(tenantId, id);
		applyCompany(entity, request, false);
		entity.setUpdatedAt(Instant.now());
		return toCompanyDto(saveWithConflict(entity));
	}

	@Transactional
	public TenantCompanyItemDto patchCompanyActive(UUID tenantId, UUID id, TenantActivePatchRequest request) {
		TenantCompanyEntity entity = requireCompany(tenantId, id);
		entity.setActive(requireActive(request));
		entity.setUpdatedAt(Instant.now());
		return toCompanyDto(saveWithConflict(entity));
	}

	@Transactional(readOnly = true)
	public Page<TenantDepartmentItemDto> listDepartments(UUID tenantId, UUID companyId, int page, int size, String sort,
			Boolean active) {
		requireCompany(tenantId, companyId);
		Pageable pageable = pageable(page, size, sort, Set.of("name", "code", "updatedAt", "createdAt"), "name");
		Page<TenantDepartmentEntity> rows = active == null
				? departmentRepository.findByTenantIdAndCompanyId(tenantId, companyId, pageable)
				: departmentRepository.findByTenantIdAndCompanyIdAndActive(tenantId, companyId, active.booleanValue(),
						pageable);
		return rows.map(this::toDepartmentDto);
	}

	@Transactional(readOnly = true)
	public TenantDepartmentItemDto getDepartment(UUID tenantId, UUID id) {
		return toDepartmentDto(requireDepartment(tenantId, id));
	}

	@Transactional
	public TenantDepartmentItemDto createDepartment(UUID tenantId, TenantDepartmentUpsertRequest request) {
		UUID companyId = requiredUuid(request.companyId(), "companyId is required");
		requireCompany(tenantId, companyId);
		TenantDepartmentEntity entity = new TenantDepartmentEntity();
		entity.setId(UUID.randomUUID());
		entity.setTenantId(tenantId);
		entity.setCompanyId(companyId);
		applyDepartment(entity, request, true);
		Instant now = Instant.now();
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		return toDepartmentDto(saveWithConflict(entity));
	}

	@Transactional
	public TenantDepartmentItemDto updateDepartment(UUID tenantId, UUID id, TenantDepartmentUpsertRequest request) {
		TenantDepartmentEntity entity = requireDepartment(tenantId, id);
		UUID companyId = requiredUuid(request.companyId(), "companyId is required");
		if (!entity.getCompanyId().equals(companyId)) {
			throw badRequest("companyId cannot be changed");
		}
		applyDepartment(entity, request, false);
		entity.setUpdatedAt(Instant.now());
		return toDepartmentDto(saveWithConflict(entity));
	}

	@Transactional
	public TenantDepartmentItemDto patchDepartmentActive(UUID tenantId, UUID id, TenantActivePatchRequest request) {
		TenantDepartmentEntity entity = requireDepartment(tenantId, id);
		entity.setActive(requireActive(request));
		entity.setUpdatedAt(Instant.now());
		return toDepartmentDto(saveWithConflict(entity));
	}

	@Transactional(readOnly = true)
	public Page<TenantJobItemDto> listJobs(UUID tenantId, UUID companyId, UUID departmentId, int page, int size,
			String sort, Boolean active) {
		requireCompany(tenantId, companyId);
		if (departmentId != null) {
			requireDepartment(tenantId, departmentId, companyId);
		}
		Pageable pageable = pageable(page, size, sort, Set.of("title", "code", "updatedAt", "createdAt"), "title");
		Page<TenantJobEntity> rows;
		if (departmentId != null) {
			rows = jobRepository.findByTenantIdAndCompanyIdAndDepartmentId(tenantId, companyId, departmentId, pageable);
		}
		else if (active != null) {
			rows = jobRepository.findByTenantIdAndCompanyIdAndActive(tenantId, companyId, active.booleanValue(), pageable);
		}
		else {
			rows = jobRepository.findByTenantIdAndCompanyId(tenantId, companyId, pageable);
		}
		return rows.map(this::toJobDto);
	}

	@Transactional(readOnly = true)
	public TenantJobItemDto getJob(UUID tenantId, UUID id) {
		return toJobDto(requireJob(tenantId, id));
	}

	@Transactional
	public TenantJobItemDto createJob(UUID tenantId, TenantJobUpsertRequest request) {
		UUID companyId = requiredUuid(request.companyId(), "companyId is required");
		UUID departmentId = requiredUuid(request.departmentId(), "departmentId is required");
		requireCompany(tenantId, companyId);
		requireDepartment(tenantId, departmentId, companyId);
		TenantJobEntity entity = new TenantJobEntity();
		entity.setId(UUID.randomUUID());
		entity.setTenantId(tenantId);
		entity.setCompanyId(companyId);
		entity.setDepartmentId(departmentId);
		applyJob(entity, request, true);
		Instant now = Instant.now();
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		return toJobDto(saveWithConflict(entity));
	}

	@Transactional
	public TenantJobItemDto updateJob(UUID tenantId, UUID id, TenantJobUpsertRequest request) {
		TenantJobEntity entity = requireJob(tenantId, id);
		UUID companyId = requiredUuid(request.companyId(), "companyId is required");
		if (!entity.getCompanyId().equals(companyId)) {
			throw badRequest("companyId cannot be changed");
		}
		UUID departmentId = requiredUuid(request.departmentId(), "departmentId is required");
		requireDepartment(tenantId, departmentId, companyId);
		entity.setDepartmentId(departmentId);
		applyJob(entity, request, false);
		entity.setUpdatedAt(Instant.now());
		return toJobDto(saveWithConflict(entity));
	}

	@Transactional
	public TenantJobItemDto patchJobActive(UUID tenantId, UUID id, TenantActivePatchRequest request) {
		TenantJobEntity entity = requireJob(tenantId, id);
		entity.setActive(requireActive(request));
		entity.setUpdatedAt(Instant.now());
		return toJobDto(saveWithConflict(entity));
	}

	@Transactional(readOnly = true)
	public Page<TenantEmployeeGroupItemDto> listEmployeeGroups(UUID tenantId, UUID companyId, int page, int size,
			String sort, Boolean active) {
		requireCompany(tenantId, companyId);
		Pageable pageable = pageable(page, size, sort, Set.of("name", "code", "updatedAt", "createdAt"), "name");
		Page<TenantEmployeeGroupEntity> rows = active == null
				? employeeGroupRepository.findByTenantIdAndCompanyId(tenantId, companyId, pageable)
				: employeeGroupRepository.findByTenantIdAndCompanyIdAndActive(tenantId, companyId, active.booleanValue(),
						pageable);
		return rows.map(this::toEmployeeGroupDto);
	}

	@Transactional(readOnly = true)
	public TenantEmployeeGroupItemDto getEmployeeGroup(UUID tenantId, UUID id) {
		return toEmployeeGroupDto(requireEmployeeGroup(tenantId, id));
	}

	@Transactional
	public TenantEmployeeGroupItemDto createEmployeeGroup(UUID tenantId, TenantEmployeeGroupUpsertRequest request) {
		UUID companyId = requiredUuid(request.companyId(), "companyId is required");
		requireCompany(tenantId, companyId);
		TenantEmployeeGroupEntity entity = new TenantEmployeeGroupEntity();
		entity.setId(UUID.randomUUID());
		entity.setTenantId(tenantId);
		entity.setCompanyId(companyId);
		applyEmployeeGroup(entity, request, true);
		Instant now = Instant.now();
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		return toEmployeeGroupDto(saveWithConflict(entity));
	}

	@Transactional
	public TenantEmployeeGroupItemDto updateEmployeeGroup(UUID tenantId, UUID id,
			TenantEmployeeGroupUpsertRequest request) {
		TenantEmployeeGroupEntity entity = requireEmployeeGroup(tenantId, id);
		UUID companyId = requiredUuid(request.companyId(), "companyId is required");
		if (!entity.getCompanyId().equals(companyId)) {
			throw badRequest("companyId cannot be changed");
		}
		applyEmployeeGroup(entity, request, false);
		entity.setUpdatedAt(Instant.now());
		return toEmployeeGroupDto(saveWithConflict(entity));
	}

	@Transactional
	public TenantEmployeeGroupItemDto patchEmployeeGroupActive(UUID tenantId, UUID id, TenantActivePatchRequest request) {
		TenantEmployeeGroupEntity entity = requireEmployeeGroup(tenantId, id);
		entity.setActive(requireActive(request));
		entity.setUpdatedAt(Instant.now());
		return toEmployeeGroupDto(saveWithConflict(entity));
	}

	@Transactional(readOnly = true)
	public Page<TenantEmployeeItemDto> listEmployees(UUID tenantId, UUID companyId, UUID departmentId, UUID jobId,
			UUID employeeGroupId, String status, int page, int size, String sort, Boolean active) {
		requireCompany(tenantId, companyId);
		Specification<TenantEmployeeEntity> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
		spec = spec.and((root, query, cb) -> cb.equal(root.get("companyId"), companyId));
		if (departmentId != null) {
			requireDepartment(tenantId, departmentId, companyId);
			spec = spec.and((root, query, cb) -> cb.equal(root.get("departmentId"), departmentId));
		}
		if (jobId != null) {
			requireJob(tenantId, jobId, companyId);
			spec = spec.and((root, query, cb) -> cb.equal(root.get("jobId"), jobId));
		}
		if (employeeGroupId != null) {
			requireEmployeeGroup(tenantId, employeeGroupId, companyId);
			spec = spec.and((root, query, cb) -> cb.equal(root.get("employeeGroupId"), employeeGroupId));
		}
		if (status != null && !status.isBlank()) {
			String normalizedStatus = normalizeEmployeeStatus(status);
			spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), normalizedStatus));
		}
		if (active != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active.booleanValue()));
		}
		Pageable pageable = pageable(page, size, sort, Set.of("lastName", "firstName", "hireDate", "updatedAt"),
				"lastName");
		return employeeRepository.findAll(spec, pageable).map(this::toEmployeeDto);
	}

	@Transactional(readOnly = true)
	public TenantEmployeeItemDto getEmployee(UUID tenantId, UUID id) {
		return toEmployeeDto(requireEmployee(tenantId, id));
	}

	@Transactional
	public TenantEmployeeItemDto createEmployee(UUID tenantId, TenantEmployeeUpsertRequest request) {
		UUID companyId = requiredUuid(request.companyId(), "companyId is required");
		UUID departmentId = requiredUuid(request.departmentId(), "departmentId is required");
		UUID jobId = requiredUuid(request.jobId(), "jobId is required");
		UUID employeeGroupId = requiredUuid(request.employeeGroupId(), "employeeGroupId is required");
		requireCompany(tenantId, companyId);
		requireDepartment(tenantId, departmentId, companyId);
		TenantJobEntity job = requireJob(tenantId, jobId, companyId);
		requireEmployeeGroup(tenantId, employeeGroupId, companyId);
		if (!job.getDepartmentId().equals(departmentId)) {
			throw badRequest("jobId does not belong to departmentId");
		}
		TenantEmployeeEntity entity = new TenantEmployeeEntity();
		entity.setId(UUID.randomUUID());
		entity.setTenantId(tenantId);
		entity.setCompanyId(companyId);
		entity.setDepartmentId(departmentId);
		entity.setJobId(jobId);
		entity.setEmployeeGroupId(employeeGroupId);
		applyEmployee(entity, request, true);
		Instant now = Instant.now();
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		return toEmployeeDto(saveWithConflict(entity));
	}

	@Transactional
	public TenantEmployeeItemDto updateEmployee(UUID tenantId, UUID id, TenantEmployeeUpsertRequest request) {
		TenantEmployeeEntity entity = requireEmployee(tenantId, id);
		UUID companyId = requiredUuid(request.companyId(), "companyId is required");
		if (!entity.getCompanyId().equals(companyId)) {
			throw badRequest("companyId cannot be changed");
		}
		UUID departmentId = requiredUuid(request.departmentId(), "departmentId is required");
		UUID jobId = requiredUuid(request.jobId(), "jobId is required");
		UUID employeeGroupId = requiredUuid(request.employeeGroupId(), "employeeGroupId is required");
		requireDepartment(tenantId, departmentId, companyId);
		TenantJobEntity job = requireJob(tenantId, jobId, companyId);
		requireEmployeeGroup(tenantId, employeeGroupId, companyId);
		if (!job.getDepartmentId().equals(departmentId)) {
			throw badRequest("jobId does not belong to departmentId");
		}
		entity.setDepartmentId(departmentId);
		entity.setJobId(jobId);
		entity.setEmployeeGroupId(employeeGroupId);
		applyEmployee(entity, request, false);
		entity.setUpdatedAt(Instant.now());
		return toEmployeeDto(saveWithConflict(entity));
	}

	@Transactional
	public TenantEmployeeItemDto patchEmployeeStatus(UUID tenantId, UUID id, TenantEmployeeStatusPatchRequest request) {
		if (request == null || request.status() == null || request.status().isBlank()) {
			throw badRequest("status is required");
		}
		TenantEmployeeEntity entity = requireEmployee(tenantId, id);
		entity.setStatus(normalizeEmployeeStatus(request.status()));
		entity.setUpdatedAt(Instant.now());
		return toEmployeeDto(saveWithConflict(entity));
	}

	@Transactional
	public TenantEmployeeItemDto patchEmployeeActive(UUID tenantId, UUID id, TenantActivePatchRequest request) {
		TenantEmployeeEntity entity = requireEmployee(tenantId, id);
		entity.setActive(requireActive(request));
		entity.setUpdatedAt(Instant.now());
		return toEmployeeDto(saveWithConflict(entity));
	}

	@Transactional(readOnly = true)
	public Page<TenantWorkTimeItemDto> listWorkTimes(UUID tenantId, UUID companyId, int page, int size, String sort,
			Boolean active) {
		requireCompany(tenantId, companyId);
		Pageable pageable = pageable(page, size, sort, Set.of("name", "code", "updatedAt", "createdAt"), "name");
		Page<TenantWorkTimeEntity> rows = active == null
				? workTimeRepository.findByTenantIdAndCompanyId(tenantId, companyId, pageable)
				: workTimeRepository.findByTenantIdAndCompanyIdAndActive(tenantId, companyId, active.booleanValue(),
						pageable);
		return rows.map(this::toWorkTimeDto);
	}

	@Transactional(readOnly = true)
	public TenantWorkTimeItemDto getWorkTime(UUID tenantId, UUID id) {
		return toWorkTimeDto(requireWorkTime(tenantId, id));
	}

	@Transactional
	public TenantWorkTimeItemDto createWorkTime(UUID tenantId, TenantWorkTimeUpsertRequest request) {
		UUID companyId = requiredUuid(request.companyId(), "companyId is required");
		requireCompany(tenantId, companyId);
		TenantWorkTimeEntity entity = new TenantWorkTimeEntity();
		entity.setId(UUID.randomUUID());
		entity.setTenantId(tenantId);
		entity.setCompanyId(companyId);
		applyWorkTime(entity, request, true);
		Instant now = Instant.now();
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		return toWorkTimeDto(saveWithConflict(entity));
	}

	@Transactional
	public TenantWorkTimeItemDto updateWorkTime(UUID tenantId, UUID id, TenantWorkTimeUpsertRequest request) {
		TenantWorkTimeEntity entity = requireWorkTime(tenantId, id);
		UUID companyId = requiredUuid(request.companyId(), "companyId is required");
		if (!entity.getCompanyId().equals(companyId)) {
			throw badRequest("companyId cannot be changed");
		}
		applyWorkTime(entity, request, false);
		entity.setUpdatedAt(Instant.now());
		return toWorkTimeDto(saveWithConflict(entity));
	}

	@Transactional
	public TenantWorkTimeItemDto patchWorkTimeActive(UUID tenantId, UUID id, TenantActivePatchRequest request) {
		TenantWorkTimeEntity entity = requireWorkTime(tenantId, id);
		entity.setActive(requireActive(request));
		entity.setUpdatedAt(Instant.now());
		return toWorkTimeDto(saveWithConflict(entity));
	}

	private void applyCompany(TenantCompanyEntity entity, TenantCompanyUpsertRequest request, boolean create) {
		if (request == null) {
			throw badRequest("Request body is required");
		}
		entity.setName(requireText(request.name(), "name", 120));
		entity.setLegalName(requireText(request.legalName(), "legalName", 180));
		entity.setRegistrationNumber(trimToNull(request.registrationNumber()));
		entity.setTaxId(requireText(request.taxId(), "taxId", 80));
		entity.setPayrollCountry(normalizeIso2(request.payrollCountry(), "payrollCountry"));
		entity.setCurrency(normalizeIso3(request.currency(), "currency"));
		entity.setPayrollFrequency(normalizePayrollFrequency(request.payrollFrequency()));
		entity.setTimezone(requireText(request.timezone(), "timezone", 60));
		entity.setDateFormat(requireText(request.dateFormat(), "dateFormat", 20));
		entity.setContactEmail(normalizeEmail(request.contactEmail()));
		entity.setContactPhone(trimToNull(request.contactPhone()));
		entity.setAddressLine1(trimToNull(request.addressLine1()));
		entity.setAddressLine2(trimToNull(request.addressLine2()));
		entity.setCity(trimToNull(request.city()));
		entity.setStateRegion(trimToNull(request.stateRegion()));
		entity.setPostalCode(trimToNull(request.postalCode()));
		entity.setCountry(request.country() == null ? null : normalizeIso2(request.country(), "country"));
		if (create) {
			entity.setActive(request.active() == null ? true : request.active().booleanValue());
		}
		else if (request.active() != null) {
			entity.setActive(request.active().booleanValue());
		}
		if (create && companyRepository.existsByTenantIdAndTaxId(entity.getTenantId(), entity.getTaxId())) {
			throw conflict("A company with this taxId already exists in tenant scope");
		}
		if (!create && companyRepository.existsByTenantIdAndTaxIdAndIdNot(entity.getTenantId(), entity.getTaxId(),
				entity.getId())) {
			throw conflict("A company with this taxId already exists in tenant scope");
		}
	}

	private void applyDepartment(TenantDepartmentEntity entity, TenantDepartmentUpsertRequest request, boolean create) {
		if (request == null) {
			throw badRequest("Request body is required");
		}
		entity.setName(requireText(request.name(), "name", 120));
		entity.setCode(requireText(request.code(), "code", 40));
		entity.setDescription(trimToNull(request.description()));
		if (request.parentDepartmentId() != null) {
			if (request.parentDepartmentId().equals(entity.getId())) {
				throw badRequest("Department cannot be its own parent");
			}
			requireDepartment(entity.getTenantId(), request.parentDepartmentId(), entity.getCompanyId());
		}
		entity.setParentDepartmentId(request.parentDepartmentId());
		if (request.managerEmployeeId() != null) {
			requireEmployee(entity.getTenantId(), request.managerEmployeeId(), entity.getCompanyId());
		}
		entity.setManagerEmployeeId(request.managerEmployeeId());
		if (create) {
			entity.setActive(request.active() == null ? true : request.active().booleanValue());
		}
		else if (request.active() != null) {
			entity.setActive(request.active().booleanValue());
		}
		if (create && departmentRepository.existsByTenantIdAndCompanyIdAndCode(entity.getTenantId(), entity.getCompanyId(),
				entity.getCode())) {
			throw conflict("A department with this code already exists in the company");
		}
		if (!create && departmentRepository.existsByTenantIdAndCompanyIdAndCodeAndIdNot(entity.getTenantId(),
				entity.getCompanyId(), entity.getCode(), entity.getId())) {
			throw conflict("A department with this code already exists in the company");
		}
	}

	private void applyJob(TenantJobEntity entity, TenantJobUpsertRequest request, boolean create) {
		if (request == null) {
			throw badRequest("Request body is required");
		}
		entity.setTitle(requireText(request.title(), "title", 140));
		entity.setCode(requireText(request.code(), "code", 40));
		entity.setDescription(trimToNull(request.description()));
		String salaryType = normalizeSalaryType(request.salaryType());
		entity.setSalaryType(salaryType);
		if (salaryType.equals("MONTHLY")) {
			entity.setDefaultSalary(requirePositive(request.defaultSalary(), "defaultSalary is required for MONTHLY"));
			entity.setDefaultHourlyRate(request.defaultHourlyRate());
		}
		else {
			entity.setDefaultHourlyRate(
					requirePositive(request.defaultHourlyRate(), "defaultHourlyRate is required for HOURLY"));
			entity.setDefaultSalary(request.defaultSalary());
		}
		if (request.standardHoursPerWeek() != null) {
			if (request.standardHoursPerWeek().compareTo(BigDecimal.ZERO) <= 0
					|| request.standardHoursPerWeek().compareTo(new BigDecimal("80")) > 0) {
				throw badRequest("standardHoursPerWeek must be > 0 and <= 80");
			}
		}
		entity.setStandardHoursPerWeek(request.standardHoursPerWeek());
		entity.setJobLevel(trimToNull(request.jobLevel()));
		entity.setJobCategory(trimToNull(request.jobCategory()));
		if (create) {
			entity.setActive(request.active() == null ? true : request.active().booleanValue());
		}
		else if (request.active() != null) {
			entity.setActive(request.active().booleanValue());
		}
		if (create && jobRepository.existsByTenantIdAndCompanyIdAndCode(entity.getTenantId(), entity.getCompanyId(),
				entity.getCode())) {
			throw conflict("A job with this code already exists in the company");
		}
		if (!create && jobRepository.existsByTenantIdAndCompanyIdAndCodeAndIdNot(entity.getTenantId(), entity.getCompanyId(),
				entity.getCode(), entity.getId())) {
			throw conflict("A job with this code already exists in the company");
		}
	}

	private void applyEmployeeGroup(TenantEmployeeGroupEntity entity, TenantEmployeeGroupUpsertRequest request,
			boolean create) {
		if (request == null) {
			throw badRequest("Request body is required");
		}
		entity.setName(requireText(request.name(), "name", 100));
		entity.setCode(requireText(request.code(), "code", 40));
		entity.setDescription(trimToNull(request.description()));
		if (create) {
			entity.setActive(request.active() == null ? true : request.active().booleanValue());
		}
		else if (request.active() != null) {
			entity.setActive(request.active().booleanValue());
		}
		if (create && employeeGroupRepository.existsByTenantIdAndCompanyIdAndCode(entity.getTenantId(),
				entity.getCompanyId(), entity.getCode())) {
			throw conflict("An employee group with this code already exists in the company");
		}
		if (!create && employeeGroupRepository.existsByTenantIdAndCompanyIdAndCodeAndIdNot(entity.getTenantId(),
				entity.getCompanyId(), entity.getCode(), entity.getId())) {
			throw conflict("An employee group with this code already exists in the company");
		}
	}

	private void applyEmployee(TenantEmployeeEntity entity, TenantEmployeeUpsertRequest request, boolean create) {
		if (request == null) {
			throw badRequest("Request body is required");
		}
		entity.setFirstName(requireText(request.firstName(), "firstName", 100));
		entity.setLastName(requireText(request.lastName(), "lastName", 100));
		entity.setDateOfBirth(request.dateOfBirth());
		if (request.hireDate() == null) {
			throw badRequest("hireDate is required");
		}
		entity.setHireDate(request.hireDate());
		entity.setEmail(normalizeEmail(request.email()));
		entity.setPhone(trimToNull(request.phone()));
		entity.setStatus(normalizeEmployeeStatus(request.status()));
		if (create) {
			entity.setActive(request.active() == null ? true : request.active().booleanValue());
		}
		else if (request.active() != null) {
			entity.setActive(request.active().booleanValue());
		}
	}

	private void applyWorkTime(TenantWorkTimeEntity entity, TenantWorkTimeUpsertRequest request, boolean create) {
		if (request == null) {
			throw badRequest("Request body is required");
		}
		entity.setName(requireText(request.name(), "name", 120));
		entity.setCode(requireText(request.code(), "code", 40));
		if (request.hoursPerDay() == null || request.hoursPerDay().compareTo(BigDecimal.ZERO) <= 0
				|| request.hoursPerDay().compareTo(new BigDecimal("24")) > 0) {
			throw badRequest("hoursPerDay must be > 0 and <= 24");
		}
		entity.setHoursPerDay(request.hoursPerDay());
		if (request.workDaysPerWeek() == null || request.workDaysPerWeek() < 1 || request.workDaysPerWeek() > 7) {
			throw badRequest("workDaysPerWeek must be between 1 and 7");
		}
		entity.setWorkDaysPerWeek(request.workDaysPerWeek().intValue());
		entity.setDescription(trimToNull(request.description()));
		if (create) {
			entity.setActive(request.active() == null ? true : request.active().booleanValue());
		}
		else if (request.active() != null) {
			entity.setActive(request.active().booleanValue());
		}
		if (create && workTimeRepository.existsByTenantIdAndCompanyIdAndCode(entity.getTenantId(), entity.getCompanyId(),
				entity.getCode())) {
			throw conflict("A work time with this code already exists in the company");
		}
		if (!create && workTimeRepository.existsByTenantIdAndCompanyIdAndCodeAndIdNot(entity.getTenantId(),
				entity.getCompanyId(), entity.getCode(), entity.getId())) {
			throw conflict("A work time with this code already exists in the company");
		}
	}

	private Pageable pageable(int page, int size, String sort, Set<String> allowedFields, String defaultField) {
		int safePage = Math.max(page, 0);
		int safeSize = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
		return PageRequest.of(safePage, safeSize, parseSort(sort, allowedFields, defaultField));
	}

	private Sort parseSort(String sort, Set<String> allowedFields, String defaultField) {
		if (sort == null || sort.isBlank()) {
			return Sort.by(new Sort.Order(Sort.Direction.ASC, defaultField), Sort.Order.desc("updatedAt"));
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

	private TenantCompanyEntity saveWithConflict(TenantCompanyEntity entity) {
		try {
			return companyRepository.save(entity);
		}
		catch (DataIntegrityViolationException ex) {
			throw conflict("Request conflicts with existing records");
		}
	}

	private TenantDepartmentEntity saveWithConflict(TenantDepartmentEntity entity) {
		try {
			return departmentRepository.save(entity);
		}
		catch (DataIntegrityViolationException ex) {
			throw conflict("Request conflicts with existing records");
		}
	}

	private TenantJobEntity saveWithConflict(TenantJobEntity entity) {
		try {
			return jobRepository.save(entity);
		}
		catch (DataIntegrityViolationException ex) {
			throw conflict("Request conflicts with existing records");
		}
	}

	private TenantEmployeeGroupEntity saveWithConflict(TenantEmployeeGroupEntity entity) {
		try {
			return employeeGroupRepository.save(entity);
		}
		catch (DataIntegrityViolationException ex) {
			throw conflict("Request conflicts with existing records");
		}
	}

	private TenantEmployeeEntity saveWithConflict(TenantEmployeeEntity entity) {
		try {
			return employeeRepository.save(entity);
		}
		catch (DataIntegrityViolationException ex) {
			throw conflict("Request conflicts with existing records");
		}
	}

	private TenantWorkTimeEntity saveWithConflict(TenantWorkTimeEntity entity) {
		try {
			return workTimeRepository.save(entity);
		}
		catch (DataIntegrityViolationException ex) {
			throw conflict("Request conflicts with existing records");
		}
	}

	private TenantCompanyEntity requireCompany(UUID tenantId, UUID id) {
		return companyRepository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
	}

	private TenantDepartmentEntity requireDepartment(UUID tenantId, UUID id) {
		return departmentRepository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
	}

	private TenantDepartmentEntity requireDepartment(UUID tenantId, UUID id, UUID companyId) {
		return departmentRepository.findByIdAndTenantIdAndCompanyId(id, tenantId, companyId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Department does not belong to specified company"));
	}

	private TenantJobEntity requireJob(UUID tenantId, UUID id) {
		return jobRepository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
	}

	private TenantJobEntity requireJob(UUID tenantId, UUID id, UUID companyId) {
		return jobRepository.findByIdAndTenantIdAndCompanyId(id, tenantId, companyId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Job does not belong to specified company"));
	}

	private TenantEmployeeGroupEntity requireEmployeeGroup(UUID tenantId, UUID id) {
		return employeeGroupRepository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee group not found"));
	}

	private TenantEmployeeGroupEntity requireEmployeeGroup(UUID tenantId, UUID id, UUID companyId) {
		return employeeGroupRepository.findByIdAndTenantIdAndCompanyId(id, tenantId, companyId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Employee group does not belong to specified company"));
	}

	private TenantEmployeeEntity requireEmployee(UUID tenantId, UUID id) {
		return employeeRepository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
	}

	private TenantEmployeeEntity requireEmployee(UUID tenantId, UUID id, UUID companyId) {
		return employeeRepository.findByIdAndTenantIdAndCompanyId(id, tenantId, companyId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Employee does not belong to specified company"));
	}

	private TenantWorkTimeEntity requireWorkTime(UUID tenantId, UUID id) {
		return workTimeRepository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work time not found"));
	}

	private String requireText(String value, String field, int maxLen) {
		if (value == null || value.isBlank()) {
			throw badRequest(field + " is required");
		}
		String v = value.trim();
		if (v.length() > maxLen) {
			throw badRequest(field + " exceeds max length " + maxLen);
		}
		return v;
	}

	private String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String normalizeIso2(String value, String field) {
		if (value == null || value.isBlank()) {
			throw badRequest(field + " is required");
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		if (!normalized.matches("^[A-Z]{2}$")) {
			throw badRequest(field + " must be ISO-2");
		}
		return normalized;
	}

	private String normalizeIso3(String value, String field) {
		if (value == null || value.isBlank()) {
			throw badRequest(field + " is required");
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		if (!normalized.matches("^[A-Z]{3}$")) {
			throw badRequest(field + " must be ISO-3");
		}
		return normalized;
	}

	private String normalizePayrollFrequency(String value) {
		String normalized = requireText(value, "payrollFrequency", 20).toUpperCase(Locale.ROOT);
		if (!ALLOWED_PAYROLL_FREQUENCIES.contains(normalized)) {
			throw badRequest("Unsupported payrollFrequency");
		}
		return normalized;
	}

	private String normalizeSalaryType(String value) {
		String normalized = requireText(value, "salaryType", 20).toUpperCase(Locale.ROOT);
		if (!ALLOWED_SALARY_TYPES.contains(normalized)) {
			throw badRequest("salaryType must be HOURLY or MONTHLY");
		}
		return normalized;
	}

	private String normalizeEmployeeStatus(String value) {
		String normalized = requireText(value, "status", 30).toUpperCase(Locale.ROOT);
		if (!ALLOWED_EMPLOYEE_STATUS.contains(normalized)) {
			throw badRequest("Unsupported employee status");
		}
		return normalized;
	}

	private String normalizeEmail(String value) {
		String email = trimToNull(value);
		if (email == null) {
			return null;
		}
		if (!email.contains("@") || email.length() > 190) {
			throw badRequest("email is invalid");
		}
		return email.toLowerCase(Locale.ROOT);
	}

	private BigDecimal requirePositive(BigDecimal value, String message) {
		if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
			throw badRequest(message);
		}
		return value;
	}

	private boolean requireActive(TenantActivePatchRequest request) {
		if (request == null || request.active() == null) {
			throw badRequest("active is required");
		}
		return request.active().booleanValue();
	}

	private UUID requiredUuid(UUID id, String message) {
		if (id == null) {
			throw badRequest(message);
		}
		return id;
	}

	private ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	private ResponseStatusException conflict(String message) {
		return new ResponseStatusException(HttpStatus.CONFLICT, message);
	}

	private TenantCompanyItemDto toCompanyDto(TenantCompanyEntity e) {
		return new TenantCompanyItemDto(e.getId(), e.getName(), e.getLegalName(), e.getRegistrationNumber(), e.getTaxId(),
				e.getPayrollCountry(), e.getCurrency(), e.getPayrollFrequency(), e.getTimezone(), e.getDateFormat(),
				e.getContactEmail(), e.getContactPhone(), e.getAddressLine1(), e.getAddressLine2(), e.getCity(),
				e.getStateRegion(), e.getPostalCode(), e.getCountry(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
	}

	private TenantDepartmentItemDto toDepartmentDto(TenantDepartmentEntity e) {
		return new TenantDepartmentItemDto(e.getId(), e.getCompanyId(), e.getName(), e.getCode(), e.getDescription(),
				e.getParentDepartmentId(), e.getManagerEmployeeId(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
	}

	private TenantJobItemDto toJobDto(TenantJobEntity e) {
		return new TenantJobItemDto(e.getId(), e.getCompanyId(), e.getDepartmentId(), e.getTitle(), e.getCode(),
				e.getDescription(), e.getSalaryType(), e.getDefaultSalary(), e.getDefaultHourlyRate(),
				e.getStandardHoursPerWeek(), e.getJobLevel(), e.getJobCategory(), e.isActive(), e.getCreatedAt(),
				e.getUpdatedAt());
	}

	private TenantEmployeeGroupItemDto toEmployeeGroupDto(TenantEmployeeGroupEntity e) {
		return new TenantEmployeeGroupItemDto(e.getId(), e.getCompanyId(), e.getName(), e.getCode(), e.getDescription(),
				e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
	}

	private TenantWorkTimeItemDto toWorkTimeDto(TenantWorkTimeEntity e) {
		return new TenantWorkTimeItemDto(e.getId(), e.getCompanyId(), e.getName(), e.getCode(), e.getHoursPerDay(),
				e.getWorkDaysPerWeek(), e.getDescription(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
	}

	private TenantEmployeeItemDto toEmployeeDto(TenantEmployeeEntity e) {
		return new TenantEmployeeItemDto(e.getId(), e.getCompanyId(), e.getDepartmentId(), e.getJobId(),
				e.getEmployeeGroupId(), e.getFirstName(), e.getLastName(), e.getDateOfBirth(), e.getHireDate(), e.getEmail(),
				e.getPhone(), e.getStatus(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
	}
}
