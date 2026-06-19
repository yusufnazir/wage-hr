package com.wagepayroll.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Tenant wage components are template-based: presentation, code suffix, ledgers,
 * payslip visibility, active flag, and formula expression (when calculation method is FORMULA) are tenant-editable.
 */
public record TenantWageComponentPutRequest(
		@NotNull UUID companyId,
		@NotBlank String name,
		String codeSuffix,
		UUID debitTenantLedgerId,
		UUID creditTenantLedgerId,
		boolean printOnPayslip,
		boolean active,
		String formulaExpression) {
}
