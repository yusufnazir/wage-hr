package com.wagepayroll.security;

public enum PrivilegeGrant {

	DENIED,
	/** Granted via tenant pool + role membership only. */
	NORMAL,
	/** Granted because the actor is a platform superadmin and the privilege is registered globally (break-glass rules apply). */
	SUPERADMIN_ELEVATED
}
