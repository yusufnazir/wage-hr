package com.wagepayroll.api.dto;

import java.util.UUID;

public record BillingProviderLinkDto(UUID tenantId, String provider, String externalCustomerId) {
}
