package com.wagepayroll.payroll;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.wagepayroll.api.dto.EvaluatedComponentAmountDto;
import com.wagepayroll.api.dto.TenantPayPeriodFormulaPreviewResultDto;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.org.TenantPayPeriodEntity;
import com.wagepayroll.domain.org.TenantPayPeriodRepository;
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;
import com.wagepayroll.payroll.engine.PayrollContext;
import com.wagepayroll.payroll.engine.PayrollEngine;
import com.wagepayroll.payroll.country.SurinameSpecialRemunerationSupport;
import com.wagepayroll.payroll.engine.PayrollRunResult;
import com.wagepayroll.payroll.PayrollCalculationTraceExportService;
import com.wagepayroll.payrollstanding.TenantPayrollPeriodInputService;
import com.wagepayroll.security.PermissionService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantPayrollFormulaPreviewService {

	private final TenantPayPeriodRepository payPeriodRepository;
	private final TenantCompanyRepository companyRepository;
	private final PayrollEngine payrollEngine;
	private final TenantPayrollPeriodInputService payrollPeriodInputService;
	private final PermissionService permissionService;
	private final PayrollCalculationTraceExportService traceExportService;

	public TenantPayrollFormulaPreviewService(TenantPayPeriodRepository payPeriodRepository,
			TenantCompanyRepository companyRepository, PayrollEngine payrollEngine,
			TenantPayrollPeriodInputService payrollPeriodInputService, PermissionService permissionService,
			PayrollCalculationTraceExportService traceExportService) {
		this.payPeriodRepository = payPeriodRepository;
		this.companyRepository = companyRepository;
		this.payrollEngine = payrollEngine;
		this.payrollPeriodInputService = payrollPeriodInputService;
		this.permissionService = permissionService;
		this.traceExportService = traceExportService;
	}

	/**
	 * Not {@code @Transactional}: the engine materializes inputs in {@code REQUIRES_NEW}. A long outer transaction
	 * would use a repeatable-read snapshot from before that commit, so persist would not see materialized rows and
	 * could hit duplicate-key errors on insert.
	 */
	public TenantPayPeriodFormulaPreviewResultDto preview(UUID tenantId, UUID payPeriodId, List<UUID> employeeIds,
			boolean persistToPeriodInputs, UUID actorUserId, String correlationId) {
		if (employeeIds == null || employeeIds.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "employeeIds is required");
		}
		TenantPayPeriodEntity period = payPeriodRepository.findByIdAndTenantId(payPeriodId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		TenantCompanyEntity company = companyRepository.findByIdAndTenantId(period.getCompanyId(), tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_COMPANY"));
		String country = normalizeCountry(company.getPayrollCountry());
		if (country.length() != 2) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PAYROLL_COUNTRY");
		}
		String currency = normalizeCurrency(company.getCurrency());
		LocalDate countryRulesAsOf = period.getEndDate();
		PayrollContext ctx = new PayrollContext(tenantId, period.getCompanyId(), country, currency, null, payPeriodId,
				employeeIds, countryRulesAsOf);
		PayrollRunResult result = payrollEngine.calculate(ctx);
		if (persistToPeriodInputs) {
			if (actorUserId == null || !permissionService.hasPrivilege(actorUserId, tenantId,
					"EMPLOYEE_PAYROLL_STANDING_MANAGE")) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PERSIST_PREVIEW_FORBIDDEN");
			}
			payrollPeriodInputService.applyFormulaPreviewToPeriodTransactions(tenantId, period.getCompanyId(),
					payPeriodId, result.evaluatedComponentAmounts(), actorUserId, correlationId);
		}
		List<EvaluatedComponentAmountDto> items = result.evaluatedComponentAmounts().stream()
				.map(TenantPayrollFormulaPreviewService::toDto)
				.toList();
		Map<UUID, String> traceText = traceExportService.renderDocuments(tenantId, period.getCompanyId(), company,
				period, result, employeeIds);
		return new TenantPayPeriodFormulaPreviewResultDto(items, result.employeeBaseTotals(), result.employeeNetPay(),
				art17AttributionPeriods(country, employeeIds),
				PayrollCalculationTraceExportService.toDtoByEmployee(result.employeeCalculationTrace()), traceText);
	}

	public String renderTraceDownload(UUID tenantId, UUID payPeriodId, UUID employeeId) {
		var preview = preview(tenantId, payPeriodId, List.of(employeeId), false, null, null);
		return preview.employeeCalculationTraceText().getOrDefault(employeeId, "");
	}

	private static Map<UUID, Integer> art17AttributionPeriods(String payrollCountryIso2, List<UUID> employeeIds) {
		if (!"SR".equalsIgnoreCase(payrollCountryIso2)) {
			return Map.of();
		}
		int periods = SurinameSpecialRemunerationSupport.DEFAULT_ATTRIBUTION_PERIODS;
		Map<UUID, Integer> out = new HashMap<>();
		for (UUID employeeId : employeeIds) {
			out.put(employeeId, periods);
		}
		return Map.copyOf(out);
	}

	private static EvaluatedComponentAmountDto toDto(EvaluatedComponentAmount e) {
		return new EvaluatedComponentAmountDto(e.employeeId(), e.tenantWageComponentId(), e.tenantWageComponentCode(),
				e.calculationMethod(), e.evaluatedAmount(), e.formulaExpression(),
				e.componentSource() != null ? e.componentSource().name() : null, e.platformWageComponentId());
	}

	private static String normalizeCountry(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		return raw.trim().toUpperCase(Locale.ROOT);
	}

	private static String normalizeCurrency(String raw) {
		if (raw == null || raw.isBlank()) {
			return "XXX";
		}
		String t = raw.trim().toUpperCase(Locale.ROOT);
		return t.length() == 3 ? t : "XXX";
	}
}
