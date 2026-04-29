package com.wagepayroll.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentUploadSessionRequestDto(@NotBlank @Size(max = 255) String originalFilename,
		@NotBlank @Size(max = 128) String contentType, @Min(0) long sizeBytes) {
}
