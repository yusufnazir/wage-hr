package com.wagepayroll.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

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
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.org.TenantPayrollOrgService;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class TenantPayrollOrgController {

	private final TenantPayrollOrgService service;

	public TenantPayrollOrgController(TenantPayrollOrgService service) {
		this.service = service;
	}

	@GetMapping("/companies")
	@RequiresPrivilege("COMPANY_VIEW")
	public ResponseEntity<ApiResponse<Object>> listCompanies(@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "sort", defaultValue = "name,asc") String sort,
			@RequestParam(name = "active", required = false) Boolean active) {
		Page<TenantCompanyItemDto> result = service.listCompanies(TenantContext.requireTenantId(), page, size, sort, active);
		return ResponseEntity.ok(ApiResponse.of(pagePayload(result), "tenant.company.listed"));
	}

	@GetMapping("/companies/{id}")
	@RequiresPrivilege("COMPANY_VIEW")
	public ResponseEntity<ApiResponse<Object>> getCompany(@PathVariable("id") UUID id) {
		TenantCompanyItemDto item = service.getCompany(TenantContext.requireTenantId(), id);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.company.fetched"));
	}

	@PostMapping("/companies")
	@RequiresPrivilege("COMPANY_MANAGE")
	public ResponseEntity<ApiResponse<Object>> createCompany(@Valid @RequestBody TenantCompanyUpsertRequest request) {
		TenantCompanyItemDto item = service.createCompany(TenantContext.requireTenantId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("item", item), "tenant.company.created"));
	}

	@PutMapping("/companies/{id}")
	@RequiresPrivilege("COMPANY_MANAGE")
	public ResponseEntity<ApiResponse<Object>> updateCompany(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantCompanyUpsertRequest request) {
		TenantCompanyItemDto item = service.updateCompany(TenantContext.requireTenantId(), id, request);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.company.updated"));
	}

	@PatchMapping("/companies/{id}/active")
	@RequiresPrivilege("COMPANY_MANAGE")
	public ResponseEntity<ApiResponse<Object>> patchCompanyActive(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantActivePatchRequest request) {
		TenantCompanyItemDto item = service.patchCompanyActive(TenantContext.requireTenantId(), id, request);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.company.active.updated"));
	}

	@PostMapping(value = "/companies/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@RequiresPrivilege("COMPANY_MANAGE")
	public ResponseEntity<ApiResponse<Object>> uploadCompanyLogo(@PathVariable("id") UUID id,
			@RequestParam("file") MultipartFile file) {
		try {
			TenantCompanyItemDto item = service.uploadCompanyLogo(TenantContext.requireTenantId(), id,
					file.getInputStream(), file.getSize(), file.getContentType());
			return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.company.logo.uploaded"));
		}
		catch (java.io.IOException e) {
			throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
					"LOGO_READ_FAILED");
		}
	}

	@DeleteMapping("/companies/{id}/logo")
	@RequiresPrivilege("COMPANY_MANAGE")
	public ResponseEntity<ApiResponse<Object>> removeCompanyLogo(@PathVariable("id") UUID id) {
		TenantCompanyItemDto item = service.removeCompanyLogo(TenantContext.requireTenantId(), id);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.company.logo.removed"));
	}

	@GetMapping("/departments")
	@RequiresPrivilege("DEPARTMENT_VIEW")
	public ResponseEntity<ApiResponse<Object>> listDepartments(@RequestParam(name = "companyId", required = false) UUID companyId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "sort", defaultValue = "name,asc") String sort,
			@RequestParam(name = "active", required = false) Boolean active) {
		Page<TenantDepartmentItemDto> result = service.listDepartments(TenantContext.requireTenantId(), companyId, page, size,
				sort, active);
		return ResponseEntity.ok(ApiResponse.of(pagePayload(result), "tenant.department.listed"));
	}

	@GetMapping("/departments/{id}")
	@RequiresPrivilege("DEPARTMENT_VIEW")
	public ResponseEntity<ApiResponse<Object>> getDepartment(@PathVariable("id") UUID id) {
		TenantDepartmentItemDto item = service.getDepartment(TenantContext.requireTenantId(), id);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.department.fetched"));
	}

	@PostMapping("/departments")
	@RequiresPrivilege("DEPARTMENT_MANAGE")
	public ResponseEntity<ApiResponse<Object>> createDepartment(@Valid @RequestBody TenantDepartmentUpsertRequest request) {
		TenantDepartmentItemDto item = service.createDepartment(TenantContext.requireTenantId(), request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(Map.of("item", item), "tenant.department.created"));
	}

	@PutMapping("/departments/{id}")
	@RequiresPrivilege("DEPARTMENT_MANAGE")
	public ResponseEntity<ApiResponse<Object>> updateDepartment(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantDepartmentUpsertRequest request) {
		TenantDepartmentItemDto item = service.updateDepartment(TenantContext.requireTenantId(), id, request);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.department.updated"));
	}

	@PatchMapping("/departments/{id}/active")
	@RequiresPrivilege("DEPARTMENT_MANAGE")
	public ResponseEntity<ApiResponse<Object>> patchDepartmentActive(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantActivePatchRequest request) {
		TenantDepartmentItemDto item = service.patchDepartmentActive(TenantContext.requireTenantId(), id, request);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.department.active.updated"));
	}

	@GetMapping("/jobs")
	@RequiresPrivilege("JOB_VIEW")
	public ResponseEntity<ApiResponse<Object>> listJobs(@RequestParam(name = "companyId", required = false) UUID companyId,
			@RequestParam(name = "departmentId", required = false) UUID departmentId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "sort", defaultValue = "title,asc") String sort,
			@RequestParam(name = "active", required = false) Boolean active) {
		Page<TenantJobItemDto> result = service.listJobs(TenantContext.requireTenantId(), companyId, departmentId, page, size,
				sort, active);
		return ResponseEntity.ok(ApiResponse.of(pagePayload(result), "tenant.job.listed"));
	}

	@GetMapping("/jobs/{id}")
	@RequiresPrivilege("JOB_VIEW")
	public ResponseEntity<ApiResponse<Object>> getJob(@PathVariable("id") UUID id) {
		TenantJobItemDto item = service.getJob(TenantContext.requireTenantId(), id);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.job.fetched"));
	}

	@PostMapping("/jobs")
	@RequiresPrivilege("JOB_MANAGE")
	public ResponseEntity<ApiResponse<Object>> createJob(@Valid @RequestBody TenantJobUpsertRequest request) {
		TenantJobItemDto item = service.createJob(TenantContext.requireTenantId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("item", item), "tenant.job.created"));
	}

	@PutMapping("/jobs/{id}")
	@RequiresPrivilege("JOB_MANAGE")
	public ResponseEntity<ApiResponse<Object>> updateJob(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantJobUpsertRequest request) {
		TenantJobItemDto item = service.updateJob(TenantContext.requireTenantId(), id, request);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.job.updated"));
	}

	@PatchMapping("/jobs/{id}/active")
	@RequiresPrivilege("JOB_MANAGE")
	public ResponseEntity<ApiResponse<Object>> patchJobActive(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantActivePatchRequest request) {
		TenantJobItemDto item = service.patchJobActive(TenantContext.requireTenantId(), id, request);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.job.active.updated"));
	}

	@GetMapping("/employee-groups")
	@RequiresPrivilege("EMPLOYEE_GROUP_VIEW")
	public ResponseEntity<ApiResponse<Object>> listEmployeeGroups(@RequestParam(name = "companyId", required = false) UUID companyId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "sort", defaultValue = "name,asc") String sort,
			@RequestParam(name = "active", required = false) Boolean active) {
		Page<TenantEmployeeGroupItemDto> result = service.listEmployeeGroups(TenantContext.requireTenantId(), companyId,
				page, size, sort, active);
		return ResponseEntity.ok(ApiResponse.of(pagePayload(result), "tenant.employee_group.listed"));
	}

	@GetMapping("/employee-groups/{id}")
	@RequiresPrivilege("EMPLOYEE_GROUP_VIEW")
	public ResponseEntity<ApiResponse<Object>> getEmployeeGroup(@PathVariable("id") UUID id) {
		TenantEmployeeGroupItemDto item = service.getEmployeeGroup(TenantContext.requireTenantId(), id);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.employee_group.fetched"));
	}

	@PostMapping("/employee-groups")
	@RequiresPrivilege("EMPLOYEE_GROUP_MANAGE")
	public ResponseEntity<ApiResponse<Object>> createEmployeeGroup(
			@Valid @RequestBody TenantEmployeeGroupUpsertRequest request) {
		TenantEmployeeGroupItemDto item = service.createEmployeeGroup(TenantContext.requireTenantId(), request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(Map.of("item", item), "tenant.employee_group.created"));
	}

	@PutMapping("/employee-groups/{id}")
	@RequiresPrivilege("EMPLOYEE_GROUP_MANAGE")
	public ResponseEntity<ApiResponse<Object>> updateEmployeeGroup(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantEmployeeGroupUpsertRequest request) {
		TenantEmployeeGroupItemDto item = service.updateEmployeeGroup(TenantContext.requireTenantId(), id, request);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.employee_group.updated"));
	}

	@PatchMapping("/employee-groups/{id}/active")
	@RequiresPrivilege("EMPLOYEE_GROUP_MANAGE")
	public ResponseEntity<ApiResponse<Object>> patchEmployeeGroupActive(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantActivePatchRequest request) {
		TenantEmployeeGroupItemDto item = service.patchEmployeeGroupActive(TenantContext.requireTenantId(), id, request);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.employee_group.active.updated"));
	}

	@GetMapping("/employees")
	@RequiresPrivilege("EMPLOYEE_VIEW")
	public ResponseEntity<ApiResponse<Object>> listEmployees(@RequestParam(name = "companyId") UUID companyId,
			@RequestParam(name = "departmentId", required = false) UUID departmentId,
			@RequestParam(name = "jobId", required = false) UUID jobId,
			@RequestParam(name = "employeeGroupId", required = false) UUID employeeGroupId,
			@RequestParam(name = "status", required = false) String status,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "sort", defaultValue = "lastName,asc") String sort,
			@RequestParam(name = "active", required = false) Boolean active) {
		Page<TenantEmployeeItemDto> result = service.listEmployees(TenantContext.requireTenantId(), companyId, departmentId,
				jobId, employeeGroupId, status, page, size, sort, active);
		return ResponseEntity.ok(ApiResponse.of(pagePayload(result), "tenant.employee.listed"));
	}

	@GetMapping("/employees/{id}")
	@RequiresPrivilege("EMPLOYEE_VIEW")
	public ResponseEntity<ApiResponse<Object>> getEmployee(@PathVariable("id") UUID id) {
		TenantEmployeeItemDto item = service.getEmployee(TenantContext.requireTenantId(), id);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.employee.fetched"));
	}

	@PostMapping("/employees")
	@RequiresPrivilege("EMPLOYEE_MANAGE")
	public ResponseEntity<ApiResponse<Object>> createEmployee(@Valid @RequestBody TenantEmployeeUpsertRequest request) {
		TenantEmployeeItemDto item = service.createEmployee(TenantContext.requireTenantId(), request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(Map.of("item", item), "tenant.employee.created"));
	}

	@PutMapping("/employees/{id}")
	@RequiresPrivilege("EMPLOYEE_MANAGE")
	public ResponseEntity<ApiResponse<Object>> updateEmployee(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantEmployeeUpsertRequest request) {
		TenantEmployeeItemDto item = service.updateEmployee(TenantContext.requireTenantId(), id, request);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.employee.updated"));
	}

	@PatchMapping("/employees/{id}/status")
	@RequiresPrivilege("EMPLOYEE_MANAGE")
	public ResponseEntity<ApiResponse<Object>> patchEmployeeStatus(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantEmployeeStatusPatchRequest request) {
		TenantEmployeeItemDto item = service.patchEmployeeStatus(TenantContext.requireTenantId(), id, request);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.employee.status.updated"));
	}

	@PatchMapping("/employees/{id}/active")
	@RequiresPrivilege("EMPLOYEE_MANAGE")
	public ResponseEntity<ApiResponse<Object>> patchEmployeeActive(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantActivePatchRequest request) {
		TenantEmployeeItemDto item = service.patchEmployeeActive(TenantContext.requireTenantId(), id, request);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.employee.active.updated"));
	}

	@GetMapping("/work-times")
	@RequiresPrivilege("WORK_TIME_VIEW")
	public ResponseEntity<ApiResponse<Object>> listWorkTimes(@RequestParam(name = "companyId", required = false) UUID companyId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "sort", defaultValue = "name,asc") String sort,
			@RequestParam(name = "active", required = false) Boolean active) {
		Page<TenantWorkTimeItemDto> result = service.listWorkTimes(TenantContext.requireTenantId(), companyId, page, size,
				sort, active);
		return ResponseEntity.ok(ApiResponse.of(pagePayload(result), "tenant.work_time.listed"));
	}

	@GetMapping("/work-times/{id}")
	@RequiresPrivilege("WORK_TIME_VIEW")
	public ResponseEntity<ApiResponse<Object>> getWorkTime(@PathVariable("id") UUID id) {
		TenantWorkTimeItemDto item = service.getWorkTime(TenantContext.requireTenantId(), id);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.work_time.fetched"));
	}

	@PostMapping("/work-times")
	@RequiresPrivilege("WORK_TIME_MANAGE")
	public ResponseEntity<ApiResponse<Object>> createWorkTime(@Valid @RequestBody TenantWorkTimeUpsertRequest request) {
		TenantWorkTimeItemDto item = service.createWorkTime(TenantContext.requireTenantId(), request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(Map.of("item", item), "tenant.work_time.created"));
	}

	@PutMapping("/work-times/{id}")
	@RequiresPrivilege("WORK_TIME_MANAGE")
	public ResponseEntity<ApiResponse<Object>> updateWorkTime(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantWorkTimeUpsertRequest request) {
		TenantWorkTimeItemDto item = service.updateWorkTime(TenantContext.requireTenantId(), id, request);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.work_time.updated"));
	}

	@PatchMapping("/work-times/{id}/active")
	@RequiresPrivilege("WORK_TIME_MANAGE")
	public ResponseEntity<ApiResponse<Object>> patchWorkTimeActive(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantActivePatchRequest request) {
		TenantWorkTimeItemDto item = service.patchWorkTimeActive(TenantContext.requireTenantId(), id, request);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.work_time.active.updated"));
	}

	private Map<String, Object> pagePayload(Page<?> pageResult) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("data", pageResult.getContent());
		payload.put("page", Map.of("number", pageResult.getNumber(), "size", pageResult.getSize(), "totalElements",
				pageResult.getTotalElements(), "totalPages", pageResult.getTotalPages()));
		return payload;
	}
}
