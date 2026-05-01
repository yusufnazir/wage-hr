package com.wagepayroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.PlatformRoleTemplateDto;
import com.wagepayroll.api.dto.PlatformRoleTemplateCreateRequest;
import com.wagepayroll.api.dto.PlatformRoleTemplatePatchRequest;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.PlatformOperatorService;
import com.wagepayroll.tenant.PlatformRoleTemplateService;

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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/role-templates")
@Validated
public class PlatformRoleTemplatesController {

	private final PlatformOperatorService platformOperatorService;
	private final PlatformRoleTemplateService platformRoleTemplateService;

	public PlatformRoleTemplatesController(PlatformOperatorService platformOperatorService,
			PlatformRoleTemplateService platformRoleTemplateService) {
		this.platformOperatorService = platformOperatorService;
		this.platformRoleTemplateService = platformRoleTemplateService;
	}

	@GetMapping
	public ApiResponse<Map<String, List<PlatformRoleTemplateDto>>> list(HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(actor);
		List<PlatformRoleTemplateDto> items = platformRoleTemplateService.list();
		return ApiResponse.of(Map.of("items", items), RequestIdFilter.currentRequestId(request));
	}

	@GetMapping("/{templateId}")
	public ApiResponse<Map<String, PlatformRoleTemplateDto>> getOne(@PathVariable("templateId") UUID templateId,
			HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(actor);
		PlatformRoleTemplateDto item = platformRoleTemplateService.getOne(templateId);
		return ApiResponse.of(Map.of("item", item), RequestIdFilter.currentRequestId(request));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<Map<String, PlatformRoleTemplateDto>>> create(@RequestBody PlatformRoleTemplateCreateRequest body,
			HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(actor);
		PlatformRoleTemplateDto item = platformRoleTemplateService.create(body);
		String rid = RequestIdFilter.currentRequestId(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("item", item), rid));
	}

	@PatchMapping("/{templateId}")
	public ApiResponse<Map<String, PlatformRoleTemplateDto>> patch(@PathVariable("templateId") UUID templateId,
			@RequestBody PlatformRoleTemplatePatchRequest body, HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(actor);
		PlatformRoleTemplateDto item = platformRoleTemplateService.patch(templateId, body);
		return ApiResponse.of(Map.of("item", item), RequestIdFilter.currentRequestId(request));
	}
}

