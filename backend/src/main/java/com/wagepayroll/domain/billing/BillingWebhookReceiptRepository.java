package com.wagepayroll.domain.billing;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingWebhookReceiptRepository extends JpaRepository<BillingWebhookReceiptEntity, UUID> {

	Optional<BillingWebhookReceiptEntity> findByProviderAndProviderEventId(String provider, String providerEventId);
}
