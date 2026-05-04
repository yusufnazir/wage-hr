package com.wagepayroll.api;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.TenantBankTemplateCreateRequest;
import com.wagepayroll.api.dto.TenantBankTemplatePutRequest;
import com.wagepayroll.api.dto.TenantBankTemplateRowDto;
import com.wagepayroll.banktemplate.TenantBankTemplateService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/tenant/bank-templates")
public class TenantBankTemplatesController {

	private final TenantBankTemplateService tenantBankTemplateService;

	public TenantBankTemplatesController(TenantBankTemplateService tenantBankTemplateService) {
		this.tenantBankTemplateService = tenantBankTemplateService;
	}

	@GetMapping
	@RequiresPrivilege("BANK_TEMPLATE_VIEW")
	public ApiResponse<Map<String, Object>> list(
			@RequestParam(name = "companyId", required = false) UUID companyId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "active", required = false) Boolean active,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(tenantBankTemplateService.list(tenantId, companyId, page, size, active), rid);
	}

	@GetMapping("/catalog")
	@RequiresPrivilege("BANK_TEMPLATE_VIEW")
	public ApiResponse<Map<String, Object>> catalog(@RequestParam(name = "companyId", required = false) UUID companyId,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		return ApiResponse.of(Map.of("items", tenantBankTemplateService.catalog(tenantId, companyId)),
				RequestIdFilter.currentRequestId(request));
	}

	@GetMapping("/{id}")
	@RequiresPrivilege("BANK_TEMPLATE_VIEW")
	public ApiResponse<Map<String, TenantBankTemplateRowDto>> get(@PathVariable("id") UUID id,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		TenantBankTemplateRowDto row = tenantBankTemplateService.get(tenantId, id);
		return ApiResponse.of(Map.of("template", row), RequestIdFilter.currentRequestId(request));
	}

	@PostMapping
	@RequiresPrivilege("BANK_TEMPLATE_MANAGE")
	public ResponseEntity<ApiResponse<Map<String, TenantBankTemplateRowDto>>> create(
			@RequestBody TenantBankTemplateCreateRequest body, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = actorUserId();
		TenantBankTemplateRowDto row = tenantBankTemplateService.create(tenantId, body, actor,
				RequestIdFilter.currentRequestId(request));
		String rid = RequestIdFilter.currentRequestId(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("template", row), rid));
	}

	@PutMapping("/{id}")
	@RequiresPrivilege("BANK_TEMPLATE_MANAGE")
	public ApiResponse<Map<String, TenantBankTemplateRowDto>> put(@PathVariable("id") UUID id,
			@RequestBody TenantBankTemplatePutRequest body, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = actorUserId();
		TenantBankTemplateRowDto row = tenantBankTemplateService.update(tenantId, id, body, actor,
				RequestIdFilter.currentRequestId(request));
		return ApiResponse.of(Map.of("template", row), RequestIdFilter.currentRequestId(request));
	}

	@PatchMapping("/{id}/activate")
	@RequiresPrivilege("BANK_TEMPLATE_MANAGE")
	public ApiResponse<Map<String, TenantBankTemplateRowDto>> activate(@PathVariable("id") UUID id,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = actorUserId();
		TenantBankTemplateRowDto row = tenantBankTemplateService.activate(tenantId, id, actor,
				RequestIdFilter.currentRequestId(request));
		return ApiResponse.of(Map.of("template", row), RequestIdFilter.currentRequestId(request));
	}

	@PatchMapping("/{id}/deactivate")
	@RequiresPrivilege("BANK_TEMPLATE_MANAGE")
	public ApiResponse<Map<String, TenantBankTemplateRowDto>> deactivate(@PathVariable("id") UUID id,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = actorUserId();
		TenantBankTemplateRowDto row = tenantBankTemplateService.deactivate(tenantId, id, actor,
				RequestIdFilter.currentRequestId(request));
		return ApiResponse.of(Map.of("template", row), RequestIdFilter.currentRequestId(request));
	}

	@DeleteMapping("/{id}")
	@RequiresPrivilege("BANK_TEMPLATE_MANAGE")
	public ResponseEntity<Void> delete(@PathVariable("id") UUID id, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = actorUserId();
		tenantBankTemplateService.delete(tenantId, id, actor, RequestIdFilter.currentRequestId(request));
		return ResponseEntity.noContent().build();
	}

	private static UUID actorUserId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(auth.getName());
	}
}
