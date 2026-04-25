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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class NavigationMenuService {

	private final NavMenuItemRepository navMenuItemRepository;
	private final PermissionService permissionService;
	private final SubscriptionGatingService subscriptionGatingService;

	public NavigationMenuService(NavMenuItemRepository navMenuItemRepository, PermissionService permissionService,
			SubscriptionGatingService subscriptionGatingService) {
		this.navMenuItemRepository = navMenuItemRepository;
		this.permissionService = permissionService;
		this.subscriptionGatingService = subscriptionGatingService;
	}

	@Transactional(readOnly = true)
	public List<NavigationItemDto> effectiveMenu(UUID userId, UUID tenantId) {
		List<String> privCodes = permissionService.effectivePrivilegeCodes(userId, tenantId);
		Set<String> privSet = new HashSet<>(privCodes);
		Set<String> activePlanFeatureCodes = new HashSet<>(subscriptionGatingService.activePlanFeatureCodesOrEmpty(tenantId));

		List<NavMenuItemEntity> rows = navMenuItemRepository.findByTenantIdOrderBySortOrderAsc(tenantId);
		List<NavMenuItemEntity> visible = new ArrayList<>();
		for (NavMenuItemEntity e : rows) {
			String req = e.getRequiredPrivilegeCode();
			if (!(req == null || req.isEmpty() || privSet.contains(req))) {
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

	private static void sortRecursive(List<NavigationItemDto> nodes) {
		nodes.sort(Comparator.comparingInt(NavigationItemDto::sortOrder));
		for (NavigationItemDto n : nodes) {
			sortRecursive(n.children());
		}
	}
}
