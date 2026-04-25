package com.wagepayroll.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.domain.billing.BillingProviderLinkEntity;
import com.wagepayroll.domain.billing.BillingProviderLinkRepository;
import com.wagepayroll.domain.billing.TenantResolutionState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StripeTenantResolverV1Test {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private BillingProviderLinkRepository billingProviderLinkRepository;

	private StripeTenantResolverV1 resolver;

	@BeforeEach
	void setUp() {
		resolver = new StripeTenantResolverV1(objectMapper, billingProviderLinkRepository);
	}

	@Test
	void resolveReturnsInsufficientWhenCustomerAbsent() {
		String raw = "{\"id\":\"evt_1\",\"object\":\"event\",\"data\":{\"object\":{\"id\":\"sub_x\",\"object\":\"subscription\"}}}";
		TenantResolutionResult r = resolver.resolve(raw);
		assertEquals(TenantResolutionState.UNRESOLVED_INSUFFICIENT_DATA, r.state());
		assertEquals("stripe_customer_missing", r.reasonCode());
		assertEquals("data.object.customer", r.missingFieldPath());
	}

	@Test
	void resolveReturnsNoMatchWhenCustomerUnlinked() {
		String raw = "{\"id\":\"evt_1\",\"object\":\"event\",\"data\":{\"object\":{\"id\":\"sub_x\",\"object\":\"subscription\",\"customer\":\"cus_orphan\"}}}";
		when(billingProviderLinkRepository.findByProviderAndExternalCustomerId(eq(BillingWebhookProvider.STRIPE), eq("cus_orphan")))
				.thenReturn(Optional.empty());
		TenantResolutionResult r = resolver.resolve(raw);
		assertEquals(TenantResolutionState.UNRESOLVED_NO_MATCH, r.state());
		assertEquals("billing_provider_link_not_found", r.reasonCode());
	}

	@Test
	void resolveReturnsResolvedWhenLinked() {
		UUID tenantId = UUID.fromString("10000000-0000-0000-0000-000000000001");
		String raw = "{\"id\":\"evt_1\",\"object\":\"event\",\"data\":{\"object\":{\"id\":\"sub_x\",\"object\":\"subscription\",\"customer\":\"cus_ok\"}}}";
		var link = new BillingProviderLinkEntity();
		link.setTenantId(tenantId);
		when(billingProviderLinkRepository.findByProviderAndExternalCustomerId(eq(BillingWebhookProvider.STRIPE), eq("cus_ok")))
				.thenReturn(Optional.of(link));
		TenantResolutionResult r = resolver.resolve(raw);
		assertEquals(TenantResolutionState.RESOLVED, r.state());
		assertEquals(tenantId, r.tenantId());
	}
}
