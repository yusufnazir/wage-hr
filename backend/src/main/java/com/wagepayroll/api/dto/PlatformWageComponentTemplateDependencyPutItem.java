package com.wagepayroll.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record PlatformWageComponentTemplateDependencyPutItem(@NotNull UUID dependsOnTemplateId) {
}
