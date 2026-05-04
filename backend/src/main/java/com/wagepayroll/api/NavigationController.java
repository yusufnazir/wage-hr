package com.wagepayroll.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

	private static final UUID NAV_GROUP_WORKSPACE_ID = UUID.fromString("50000000-0000-0000-0000-000000000091");
	private static final UUID NAV_GROUP_ADMINISTRATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000092");
	private static final UUID NAV_GROUP_SECURITY_ID = UUID.fromString("50000000-0000-0000-0000-000000000093");

	private static final Set<String> SECURITY_LABEL_KEYS = Set.of("nav.users", "nav.role_admin");
	private static final UUID NAV_PLATFORM_SETTINGS_ID = UUID.fromString("50000000-0000-0000-0000-000000000099");
	private static final UUID NAV_PLATFORM_TENANTS_ID = UUID.fromString("50000000-0000-0000-0000-000000000098");
	private static final UUID NAV_PLATFORM_ROLE_TEMPLATES_ID = UUID.fromString("50000000-0000-0000-0000-000000000097");
	private static final UUID NAV_PLATFORM_MAIL_TEMPLATES_ID = UUID.fromString("50000000-0000-0000-0000-000000000096");
	private static final UUID NAV_PLATFORM_CURRENCIES_ID = UUID.fromString("50000000-0000-0000-0000-000000000095");
	private static final UUID NAV_PLATFORM_COUNTRIES_ID = UUID.fromString("50000000-0000-0000-0000-000000000094");
	private static final UUID NAV_PLATFORM_BANK_TEMPLATES_ID = UUID.fromString("50000000-0000-0000-0000-000000000090");

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
		boolean isPlatformSuperadmin = userAccountRepository.findById(userId).map(u -> u.isPlatformSuperadmin()).orElse(false);
		Optional<UUID> tenantIdOpt = TenantContext.current().flatMap(c -> Optional.ofNullable(c.tenantId()));
		if (tenantIdOpt.isEmpty()) {
			if (isPlatformSuperadmin) {
				List<NavigationItemDto> tree = buildNavigationGroups(List.of(), platformItems());
				String rid = RequestIdFilter.currentRequestId(request);
				return ApiResponse.of(Map.of("items", tree), rid);
			}
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TENANT_CONTEXT_REQUIRED");
		}
		UUID tenantId = tenantIdOpt.get();
		List<NavigationItemDto> tenantItems = new ArrayList<>(navigationMenuService.effectiveMenu(userId, tenantId));
		List<NavigationItemDto> tree = buildNavigationGroups(tenantItems, isPlatformSuperadmin ? platformItems() : List.of());
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("items", tree), rid);
	}

	private static List<NavigationItemDto> buildNavigationGroups(List<NavigationItemDto> tenantItems,
			List<NavigationItemDto> platformItems) {
		List<NavigationItemDto> workspaceItems = new ArrayList<>();
		List<NavigationItemDto> securityItems = new ArrayList<>();
		for (NavigationItemDto item : tenantItems) {
			if (SECURITY_LABEL_KEYS.contains(item.labelKey())) {
				securityItems.add(item);
			} else {
				workspaceItems.add(item);
			}
		}
		List<NavigationItemDto> groups = new ArrayList<>();
		if (!workspaceItems.isEmpty()) {
			groups.add(new NavigationItemDto(NAV_GROUP_WORKSPACE_ID, null, "nav.group.workspace", 0, workspaceItems));
		}
		if (!securityItems.isEmpty()) {
			groups.add(new NavigationItemDto(NAV_GROUP_SECURITY_ID, null, "nav.group.security", 50, securityItems));
		}
		if (!platformItems.isEmpty()) {
			groups.add(new NavigationItemDto(NAV_GROUP_ADMINISTRATION_ID, null, "nav.group.administration", 100, platformItems));
		}
		groups.sort(Comparator.comparingInt(NavigationItemDto::sortOrder));
		return groups;
	}

	private static List<NavigationItemDto> platformItems() {
		List<NavigationItemDto> items = new ArrayList<>();
		items.add(new NavigationItemDto(NAV_PLATFORM_TENANTS_ID, "/app/platform-tenants", "nav.platform_tenants", 29,
				List.of()));
		items.add(new NavigationItemDto(NAV_PLATFORM_ROLE_TEMPLATES_ID, "/app/platform-role-templates",
				"nav.platform_role_templates", 29, List.of()));
		items.add(new NavigationItemDto(NAV_PLATFORM_CURRENCIES_ID, "/app/platform-currencies",
				"nav.platform_currencies", 29, List.of()));
		items.add(new NavigationItemDto(NAV_PLATFORM_COUNTRIES_ID, "/app/platform-countries",
				"nav.platform_countries", 30, List.of()));
		items.add(new NavigationItemDto(NAV_PLATFORM_BANK_TEMPLATES_ID, "/app/platform-bank-templates",
				"nav.platform_bank_templates", 31, List.of()));
		items.add(new NavigationItemDto(NAV_PLATFORM_MAIL_TEMPLATES_ID, "/app/platform-mail-templates",
				"nav.platform_mail_templates", 32, List.of()));
		items.add(new NavigationItemDto(NAV_PLATFORM_SETTINGS_ID, "/app/platform-settings", "nav.platform_settings", 33,
				List.of()));
		items.sort(Comparator.comparingInt(NavigationItemDto::sortOrder));
		return items;
	}
}
