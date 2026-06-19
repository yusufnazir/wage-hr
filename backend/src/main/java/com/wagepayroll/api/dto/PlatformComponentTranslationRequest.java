package com.wagepayroll.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlatformComponentTranslationRequest(
		@NotBlank @Size(max = 35) String locale,
		@NotBlank @Size(max = 200) String name,
		@Size(max = 500) String description) {
}
