package com.wagepayroll.payroll;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.api.dto.CompanyCalendarAdvanceResultDto;
import com.wagepayroll.api.dto.TenantPayPeriodFinalizeRequest;
import com.wagepayroll.api.dto.TenantPayPeriodFinalizeResultDto;
import com.wagepayroll.api.dto.TenantPayrollResultLineRowDto;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.org.TenantEmployeeRepository;
import com.wagepayroll.domain.org.TenantPayPeriodEntity;
import com.wagepayroll.domain.org.TenantPayPeriodRepository;
import com.wagepayroll.domain.org.TenantPayPeriodRunEntity;
import com.wagepayroll.domain.org.TenantPayPeriodRunRepository;
import com.wagepayroll.domain.wagecomponent.TenantPayrollResultLineEntity;
import com.wagepayroll.domain.wagecomponent.TenantPayrollResultLineRepository;
import com.wagepayroll.payroll.engine.PayrollContext;
import com.wagepayroll.payroll.engine.PayrollEngine;
import com.wagepayroll.payroll.engine.PayrollRunResult;
import com.wagepayroll.employeepayment.TenantEmployeePaymentService;
import com.wagepayroll.payperiod.TenantPayPeriodService;
import com.wagepayroll.payrollstanding.TenantPayrollPeriodInputService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantPayrollFinalizeService {

	private final TenantPayPeriodRepository payPeriodRepository;
	private final TenantPayPeriodRunRepository runRepository;
	private final TenantCompanyRepository companyRepository;
	private final TenantEmployeeRepository employeeRepository;
	private final TenantPayrollResultLineRepository resultLineRepository;
	private final PayrollEngine payrollEngine;
	private final TenantPayrollPeriodInputService payrollPeriodInputService;
	private final TenantPayPeriodService payPeriodService;
	private final TenantEmployeePaymentService employeePaymentService;
	private final AuditService auditService;

	public TenantPayrollFinalizeService(TenantPayPeriodRepository payPeriodRepository,
			TenantPayPeriodRunRepository runRepository, TenantCompanyRepository companyRepository,
			TenantEmployeeRepository employeeRepository, TenantPayrollResultLineRepository resultLineRepository,
			PayrollEngine payrollEngine, TenantPayrollPeriodInputService payrollPeriodInputService,
			TenantPayPeriodService payPeriodService, TenantEmployeePaymentService employeePaymentService,
			AuditService auditService) {
		this.payPeriodRepository = payPeriodRepository;
		this.runRepository = runRepository;
		this.companyRepository = companyRepository;
		this.employeeRepository = employeeRepository;
		this.resultLineRepository = resultLineRepository;
		this.payrollEngine = payrollEngine;
		this.payrollPeriodInputService = payrollPeriodInputService;
		this.payPeriodService = payPeriodService;
		this.employeePaymentService = employeePaymentService;
		this.auditService = auditService;
	}

	@Transactional
	public TenantPayPeriodFinalizeResultDto finalize(UUID tenantId, UUID payPeriodId, UUID runId,
			TenantPayPeriodFinalizeRequest request, UUID actorId, String correlationId) {
		TenantPayPeriodEntity period = payPeriodRepository.findByIdAndTenantId(payPeriodId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pay period not found"));
		TenantPayPeriodRunEntity run = runRepository.findByIdAndTenantId(runId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pay period run not found"));
		if (!run.getPayPeriodId().equals(payPeriodId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RUN_PAY_PERIOD_MISMATCH");
		}
		if (resultLineRepository.existsByTenantIdAndPayPeriodRunId(tenantId, runId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "RUN_ALREADY_FINALIZED");
		}
		TenantCompanyEntity company = companyRepository.findByIdAndTenantId(period.getCompanyId(), tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_COMPANY"));
		List<UUID> employeeIds = resolveEmployeeIds(tenantId, company.getId(), request);
		if (employeeIds.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "employeeIds is required");
		}
		if (request != null && Boolean.TRUE.equals(request.materializeInputs())) {
			payrollPeriodInputService.materializeForPayPeriod(tenantId, company.getId(), payPeriodId, actorId,
					correlationId);
		}
		String country = normalizeCountry(company.getPayrollCountry());
		String currency = normalizeCurrency(company.getCurrency());
		PayrollContext ctx = new PayrollContext(tenantId, company.getId(), country, currency, runId, payPeriodId,
				employeeIds, period.getEndDate());
		PayrollRunResult result = payrollEngine.calculate(ctx);
		int linesCreated = result.persistedResultLineCount();
		employeePaymentService.materializePaymentsForFinalize(tenantId, company.getId(), payPeriodId, runId,
				result.employeeNetPay());
		CompanyCalendarAdvanceResultDto calendarAdvance = payPeriodService.advanceCompanyCalendarAfterFinalize(
				tenantId, payPeriodId, run.getRunType());
		auditService.append(tenantId, actorId, AuditActionCodes.TENANT_PAYROLL_RUN_FINALIZED,
				AuditResourceTypes.TENANT_PAY_PERIOD_RUN, runId.toString(), correlationId,
				Map.of("payPeriodId", payPeriodId.toString(), "linesCreated", linesCreated, "employeeCount",
						employeeIds.size(), "balancesUpdated", result.balancesUpdated(), "postingsCreated",
						result.postingsCreated(), "calendarAdvanced", calendarAdvance.advanced()));
		return new TenantPayPeriodFinalizeResultDto(runId, linesCreated, employeeIds.size(), result.employeeNetPay(),
				result.balancesUpdated(), result.postingsCreated(), calendarAdvance);
	}

	@Transactional(readOnly = true)
	public List<TenantPayrollResultLineRowDto> listResultLines(UUID tenantId, UUID runId, UUID employeeId) {
		runRepository.findByIdAndTenantId(runId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pay period run not found"));
		List<TenantPayrollResultLineEntity> rows = employeeId != null
				? resultLineRepository.findByTenantIdAndPayPeriodRunIdAndEmployeeIdOrderByProcessingOrderSnapshotAsc(
						tenantId, runId, employeeId)
				: resultLineRepository.findByTenantIdAndPayPeriodRunIdOrderByEmployeeIdAscProcessingOrderSnapshotAsc(
						tenantId, runId);
		return rows.stream().map(this::toRowDto).toList();
	}

	private List<UUID> resolveEmployeeIds(UUID tenantId, UUID companyId, TenantPayPeriodFinalizeRequest request) {
		if (request != null && request.employeeIds() != null && !request.employeeIds().isEmpty()) {
			Set<UUID> unique = new LinkedHashSet<>();
			for (UUID id : request.employeeIds()) {
				if (id == null) {
					continue;
				}
				employeeRepository.findByIdAndTenantIdAndCompanyId(id, tenantId, companyId)
						.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_EMPLOYEE"));
				unique.add(id);
			}
			return List.copyOf(unique);
		}
		return employeeRepository.findActiveIdsByTenantIdAndCompanyId(tenantId, companyId);
	}

	private TenantPayrollResultLineRowDto toRowDto(TenantPayrollResultLineEntity e) {
		return new TenantPayrollResultLineRowDto(e.getId(), e.getPayPeriodRunId(), e.getEmployeeId(),
				e.getComponentSource().name(), e.getComponentRefId(), e.getPhase().name(),
				e.getProcessingOrderSnapshot(), e.getQuantity(), e.getRate(), e.getAmount(), e.getRoundedAmount(),
				e.getCreatedAt());
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
