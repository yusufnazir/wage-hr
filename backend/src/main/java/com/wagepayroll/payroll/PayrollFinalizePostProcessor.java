package com.wagepayroll.payroll;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.wagepayroll.domain.wagecomponent.TenantPayrollResultLineEntity;
import com.wagepayroll.domain.wagecomponent.TenantPayrollResultLineRepository;
import com.wagepayroll.payroll.engine.PayrollRunState;

/**
 * Post-finalize side effects: balance updates then ledger postings (after result lines are persisted).
 */
@Component
public class PayrollFinalizePostProcessor {

	private final TenantPayrollResultLineRepository resultLineRepository;
	private final TenantWageComponentBalanceService balanceService;
	private final PayrollLedgerPostingService ledgerPostingService;

	public PayrollFinalizePostProcessor(TenantPayrollResultLineRepository resultLineRepository,
			TenantWageComponentBalanceService balanceService, PayrollLedgerPostingService ledgerPostingService) {
		this.resultLineRepository = resultLineRepository;
		this.balanceService = balanceService;
		this.ledgerPostingService = ledgerPostingService;
	}

	public void afterPersist(PayrollRunState state) {
		UUID runId = state.context().payPeriodRunId();
		if (runId == null || state.persistedResultLineCount() <= 0) {
			return;
		}
		UUID tenantId = state.context().tenantId();
		List<TenantPayrollResultLineEntity> lines = resultLineRepository
				.findByTenantIdAndPayPeriodRunIdOrderByEmployeeIdAscProcessingOrderSnapshotAsc(tenantId, runId);
		int balancesUpdated = balanceService.applyBalancesForRun(tenantId, state.context().companyId(),
				state.context().currencyIso3(), runId, lines);
		int postingsCreated = ledgerPostingService.createPostingsForRun(tenantId, state.context().currencyIso3(), runId,
				lines);
		state.setBalancesUpdated(balancesUpdated);
		state.setPostingsCreated(postingsCreated);
	}
}
