package com.wagepayroll.api;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.TenantSubscriptionPayloadDto;
import com.wagepayroll.api.dto.UpsertTenantSubscriptionRequest;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.PlatformOperatorService;
import com.wagepayroll.subscription.TenantSubscriptionService;
import com.wagepayroll.subscription.TenantSubscriptionStatus;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/platform/tenants")
public class PlatformTenantSubscriptionController {

	private final PlatformOperatorService platformOperatorService;
	private final TenantSubscriptionService tenantSubscriptionService;
	private final AuditService auditService;

	public PlatformTenantSubscriptionController(PlatformOperatorService platformOperatorService,
			TenantSubscriptionService tenantSubscriptionService, AuditService auditService) {
		this.platformOperatorService = platformOperatorService;
		this.tenantSubscriptionService = tenantSubscriptionService;
		this.auditService = auditService;
	}

	@GetMapping("/{tenantId}/subscription")
	public ApiResponse<Map<String, TenantSubscriptionPayloadDto>> getSubscription(@PathVariable("tenantId") UUID tenantId,
			HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(actor);
		TenantSubscriptionPayloadDto dto = tenantSubscriptionService.findByTenantId(tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND"));
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("subscription", dto), rid);
	}

	@PutMapping("/{tenantId}/subscription")
	public ApiResponse<Map<String, TenantSubscriptionPayloadDto>> putSubscription(@PathVariable("tenantId") UUID tenantId,
			@RequestBody UpsertTenantSubscriptionRequest body, HttpServletRequest request) {
		if (body == null || body.commercialPlanId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		final TenantSubscriptionStatus status;
		try {
			status = TenantSubscriptionStatus.parse(body.status());
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SUBSCRIPTION_STATUS");
		}
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(actor);
		TenantSubscriptionPayloadDto dto = tenantSubscriptionService.upsert(tenantId, body.commercialPlanId(), status);
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(tenantId, actor, AuditActionCodes.TENANT_SUBSCRIPTION_UPSERTED, AuditResourceTypes.TENANT,
				tenantId.toString(), rid,
				Map.of("commercialPlanId", dto.commercialPlanId().toString(), "status", dto.status()));
		return ApiResponse.of(Map.of("subscription", dto), rid);
	}
}
