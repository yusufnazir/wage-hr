package com.wagepayroll.api;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.PlatformCountryDto;
import com.wagepayroll.api.dto.PlatformCountryUpsertRequest;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.country.PlatformCountryService;
import com.wagepayroll.security.PlatformOperatorService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/countries")
public class PlatformCountriesController {

	private final PlatformOperatorService platformOperatorService;
	private final PlatformCountryService platformCountryService;
	private final AuditService auditService;

	public PlatformCountriesController(PlatformOperatorService platformOperatorService,
			PlatformCountryService platformCountryService, AuditService auditService) {
		this.platformOperatorService = platformOperatorService;
		this.platformCountryService = platformCountryService;
		this.auditService = auditService;
	}

	@GetMapping
	public ApiResponse<Map<String, Object>> list(
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "50") int size,
			@RequestParam(name = "search", required = false) String search,
			@RequestParam(name = "active", required = false) Boolean active,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(platformCountryService.listPlatform(page, size, search, active, locale), rid);
	}

	@GetMapping("/{id}")
	public ApiResponse<Map<String, PlatformCountryDto>> get(
			@PathVariable("id") UUID id,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		PlatformCountryDto dto = platformCountryService.get(id, locale);
		return ApiResponse.of(Map.of("item", dto), RequestIdFilter.currentRequestId(request));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<Map<String, PlatformCountryDto>>> create(
			@RequestBody PlatformCountryUpsertRequest body,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformCountryDto row = platformCountryService.create(body, locale);
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(null, actor, AuditActionCodes.PLATFORM_COUNTRY_CREATED, AuditResourceTypes.PLATFORM_COUNTRY,
				row.id().toString(), rid, Map.of("isoAlpha2", row.isoAlpha2()));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("item", row), rid));
	}

	@PutMapping("/{id}")
	public ApiResponse<Map<String, PlatformCountryDto>> update(
			@PathVariable("id") UUID id,
			@RequestBody PlatformCountryUpsertRequest body,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformCountryDto row = platformCountryService.update(id, body, locale);
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(null, actor, AuditActionCodes.PLATFORM_COUNTRY_UPDATED, AuditResourceTypes.PLATFORM_COUNTRY,
				row.id().toString(), rid, Map.of("isoAlpha2", row.isoAlpha2()));
		return ApiResponse.of(Map.of("item", row), rid);
	}

	@PatchMapping("/{id}/activate")
	public ApiResponse<Map<String, PlatformCountryDto>> activate(
			@PathVariable("id") UUID id,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformCountryDto row = platformCountryService.activate(id, locale);
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(null, actor, AuditActionCodes.PLATFORM_COUNTRY_ACTIVATED, AuditResourceTypes.PLATFORM_COUNTRY,
				row.id().toString(), rid, Map.of("isoAlpha2", row.isoAlpha2()));
		return ApiResponse.of(Map.of("item", row), rid);
	}

	@PatchMapping("/{id}/deactivate")
	public ApiResponse<Map<String, PlatformCountryDto>> deactivate(
			@PathVariable("id") UUID id,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformCountryDto row = platformCountryService.deactivate(id, locale);
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(null, actor, AuditActionCodes.PLATFORM_COUNTRY_DEACTIVATED,
				AuditResourceTypes.PLATFORM_COUNTRY, row.id().toString(), rid, Map.of("isoAlpha2", row.isoAlpha2()));
		return ApiResponse.of(Map.of("item", row), rid);
	}

	private UUID requirePlatformSuperadmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(userId);
		return userId;
	}
}
