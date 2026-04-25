package com.wagepayroll.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePaypalSubscriptionRequest(
		@NotNull UUID commercialPlanId,
		@NotBlank @Size(max = 128) String planId,
		@NotBlank @Size(max = 2048) String returnUrl,
		@NotBlank @Size(max = 2048) String cancelUrl) {
}
