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

	USER_INVITE("USER_INVITE", "CREATE", "TENANT_INVITATION");

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
