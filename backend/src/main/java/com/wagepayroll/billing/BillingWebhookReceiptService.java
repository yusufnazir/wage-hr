package com.wagepayroll.billing;

import java.util.UUID;

import com.wagepayroll.domain.billing.BillingWebhookReceiptEntity;
import com.wagepayroll.domain.billing.BillingWebhookReceiptRepository;

import jakarta.persistence.EntityManager;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingWebhookReceiptService {

	private final BillingWebhookReceiptRepository billingWebhookReceiptRepository;
	private final EntityManager entityManager;

	public BillingWebhookReceiptService(BillingWebhookReceiptRepository billingWebhookReceiptRepository,
			EntityManager entityManager) {
		this.billingWebhookReceiptRepository = billingWebhookReceiptRepository;
		this.entityManager = entityManager;
	}

	public enum InsertOutcome {
		INSERTED,
		DUPLICATE
	}

	/**
	 * Persists a receipt row for idempotent webhook handling. Duplicate (provider, provider_event_id)
	 * returns {@link InsertOutcome#DUPLICATE} without throwing.
	 */
	@Transactional(noRollbackFor = DataIntegrityViolationException.class)
	public InsertOutcome tryInsertReceipt(WebhookReceiptInsert insert) {
		BillingWebhookReceiptEntity row = new BillingWebhookReceiptEntity();
		row.setId(UUID.randomUUID());
		row.setProvider(insert.provider());
		row.setProviderEventId(insert.providerEventId());
		row.setReceivedAt(insert.receivedAt());
		row.setProcessedAt(insert.receivedAt());
		row.setTenantId(insert.tenantId());
		row.setRawPayload(insert.rawPayload());
		row.setEventType(insert.eventType());
		row.setTenantResolutionState(insert.tenantResolutionState());
		row.setTenantResolutionReasonCode(insert.tenantResolutionReasonCode());
		row.setTenantResolutionMissingFieldPath(insert.tenantResolutionMissingFieldPath());
		row.setTenantResolutionResolverVersion(insert.tenantResolutionResolverVersion());
		try {
			billingWebhookReceiptRepository.saveAndFlush(row);
			return InsertOutcome.INSERTED;
		}
		catch (DataIntegrityViolationException ex) {
			entityManager.clear();
			return InsertOutcome.DUPLICATE;
		}
	}
}
