package com.wagepayroll.settings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.api.dto.NavigationItemDto;
import com.wagepayroll.domain.navmenu.NavMenuItemEntity;
import com.wagepayroll.domain.navmenu.NavMenuItemRepository;
import com.wagepayroll.security.PermissionService;
import com.wagepayroll.subscription.SubscriptionGatingService;

import com.wagepayroll.domain.user.UserAccountRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class NavigationMenuService {

	private final NavMenuItemRepository navMenuItemRepository;
	private final PermissionService permissionService;
	private final SubscriptionGatingService subscriptionGatingService;
	private final UserAccountRepository userAccountRepository;

	public NavigationMenuService(NavMenuItemRepository navMenuItemRepository, PermissionService permissionService,
			SubscriptionGatingService subscriptionGatingService, UserAccountRepository userAccountRepository) {
		this.navMenuItemRepository = navMenuItemRepository;
		this.permissionService = permissionService;
		this.subscriptionGatingService = subscriptionGatingService;
		this.userAccountRepository = userAccountRepository;
	}

	@Transactional(readOnly = true)
	public List<NavigationItemDto> effectiveMenu(UUID userId, UUID tenantId) {
		// Platform superadmins see all tenant nav items unconditionally (they bypass the pool ceiling).
		// Regular users are filtered to their effective privilege grants within the tenant.
		boolean isSuperadmin = userAccountRepository.findById(userId).map(u -> u.isPlatformSuperadmin()).orElse(false);
		Set<String> privSet;
		if (isSuperadmin) {
			privSet = null; // null means "bypass privilege filter"
		} else {
			privSet = new HashSet<>(permissionService.effectivePrivilegeCodes(userId, tenantId));
		}
		Set<String> activePlanFeatureCodes = new HashSet<>(subscriptionGatingService.activePlanFeatureCodesOrEmpty(tenantId));

		List<NavMenuItemEntity> rows = navMenuItemRepository.findAllByOrderBySortOrderAsc();
		if (rows.isEmpty()) {
			return defaultMenuWhenTenantRowsMissing(privSet, activePlanFeatureCodes);
		}
		List<NavMenuItemEntity> visible = new ArrayList<>();
		for (NavMenuItemEntity e : rows) {
			String req = e.getRequiredPrivilegeCode();
			if (privSet != null && !(req == null || req.isEmpty() || privSet.contains(req))) {
				continue;
			}
			String reqPf = e.getRequiredPlanFeatureCode();
			if (StringUtils.hasText(reqPf) && !activePlanFeatureCodes.contains(reqPf.trim())) {
				continue;
			}
			visible.add(e);
		}

		Set<UUID> visibleIds = new HashSet<>();
		for (NavMenuItemEntity e : visible) {
			visibleIds.add(e.getId());
		}

		Map<UUID, NavigationItemDto> idToDto = new HashMap<>();
		for (NavMenuItemEntity e : visible) {
			idToDto.put(e.getId(),
					new NavigationItemDto(e.getId(), e.getPath(), e.getLabelKey(), e.getSortOrder(), new ArrayList<>()));
		}

		List<NavigationItemDto> roots = new ArrayList<>();
		for (NavMenuItemEntity e : visible) {
			NavigationItemDto self = idToDto.get(e.getId());
			UUID pid = e.getParentId();
			if (pid != null && visibleIds.contains(pid)) {
				idToDto.get(pid).children().add(self);
			}
			else {
				roots.add(self);
			}
		}

		sortRecursive(roots);
		return roots;
	}

	private List<NavigationItemDto> defaultMenuWhenTenantRowsMissing(Set<String> privSet, Set<String> activePlanFeatureCodes) {
		List<NavigationItemDto> defaults = new ArrayList<>();
		addDefaultIfVisible(defaults, privSet, activePlanFeatureCodes, "/app", "nav.dashboard", 0, null, null);
		addDefaultIfVisible(defaults, privSet, activePlanFeatureCodes, "/app/users", "nav.users", 10, "USER_VIEW", null);
		addDefaultIfVisible(defaults, privSet, activePlanFeatureCodes, "/app/roles", "nav.roles", 12, "ROLE_VIEW", null);
		addDefaultIfVisible(defaults, privSet, activePlanFeatureCodes, "/app/tenant-currencies", "nav.tenant_currencies", 16,
				"TENANT_CURRENCY_VIEW", null); // no plan-feature gate — tenant currencies is a core feature
		addDefaultIfVisible(defaults, privSet, activePlanFeatureCodes, "/app/documents", "nav.documents", 15,
				"DOCUMENT_VIEW", null);
		addDefaultIfVisible(defaults, privSet, activePlanFeatureCodes, "/app/settings", "nav.tenant_settings", 20,
				"TENANT_SETTINGS_EDIT", null);
		addDefaultIfVisible(defaults, privSet, activePlanFeatureCodes, "/app/companies", "nav.companies", 40,
				"COMPANY_VIEW", null);
		addDefaultIfVisible(defaults, privSet, activePlanFeatureCodes, "/app/departments", "nav.departments", 41,
				"DEPARTMENT_VIEW", null);
		addDefaultIfVisible(defaults, privSet, activePlanFeatureCodes, "/app/jobs", "nav.jobs", 42,
				"JOB_VIEW", null);
		addDefaultIfVisible(defaults, privSet, activePlanFeatureCodes, "/app/employee-groups", "nav.employee_groups", 43,
				"EMPLOYEE_GROUP_VIEW", null);
		addDefaultIfVisible(defaults, privSet, activePlanFeatureCodes, "/app/employees", "nav.employees", 44,
				"EMPLOYEE_VIEW", null);
		defaults.sort(Comparator.comparingInt(NavigationItemDto::sortOrder));
		return defaults;
	}

	private static void addDefaultIfVisible(List<NavigationItemDto> out, Set<String> privSet,
			Set<String> activePlanFeatureCodes, String path, String labelKey, int sortOrder, String requiredPrivilege,
			String requiredPlanFeature) {
		if (privSet != null && requiredPrivilege != null && !requiredPrivilege.isBlank() && !privSet.contains(requiredPrivilege)) {
			return;
		}
		if (StringUtils.hasText(requiredPlanFeature) && !activePlanFeatureCodes.contains(requiredPlanFeature.trim())) {
			return;
		}
		out.add(new NavigationItemDto(UUID.randomUUID(), path, labelKey, sortOrder, new ArrayList<>()));
	}

	private static void sortRecursive(List<NavigationItemDto> nodes) {
		nodes.sort(Comparator.comparingInt(NavigationItemDto::sortOrder));
		for (NavigationItemDto n : nodes) {
			sortRecursive(n.children());
		}
	}
}
