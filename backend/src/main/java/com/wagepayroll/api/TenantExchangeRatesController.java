package com.wagepayroll.api;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.wagepayroll.api.dto.TenantExchangeRateCreateRequest;
import com.wagepayroll.api.dto.TenantExchangeRateItemDto;
import com.wagepayroll.api.dto.TenantExchangeRatePatchRequest;
import com.wagepayroll.api.dto.TenantExchangeRateResolveDto;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.currency.TenantExchangeRateService;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/exchange-rates")
public class TenantExchangeRatesController {

	private final TenantExchangeRateService tenantExchangeRateService;
	private final AuditService auditService;

	public TenantExchangeRatesController(TenantExchangeRateService tenantExchangeRateService, AuditService auditService) {
		this.tenantExchangeRateService = tenantExchangeRateService;
		this.auditService = auditService;
	}

	@GetMapping
	@RequiresPrivilege("EXCHANGE_RATE_VIEW")
	public ResponseEntity<ApiResponse<Object>> list(@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "sort", defaultValue = "effectiveDate,desc") String sort) {
		UUID tenantId = TenantContext.requireTenantId();
		Page<TenantExchangeRateItemDto> result = tenantExchangeRateService.list(tenantId, page, size, sort);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("data", result.getContent());
		payload.put("page", Map.of("number", result.getNumber(), "size", result.getSize(), "totalElements",
				result.getTotalElements(), "totalPages", result.getTotalPages()));
		return ResponseEntity.ok(ApiResponse.of(payload, "tenant.exchange_rate.listed"));
	}

	@GetMapping("/{id}")
	@RequiresPrivilege("EXCHANGE_RATE_VIEW")
	public ResponseEntity<ApiResponse<Object>> get(@PathVariable("id") UUID id) {
		UUID tenantId = TenantContext.requireTenantId();
		TenantExchangeRateItemDto item = tenantExchangeRateService.get(tenantId, id);
		return ResponseEntity.ok(ApiResponse.of(Map.of("item", item), "tenant.exchange_rate.fetched"));
	}

	@PostMapping
	@RequiresPrivilege("EXCHANGE_RATE_MANAGE")
	public ResponseEntity<ApiResponse<Object>> create(@Valid @RequestBody TenantExchangeRateCreateRequest request,
			Authentication authentication, HttpServletRequest httpRequest) {
		UUID tenantId = TenantContext.requireTenantId();
		TenantExchangeRateItemDto created = tenantExchangeRateService.create(tenantId, request);

		Map<String, Object> auditMetadata = new LinkedHashMap<>();
		auditMetadata.put("fromCurrencyCode", created.fromCurrencyCode());
		auditMetadata.put("toCurrencyCode", created.toCurrencyCode());
		auditMetadata.put("rate", created.rate().toPlainString());
		auditMetadata.put("effectiveDate", created.effectiveDate().toString());
		auditService.append(tenantId, actor(authentication), AuditActionCodes.EXCHANGE_RATE_CREATED,
				AuditResourceTypes.EXCHANGE_RATE, created.id().toString(), RequestIdFilter.currentRequestId(httpRequest),
				auditMetadata);

		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.of(Map.of("item", created), "tenant.exchange_rate.created"));
	}

	@PatchMapping("/{id}")
	@RequiresPrivilege("EXCHANGE_RATE_MANAGE")
	public ResponseEntity<ApiResponse<Object>> patch(@PathVariable("id") UUID id,
			@Valid @RequestBody TenantExchangeRatePatchRequest request, Authentication authentication,
			HttpServletRequest httpRequest) {
		UUID tenantId = TenantContext.requireTenantId();
		TenantExchangeRateService.UpdateResult updated = tenantExchangeRateService.patch(tenantId, id, request);

		Map<String, Object> auditMetadata = new LinkedHashMap<>();
		auditMetadata.put("id", updated.item().id().toString());
		auditMetadata.putAll(updated.changedFields());
		auditService.append(tenantId, actor(authentication), AuditActionCodes.EXCHANGE_RATE_UPDATED,
				AuditResourceTypes.EXCHANGE_RATE, updated.item().id().toString(),
				RequestIdFilter.currentRequestId(httpRequest), auditMetadata);

		return ResponseEntity.ok(ApiResponse.of(Map.of("item", updated.item()), "tenant.exchange_rate.updated"));
	}

	@DeleteMapping("/{id}")
	@RequiresPrivilege("EXCHANGE_RATE_MANAGE")
	public ResponseEntity<Void> delete(@PathVariable("id") UUID id, Authentication authentication,
			HttpServletRequest httpRequest) {
		UUID tenantId = TenantContext.requireTenantId();
		TenantExchangeRateItemDto deleted = tenantExchangeRateService.delete(tenantId, id);

		Map<String, Object> auditMetadata = new LinkedHashMap<>();
		auditMetadata.put("fromCurrencyCode", deleted.fromCurrencyCode());
		auditMetadata.put("toCurrencyCode", deleted.toCurrencyCode());
		auditMetadata.put("effectiveDate", deleted.effectiveDate().toString());
		auditService.append(tenantId, actor(authentication), AuditActionCodes.EXCHANGE_RATE_DELETED,
				AuditResourceTypes.EXCHANGE_RATE, deleted.id().toString(), RequestIdFilter.currentRequestId(httpRequest),
				auditMetadata);

		return ResponseEntity.noContent().build();
	}

	@GetMapping("/resolve")
	@RequiresPrivilege("EXCHANGE_RATE_VIEW")
	public ResponseEntity<ApiResponse<TenantExchangeRateResolveDto>> resolve(@RequestParam("from") String from,
			@RequestParam("to") String to, @RequestParam("date") LocalDate date) {
		UUID tenantId = TenantContext.requireTenantId();
		TenantExchangeRateResolveDto resolved = tenantExchangeRateService.resolve(tenantId, from, to, date);
		return ResponseEntity.ok(ApiResponse.of(resolved, "tenant.exchange_rate.resolved"));
	}

	private UUID actor(Authentication authentication) {
		return UUID.fromString(authentication.getName());
	}
}
