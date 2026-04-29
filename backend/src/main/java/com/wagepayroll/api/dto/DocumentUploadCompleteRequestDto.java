package com.wagepayroll.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DocumentUploadCompleteRequestDto(@NotNull UUID documentId, @NotBlank @Size(max = 512) String storageKey,
		@NotBlank @Size(max = 255) String originalFilename, @NotBlank @Size(max = 128) String contentType, @Min(0) long sizeBytes) {
}
