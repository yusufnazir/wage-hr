package com.wagepayroll.api.dto;

import java.util.UUID;

public record PlatformWageComponentTemplateDependencyRowDto(UUID id, UUID dependsOnTemplateId, String dependsOnTemplateCode,
		String dependsOnTemplateName) {
}
