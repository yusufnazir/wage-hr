package com.wagepayroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.SettingEntryDto;
import com.wagepayroll.api.dto.SettingsPatchRequest;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.settings.TenantSettingsService;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant")
public class TenantSettingsController {

	private final TenantSettingsService tenantSettingsService;
	private final AuditService auditService;

	public TenantSettingsController(TenantSettingsService tenantSettingsService, AuditService auditService) {
		this.tenantSettingsService = tenantSettingsService;
		this.auditService = auditService;
	}

	@GetMapping("/settings")
	@RequiresPrivilege("TENANT_SETTINGS_EDIT")
	public ApiResponse<Map<String, List<SettingEntryDto>>> getSettings(HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		List<SettingEntryDto> entries = tenantSettingsService.list(tenantId);
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("entries", entries), rid);
	}

	@PatchMapping("/settings")
	@RequiresPrivilege("TENANT_SETTINGS_EDIT")
	public ResponseEntity<Void> patchSettings(@RequestBody SettingsPatchRequest body, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		tenantSettingsService.patch(tenantId, body);
		if (body != null && body.entries() != null && !body.entries().isEmpty()) {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			UUID actor = UUID.fromString(auth.getName());
			List<String> keys = body.entries().stream().map(SettingEntryDto::key).collect(Collectors.toList());
			auditService.append(tenantId, actor, AuditActionCodes.TENANT_SETTINGS_PATCHED, AuditResourceTypes.TENANT_SETTING,
					null, RequestIdFilter.currentRequestId(request), Map.of("keys", keys));
		}
		return ResponseEntity.noContent().build();
	}
}
