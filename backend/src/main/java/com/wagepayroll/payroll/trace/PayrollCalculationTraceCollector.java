package com.wagepayroll.payroll.trace;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.payroll.model.NetEffect;

/**
 * Collects human-readable trace lines during a payroll engine pass.
 */
public final class PayrollCalculationTraceCollector {

	private final List<PayrollCalculationTraceLine> lines = new ArrayList<>();

	private int nextSequence = 1;

	public void addTenantComponent(String enginePhase, UUID employeeId, TenantWageComponentEntity comp,
			BigDecimal factorQuantity, BigDecimal factorRate, String factorExplanation, BigDecimal amount,
			String amountExplanation, String resolvedFormulaExpression, boolean includedInResult, String skipReason) {
		lines.add(new PayrollCalculationTraceLine(nextSequence++, enginePhase, employeeId, comp.getCode(), comp.getName(),
				"TENANT", comp.getComponentType().name(), comp.getCategory(), comp.getNetEffect().name(),
				PayrollCalculationTraceSupport.payEffectLabel(comp.getNetEffect()),
				PayrollCalculationTraceSupport.taxationSummary(comp), comp.getCalculationMethod().name(),
				comp.getCountryRuleKey(), comp.getProcessingOrder(), factorQuantity, factorRate, factorExplanation, amount,
				amountExplanation, resolvedFormulaExpression, includedInResult, skipReason));
	}

	public void addPlatformStatutory(String enginePhase, UUID employeeId, String platformCode, String displayName,
			int processingOrderHint, NetEffect netEffect, String category, String calculationMethod,
			BigDecimal amount, String factorExplanation, String amountExplanation, boolean includedInResult,
			String skipReason) {
		lines.add(new PayrollCalculationTraceLine(nextSequence++, enginePhase, employeeId, platformCode, displayName,
				"PLATFORM", "DEDUCTION", category, netEffect.name(),
				PayrollCalculationTraceSupport.payEffectLabel(netEffect),
				"Not part of taxable wage base", calculationMethod, null, processingOrderHint, null, null,
				factorExplanation, amount, amountExplanation, null, includedInResult, skipReason));
	}

	public void addSummary(String enginePhase, UUID employeeId, String title, String amountExplanation,
			BigDecimal amount) {
		lines.add(new PayrollCalculationTraceLine(nextSequence++, enginePhase, employeeId, "—", title, "SUMMARY", "—", "—",
				"NO_EFFECT", "Information", "—", "SUMMARY", null, null, null, null, null, amount, amountExplanation, null,
				true, null));
	}

	public List<PayrollCalculationTraceLine> lines() {
		return List.copyOf(lines);
	}

	public Map<UUID, List<PayrollCalculationTraceLine>> linesByEmployee() {
		Map<UUID, List<PayrollCalculationTraceLine>> out = new LinkedHashMap<>();
		for (PayrollCalculationTraceLine line : lines) {
			out.computeIfAbsent(line.employeeId(), ignored -> new ArrayList<>()).add(line);
		}
		for (List<PayrollCalculationTraceLine> employeeLines : out.values()) {
			employeeLines.sort((a, b) -> Integer.compare(a.sequence(), b.sequence()));
		}
		return Map.copyOf(out);
	}
}
