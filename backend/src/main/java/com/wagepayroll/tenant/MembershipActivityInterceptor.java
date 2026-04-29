package com.wagepayroll.tenant;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class MembershipActivityInterceptor implements HandlerInterceptor {

	private final MembershipActivityTouchService membershipActivityTouchService;

	public MembershipActivityInterceptor(MembershipActivityTouchService membershipActivityTouchService) {
		this.membershipActivityTouchService = membershipActivityTouchService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
			return true;
		}
		return TenantContext.current()
				.filter(c -> c.tenantId() != null)
				.map(c -> {
					UUID userId = UUID.fromString(auth.getName());
					membershipActivityTouchService.touchLastSeen(c.tenantId(), userId);
					return true;
				})
				.orElse(true);
	}
}
