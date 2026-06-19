package com.wagepayroll.payroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wagepayroll.domain.wagecomponent.TenantPayrollResultLineEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentBalanceEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentBalanceRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentBalanceTransactionEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentBalanceTransactionRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.payroll.model.BalanceTransactionKind;
import com.wagepayroll.payroll.model.PayrollComponentSource;

@Service
public class TenantWageComponentBalanceService {

	private static final Logger log = LoggerFactory.getLogger(TenantWageComponentBalanceService.class);

	private final TenantWageComponentBalanceRepository balanceRepository;
	private final TenantWageComponentBalanceTransactionRepository transactionRepository;
	private final TenantWageComponentRepository wageComponentRepository;

	public TenantWageComponentBalanceService(TenantWageComponentBalanceRepository balanceRepository,
			TenantWageComponentBalanceTransactionRepository transactionRepository,
			TenantWageComponentRepository wageComponentRepository) {
		this.balanceRepository = balanceRepository;
		this.transactionRepository = transactionRepository;
		this.wageComponentRepository = wageComponentRepository;
	}

	@Transactional
	public int applyBalancesForRun(UUID tenantId, UUID companyId, String currencyCode, UUID payPeriodRunId,
			List<TenantPayrollResultLineEntity> resultLines) {
		if (resultLines.isEmpty()) {
			return 0;
		}
		if (transactionRepository.countByTenantIdAndPayPeriodRunId(tenantId, payPeriodRunId) > 0) {
			log.debug("Balance transactions already exist for run {}", payPeriodRunId);
			return 0;
		}
		Map<UUID, TenantWageComponentEntity> componentsById = loadTenantComponents(tenantId, resultLines);
		Instant now = Instant.now();
		int updated = 0;
		for (TenantPayrollResultLineEntity line : resultLines) {
			if (line.getComponentSource() != PayrollComponentSource.TENANT) {
				continue;
			}
			TenantWageComponentEntity comp = componentsById.get(line.getComponentRefId());
			if (comp == null || !comp.isMaintainsBalance()) {
				continue;
			}
			BigDecimal change = PayrollBalanceChangeCalculator.computeChangeAmount(line.getRoundedAmount(),
					comp.getBalanceDirection(), comp.getNetEffect());
			if (change.signum() == 0) {
				continue;
			}
			TenantWageComponentBalanceEntity balance = getOrCreate(tenantId, companyId, line.getEmployeeId(), comp,
					currencyCode, now);
			if (transactionRepository.existsByTenantIdAndBalanceIdAndPayPeriodRunId(tenantId, balance.getId(),
					payPeriodRunId)) {
				continue;
			}
			BigDecimal after = balance.getCurrentBalance().add(change);
			TenantWageComponentBalanceTransactionEntity tx = new TenantWageComponentBalanceTransactionEntity();
			tx.setId(UUID.randomUUID());
			tx.setTenantId(tenantId);
			tx.setBalanceId(balance.getId());
			tx.setChangeAmount(change);
			tx.setBalanceAfter(after);
			tx.setTransactionKind(BalanceTransactionKind.DEDUCTION);
			tx.setPayPeriodRunId(payPeriodRunId);
			tx.setRemarks("Payroll finalize");
			tx.setOccurredAt(now);
			tx.setCreatedAt(now);
			transactionRepository.save(tx);
			balance.setCurrentBalance(after);
			balance.setUpdatedAt(now);
			balanceRepository.save(balance);
			updated++;
		}
		return updated;
	}

	public TenantWageComponentBalanceEntity getOrCreate(UUID tenantId, UUID companyId, UUID employeeId,
			TenantWageComponentEntity component, String currencyCode, Instant now) {
		return balanceRepository
				.findByTenantIdAndCompanyIdAndEmployeeIdAndTenantWageComponentId(tenantId, companyId, employeeId,
						component.getId())
				.orElseGet(() -> {
					TenantWageComponentBalanceEntity created = new TenantWageComponentBalanceEntity();
					created.setId(UUID.randomUUID());
					created.setTenantId(tenantId);
					created.setCompanyId(companyId);
					created.setEmployeeId(employeeId);
					created.setTenantWageComponentId(component.getId());
					created.setCurrencyCode(currencyCode);
					created.setCurrentBalance(BigDecimal.ZERO);
					created.setUpdatedAt(now);
					return balanceRepository.save(created);
				});
	}

	private Map<UUID, TenantWageComponentEntity> loadTenantComponents(UUID tenantId,
			List<TenantPayrollResultLineEntity> lines) {
		List<UUID> ids = lines.stream().filter(l -> l.getComponentSource() == PayrollComponentSource.TENANT)
				.map(TenantPayrollResultLineEntity::getComponentRefId).distinct().toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		return wageComponentRepository.findAllById(ids).stream()
				.filter(c -> tenantId.equals(c.getTenantId()))
				.collect(Collectors.toMap(TenantWageComponentEntity::getId, Function.identity()));
	}
}
