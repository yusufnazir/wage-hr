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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayPalTenantResolverV1Test {

	private final ObjectMapper mapper = new ObjectMapper();

	@Mock
	private BillingProviderLinkRepository billingProviderLinkRepository;

	@InjectMocks
	private PayPalTenantResolverV1 resolver;

	@Test
	void resolveReturnsResolvedWhenLinkExists() throws Exception {
		UUID tenantId = UUID.fromString("10000000-0000-0000-0000-000000000001");
		var root = mapper.readTree(
				"{\"resource\":{\"payer\":{\"payer_id\":\"PAYER_X\"}}}");
		var link = new BillingProviderLinkEntity();
		link.setTenantId(tenantId);
		when(billingProviderLinkRepository.findByProviderAndExternalCustomerId(eq(BillingWebhookProvider.PAYPAL), eq("PAYER_X")))
				.thenReturn(Optional.of(link));
		TenantResolutionResult r = resolver.resolve(root);
		assertEquals(TenantResolutionState.RESOLVED, r.state());
		assertEquals(tenantId, r.tenantId());
		assertEquals(PayPalTenantResolverV1.VERSION, r.resolverVersion());
	}

	@Test
	void resolveReturnsNoMatchWhenPayerPresentButUnlinked() throws Exception {
		var root = mapper.readTree("{\"resource\":{\"payer\":{\"payer_id\":\"PAYER_ORPHAN\"}}}");
		when(billingProviderLinkRepository.findByProviderAndExternalCustomerId(eq(BillingWebhookProvider.PAYPAL), eq("PAYER_ORPHAN")))
				.thenReturn(Optional.empty());
		TenantResolutionResult r = resolver.resolve(root);
		assertEquals(TenantResolutionState.UNRESOLVED_NO_MATCH, r.state());
		assertEquals("billing_provider_link_not_found", r.reasonCode());
	}

	@Test
	void resolveReturnsInsufficientWhenPayerMissing() throws Exception {
		var root = mapper.readTree("{\"resource\":{\"amount\":{\"value\":\"1\"}}}");
		TenantResolutionResult r = resolver.resolve(root);
		assertEquals(TenantResolutionState.UNRESOLVED_INSUFFICIENT_DATA, r.state());
		assertEquals("payer_missing", r.reasonCode());
		assertEquals("resource.payer", r.missingFieldPath());
	}

	@Test
	void extractPayerIdPrefersNestedPayer() throws Exception {
		var resource = mapper.readTree("{\"payer_id\":\"FLAT\",\"payer\":{\"payer_id\":\"NESTED\"}}");
		assertEquals(Optional.of("NESTED"), PayPalTenantResolverV1.extractPayerId(resource));
	}
}
