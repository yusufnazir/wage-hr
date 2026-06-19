package com.wagepayroll.api;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.TenantPaymentLocationCreateRequest;
import com.wagepayroll.api.dto.TenantPaymentLocationRowDto;
import com.wagepayroll.api.dto.TenantPaymentLocationUpdateRequest;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.paymentlocation.TenantPaymentLocationService;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/payment-locations")
public class TenantPaymentLocationsController {

	private final TenantPaymentLocationService service;

	public TenantPaymentLocationsController(TenantPaymentLocationService service) {
		this.service = service;
	}

	@GetMapping
	@RequiresPrivilege("PAYMENT_LOCATION_VIEW")
	public ApiResponse<Map<String, Object>> list(
			@RequestParam(name = "companyId", required = false) UUID companyId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "active", required = false) Boolean active,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		return ApiResponse.of(service.list(tenantId, companyId, page, size, active),
				RequestIdFilter.currentRequestId(request));
	}

	@GetMapping("/{id}")
	@RequiresPrivilege("PAYMENT_LOCATION_VIEW")
	public ApiResponse<Map<String, TenantPaymentLocationRowDto>> get(@PathVariable("id") UUID id,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		TenantPaymentLocationRowDto row = service.get(tenantId, id);
		return ApiResponse.of(Map.of("item", row), RequestIdFilter.currentRequestId(request));
	}

	@PostMapping
	@RequiresPrivilege("PAYMENT_LOCATION_MANAGE")
	public ResponseEntity<ApiResponse<Map<String, TenantPaymentLocationRowDto>>> create(
			@RequestBody TenantPaymentLocationCreateRequest body, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = actorUserId();
		TenantPaymentLocationRowDto row = service.create(tenantId, body, actor,
				RequestIdFilter.currentRequestId(request));
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(Map.of("item", row), RequestIdFilter.currentRequestId(request)));
	}

	@PutMapping("/{id}")
	@RequiresPrivilege("PAYMENT_LOCATION_MANAGE")
	public ApiResponse<Map<String, TenantPaymentLocationRowDto>> update(@PathVariable("id") UUID id,
			@RequestBody TenantPaymentLocationUpdateRequest body, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = actorUserId();
		TenantPaymentLocationRowDto row = service.update(tenantId, id, body, actor,
				RequestIdFilter.currentRequestId(request));
		return ApiResponse.of(Map.of("item", row), RequestIdFilter.currentRequestId(request));
	}

	@PatchMapping("/{id}/activate")
	@RequiresPrivilege("PAYMENT_LOCATION_MANAGE")
	public ApiResponse<Map<String, TenantPaymentLocationRowDto>> activate(@PathVariable("id") UUID id,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = actorUserId();
		TenantPaymentLocationRowDto row = service.activate(tenantId, id, actor,
				RequestIdFilter.currentRequestId(request));
		return ApiResponse.of(Map.of("item", row), RequestIdFilter.currentRequestId(request));
	}

	@PatchMapping("/{id}/deactivate")
	@RequiresPrivilege("PAYMENT_LOCATION_MANAGE")
	public ApiResponse<Map<String, TenantPaymentLocationRowDto>> deactivate(@PathVariable("id") UUID id,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = actorUserId();
		TenantPaymentLocationRowDto row = service.deactivate(tenantId, id, actor,
				RequestIdFilter.currentRequestId(request));
		return ApiResponse.of(Map.of("item", row), RequestIdFilter.currentRequestId(request));
	}

	private UUID actorUserId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null) {
			return null;
		}
		try {
			return UUID.fromString(auth.getName());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
