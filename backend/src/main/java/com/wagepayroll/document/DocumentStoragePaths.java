package com.wagepayroll.document;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Canonical object keys: {@code tenants/{tenantId}/documents/{documentId}/{safeFilename}}.
 */
public final class DocumentStoragePaths {

	private static final Pattern SAFE = Pattern.compile("[^a-zA-Z0-9._-]+");

	private DocumentStoragePaths() {
	}

	public static String sanitizeFilenameSegment(String originalFilename) {
		if (originalFilename == null || originalFilename.isBlank()) {
			return "upload";
		}
		String trimmed = originalFilename.trim();
		int slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
		String base = slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
		if (base.isBlank()) {
			return "upload";
		}
		if (base.contains("..")) {
			throw new IllegalArgumentException("invalid filename");
		}
		String sanitized = SAFE.matcher(base).replaceAll("_");
		if (sanitized.length() > 200) {
			sanitized = sanitized.substring(0, 200);
		}
		return sanitized.isBlank() ? "upload" : sanitized;
	}

	public static String buildObjectKey(UUID tenantId, UUID documentId, String originalFilename) {
		String safe = sanitizeFilenameSegment(originalFilename);
		return "tenants/" + tenantId + "/documents/" + documentId + "/" + safe;
	}
}
