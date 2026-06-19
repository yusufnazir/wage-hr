package com.wagepayroll.api.dto;

import java.time.LocalDate;

public record TenantPayPeriodGenerateRequest(LocalDate fromDate, Integer yearsAhead) {
}
