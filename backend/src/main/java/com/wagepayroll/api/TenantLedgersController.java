package com.wagepayroll.api;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.ledger.TenantLedgerService;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ledgers")
public class TenantLedgersController {

	private final TenantLedgerService tenantLedgerService;

	public TenantLedgersController(TenantLedgerService tenantLedgerService) {
		this.tenantLedgerService = tenantLedgerService;
	}

	@GetMapping
	@RequiresPrivilege("WAGE_COMPONENT_VIEW")
	public ResponseEntity<ApiResponse<Object>> list(@RequestParam(name = "companyId") UUID companyId,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		return ResponseEntity.ok(ApiResponse.of(Map.of("items", tenantLedgerService.listForCompany(tenantId, companyId)),
				RequestIdFilter.currentRequestId(request)));
	}
}
