package com.wagepayroll.tenant;

public enum HostMode {
	AUTH,
	APP,
	API,
	/** Platform operator workspace: {@code admin.{baseDomain}} — no tenant from host; lens via {@code X-Tenant-Id}. */
	ADMIN,
	TENANT,
	UNKNOWN
}
