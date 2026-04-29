package com.wagepayroll.security;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.wagepayroll.domain.user.UserAccountRepository;
import com.wagepayroll.tenant.HostMode;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code admin.{baseDomain}} is reserved for platform operators. Authenticated users who are not platform
 * superadmins receive 403 on API routes (permitAll paths are unchanged because this filter skips anonymous).
 */
@Component
@Order(30)
public class AdminHostPlatformOperatorFilter extends OncePerRequestFilter {

	private final UserAccountRepository userAccountRepository;

	public AdminHostPlatformOperatorFilter(UserAccountRepository userAccountRepository) {
		this.userAccountRepository = userAccountRepository;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (TenantContext.current().map(c -> c.hostMode() != HostMode.ADMIN).orElse(true)) {
			filterChain.doFilter(request, response);
			return;
		}
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
			filterChain.doFilter(request, response);
			return;
		}
		UUID userId = UUID.fromString(auth.getName());
		if (!userAccountRepository.findById(userId).map(u -> u.isPlatformSuperadmin()).orElse(false)) {
			response.sendError(HttpStatus.FORBIDDEN.value(), "Admin host requires platform operator session");
			return;
		}
		filterChain.doFilter(request, response);
	}
}
