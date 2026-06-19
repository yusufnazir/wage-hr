package com.wagepayroll.payroll;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.wagepayroll.api.dto.PayrollCalculationTraceLineDto;
import com.wagepayroll.domain.compensation.TenantEmployeeCompensationEntity;
import com.wagepayroll.domain.compensation.TenantEmployeeCompensationRepository;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantEmployeeEntity;
import com.wagepayroll.domain.org.TenantEmployeeRepository;
import com.wagepayroll.domain.org.TenantPayPeriodEntity;
import com.wagepayroll.payroll.engine.PayrollRunResult;
import com.wagepayroll.payroll.trace.PayrollCalculationTraceDocumentRenderer;
import com.wagepayroll.payroll.trace.PayrollCalculationTraceLine;

@Service
public class PayrollCalculationTraceExportService {

	private final TenantEmployeeRepository employeeRepository;
	private final TenantEmployeeCompensationRepository compensationRepository;

	public PayrollCalculationTraceExportService(TenantEmployeeRepository employeeRepository,
			TenantEmployeeCompensationRepository compensationRepository) {
		this.employeeRepository = employeeRepository;
		this.compensationRepository = compensationRepository;
	}

	public Map<UUID, String> renderDocuments(UUID tenantId, UUID companyId, TenantCompanyEntity company,
			TenantPayPeriodEntity period, PayrollRunResult result, List<UUID> employeeIds) {
		Map<UUID, TenantEmployeeEntity> employees = employeeRepository
				.findByTenantIdAndCompanyIdAndIdIn(tenantId, companyId, employeeIds).stream()
				.collect(Collectors.toMap(TenantEmployeeEntity::getId, e -> e, (a, b) -> a));
		Map<UUID, TenantEmployeeCompensationEntity> compensationByEmployee = compensationRepository
				.findByTenantIdAndEmployeeIdIn(tenantId, employeeIds).stream()
				.collect(Collectors.toMap(TenantEmployeeCompensationEntity::getEmployeeId, c -> c, (a, b) -> a));
		Map<UUID, String> out = new HashMap<>();
		for (UUID employeeId : employeeIds) {
			TenantEmployeeEntity employee = employees.get(employeeId);
			String label = employee != null ? employee.getFirstName() + " " + employee.getLastName() : employeeId.toString();
			if (employee != null && employee.getBadgeNumber() != null && !employee.getBadgeNumber().isBlank()) {
				label = "[" + employee.getBadgeNumber() + "] " + label;
			}
			List<PayrollCalculationTraceLine> lines = result.employeeCalculationTrace().getOrDefault(employeeId, List.of());
			var ctx = new PayrollCalculationTraceDocumentRenderer.RenderContext(
					company.getName(),
					company.getPayrollCountry(),
					company.getCurrency(),
					period.getYear(),
					period.getStartDate().getMonthValue(),
					period.getStartDate().toString(),
					period.getEndDate().toString(),
					employeeId,
					label,
					compensationByEmployee.get(employeeId),
					result.employeeBaseTotals().get(employeeId),
					result.employeeNetPay().get(employeeId),
					lines);
			out.put(employeeId, PayrollCalculationTraceDocumentRenderer.render(ctx));
		}
		return Map.copyOf(out);
	}

	public static PayrollCalculationTraceLineDto toDto(PayrollCalculationTraceLine line) {
		return new PayrollCalculationTraceLineDto(line.sequence(), line.enginePhase(), line.employeeId(),
				line.componentCode(), line.componentName(), line.componentSource(), line.componentType(),
				line.category(), line.netEffect(), line.payEffect(), line.taxationSummary(), line.calculationMethod(),
				line.countryRuleKey(), line.processingOrder(), line.factorQuantity(), line.factorRate(),
				line.factorExplanation(), line.amount(), line.amountExplanation(), line.formulaExpression(),
				line.includedInResult(), line.skipReason());
	}

	public static Map<UUID, List<PayrollCalculationTraceLineDto>> toDtoByEmployee(
			Map<UUID, List<PayrollCalculationTraceLine>> traceByEmployee) {
		if (traceByEmployee == null || traceByEmployee.isEmpty()) {
			return Map.of();
		}
		Map<UUID, List<PayrollCalculationTraceLineDto>> out = new HashMap<>();
		for (var entry : traceByEmployee.entrySet()) {
			out.put(entry.getKey(), entry.getValue().stream().map(PayrollCalculationTraceExportService::toDto).toList());
		}
		return Map.copyOf(out);
	}
}
