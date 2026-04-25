package com.wagepayroll.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.wagepayroll.domain.billing.BillingUsageEventEntity;
import com.wagepayroll.domain.billing.BillingUsageEventRepository;

import jakarta.persistence.EntityManager;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BillingUsageEventService {

	private final BillingUsageEventRepository billingUsageEventRepository;
	private final EntityManager entityManager;

	public BillingUsageEventService(BillingUsageEventRepository billingUsageEventRepository, EntityManager entityManager) {
		this.billingUsageEventRepository = billingUsageEventRepository;
		this.entityManager = entityManager;
	}

	public enum SubmitOutcome {
		ACCEPTED,
		DUPLICATE
	}

	@Transactional(noRollbackFor = DataIntegrityViolationException.class)
	public SubmitOutcome trySubmit(UUID tenantId, BillingMetricKey metricKey, BigDecimal quantity, String idempotencyKey) {
		if (tenantId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TENANT_ID_REQUIRED");
		}
		if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 255) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BILLING_USAGE_IDEMPOTENCY_KEY_INVALID");
		}
		if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BILLING_USAGE_QUANTITY_INVALID");
		}
		Instant now = Instant.now();
		BillingUsageEventEntity row = new BillingUsageEventEntity();
		row.setId(UUID.randomUUID());
		row.setTenantId(tenantId);
		row.setMetricKey(metricKey.wireValue());
		row.setQuantity(quantity);
		row.setIdempotencyKey(idempotencyKey.trim());
		row.setRecordedAt(now);
		try {
			billingUsageEventRepository.saveAndFlush(row);
			return SubmitOutcome.ACCEPTED;
		}
		catch (DataIntegrityViolationException ex) {
			entityManager.clear();
			return SubmitOutcome.DUPLICATE;
		}
	}
}
