package com.wagepayroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.PlanFeatureCatalogItemDto;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.domain.plan.PlanFeatureEntity;
import com.wagepayroll.domain.plan.PlanFeatureRepository;
import com.wagepayroll.security.PlatformOperatorService;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/plan-features")
public class PlatformPlanFeaturesController {

	private final PlatformOperatorService platformOperatorService;
	private final PlanFeatureRepository planFeatureRepository;

	public PlatformPlanFeaturesController(PlatformOperatorService platformOperatorService,
			PlanFeatureRepository planFeatureRepository) {
		this.platformOperatorService = platformOperatorService;
		this.planFeatureRepository = planFeatureRepository;
	}

	@GetMapping
	public ApiResponse<Map<String, List<PlanFeatureCatalogItemDto>>> list(HttpServletRequest request) {
		requirePlatformSuperadminUser();
		List<PlanFeatureCatalogItemDto> items = planFeatureRepository.findAllByOrderBySortOrderAscCodeAsc().stream()
				.map(PlatformPlanFeaturesController::toDto)
				.collect(Collectors.toList());
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("features", items), rid);
	}

	private void requirePlatformSuperadminUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(userId);
	}

	private static PlanFeatureCatalogItemDto toDto(PlanFeatureEntity e) {
		return new PlanFeatureCatalogItemDto(e.getId(), e.getCode(), e.getSortOrder(), e.getCreatedAt(), e.getUpdatedAt());
	}
}
