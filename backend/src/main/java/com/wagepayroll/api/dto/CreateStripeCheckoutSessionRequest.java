package com.wagepayroll.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStripeCheckoutSessionRequest(@NotNull UUID commercialPlanId, @NotBlank @Size(max = 255) String priceId,
		@NotBlank @Size(max = 2048) String successUrl, @NotBlank @Size(max = 2048) String cancelUrl) {
}
