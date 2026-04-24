package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

public record TenantSummaryDto(UUID id, String handle, String name, List<String> roles) {
}
