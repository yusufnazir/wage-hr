package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

public record TenantPayPeriodFinalizeRequest(List<UUID> employeeIds, Boolean materializeInputs) {
}
