package com.wagepayroll.common.api;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";
	public static final String ATTR = RequestIdFilter.class.getName() + ".requestId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String id = request.getHeader(HEADER);
		if (id == null || id.isBlank()) {
			id = UUID.randomUUID().toString();
		}
		request.setAttribute(ATTR, id);
		response.setHeader(HEADER, id);
		filterChain.doFilter(request, response);
	}

	public static String currentRequestId(HttpServletRequest request) {
		Object v = request.getAttribute(ATTR);
		return v != null ? v.toString() : null;
	}
}
