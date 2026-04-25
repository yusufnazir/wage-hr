package com.wagepayroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.CommercialPlanDetailDto;
import com.wagepayroll.api.dto.CommercialPlanListItemDto;
import com.wagepayroll.api.dto.CreateCommercialPlanRequest;
import com.wagepayroll.api.dto.ReplaceCommercialPlanRequest;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.plans.CommercialPlanService;
import com.wagepayroll.security.PlatformOperatorService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/commercial-plans")
public class PlatformCommercialPlansController {

	private final PlatformOperatorService platformOperatorService;
	private final CommercialPlanService commercialPlanService;

	public PlatformCommercialPlansController(PlatformOperatorService platformOperatorService,
			CommercialPlanService commercialPlanService) {
		this.platformOperatorService = platformOperatorService;
		this.commercialPlanService = commercialPlanService;
	}

	@GetMapping
	public ApiResponse<Map<String, List<CommercialPlanListItemDto>>> list(HttpServletRequest request) {
		requirePlatformSuperadminUser();
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("plans", commercialPlanService.listPlans()), rid);
	}

	@GetMapping("/{id}")
	public ApiResponse<CommercialPlanDetailDto> get(@PathVariable("id") UUID id, HttpServletRequest request) {
		requirePlatformSuperadminUser();
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(commercialPlanService.getPlan(id), rid);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CommercialPlanDetailDto> create(@RequestBody CreateCommercialPlanRequest body, HttpServletRequest request) {
		requirePlatformSuperadminUser();
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(commercialPlanService.create(body), rid);
	}

	@PutMapping("/{id}")
	public ApiResponse<CommercialPlanDetailDto> replace(@PathVariable("id") UUID id, @RequestBody ReplaceCommercialPlanRequest body,
			HttpServletRequest request) {
		requirePlatformSuperadminUser();
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(commercialPlanService.replace(id, body), rid);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable("id") UUID id, HttpServletRequest request) {
		requirePlatformSuperadminUser();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		String rid = RequestIdFilter.currentRequestId(request);
		commercialPlanService.delete(id, actor, rid);
	}

	private void requirePlatformSuperadminUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(userId);
	}
}
