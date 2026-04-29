package com.wagepayroll.api;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.TenantRoleCreateRequest;
import com.wagepayroll.api.dto.TenantRoleDetailResponseDto;
import com.wagepayroll.api.dto.TenantRoleDto;
import com.wagepayroll.api.dto.TenantRolePatchRequest;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;
import com.wagepayroll.tenant.TenantRoleAdminService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/roles")
@Validated
public class TenantRolesController {

	private final TenantRoleAdminService tenantRoleAdminService;

	public TenantRolesController(TenantRoleAdminService tenantRoleAdminService) {
		this.tenantRoleAdminService = tenantRoleAdminService;
	}

	@GetMapping
	@RequiresPrivilege("ROLE_VIEW")
	public ApiResponse<Map<String, Object>> list(@RequestParam(name = "q", required = false) String q,
			@RequestParam(name = "sort", defaultValue = "NAME_ASC") String sort, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Map<String, Object> data = tenantRoleAdminService.list(tenantId, q, sort);
		return ApiResponse.of(data, RequestIdFilter.currentRequestId(request));
	}

	@PostMapping
	@RequiresPrivilege("ROLE_EDIT")
	public ResponseEntity<ApiResponse<Map<String, TenantRoleDto>>> create(@RequestBody TenantRoleCreateRequest body,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		TenantRoleDto role = tenantRoleAdminService.create(tenantId, actor, body, request);
		String rid = RequestIdFilter.currentRequestId(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("role", role), rid));
	}

	@GetMapping("/{roleId}")
	@RequiresPrivilege("ROLE_VIEW")
	public ApiResponse<TenantRoleDetailResponseDto> getOne(@PathVariable("roleId") UUID roleId,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		TenantRoleDetailResponseDto data = tenantRoleAdminService.getOne(tenantId, roleId);
		return ApiResponse.of(data, RequestIdFilter.currentRequestId(request));
	}

	@PatchMapping("/{roleId}")
	@RequiresPrivilege("ROLE_EDIT")
	public ApiResponse<Map<String, TenantRoleDto>> patch(@PathVariable("roleId") UUID roleId,
			@RequestBody TenantRolePatchRequest body, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		TenantRoleDto role = tenantRoleAdminService.patch(tenantId, roleId, actor, body, request);
		return ApiResponse.of(Map.of("role", role), RequestIdFilter.currentRequestId(request));
	}
}

