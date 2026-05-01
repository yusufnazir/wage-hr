package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

public record MailTemplateDetailDto(UUID id, String code, String contentVersion, boolean active, String updatedAt,
		List<MailTemplateLocaleResponseDto> locales) {
}
