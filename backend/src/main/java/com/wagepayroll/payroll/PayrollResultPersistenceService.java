package com.wagepayroll.payroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.wagepayroll.domain.wagecomponent.PlatformWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentRepository;
import com.wagepayroll.domain.wagecomponent.TenantPayrollResultLineEntity;
import com.wagepayroll.domain.wagecomponent.TenantPayrollResultLineRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionRepository;
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;
import com.wagepayroll.payroll.engine.EvaluatedComponentSource;
import com.wagepayroll.payroll.engine.PayrollRunResult;
import com.wagepayroll.payroll.engine.PayrollRunState;
import com.wagepayroll.payroll.model.PayrollComponentSource;
import com.wagepayroll.payroll.model.PayrollPhase;

@Service
public class PayrollResultPersistenceService {

	private final TenantPayrollResultLineRepository resultLineRepository;
	private final TenantWageComponentRepository tenantWageComponentRepository;
	private final PlatformWageComponentRepository platformWageComponentRepository;
	private final TenantWageComponentTransactionRepository transactionRepository;

	public PayrollResultPersistenceService(TenantPayrollResultLineRepository resultLineRepository,
			TenantWageComponentRepository tenantWageComponentRepository,
			PlatformWageComponentRepository platformWageComponentRepository,
			TenantWageComponentTransactionRepository transactionRepository) {
		this.resultLineRepository = resultLineRepository;
		this.tenantWageComponentRepository = tenantWageComponentRepository;
		this.platformWageComponentRepository = platformWageComponentRepository;
		this.transactionRepository = transactionRepository;
	}

	@Transactional
	public int persistRun(PayrollRunState state) {
		UUID runId = state.context().payPeriodRunId();
		if (runId == null) {
			return 0;
		}
		UUID tenantId = state.context().tenantId();
		if (resultLineRepository.existsByTenantIdAndPayPeriodRunId(tenantId, runId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "RUN_ALREADY_FINALIZED");
		}
		PayrollRunResult result = state.toResult();
		if (result.evaluatedComponentAmounts().isEmpty()) {
			return 0;
		}
		UUID companyId = state.context().companyId();
		UUID payPeriodId = state.context().payPeriodId();
		Map<String, TenantWageComponentTransactionEntity> txByKey = indexTransactions(tenantId, companyId, payPeriodId);
		Map<UUID, TenantWageComponentEntity> tenantById = loadTenantComponents(result.evaluatedComponentAmounts());
		Map<UUID, PlatformWageComponentEntity> platformById = loadPlatformComponents(result.evaluatedComponentAmounts());
		Instant now = Instant.now();
		List<TenantPayrollResultLineEntity> rows = new ArrayList<>();
		for (EvaluatedComponentAmount ev : result.evaluatedComponentAmounts()) {
			if (ev.evaluatedAmount() == null) {
				continue;
			}
			rows.add(toLine(state, ev, runId, companyId, txByKey, tenantById, platformById, now));
		}
		resultLineRepository.saveAll(rows);
		state.setPersistedResultLineCount(rows.size());
		return rows.size();
	}

	private Map<String, TenantWageComponentTransactionEntity> indexTransactions(UUID tenantId, UUID companyId,
			UUID payPeriodId) {
		if (payPeriodId == null) {
			return Map.of();
		}
		Map<String, TenantWageComponentTransactionEntity> out = new HashMap<>();
		for (TenantWageComponentTransactionEntity tx : transactionRepository
				.findByTenantIdAndCompanyIdAndPayPeriodId(tenantId, companyId, payPeriodId)) {
			out.putIfAbsent(txKey(tx.getEmployeeId(), tx.getTenantWageComponentId()), tx);
		}
		return out;
	}

	private Map<UUID, TenantWageComponentEntity> loadTenantComponents(List<EvaluatedComponentAmount> amounts) {
		List<UUID> ids = amounts.stream().filter(a -> a.componentSource() == EvaluatedComponentSource.TENANT)
				.map(EvaluatedComponentAmount::tenantWageComponentId).filter(id -> id != null).distinct().toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		return tenantWageComponentRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(TenantWageComponentEntity::getId, Function.identity()));
	}

	private Map<UUID, PlatformWageComponentEntity> loadPlatformComponents(List<EvaluatedComponentAmount> amounts) {
		List<UUID> ids = amounts.stream().filter(a -> a.componentSource() == EvaluatedComponentSource.PLATFORM)
				.map(EvaluatedComponentAmount::platformWageComponentId).filter(id -> id != null).distinct().toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		return platformWageComponentRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(PlatformWageComponentEntity::getId, Function.identity()));
	}

	private TenantPayrollResultLineEntity toLine(PayrollRunState state, EvaluatedComponentAmount ev, UUID runId,
			UUID companyId, Map<String, TenantWageComponentTransactionEntity> txByKey,
			Map<UUID, TenantWageComponentEntity> tenantById, Map<UUID, PlatformWageComponentEntity> platformById,
			Instant now) {
		PayrollPhase phase;
		int processingOrder;
		UUID componentRefId;
		PayrollComponentSource source;
		if (ev.componentSource() == EvaluatedComponentSource.PLATFORM) {
			if (ev.platformWageComponentId() == null) {
				throw new IllegalStateException("platform component line missing platformWageComponentId");
			}
			PlatformWageComponentEntity comp = platformById.get(ev.platformWageComponentId());
			if (comp == null) {
				throw new IllegalStateException("unknown platform wage component: " + ev.platformWageComponentId());
			}
			phase = comp.getPhase();
			processingOrder = comp.getProcessingOrder();
			componentRefId = ev.platformWageComponentId();
			source = PayrollComponentSource.PLATFORM;
		}
		else {
			if (ev.tenantWageComponentId() == null) {
				throw new IllegalStateException("tenant component line missing tenantWageComponentId");
			}
			TenantWageComponentEntity comp = tenantById.get(ev.tenantWageComponentId());
			if (comp == null) {
				throw new IllegalStateException("unknown tenant wage component: " + ev.tenantWageComponentId());
			}
			phase = comp.getPhase();
			processingOrder = comp.getProcessingOrder();
			componentRefId = ev.tenantWageComponentId();
			source = PayrollComponentSource.TENANT;
		}
		BigDecimal rounded = ev.evaluatedAmount();
		TenantPayrollResultLineEntity line = new TenantPayrollResultLineEntity();
		line.setId(UUID.randomUUID());
		line.setTenantId(state.context().tenantId());
		line.setCompanyId(companyId);
		line.setPayPeriodRunId(runId);
		line.setEmployeeId(ev.employeeId());
		line.setComponentSource(source);
		line.setComponentRefId(componentRefId);
		line.setPhase(phase);
		line.setProcessingOrderSnapshot(processingOrder);
		TenantWageComponentTransactionEntity tx = ev.tenantWageComponentId() != null
				? txByKey.get(txKey(ev.employeeId(), ev.tenantWageComponentId()))
				: null;
		if (tx != null) {
			line.setQuantity(tx.getQuantity());
			line.setRate(tx.getRate());
		}
		line.setAmount(rounded);
		line.setRoundedAmount(rounded);
		line.setCreatedAt(now);
		return line;
	}

	private static String txKey(UUID employeeId, UUID componentId) {
		return employeeId + ":" + componentId;
	}
}
