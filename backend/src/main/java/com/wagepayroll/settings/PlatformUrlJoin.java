package com.wagepayroll.settings;

/**
 * Canonical join for {@code platform.base_url} + absolute paths (see {@code docs/modules/platform-settings.md}):
 * strip trailing slashes from the base, then append an {@code absolutePath} that starts with {@code /}.
 */
public final class PlatformUrlJoin {

	private PlatformUrlJoin() {
	}

	public static String joinPublicBaseAndPath(String base, String absolutePath) {
		if (absolutePath == null || absolutePath.isBlank()) {
			throw new IllegalArgumentException("absolutePath required");
		}
		if (!absolutePath.startsWith("/")) {
			throw new IllegalArgumentException("absolutePath must start with /");
		}
		String b = base == null ? "" : base.trim();
		while (b.endsWith("/")) {
			b = b.substring(0, b.length() - 1);
		}
		return b + absolutePath;
	}
}
