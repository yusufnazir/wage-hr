package com.wagepayroll.security;

public final class BreakGlassHeaders {

	public static final String REASON = "X-Break-Glass-Reason";

	public static final int REASON_MIN_LEN = 3;

	public static final int REASON_MAX_LEN = 500;

	private BreakGlassHeaders() {
	}
}
