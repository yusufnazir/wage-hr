package com.wagepayroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.PermissionService;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/privileges")
public class TenantPrivilegePoolController {

	private final PermissionService permissionService;

	public TenantPrivilegePoolController(PermissionService permissionService) {
		this.permissionService = permissionService;
	}

	@GetMapping("/pool")
	@RequiresPrivilege("TENANT_SETTINGS_EDIT")
	public ApiResponse<Map<String, List<String>>> pool(HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		List<String> codes = permissionService.tenantPoolPrivilegeCodes(tenantId);
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("privileges", codes), rid);
	}
}
