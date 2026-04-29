package com.wagepayroll.api;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.wagepayroll.api.dto.TenantRoleOptionDto;
import com.wagepayroll.api.dto.TenantUserDetailDto;
import com.wagepayroll.api.dto.TenantUserPatchRequest;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;
import com.wagepayroll.tenant.TenantUserAdminService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/users")
@Validated
public class TenantUsersController {

	private final TenantUserAdminService tenantUserAdminService;

	public TenantUsersController(TenantUserAdminService tenantUserAdminService) {
		this.tenantUserAdminService = tenantUserAdminService;
	}

	@GetMapping
	@RequiresPrivilege("USER_VIEW")
	public ApiResponse<Map<String, Object>> list(@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(20) int size,
			@RequestParam(name = "sort", defaultValue = "EMAIL_ASC") String sort,
			@RequestParam(name = "email", required = false) String email,
			@RequestParam(name = "status", required = false) String status,
			@RequestParam(name = "role", required = false) String role, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Map<String, Object> data = tenantUserAdminService.list(tenantId, page, size, sort, email, status, role);
		return ApiResponse.of(data, RequestIdFilter.currentRequestId(request));
	}

	@GetMapping("/role-options")
	@RequiresPrivilege("USER_VIEW")
	public ApiResponse<Map<String, List<TenantRoleOptionDto>>> roleOptions(HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		List<TenantRoleOptionDto> roles = tenantUserAdminService.listTenantRoleOptions(tenantId);
		return ApiResponse.of(Map.of("roles", roles), RequestIdFilter.currentRequestId(request));
	}

	@GetMapping("/{userId}")
	public ApiResponse<Map<String, TenantUserDetailDto>> getOne(@PathVariable("userId") UUID userId,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		tenantUserAdminService.assertCanViewUser(actor, tenantId, userId);
		TenantUserDetailDto dto = tenantUserAdminService.getDetail(tenantId, userId, actor);
		return ApiResponse.of(Map.of("user", dto), RequestIdFilter.currentRequestId(request));
	}

	@PatchMapping("/{userId}")
	@RequiresPrivilege("USER_EDIT")
	public ResponseEntity<Void> patch(@PathVariable("userId") UUID userId, @RequestBody TenantUserPatchRequest body,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		tenantUserAdminService.patch(tenantId, userId, actor, body, request);
		return ResponseEntity.noContent().build();
	}
}
