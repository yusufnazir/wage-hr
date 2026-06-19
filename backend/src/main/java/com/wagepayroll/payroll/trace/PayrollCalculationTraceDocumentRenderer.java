package com.wagepayroll.payroll.trace;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wagepayroll.domain.compensation.TenantEmployeeCompensationEntity;

/**
 * Builds plain-text payroll calculation logs for end users.
 */
public final class PayrollCalculationTraceDocumentRenderer {

	private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss' UTC'")
			.withZone(ZoneOffset.UTC);

	private PayrollCalculationTraceDocumentRenderer() {
	}

	public record RenderContext(
			String companyName,
			String payrollCountry,
			String currency,
			int periodYear,
			int periodNumber,
			String periodStart,
			String periodEnd,
			UUID employeeId,
			String employeeLabel,
			TenantEmployeeCompensationEntity compensation,
			Map<String, BigDecimal> baseTotals,
			BigDecimal netPay,
			List<PayrollCalculationTraceLine> lines) {
	}

	public static String render(RenderContext ctx) {
		StringBuilder sb = new StringBuilder(8192);
		sb.append("PAYROLL CALCULATION LOG\n");
		sb.append("=======================\n\n");
		sb.append("Generated: ").append(GENERATED_AT.format(Instant.now())).append('\n');
		sb.append("Company: ").append(nullToDash(ctx.companyName())).append('\n');
		sb.append("Payroll country: ").append(nullToDash(ctx.payrollCountry())).append('\n');
		sb.append("Currency: ").append(nullToDash(ctx.currency())).append('\n');
		sb.append("Pay period: ").append(ctx.periodYear()).append(" / ").append(ctx.periodNumber()).append(" (")
				.append(ctx.periodStart()).append(" – ").append(ctx.periodEnd()).append(")\n\n");

		sb.append("EMPLOYEE\n");
		sb.append("--------\n");
		sb.append("Name: ").append(nullToDash(ctx.employeeLabel())).append('\n');
		sb.append("Id: ").append(ctx.employeeId()).append('\n');
		if (ctx.compensation() != null) {
			var c = ctx.compensation();
			sb.append("Compensation rate: ").append(PayrollCalculationTraceSupport.formatMoney(c.getWageAmount()))
					.append(" (").append(c.getWageType()).append(")\n");
			sb.append("Apply taxes: ").append(c.isApplyTaxes() ? "yes" : "no").append('\n');
			sb.append("Apply tax exempt (belastingvrij): ").append(c.isApplyTaxExempt() ? "yes" : "no").append('\n');
			sb.append("Apply AOV: ").append(c.isApplyAov() ? "yes" : "no").append('\n');
		}
		sb.append('\n');

		sb.append("COMPONENTS (processing order)\n");
		sb.append("-----------------------------\n");
		if (ctx.lines() == null || ctx.lines().isEmpty()) {
			sb.append("No trace lines recorded.\n");
		}
		else {
			for (PayrollCalculationTraceLine line : ctx.lines()) {
				appendLine(sb, line);
			}
		}

		sb.append("\nPAYROLL BASES (after calculation)\n");
		sb.append("---------------------------------\n");
		if (ctx.baseTotals() == null || ctx.baseTotals().isEmpty()) {
			sb.append("—\n");
		}
		else {
			ctx.baseTotals().entrySet().stream().sorted(Map.Entry.comparingByKey())
					.forEach(e -> sb.append("  ").append(e.getKey()).append(": ")
							.append(PayrollCalculationTraceSupport.formatMoney(e.getValue())).append('\n'));
		}

		sb.append("\nNET PAY\n");
		sb.append("-------\n");
		sb.append("  ").append(PayrollCalculationTraceSupport.formatMoney(ctx.netPay())).append('\n');

		sb.append("\n--- End of log ---\n");
		return sb.toString();
	}

	private static void appendLine(StringBuilder sb, PayrollCalculationTraceLine line) {
		sb.append('\n');
		sb.append('#').append(line.sequence()).append(' ');
		sb.append('[').append(line.componentCode()).append("] ");
		sb.append(line.componentName()).append('\n');
		sb.append("  Phase: ").append(line.enginePhase()).append('\n');
		if (line.processingOrder() != null) {
			sb.append("  Processing order: ").append(line.processingOrder()).append('\n');
		}
		sb.append("  Source: ").append(line.componentSource()).append('\n');
		sb.append("  Type: ").append(line.componentType()).append(" / ").append(line.category()).append('\n');
		sb.append("  Pay effect: ").append(line.payEffect()).append('\n');
		sb.append("  Taxation: ").append(line.taxationSummary()).append('\n');
		sb.append("  Method: ").append(nullToDash(line.calculationMethod())).append('\n');
		if (line.countryRuleKey() != null && !line.countryRuleKey().isBlank()) {
			sb.append("  Country rule: ").append(line.countryRuleKey()).append('\n');
		}
		sb.append("  Factor: ").append(PayrollCalculationTraceSupport.formatFactor(line.factorQuantity(), line.factorRate()))
				.append('\n');
		if (line.factorExplanation() != null && !line.factorExplanation().isBlank()) {
			sb.append("  How factor is decided: ").append(line.factorExplanation()).append('\n');
		}
		sb.append("  Amount: ").append(PayrollCalculationTraceSupport.formatMoney(line.amount())).append('\n');
		if (line.amountExplanation() != null && !line.amountExplanation().isBlank()) {
			sb.append("  How amount is decided: ").append(line.amountExplanation()).append('\n');
		}
		if (line.formulaExpression() != null && !line.formulaExpression().isBlank()) {
			sb.append("  Formula config: ").append(line.formulaExpression()).append('\n');
		}
		if (!line.includedInResult()) {
			sb.append("  Result: omitted from payslip");
			if (line.skipReason() != null && !line.skipReason().isBlank()) {
				sb.append(" — ").append(line.skipReason());
			}
			sb.append('\n');
		}
	}

	private static String nullToDash(String value) {
		return value == null || value.isBlank() ? "—" : value;
	}
}
