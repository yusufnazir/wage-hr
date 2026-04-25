package com.wagepayroll.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.TenantSubscriptionPayloadDto;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.subscription.TenantSubscriptionService;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/me")
public class MeSubscriptionController {

	private final TenantSubscriptionService tenantSubscriptionService;

	public MeSubscriptionController(TenantSubscriptionService tenantSubscriptionService) {
		this.tenantSubscriptionService = tenantSubscriptionService;
	}

	@GetMapping("/subscription")
	public ApiResponse<Map<String, TenantSubscriptionPayloadDto>> subscription(HttpServletRequest request) {
		UUID tenantId = TenantContext.current().flatMap(c -> Optional.ofNullable(c.tenantId()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "TENANT_CONTEXT_REQUIRED"));
		String rid = RequestIdFilter.currentRequestId(request);
		Map<String, TenantSubscriptionPayloadDto> data = new LinkedHashMap<>();
		data.put("subscription", tenantSubscriptionService.findByTenantId(tenantId).orElse(null));
		return ApiResponse.of(data, rid);
	}
}
