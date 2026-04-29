package com.wagepayroll.tenant;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.wagepayroll.config.AppHostProperties;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Validates tenant {@code handle} for creation (lowercase DNS label style). Align with
 * {@link SubdomainParser} reserved hostnames — see {@code docs/modules/platform-tenant-admin.md}.
 */
@Component
public class TenantHandleValidator {

	private static final Pattern HANDLE_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");

	private final AppHostProperties appHostProperties;

	public TenantHandleValidator(AppHostProperties appHostProperties) {
		this.appHostProperties = appHostProperties;
	}

	public String normalizeAndValidate(String rawHandle) {
		if (rawHandle == null || !StringUtils.hasText(rawHandle.trim())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TENANT_HANDLE");
		}
		String handle = rawHandle.trim().toLowerCase(Locale.ROOT);
		if (handle.length() > 64) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TENANT_HANDLE");
		}
		if (!HANDLE_PATTERN.matcher(handle).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TENANT_HANDLE");
		}
		Set<String> reserved = SubdomainParser.reserved(appHostProperties.getAuthSubdomain(),
				appHostProperties.getAppSubdomain(), appHostProperties.getReservedSubdomainsExtra());
		if (reserved.contains(handle)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RESERVED_TENANT_HANDLE");
		}
		return handle;
	}
}
