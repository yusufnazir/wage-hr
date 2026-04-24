package com.wagepayroll.tenant;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.util.StringUtils;

public final class SubdomainParser {

	private SubdomainParser() {
	}

	public static ParsedHost parse(String hostHeader, String baseDomain, Set<String> reservedLowercase) {
		if (!StringUtils.hasText(hostHeader)) {
			return new ParsedHost(HostMode.UNKNOWN, null, hostHeader);
		}
		String host = hostHeader.trim().toLowerCase(Locale.ROOT);
		int colon = host.indexOf(':');
		if (colon >= 0) {
			host = host.substring(0, colon);
		}
		String base = baseDomain.trim().toLowerCase(Locale.ROOT);
		if (!host.endsWith(base) || host.equals(base)) {
			return new ParsedHost(HostMode.UNKNOWN, null, hostHeader);
		}
		String prefix = host.substring(0, host.length() - base.length());
		if (prefix.endsWith(".")) {
			prefix = prefix.substring(0, prefix.length() - 1);
		}
		int dot = prefix.lastIndexOf('.');
		String sub = dot >= 0 ? prefix.substring(dot + 1) : prefix;
		if (!StringUtils.hasText(sub)) {
			return new ParsedHost(HostMode.UNKNOWN, null, hostHeader);
		}
		if (reservedLowercase.contains(sub)) {
			if ("auth".equals(sub)) {
				return new ParsedHost(HostMode.AUTH, null, hostHeader);
			}
			if ("app".equals(sub)) {
				return new ParsedHost(HostMode.APP, null, hostHeader);
			}
			if ("api".equals(sub)) {
				return new ParsedHost(HostMode.API, null, hostHeader);
			}
			return new ParsedHost(HostMode.UNKNOWN, null, hostHeader);
		}
		return new ParsedHost(HostMode.TENANT, sub, hostHeader);
	}

	public static Set<String> reserved(String auth, String app, String extraCsv) {
		Set<String> s = new HashSet<>(Arrays.asList("www", "api", "admin", "static", "cdn", "status"));
		s.add(auth.toLowerCase(Locale.ROOT));
		s.add(app.toLowerCase(Locale.ROOT));
		if (StringUtils.hasText(extraCsv)) {
			for (String p : extraCsv.split(",")) {
				if (StringUtils.hasText(p.trim())) {
					s.add(p.trim().toLowerCase(Locale.ROOT));
				}
			}
		}
		return s;
	}

	public record ParsedHost(HostMode mode, String tenantHandle, String rawHost) {
	}
}
