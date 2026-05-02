package com.wagepayroll.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * Application catalog: every {@code privilege.code} shipped in Liquibase must have a matching entry
 * (action + resource) for documentation and platform catalog responses.
 */
public enum DefinedPrivilege {

	USER_VIEW("USER_VIEW", "VIEW", "USER"),
	USER_EDIT("USER_EDIT", "EDIT", "USER"),
	TENANT_SETTINGS_EDIT("TENANT_SETTINGS_EDIT", "EDIT", "TENANT_SETTINGS"),

	USER_INVITE("USER_INVITE", "CREATE", "TENANT_INVITATION"),

	DOCUMENT_VIEW("DOCUMENT_VIEW", "VIEW", "DOCUMENT"),
	DOCUMENT_EDIT("DOCUMENT_EDIT", "EDIT", "DOCUMENT"),

	ROLE_VIEW("ROLE_VIEW", "VIEW", "ROLE"),
	ROLE_EDIT("ROLE_EDIT", "EDIT", "ROLE"),

	PLATFORM_CURRENCY_VIEW("PLATFORM_CURRENCY_VIEW", "VIEW", "PLATFORM_CURRENCY"),
	PLATFORM_CURRENCY_EDIT("PLATFORM_CURRENCY_EDIT", "EDIT", "PLATFORM_CURRENCY"),
	TENANT_CURRENCY_VIEW("TENANT_CURRENCY_VIEW", "VIEW", "TENANT_CURRENCY"),
	TENANT_CURRENCY_EDIT("TENANT_CURRENCY_EDIT", "EDIT", "TENANT_CURRENCY"),
	EXCHANGE_RATE_VIEW("EXCHANGE_RATE_VIEW", "VIEW", "EXCHANGE_RATE"),
	EXCHANGE_RATE_MANAGE("EXCHANGE_RATE_MANAGE", "EDIT", "EXCHANGE_RATE"),
	COMPANY_VIEW("COMPANY_VIEW", "VIEW", "COMPANY"),
	COMPANY_MANAGE("COMPANY_MANAGE", "EDIT", "COMPANY"),
	DEPARTMENT_VIEW("DEPARTMENT_VIEW", "VIEW", "DEPARTMENT"),
	DEPARTMENT_MANAGE("DEPARTMENT_MANAGE", "EDIT", "DEPARTMENT"),
	JOB_VIEW("JOB_VIEW", "VIEW", "JOB"),
	JOB_MANAGE("JOB_MANAGE", "EDIT", "JOB"),
	EMPLOYEE_GROUP_VIEW("EMPLOYEE_GROUP_VIEW", "VIEW", "EMPLOYEE_GROUP"),
	EMPLOYEE_GROUP_MANAGE("EMPLOYEE_GROUP_MANAGE", "EDIT", "EMPLOYEE_GROUP"),
	EMPLOYEE_VIEW("EMPLOYEE_VIEW", "VIEW", "EMPLOYEE"),
	EMPLOYEE_MANAGE("EMPLOYEE_MANAGE", "EDIT", "EMPLOYEE");

	private final String code;
	private final String action;
	private final String resource;

	DefinedPrivilege(String code, String action, String resource) {
		this.code = code;
		this.action = action;
		this.resource = resource;
	}

	public String code() {
		return code;
	}

	public String action() {
		return action;
	}

	public String resource() {
		return resource;
	}

	public static Optional<DefinedPrivilege> forCode(String privilegeCode) {
		return Arrays.stream(values()).filter(d -> d.code.equals(privilegeCode)).findFirst();
	}
}
