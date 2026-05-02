package com.wagepayroll.api.dto;

import java.util.UUID;

public record TenantPayPeriodRunCreateRequest(UUID payPeriodId, String runType) {
}
