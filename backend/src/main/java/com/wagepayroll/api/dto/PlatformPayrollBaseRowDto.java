package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PlatformPayrollBaseRowDto(
		UUID id,
		String code,
		String name,
		String category,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
