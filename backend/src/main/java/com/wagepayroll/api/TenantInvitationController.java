package com.wagepayroll.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.CreateInvitationRequest;
import com.wagepayroll.api.dto.TenantInvitationListItemDto;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.invitation.TenantInvitationService;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/invitations")
public class TenantInvitationController {

	private final TenantInvitationService tenantInvitationService;

	public TenantInvitationController(TenantInvitationService tenantInvitationService) {
		this.tenantInvitationService = tenantInvitationService;
	}

	@PostMapping
	@RequiresPrivilege("USER_INVITE")
	public ApiResponse<Map<String, Object>> create(@RequestBody CreateInvitationRequest body, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		String tenantHandle = TenantContext.requireTenantHandle();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		TenantInvitationService.CreateInvitationResult r = tenantInvitationService.create(tenantId, tenantHandle, actor,
				body.email(), body.roleId());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("invitationId", r.invitationId());
		data.put("expiresAt", r.expiresAt().toString());
		data.put("idempotentReplay", r.idempotentReplay());
		if (r.devPlainToken() != null) {
			data.put("devPlainToken", r.devPlainToken());
		}
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(data, rid);
	}

	@GetMapping
	@RequiresPrivilege("USER_INVITE")
	public ApiResponse<Map<String, List<TenantInvitationListItemDto>>> listPending(HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		List<TenantInvitationListItemDto> items = tenantInvitationService.listPending(tenantId);
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("invitations", items), rid);
	}
}
