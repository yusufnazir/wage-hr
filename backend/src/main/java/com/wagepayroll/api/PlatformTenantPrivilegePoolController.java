package com.wagepayroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.TenantPrivilegePoolReplaceRequest;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.PlatformOperatorService;
import com.wagepayroll.settings.PlatformTenantPrivilegePoolService;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/tenants")
public class PlatformTenantPrivilegePoolController {

	private final PlatformOperatorService platformOperatorService;
	private final PlatformTenantPrivilegePoolService platformTenantPrivilegePoolService;
	private final AuditService auditService;

	public PlatformTenantPrivilegePoolController(PlatformOperatorService platformOperatorService,
			PlatformTenantPrivilegePoolService platformTenantPrivilegePoolService, AuditService auditService) {
		this.platformOperatorService = platformOperatorService;
		this.platformTenantPrivilegePoolService = platformTenantPrivilegePoolService;
		this.auditService = auditService;
	}

	@PutMapping("/{tenantId}/privilege-pool")
	public ApiResponse<Map<String, List<String>>> replacePool(@PathVariable("tenantId") UUID tenantId,
			@RequestBody TenantPrivilegePoolReplaceRequest body, HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(actor);
		List<String> applied = platformTenantPrivilegePoolService.replacePool(tenantId, body.codes());
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(tenantId, actor, AuditActionCodes.TENANT_PRIVILEGE_POOL_REPLACED, AuditResourceTypes.TENANT,
				tenantId.toString(), rid, Map.of("privileges", applied));
		return ApiResponse.of(Map.of("privileges", applied), rid);
	}
}
