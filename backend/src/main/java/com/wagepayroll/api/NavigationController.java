package com.wagepayroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.NavigationItemDto;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.settings.NavigationMenuService;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/me")
public class NavigationController {

	private final NavigationMenuService navigationMenuService;

	public NavigationController(NavigationMenuService navigationMenuService) {
		this.navigationMenuService = navigationMenuService;
	}

	@GetMapping("/navigation")
	public ApiResponse<Map<String, List<NavigationItemDto>>> navigation(HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		UUID tenantId = TenantContext.current()
				.flatMap(c -> java.util.Optional.ofNullable(c.tenantId()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "TENANT_CONTEXT_REQUIRED"));
		List<NavigationItemDto> tree = navigationMenuService.effectiveMenu(userId, tenantId);
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("items", tree), rid);
	}
}
