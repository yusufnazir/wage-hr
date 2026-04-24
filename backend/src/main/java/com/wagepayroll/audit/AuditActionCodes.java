package com.wagepayroll.audit;

public final class AuditActionCodes {

	public static final String USER_LOCALE_CHANGED = "USER_LOCALE_CHANGED";

	public static final String TENANT_SETTINGS_PATCHED = "TENANT_SETTINGS_PATCHED";

	public static final String PLATFORM_SETTINGS_PATCHED = "PLATFORM_SETTINGS_PATCHED";

	/** Platform superadmin used tenant-scoped privilege outside normal role + pool (see {@code security.md}). */
	public static final String SUPERADMIN_TENANT_ELEVATED_ACCESS = "SUPERADMIN_TENANT_ELEVATED_ACCESS";

	/** Platform superadmin replaced {@code tenant_privilege_allowance} for a tenant. */
	public static final String TENANT_PRIVILEGE_POOL_REPLACED = "TENANT_PRIVILEGE_POOL_REPLACED";

	/** Authenticated user downloaded {@code GET /api/v1/me/privacy/export}. */
	public static final String SUBJECT_DATA_EXPORTED = "SUBJECT_DATA_EXPORTED";

	/** Subject requested account erasure (M1: audit only; fulfillment pipeline TBD). */
	public static final String SUBJECT_ERASURE_REQUESTED = "SUBJECT_ERASURE_REQUESTED";

	private AuditActionCodes() {
	}
}
