package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

public record TenantPayPeriodFormulaPreviewRequest(@NotEmpty List<UUID> employeeIds,
		Boolean persistToPeriodInputs) {
}
