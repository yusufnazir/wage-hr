package com.wagepayroll.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDocumentAttachmentRequestDto(
		@NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$", message = "entityType must match [A-Z][A-Z0-9_]{0,63}") String entityType,
		@NotNull UUID entityId) {
}
