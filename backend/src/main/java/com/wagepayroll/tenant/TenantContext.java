package com.wagepayroll.tenant;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;

public final class TenantContext {

	private static final ThreadLocal<Context> CTX = new ThreadLocal<>();

	private TenantContext() {
	}

	public static void set(HostMode hostMode, UUID tenantId, String tenantHandle, String hostHeader) {
		CTX.set(new Context(hostMode, tenantId, tenantHandle, hostHeader));
	}

	public static void clear() {
		CTX.remove();
	}

	public static Optional<Context> current() {
		return Optional.ofNullable(CTX.get());
	}

	public static UUID requireTenantId() {
		return current().flatMap(c -> Optional.ofNullable(c.tenantId()))
				.orElseThrow(() -> new AccessDeniedException("Tenant context required"));
	}

	public static String requireTenantHandle() {
		return current().map(Context::tenantHandle).filter(h -> h != null && !h.isBlank())
				.orElseThrow(() -> new AccessDeniedException("Tenant handle required"));
	}

	public record Context(HostMode hostMode, UUID tenantId, String tenantHandle, String hostHeader) {
	}
}
