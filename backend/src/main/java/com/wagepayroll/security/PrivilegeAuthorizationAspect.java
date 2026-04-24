package com.wagepayroll.security;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.auth.Sha256Hex;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.tenant.TenantContext;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class PrivilegeAuthorizationAspect {

	private final PermissionService permissionService;
	private final AuditService auditService;

	public PrivilegeAuthorizationAspect(PermissionService permissionService, AuditService auditService) {
		this.permissionService = permissionService;
		this.auditService = auditService;
	}

	@Around("@annotation(requiresPrivilege)")
	public Object enforce(ProceedingJoinPoint pjp, RequiresPrivilege requiresPrivilege) throws Throwable {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
			throw new AccessDeniedException("Unauthenticated");
		}
		UUID userId = UUID.fromString(auth.getName());
		UUID tenantId = TenantContext.current()
				.flatMap(c -> java.util.Optional.ofNullable(c.tenantId()))
				.orElseThrow(() -> new AccessDeniedException("Tenant context required"));
		String privilegeCode = requiresPrivilege.value();
		PrivilegeGrant grant = permissionService.evaluateTenantPrivilege(userId, tenantId, privilegeCode);
		if (grant == PrivilegeGrant.DENIED) {
			throw new AccessDeniedException("Missing privilege " + privilegeCode);
		}
		if (grant == PrivilegeGrant.SUPERADMIN_ELEVATED) {
			enforceBreakGlassHeaderIfMutating();
		}
		Object result = pjp.proceed();
		if (grant == PrivilegeGrant.SUPERADMIN_ELEVATED) {
			appendElevationAudit(userId, tenantId, privilegeCode);
		}
		return result;
	}

	private HttpServletRequest currentRequest() {
		ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		if (attrs == null) {
			throw new IllegalStateException("No HTTP request in context");
		}
		return attrs.getRequest();
	}

	private void enforceBreakGlassHeaderIfMutating() {
		HttpServletRequest req = currentRequest();
		String method = req.getMethod();
		if (!isMutating(method)) {
			return;
		}
		String raw = req.getHeader(BreakGlassHeaders.REASON);
		if (raw == null) {
			throw new AccessDeniedException("BREAK_GLASS_REASON_REQUIRED");
		}
		String reason = raw.trim();
		if (reason.length() < BreakGlassHeaders.REASON_MIN_LEN || reason.length() > BreakGlassHeaders.REASON_MAX_LEN) {
			throw new AccessDeniedException("BREAK_GLASS_REASON_LENGTH");
		}
	}

	private static boolean isMutating(String method) {
		return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
	}

	private void appendElevationAudit(UUID userId, UUID tenantId, String privilegeCode) {
		HttpServletRequest req = currentRequest();
		Map<String, Object> meta = new HashMap<>();
		meta.put("privilege", privilegeCode);
		meta.put("method", req.getMethod());
		String uri = req.getRequestURI();
		if (uri != null && uri.length() > 240) {
			uri = uri.substring(0, 240);
		}
		meta.put("path", uri);
		if (isMutating(req.getMethod())) {
			String reason = req.getHeader(BreakGlassHeaders.REASON).trim();
			meta.put("reasonLength", reason.length());
			meta.put("reasonSha256", Sha256Hex.ofUtf8String(reason));
		}
		else {
			meta.put("readElevation", true);
		}
		String correlationId = RequestIdFilter.currentRequestId(req);
		auditService.append(tenantId, userId, AuditActionCodes.SUPERADMIN_TENANT_ELEVATED_ACCESS, AuditResourceTypes.TENANT,
				tenantId.toString(), correlationId, meta);
	}
}
