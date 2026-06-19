package com.wagepayroll.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wagepayroll.domain.org.TenantPayPeriodEntity;
import com.wagepayroll.domain.org.TenantPayPeriodRepository;
import com.wagepayroll.domain.payroll.TenantPayrollYtdAccumulatorEntity;
import com.wagepayroll.domain.payroll.TenantPayrollYtdAccumulatorRepository;
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;
import com.wagepayroll.payroll.engine.PayrollRunState;

@Service
public class PayrollYtdAccumulatorService {

	public static final String WAGE_TAX_PAID = "WAGE_TAX_PAID";

	private final TenantPayPeriodRepository payPeriodRepository;
	private final TenantPayrollYtdAccumulatorRepository ytdRepository;

	public PayrollYtdAccumulatorService(TenantPayPeriodRepository payPeriodRepository,
			TenantPayrollYtdAccumulatorRepository ytdRepository) {
		this.payPeriodRepository = payPeriodRepository;
		this.ytdRepository = ytdRepository;
	}

	@Transactional
	public void applyPeriodDeltas(PayrollRunState state) {
		var context = state.context();
		if (context.payPeriodRunId() == null || context.payPeriodId() == null) {
			return;
		}
		TenantPayPeriodEntity period = payPeriodRepository.findByIdAndTenantId(context.payPeriodId(), context.tenantId())
				.orElse(null);
		if (period == null) {
			return;
		}
		int taxYear = period.getYear();
		String currency = context.currencyIso3();
		Instant now = Instant.now();
		for (UUID employeeId : context.employeeIds()) {
			Map<String, BigDecimal> bases = state.employeeBaseTotals().get(employeeId);
			if (bases != null) {
				for (Map.Entry<String, BigDecimal> entry : bases.entrySet()) {
					if (entry.getValue() != null && entry.getValue().signum() != 0) {
						upsertIncrement(context.tenantId(), context.companyId(), employeeId, taxYear, entry.getKey(),
								entry.getValue(), currency, now);
					}
				}
			}
			for (EvaluatedComponentAmount line : state.statutoryEvaluatedAmounts()) {
				if (!employeeId.equals(line.employeeId()) || line.evaluatedAmount() == null) {
					continue;
				}
				if ("WAGE_TAX".equals(line.tenantWageComponentCode())) {
					upsertIncrement(context.tenantId(), context.companyId(), employeeId, taxYear, WAGE_TAX_PAID,
							line.evaluatedAmount(), currency, now);
				}
			}
		}
	}

	private void upsertIncrement(UUID tenantId, UUID companyId, UUID employeeId, int taxYear, String code,
			BigDecimal delta, String currency, Instant now) {
		BigDecimal increment = delta.setScale(4, RoundingMode.HALF_UP);
		TenantPayrollYtdAccumulatorEntity row = ytdRepository
				.findByTenantIdAndEmployeeIdAndTaxYearAndAccumulatorCode(tenantId, employeeId, taxYear, code)
				.orElseGet(() -> {
					TenantPayrollYtdAccumulatorEntity created = new TenantPayrollYtdAccumulatorEntity();
					created.setId(UUID.randomUUID());
					created.setTenantId(tenantId);
					created.setCompanyId(companyId);
					created.setEmployeeId(employeeId);
					created.setTaxYear(taxYear);
					created.setAccumulatorCode(code);
					created.setAmount(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
					created.setCurrencyIso3(currency);
					return created;
				});
		row.setAmount(row.getAmount().add(increment).setScale(4, RoundingMode.HALF_UP));
		row.setCurrencyIso3(currency);
		row.setUpdatedAt(now);
		ytdRepository.save(row);
	}
}
