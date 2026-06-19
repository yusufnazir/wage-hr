package com.wagepayroll.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.LocalePatchRequest;
import com.wagepayroll.api.dto.TenantSummaryDto;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.domain.user.UserAccountEntity;
import com.wagepayroll.domain.user.UserAccountRepository;
import com.wagepayroll.i18n.UserLocaleService;
import com.wagepayroll.security.PermissionService;
import com.wagepayroll.settings.PlatformBrandingService;
import com.wagepayroll.subscription.SubscriptionGatingService;
import com.wagepayroll.tenant.TenantContext;
import com.wagepayroll.tenant.TenantDirectoryService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class MeController {

	private final UserAccountRepository userAccountRepository;
	private final PermissionService permissionService;
	private final UserLocaleService userLocaleService;
	private final AuditService auditService;
	private final TenantDirectoryService tenantDirectoryService;
	private final SubscriptionGatingService subscriptionGatingService;
	private final PlatformBrandingService platformBrandingService;

	public MeController(UserAccountRepository userAccountRepository, PermissionService permissionService,
			UserLocaleService userLocaleService, AuditService auditService, TenantDirectoryService tenantDirectoryService,
			SubscriptionGatingService subscriptionGatingService, PlatformBrandingService platformBrandingService) {
		this.userAccountRepository = userAccountRepository;
		this.permissionService = permissionService;
		this.userLocaleService = userLocaleService;
		this.auditService = auditService;
		this.tenantDirectoryService = tenantDirectoryService;
		this.subscriptionGatingService = subscriptionGatingService;
		this.platformBrandingService = platformBrandingService;
	}

	@GetMapping("/me")
	public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		UserAccountEntity user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown or removed user"));
		String rid = RequestIdFilter.currentRequestId(request);
		var tenant = TenantContext.current();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("userId", userId.toString());
		payload.put("email", user.getEmail());
		payload.put("locale", user.getPreferredLocale());
		payload.put("platformSuperadmin", user.isPlatformSuperadmin());
		if (tenant.isEmpty() || tenant.get().tenantId() == null) {
			payload.put("privileges", List.of());
			payload.put("tenantHandle", null);
			payload.put("tenantId", null);
			payload.put("planFeatureCodes", List.of());
			return ApiResponse.of(payload, rid);
		}
		UUID tenantId = tenant.get().tenantId();
		List<String> privs = permissionService.effectivePrivilegeCodes(userId, tenantId);
		payload.put("privileges", privs);
		payload.put("tenantHandle", tenant.get().tenantHandle());
		payload.put("tenantId", tenantId.toString());
		payload.put("planFeatureCodes", subscriptionGatingService.activePlanFeatureCodesOrEmpty(tenantId));
		var branding = platformBrandingService.tenantMeBranding();
		payload.put("applicationName", branding.applicationName());
		payload.put("dateFormat", branding.dateFormat());
		payload.put("publicBaseUrl", branding.publicBaseUrl());
		return ApiResponse.of(payload, rid);
	}

	/**
	 * All tenants the principal is a member of (independent of current {@code TenantContext}).
	 */
	@GetMapping("/me/tenants")
	public ApiResponse<Map<String, List<TenantSummaryDto>>> tenants(HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		if (userAccountRepository.findById(userId).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown or removed user");
		}
		List<TenantSummaryDto> list = tenantDirectoryService.listTenantSummaries(userId);
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("tenants", list), rid);
	}

	@PatchMapping("/me/locale")
	public ResponseEntity<Void> patchLocale(@RequestBody LocalePatchRequest body, HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		if (userAccountRepository.findById(userId).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown or removed user");
		}
		String locale = userLocaleService.setPreferredLocale(userId, body.locale());
		auditService.append(null, userId, AuditActionCodes.USER_LOCALE_CHANGED, AuditResourceTypes.USER_ACCOUNT,
				userId.toString(), RequestIdFilter.currentRequestId(request), Map.of("locale", locale));
		return ResponseEntity.noContent().build();
	}
}
