package com.wagepayroll.api.dto;

/**
 * Whitelist-only payload for {@code GET /api/v1/platform/public-surface}. No secrets, no raw settings map.
 */
public record PlatformPublicSurfaceDto(String applicationName, String publicBaseUrl, String dateFormat) {
}
