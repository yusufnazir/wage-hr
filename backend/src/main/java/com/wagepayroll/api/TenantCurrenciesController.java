package com.wagepayroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.TenantCurrenciesDto;
import com.wagepayroll.api.dto.TenantCurrenciesReplaceRequest;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.currency.TenantCurrencyService;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/currencies")
public class TenantCurrenciesController {

	private final TenantCurrencyService tenantCurrencyService;
	private final AuditService auditService;

	public TenantCurrenciesController(TenantCurrencyService tenantCurrencyService, AuditService auditService) {
		this.tenantCurrencyService = tenantCurrencyService;
		this.auditService = auditService;
	}

	@GetMapping
	@RequiresPrivilege("TENANT_CURRENCY_VIEW")
	public ApiResponse<Map<String, Object>> get(HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		TenantCurrenciesDto payload = tenantCurrencyService.get(tenantId);
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("items", payload.items(), "assignedCodes", payload.assignedCodes()), rid);
	}

	@PutMapping
	@RequiresPrivilege("TENANT_CURRENCY_EDIT")
	public ResponseEntity<Void> replace(@RequestBody TenantCurrenciesReplaceRequest body, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		List<String> assigned = tenantCurrencyService.replace(tenantId, body);
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		auditService.append(tenantId, actor, AuditActionCodes.TENANT_CURRENCIES_REPLACED, AuditResourceTypes.TENANT_CURRENCY,
				null, RequestIdFilter.currentRequestId(request), Map.of("codes", assigned));
		return ResponseEntity.noContent().build();
	}
}
