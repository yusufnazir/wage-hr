package com.wagepayroll.tenant;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.config.AppHostProperties;
import com.wagepayroll.domain.tenant.TenantEntity;
import com.wagepayroll.domain.tenant.TenantRepository;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(20)
public class TenantContextFilter extends OncePerRequestFilter {

	private final AppHostProperties props;
	private final TenantRepository tenantRepository;
	private final ObjectMapper objectMapper;

	public TenantContextFilter(AppHostProperties props, TenantRepository tenantRepository, ObjectMapper objectMapper) {
		this.props = props;
		this.tenantRepository = tenantRepository;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		try {
			String host = Optional.ofNullable(request.getHeader("X-Forwarded-Host")).orElse(request.getServerName());
			if (!StringUtils.hasText(host)) {
				host = request.getHeader("Host");
			}
			Set<String> reserved = SubdomainParser.reserved(props.getAuthSubdomain(), props.getAppSubdomain(),
					props.getReservedSubdomainsExtra());
			SubdomainParser.ParsedHost parsed = SubdomainParser.parse(host, props.getBaseDomain(), reserved);

			UUID tenantId = null;
			String handle = null;
			if (parsed.mode() == HostMode.TENANT && parsed.tenantHandle() != null) {
				String hostHandle = parsed.tenantHandle();
				Optional<TenantEntity> t = tenantRepository.findByHandle(hostHandle);
				if (t.isEmpty()) {
					if (isApiRequest(request)) {
						writeProblem(response, HttpStatus.NOT_FOUND, "Unknown tenant", "UNKNOWN_TENANT");
					}
					else {
						response.sendError(HttpStatus.NOT_FOUND.value(), "Unknown tenant");
					}
					return;
				}
				tenantId = t.get().getId();
				handle = t.get().getHandle();
			}

			/*
			 * Explicit X-Tenant-Id (e.g. BFF superadmin lens cookie) wins over host-derived tenant when it resolves to
			 * an existing row — including on TENANT subdomains so app.lvh.me is not required to switch lens.
			 */
			if (parsed.mode() != HostMode.AUTH) {
				String tid = request.getHeader("X-Tenant-Id");
				if (StringUtils.hasText(tid)) {
					try {
						UUID u = UUID.fromString(tid.trim());
						Optional<TenantEntity> t = tenantRepository.findById(u);
						if (t.isPresent()) {
							tenantId = t.get().getId();
							handle = t.get().getHandle();
						}
						else if (isApiRequest(request)) {
							writeProblem(response, HttpStatus.NOT_FOUND, "Unknown tenant id", "UNKNOWN_TENANT_ID");
							return;
						}
					}
					catch (IllegalArgumentException ex) {
						if (isApiRequest(request)) {
							writeProblem(response, HttpStatus.BAD_REQUEST, "Invalid X-Tenant-Id header",
									"INVALID_TENANT_ID_HEADER");
							return;
						}
					}
				}
			}

			TenantContext.set(parsed.mode(), tenantId, handle, host);
			filterChain.doFilter(request, response);
		}
		finally {
			TenantContext.clear();
		}
	}

	private static boolean isApiRequest(HttpServletRequest request) {
		return request.getRequestURI().startsWith("/api/");
	}

	private void writeProblem(HttpServletResponse response, HttpStatus status, String detail, String code)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		pd.setProperty("code", code);
		objectMapper.writeValue(response.getOutputStream(), pd);
	}
}
