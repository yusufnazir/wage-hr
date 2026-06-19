package com.wagepayroll.api.dto;

public record TenantMaterializePayrollInputsResultDto(
		int created,
		int updated,
		int skippedManualOverride,
		int skippedInactiveEmployee,
		int skippedInactiveInstruction,
		int skippedInactiveWageComponent) {
}
