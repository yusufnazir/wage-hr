package com.wagepayroll.api;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.PlatformBankTemplateCreateRequest;
import com.wagepayroll.api.dto.PlatformBankTemplatePutRequest;
import com.wagepayroll.api.dto.PlatformBankTemplateRowDto;
import com.wagepayroll.banktemplate.PlatformBankTemplateService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
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
@RequestMapping("/api/v1/platform/bank-templates")
public class PlatformBankTemplatesController {

	private final PlatformOperatorService platformOperatorService;
	private final PlatformBankTemplateService platformBankTemplateService;

	public PlatformBankTemplatesController(PlatformOperatorService platformOperatorService,
			PlatformBankTemplateService platformBankTemplateService) {
		this.platformOperatorService = platformOperatorService;
		this.platformBankTemplateService = platformBankTemplateService;
	}

	@GetMapping
	public ApiResponse<Map<String, Object>> list(
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "country", required = false) String country,
			@RequestParam(name = "active", required = false) Boolean active,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(platformBankTemplateService.list(page, size, country, active), rid);
	}

	@GetMapping("/{id}")
	public ApiResponse<Map<String, PlatformBankTemplateRowDto>> get(@PathVariable("id") UUID id,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		PlatformBankTemplateRowDto row = platformBankTemplateService.get(id);
		return ApiResponse.of(Map.of("template", row), RequestIdFilter.currentRequestId(request));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<Map<String, PlatformBankTemplateRowDto>>> create(
			@RequestBody PlatformBankTemplateCreateRequest body, HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformBankTemplateRowDto row = platformBankTemplateService.create(body, actor,
				RequestIdFilter.currentRequestId(request));
		String rid = RequestIdFilter.currentRequestId(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("template", row), rid));
	}

	@PutMapping("/{id}")
	public ApiResponse<Map<String, PlatformBankTemplateRowDto>> put(@PathVariable("id") UUID id,
			@RequestBody PlatformBankTemplatePutRequest body, HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformBankTemplateRowDto row = platformBankTemplateService.update(id, body, actor,
				RequestIdFilter.currentRequestId(request));
		return ApiResponse.of(Map.of("template", row), RequestIdFilter.currentRequestId(request));
	}

	@PatchMapping("/{id}/activate")
	public ApiResponse<Map<String, PlatformBankTemplateRowDto>> activate(@PathVariable("id") UUID id,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformBankTemplateRowDto row = platformBankTemplateService.activate(id, actor,
				RequestIdFilter.currentRequestId(request));
		return ApiResponse.of(Map.of("template", row), RequestIdFilter.currentRequestId(request));
	}

	@PatchMapping("/{id}/deactivate")
	public ApiResponse<Map<String, PlatformBankTemplateRowDto>> deactivate(@PathVariable("id") UUID id,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformBankTemplateRowDto row = platformBankTemplateService.deactivate(id, actor,
				RequestIdFilter.currentRequestId(request));
		return ApiResponse.of(Map.of("template", row), RequestIdFilter.currentRequestId(request));
	}

	private UUID requirePlatformSuperadmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(userId);
		return userId;
	}
}
