package com.wagepayroll.payroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wagepayroll.domain.wagecomponent.TenantPayrollLedgerPostingEntity;
import com.wagepayroll.domain.wagecomponent.TenantPayrollLedgerPostingRepository;
import com.wagepayroll.domain.wagecomponent.TenantPayrollResultLineEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.payroll.model.PayrollComponentSource;

@Service
public class PayrollLedgerPostingService {

	private static final Logger log = LoggerFactory.getLogger(PayrollLedgerPostingService.class);

	private final TenantPayrollLedgerPostingRepository postingRepository;
	private final TenantWageComponentRepository wageComponentRepository;

	public PayrollLedgerPostingService(TenantPayrollLedgerPostingRepository postingRepository,
			TenantWageComponentRepository wageComponentRepository) {
		this.postingRepository = postingRepository;
		this.wageComponentRepository = wageComponentRepository;
	}

	@Transactional
	public int createPostingsForRun(UUID tenantId, String currencyCode, UUID payPeriodRunId,
			List<TenantPayrollResultLineEntity> resultLines) {
		if (resultLines.isEmpty()) {
			return 0;
		}
		if (postingRepository.existsByTenantIdAndPayPeriodRunId(tenantId, payPeriodRunId)) {
			log.debug("Ledger postings already exist for run {}", payPeriodRunId);
			return 0;
		}
		Map<UUID, TenantWageComponentEntity> componentsById = loadTenantComponents(tenantId, resultLines);
		Instant now = Instant.now();
		int sequence = 0;
		List<TenantPayrollLedgerPostingEntity> postings = new ArrayList<>();
		for (TenantPayrollResultLineEntity line : resultLines) {
			if (line.getComponentSource() != PayrollComponentSource.TENANT) {
				continue;
			}
			TenantWageComponentEntity comp = componentsById.get(line.getComponentRefId());
			if (comp == null) {
				continue;
			}
			UUID debitId = comp.getDebitTenantLedgerId();
			UUID creditId = comp.getCreditTenantLedgerId();
			if (debitId == null || creditId == null) {
				log.warn("MISSING_LEDGER_LINK tenantWageComponentId={} resultLineId={}", comp.getId(), line.getId());
				continue;
			}
			BigDecimal amount = line.getRoundedAmount();
			if (amount == null || amount.signum() == 0) {
				continue;
			}
			TenantPayrollLedgerPostingEntity posting = new TenantPayrollLedgerPostingEntity();
			posting.setId(UUID.randomUUID());
			posting.setTenantId(tenantId);
			posting.setPayPeriodRunId(payPeriodRunId);
			posting.setEmployeeId(line.getEmployeeId());
			posting.setTenantPayrollResultLineId(line.getId());
			posting.setDebitTenantLedgerId(debitId);
			posting.setCreditTenantLedgerId(creditId);
			posting.setAmount(amount.abs());
			posting.setCurrencyCode(currencyCode);
			posting.setPostingSequence(++sequence);
			posting.setCreatedAt(now);
			postings.add(posting);
		}
		if (!postings.isEmpty()) {
			postingRepository.saveAll(postings);
		}
		return postings.size();
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
