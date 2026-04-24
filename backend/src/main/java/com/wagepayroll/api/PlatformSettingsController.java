package com.wagepayroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.PrivilegeCatalogEntryDto;
import com.wagepayroll.api.dto.SettingEntryDto;
import com.wagepayroll.api.dto.SettingsPatchRequest;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.domain.privilege.PrivilegeEntity;
import com.wagepayroll.domain.privilege.PrivilegeRepository;
import com.wagepayroll.security.DefinedPrivilege;
import com.wagepayroll.security.PlatformOperatorService;
import com.wagepayroll.settings.PlatformSettingsService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformSettingsController {

	private final PlatformOperatorService platformOperatorService;
	private final PlatformSettingsService platformSettingsService;
	private final AuditService auditService;
	private final PrivilegeRepository privilegeRepository;

	public PlatformSettingsController(PlatformOperatorService platformOperatorService,
			PlatformSettingsService platformSettingsService, AuditService auditService,
			PrivilegeRepository privilegeRepository) {
		this.platformOperatorService = platformOperatorService;
		this.platformSettingsService = platformSettingsService;
		this.auditService = auditService;
		this.privilegeRepository = privilegeRepository;
	}

	@GetMapping("/privileges/catalog")
	public ApiResponse<Map<String, List<PrivilegeCatalogEntryDto>>> privilegeCatalog(HttpServletRequest request) {
		requirePlatformSuperadminUser();
		List<PrivilegeCatalogEntryDto> entries = privilegeRepository.findAllByOrderByCodeAsc().stream()
				.map(PlatformSettingsController::toPrivilegeCatalogEntry)
				.collect(Collectors.toList());
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("entries", entries), rid);
	}

	@GetMapping("/settings")
	public ApiResponse<Map<String, List<SettingEntryDto>>> getSettings(HttpServletRequest request) {
		requirePlatformSuperadminUser();
		List<SettingEntryDto> entries = platformSettingsService.list();
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("entries", entries), rid);
	}

	@PatchMapping("/settings")
	public ResponseEntity<Void> patchSettings(@RequestBody SettingsPatchRequest body, HttpServletRequest request) {
		requirePlatformSuperadminUser();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		platformSettingsService.patch(body);
		if (body != null && body.entries() != null && !body.entries().isEmpty()) {
			List<String> keys = body.entries().stream().map(SettingEntryDto::key).collect(Collectors.toList());
			auditService.append(null, actor, AuditActionCodes.PLATFORM_SETTINGS_PATCHED, AuditResourceTypes.PLATFORM_SETTING,
					null, RequestIdFilter.currentRequestId(request), Map.of("keys", keys));
		}
		return ResponseEntity.noContent().build();
	}

	private void requirePlatformSuperadminUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(userId);
	}

	private static PrivilegeCatalogEntryDto toPrivilegeCatalogEntry(PrivilegeEntity p) {
		return DefinedPrivilege.forCode(p.getCode())
				.map(d -> new PrivilegeCatalogEntryDto(p.getCode(), d.action(), d.resource(), p.getDescription()))
				.orElseGet(() -> new PrivilegeCatalogEntryDto(p.getCode(), null, null, p.getDescription()));
	}
}
