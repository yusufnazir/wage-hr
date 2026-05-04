package com.wagepayroll.audit;

public final class AuditActionCodes {

	public static final String USER_LOCALE_CHANGED = "USER_LOCALE_CHANGED";

	public static final String TENANT_SETTINGS_PATCHED = "TENANT_SETTINGS_PATCHED";

	public static final String PLATFORM_SETTINGS_PATCHED = "PLATFORM_SETTINGS_PATCHED";

	public static final String PLATFORM_MAIL_TEMPLATES_UPDATED = "PLATFORM_MAIL_TEMPLATES_UPDATED";

	/** Platform superadmin used tenant-scoped privilege outside normal role + pool (see {@code security.md}). */
	public static final String SUPERADMIN_TENANT_ELEVATED_ACCESS = "SUPERADMIN_TENANT_ELEVATED_ACCESS";

	/** Platform superadmin upserted {@code tenant_subscription} for a tenant. */
	public static final String TENANT_SUBSCRIPTION_UPSERTED = "TENANT_SUBSCRIPTION_UPSERTED";

	/** Stripe webhook handler reconciled {@code tenant_subscription} (M3 minimal auto-reconcile). */
	public static final String TENANT_SUBSCRIPTION_STRIPE_RECONCILED = "TENANT_SUBSCRIPTION_STRIPE_RECONCILED";

	/** PayPal webhook handler reconciled {@code tenant_subscription} (M3 minimal auto-reconcile). */
	public static final String TENANT_SUBSCRIPTION_PAYPAL_RECONCILED = "TENANT_SUBSCRIPTION_PAYPAL_RECONCILED";

	/** Platform superadmin upserted {@code billing_provider_link} for a tenant. */
	public static final String BILLING_PROVIDER_LINK_UPSERTED = "BILLING_PROVIDER_LINK_UPSERTED";

	/** Platform superadmin deleted an unused {@code commercial_plan}. */
	public static final String COMMERCIAL_PLAN_DELETED = "COMMERCIAL_PLAN_DELETED";

	/** Platform superadmin created a {@code tenant} row. */
	public static final String PLATFORM_TENANT_CREATED = "PLATFORM_TENANT_CREATED";

	/** Platform superadmin updated a {@code tenant} row (v1: display name only). */
	public static final String PLATFORM_TENANT_UPDATED = "PLATFORM_TENANT_UPDATED";

	/** Authenticated user downloaded {@code GET /api/v1/me/privacy/export}. */
	public static final String SUBJECT_DATA_EXPORTED = "SUBJECT_DATA_EXPORTED";

	/** Subject requested account erasure (M1: audit only; fulfillment pipeline TBD). */
	public static final String SUBJECT_ERASURE_REQUESTED = "SUBJECT_ERASURE_REQUESTED";

	/** Tenant admin updated another user's email (tenant-scoped directory). */
	public static final String TENANT_USER_EMAIL_UPDATED = "TENANT_USER_EMAIL_UPDATED";

	/** Tenant admin replaced role assignments for a tenant user. */
	public static final String TENANT_USER_ROLES_REPLACED = "TENANT_USER_ROLES_REPLACED";

	/** Tenant admin created a tenant-scoped role. */
	public static final String TENANT_ROLE_CREATED = "TENANT_ROLE_CREATED";

	/** Tenant admin updated a tenant-scoped role name and/or privileges. */
	public static final String TENANT_ROLE_UPDATED = "TENANT_ROLE_UPDATED";

	/** Platform superadmin created a platform currency catalog row. */
	public static final String PLATFORM_CURRENCY_CREATED = "PLATFORM_CURRENCY_CREATED";

	/** Platform superadmin updated a platform currency catalog row. */
	public static final String PLATFORM_CURRENCY_UPDATED = "PLATFORM_CURRENCY_UPDATED";

	/** Platform superadmin created a platform country catalog row. */
	public static final String PLATFORM_COUNTRY_CREATED = "PLATFORM_COUNTRY_CREATED";

	/** Platform superadmin updated a platform country catalog row. */
	public static final String PLATFORM_COUNTRY_UPDATED = "PLATFORM_COUNTRY_UPDATED";

	/** Platform superadmin activated a platform country catalog row. */
	public static final String PLATFORM_COUNTRY_ACTIVATED = "PLATFORM_COUNTRY_ACTIVATED";

	/** Platform superadmin deactivated a platform country catalog row. */
	public static final String PLATFORM_COUNTRY_DEACTIVATED = "PLATFORM_COUNTRY_DEACTIVATED";

	/** Tenant admin replaced tenant currency assignments from the platform catalog. */
	public static final String TENANT_CURRENCIES_REPLACED = "TENANT_CURRENCIES_REPLACED";

	/** Tenant admin created a tenant-scoped exchange rate. */
	public static final String EXCHANGE_RATE_CREATED = "EXCHANGE_RATE_CREATED";

	/** Tenant admin updated mutable fields of a tenant-scoped exchange rate. */
	public static final String EXCHANGE_RATE_UPDATED = "EXCHANGE_RATE_UPDATED";

	/** Tenant admin deleted a tenant-scoped exchange rate. */
	public static final String EXCHANGE_RATE_DELETED = "EXCHANGE_RATE_DELETED";

	public static final String PLATFORM_BANK_TEMPLATE_CREATED = "PLATFORM_BANK_TEMPLATE_CREATED";

	public static final String PLATFORM_BANK_TEMPLATE_UPDATED = "PLATFORM_BANK_TEMPLATE_UPDATED";

	public static final String PLATFORM_BANK_TEMPLATE_ACTIVATED = "PLATFORM_BANK_TEMPLATE_ACTIVATED";

	public static final String PLATFORM_BANK_TEMPLATE_DEACTIVATED = "PLATFORM_BANK_TEMPLATE_DEACTIVATED";

	public static final String TENANT_BANK_TEMPLATE_UPDATED = "TENANT_BANK_TEMPLATE_UPDATED";

	public static final String TENANT_BANK_TEMPLATE_CREATED = "TENANT_BANK_TEMPLATE_CREATED";

	public static final String TENANT_BANK_TEMPLATE_ACTIVATED = "TENANT_BANK_TEMPLATE_ACTIVATED";

	public static final String TENANT_BANK_TEMPLATE_DEACTIVATED = "TENANT_BANK_TEMPLATE_DEACTIVATED";

	public static final String TENANT_BANK_TEMPLATE_DELETED = "TENANT_BANK_TEMPLATE_DELETED";

	private AuditActionCodes() {
	}
}
