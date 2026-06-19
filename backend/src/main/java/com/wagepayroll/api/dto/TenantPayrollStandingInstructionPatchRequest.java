package com.wagepayroll.api.dto;

import java.time.LocalDate;

public record TenantPayrollStandingInstructionPatchRequest(
		LocalDate effectiveTo,
		Boolean active,
		String remarks) {
}
