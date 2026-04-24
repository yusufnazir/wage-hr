package com.wagepayroll.api;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.AcceptInvitationRequest;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.invitation.TenantInvitationService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/invitations")
public class InvitationAcceptController {

	private final TenantInvitationService tenantInvitationService;

	public InvitationAcceptController(TenantInvitationService tenantInvitationService) {
		this.tenantInvitationService = tenantInvitationService;
	}

	@PostMapping("/accept")
	public ApiResponse<Map<String, String>> accept(@RequestBody AcceptInvitationRequest body, HttpServletRequest request) {
		tenantInvitationService.accept(body);
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("status", "accepted"), rid);
	}
}
