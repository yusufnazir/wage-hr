package com.wagepayroll.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStripeBillingPortalSessionRequest(@NotBlank @Size(max = 2048) String returnUrl) {
}
