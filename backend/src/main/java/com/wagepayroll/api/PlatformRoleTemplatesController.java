package com.wagepayroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.PlatformRoleTemplateDto;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.PlatformOperatorService;
import com.wagepayroll.tenant.PlatformRoleTemplateService;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/role-templates")
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
}

