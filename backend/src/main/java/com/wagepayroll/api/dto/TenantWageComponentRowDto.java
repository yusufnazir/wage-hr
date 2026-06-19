package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TenantWageComponentRowDto(
		UUID id,
		UUID companyId,
		UUID platformTemplateId,
		String templateCode,
		String code,
		String name,
		String description,
		String componentType,
		String category,
		String netEffect,
		boolean taxableWageTax,
		boolean taxableSocialSecurity,
		boolean taxablePension,
		boolean taxableVacationReserve,
		String calculationMethod,
		String percentageBase,
		String formulaExpression,
		BigDecimal defaultAmount,
		String roundingStrategy,
		int processingOrder,
		String phase,
		boolean maintainsBalance,
		String balanceType,
		String balanceDirection,
		UUID counterComponentId,
		UUID debitTenantLedgerId,
		UUID creditTenantLedgerId,
		String postingStrategy,
		boolean printOnPayslip,
		boolean auxiliary,
		boolean applyInPayroll,
		String recurrence,
		String countryRuleKey,
		UUID platformCountryTaxRuleId,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
