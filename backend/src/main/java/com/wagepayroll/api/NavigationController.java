package com.wagepayroll.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.NavigationItemDto;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.domain.user.UserAccountRepository;
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

	private static final UUID NAV_PLATFORM_SETTINGS_ID = UUID.fromString("50000000-0000-0000-0000-000000000099");
	private static final UUID NAV_PLATFORM_TENANTS_ID = UUID.fromString("50000000-0000-0000-0000-000000000098");
	private static final UUID NAV_PLATFORM_ROLE_TEMPLATES_ID = UUID.fromString("50000000-0000-0000-0000-000000000097");

	private final NavigationMenuService navigationMenuService;
	private final UserAccountRepository userAccountRepository;

	public NavigationController(NavigationMenuService navigationMenuService, UserAccountRepository userAccountRepository) {
		this.navigationMenuService = navigationMenuService;
		this.userAccountRepository = userAccountRepository;
	}

	@GetMapping("/navigation")
	public ApiResponse<Map<String, List<NavigationItemDto>>> navigation(HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		Optional<UUID> tenantIdOpt = TenantContext.current().flatMap(c -> Optional.ofNullable(c.tenantId()));
		if (tenantIdOpt.isEmpty()) {
			if (userAccountRepository.findById(userId).map(u -> u.isPlatformSuperadmin()).orElse(false)) {
				List<NavigationItemDto> tree = new ArrayList<>();
				tree.add(new NavigationItemDto(NAV_PLATFORM_TENANTS_ID, "/app/platform-tenants", "nav.platform_tenants", 29,
						List.of()));
				tree.add(new NavigationItemDto(NAV_PLATFORM_ROLE_TEMPLATES_ID, "/app/platform-role-templates",
						"nav.platform_role_templates", 29, List.of()));
				tree.add(new NavigationItemDto(NAV_PLATFORM_SETTINGS_ID, "/app/platform-settings", "nav.platform_settings", 30,
						List.of()));
				tree.sort(Comparator.comparingInt(NavigationItemDto::sortOrder));
				String rid = RequestIdFilter.currentRequestId(request);
				return ApiResponse.of(Map.of("items", tree), rid);
			}
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TENANT_CONTEXT_REQUIRED");
		}
		UUID tenantId = tenantIdOpt.get();
		List<NavigationItemDto> tree = new ArrayList<>(navigationMenuService.effectiveMenu(userId, tenantId));
		if (userAccountRepository.findById(userId).map(u -> u.isPlatformSuperadmin()).orElse(false)) {
			tree.add(new NavigationItemDto(NAV_PLATFORM_TENANTS_ID, "/app/platform-tenants", "nav.platform_tenants", 29,
					List.of()));
			tree.add(new NavigationItemDto(NAV_PLATFORM_ROLE_TEMPLATES_ID, "/app/platform-role-templates",
					"nav.platform_role_templates", 29, List.of()));
			tree.add(new NavigationItemDto(NAV_PLATFORM_SETTINGS_ID, "/app/platform-settings", "nav.platform_settings", 30,
					List.of()));
		}
		tree.sort(Comparator.comparingInt(NavigationItemDto::sortOrder));
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("items", tree), rid);
	}
}
