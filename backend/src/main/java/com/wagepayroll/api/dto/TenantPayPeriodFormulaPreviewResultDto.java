package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TenantPayPeriodFormulaPreviewResultDto(List<EvaluatedComponentAmountDto> items,
		Map<UUID, Map<String, BigDecimal>> employeeBaseTotals, Map<UUID, BigDecimal> employeeNetPay,
		/**
		 * Art. 17 lid 2e — aantal loontijdvakken (N) per employee for vacation/bonus wage tax (1021/1022).
		 * Empty when payroll country is not Suriname.
		 */
		Map<UUID, Integer> employeeArt17AttributionPeriods,
		Map<UUID, List<PayrollCalculationTraceLineDto>> employeeCalculationTraceLines,
		Map<UUID, String> employeeCalculationTraceText) {
}
