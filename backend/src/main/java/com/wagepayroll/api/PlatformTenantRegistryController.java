package com.wagepayroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.PlatformTenantCreateRequest;
import com.wagepayroll.api.dto.PlatformTenantPatchRequest;
import com.wagepayroll.api.dto.PlatformTenantRowDto;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.PlatformOperatorService;
import com.wagepayroll.tenant.PlatformTenantRegistryService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/tenants")
public class PlatformTenantRegistryController {

	private final PlatformOperatorService platformOperatorService;
	private final PlatformTenantRegistryService platformTenantRegistryService;
	private final AuditService auditService;

	public PlatformTenantRegistryController(PlatformOperatorService platformOperatorService,
			PlatformTenantRegistryService platformTenantRegistryService, AuditService auditService) {
		this.platformOperatorService = platformOperatorService;
		this.platformTenantRegistryService = platformTenantRegistryService;
		this.auditService = auditService;
	}

	@GetMapping
	public ApiResponse<Map<String, Object>> list(@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size, HttpServletRequest request) {
		requirePlatformSuperadminUser();
		Map<String, Object> data = platformTenantRegistryService.list(page, size);
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(data, rid);
	}

	@PostMapping
	public ResponseEntity<ApiResponse<Map<String, PlatformTenantRowDto>>> create(
			@RequestBody PlatformTenantCreateRequest body, HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(actor);
		PlatformTenantRowDto row = platformTenantRegistryService.create(body);
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(row.id(), actor, AuditActionCodes.PLATFORM_TENANT_CREATED, AuditResourceTypes.TENANT,
				row.id().toString(), rid, Map.of("handle", row.handle()));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("tenant", row), rid));
	}

	@GetMapping("/{tenantId}")
	public ApiResponse<Map<String, PlatformTenantRowDto>> getOne(@PathVariable("tenantId") UUID tenantId,
			HttpServletRequest request) {
		requirePlatformSuperadminUser();
		PlatformTenantRowDto row = platformTenantRegistryService.get(tenantId);
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("tenant", row), rid);
	}

	@PatchMapping("/{tenantId}")
	public ApiResponse<Map<String, PlatformTenantRowDto>> patch(@PathVariable("tenantId") UUID tenantId,
			@RequestBody PlatformTenantPatchRequest body, HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(actor);
		PlatformTenantRowDto row = platformTenantRegistryService.patch(tenantId, body);
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(tenantId, actor, AuditActionCodes.PLATFORM_TENANT_UPDATED, AuditResourceTypes.TENANT,
				tenantId.toString(), rid, Map.of("fields", List.of("name")));
		return ApiResponse.of(Map.of("tenant", row), rid);
	}

	private void requirePlatformSuperadminUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(userId);
	}
}
