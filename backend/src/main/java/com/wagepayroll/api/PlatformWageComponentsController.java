package com.wagepayroll.api;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.PlatformOperatorService;
import com.wagepayroll.wagecomponent.PlatformWageComponentCatalogService;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/wage-components")
public class PlatformWageComponentsController {

	private final PlatformOperatorService platformOperatorService;
	private final PlatformWageComponentCatalogService catalogService;

	public PlatformWageComponentsController(PlatformOperatorService platformOperatorService,
			PlatformWageComponentCatalogService catalogService) {
		this.platformOperatorService = platformOperatorService;
		this.catalogService = catalogService;
	}

	@GetMapping
	public ApiResponse<Map<String, Object>> list(
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "50") int size,
			@RequestParam(name = "country") String country,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		return ApiResponse.of(catalogService.list(page, size, country), RequestIdFilter.currentRequestId(request));
	}

	private void requirePlatformSuperadmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(userId);
	}
}
