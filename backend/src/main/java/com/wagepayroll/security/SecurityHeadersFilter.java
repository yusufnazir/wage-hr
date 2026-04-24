package com.wagepayroll.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.wagepayroll.config.AppSecurityHeadersProperties;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(15)
public class SecurityHeadersFilter extends OncePerRequestFilter {

	private final AppSecurityHeadersProperties props;

	public SecurityHeadersFilter(AppSecurityHeadersProperties props) {
		this.props = props;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		response.setHeader("Content-Security-Policy", props.getContentSecurityPolicy());
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader("Referrer-Policy", props.getReferrerPolicy());
		response.setHeader("Permissions-Policy", props.getPermissionsPolicy());
		filterChain.doFilter(request, response);
	}
}
