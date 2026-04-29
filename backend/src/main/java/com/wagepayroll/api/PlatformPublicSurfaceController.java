package com.wagepayroll.api;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.PlatformPublicSurfaceDto;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.settings.PlatformBrandingService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated read of global branding for auth shells (see {@code docs/modules/platform-settings.md}).
 */
@RestController
@RequestMapping("/api/v1/platform")
public class PlatformPublicSurfaceController {

	private final PlatformBrandingService platformBrandingService;

	public PlatformPublicSurfaceController(PlatformBrandingService platformBrandingService) {
		this.platformBrandingService = platformBrandingService;
	}

	@GetMapping("/public-surface")
	public ApiResponse<PlatformPublicSurfaceDto> publicSurface(HttpServletRequest request) {
		String rid = RequestIdFilter.currentRequestId(request);
		PlatformPublicSurfaceDto data = new PlatformPublicSurfaceDto(platformBrandingService.applicationName(),
				platformBrandingService.publicBaseUrl(), platformBrandingService.dateFormatToken());
		return ApiResponse.of(data, rid);
	}
}
