package com.wagepayroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.BillingProviderLinkDto;
import com.wagepayroll.api.dto.UpsertBillingProviderLinkRequest;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.billing.BillingProvider;
import com.wagepayroll.billing.BillingProviderLinkService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.PlatformOperatorService;

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
public class PlatformTenantBillingProviderLinkController {

	private final PlatformOperatorService platformOperatorService;
	private final BillingProviderLinkService billingProviderLinkService;
	private final AuditService auditService;

	public PlatformTenantBillingProviderLinkController(PlatformOperatorService platformOperatorService,
			BillingProviderLinkService billingProviderLinkService, AuditService auditService) {
		this.platformOperatorService = platformOperatorService;
		this.billingProviderLinkService = billingProviderLinkService;
		this.auditService = auditService;
	}

	@GetMapping("/{tenantId}/billing-provider-links")
	public ApiResponse<Map<String, List<BillingProviderLinkDto>>> list(@PathVariable("tenantId") UUID tenantId,
			HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(actor);
		List<BillingProviderLinkDto> links = billingProviderLinkService.listByTenant(tenantId);
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("links", links), rid);
	}

	@PutMapping("/{tenantId}/billing-provider-links/{provider}")
	public ApiResponse<Map<String, BillingProviderLinkDto>> put(@PathVariable("tenantId") UUID tenantId,
			@PathVariable("provider") String providerSegment, @RequestBody UpsertBillingProviderLinkRequest body,
			HttpServletRequest request) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		BillingProvider provider = BillingProvider.parsePathSegment(providerSegment);
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(actor);
		BillingProviderLinkDto dto = billingProviderLinkService.upsert(tenantId, provider, body.externalCustomerId());
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(tenantId, actor, AuditActionCodes.BILLING_PROVIDER_LINK_UPSERTED, AuditResourceTypes.TENANT,
				tenantId.toString(), rid,
				Map.of("provider", dto.provider(), "externalCustomerId", dto.externalCustomerId()));
		return ApiResponse.of(Map.of("link", dto), rid);
	}
}
