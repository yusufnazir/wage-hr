package com.wagepayroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.PlatformCurrencyCreateRequest;
import com.wagepayroll.api.dto.PlatformCurrencyDto;
import com.wagepayroll.api.dto.PlatformCurrencyPatchRequest;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.currency.PlatformCurrencyService;
import com.wagepayroll.security.PlatformOperatorService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/currencies")
public class PlatformCurrenciesController {

	private final PlatformOperatorService platformOperatorService;
	private final PlatformCurrencyService platformCurrencyService;
	private final AuditService auditService;

	public PlatformCurrenciesController(PlatformOperatorService platformOperatorService,
			PlatformCurrencyService platformCurrencyService, AuditService auditService) {
		this.platformOperatorService = platformOperatorService;
		this.platformCurrencyService = platformCurrencyService;
		this.auditService = auditService;
	}

	@GetMapping
	public ApiResponse<Map<String, List<PlatformCurrencyDto>>> list(HttpServletRequest request) {
		requirePlatformSuperadmin();
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("items", platformCurrencyService.list()), rid);
	}

	@PostMapping
	public ResponseEntity<ApiResponse<Map<String, PlatformCurrencyDto>>> create(@RequestBody PlatformCurrencyCreateRequest body,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformCurrencyDto row = platformCurrencyService.create(body);
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(null, actor, AuditActionCodes.PLATFORM_CURRENCY_CREATED, AuditResourceTypes.PLATFORM_CURRENCY,
				row.id().toString(), rid, Map.of("code", row.code()));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("item", row), rid));
	}

	@PatchMapping("/{id}")
	public ApiResponse<Map<String, PlatformCurrencyDto>> patch(@PathVariable("id") UUID id,
			@RequestBody PlatformCurrencyPatchRequest body, HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformCurrencyDto row = platformCurrencyService.patch(id, body);
		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(null, actor, AuditActionCodes.PLATFORM_CURRENCY_UPDATED, AuditResourceTypes.PLATFORM_CURRENCY,
				row.id().toString(), rid, Map.of("code", row.code()));
		return ApiResponse.of(Map.of("item", row), rid);
	}

	private UUID requirePlatformSuperadmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(userId);
		return userId;
	}
}
