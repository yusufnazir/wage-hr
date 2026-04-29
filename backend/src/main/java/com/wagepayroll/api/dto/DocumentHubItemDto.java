package com.wagepayroll.api.dto;

import java.time.Instant;

public record DocumentHubItemDto(String id, String originalFilename, String contentType, long sizeBytes, Instant createdAt,
		String hubSource) {
}
