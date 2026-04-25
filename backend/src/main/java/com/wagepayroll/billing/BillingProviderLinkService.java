package com.wagepayroll.billing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wagepayroll.api.dto.BillingProviderLinkDto;
import com.wagepayroll.domain.billing.BillingProviderLinkEntity;
import com.wagepayroll.domain.billing.BillingProviderLinkRepository;
import com.wagepayroll.domain.tenant.TenantRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BillingProviderLinkService {

	private final BillingProviderLinkRepository billingProviderLinkRepository;
	private final TenantRepository tenantRepository;

	public BillingProviderLinkService(BillingProviderLinkRepository billingProviderLinkRepository,
			TenantRepository tenantRepository) {
		this.billingProviderLinkRepository = billingProviderLinkRepository;
		this.tenantRepository = tenantRepository;
	}

	@Transactional(readOnly = true)
	public List<BillingProviderLinkDto> listByTenant(UUID tenantId) {
		requireTenant(tenantId);
		return billingProviderLinkRepository.findAllByTenantIdOrderByProviderAsc(tenantId).stream()
				.map(e -> new BillingProviderLinkDto(e.getTenantId(), e.getProvider(), e.getExternalCustomerId())).toList();
	}

	@Transactional
	public BillingProviderLinkDto upsert(UUID tenantId, BillingProvider provider, String externalCustomerId) {
		requireTenant(tenantId);
		String ext = normalizeExternalCustomerId(externalCustomerId);
		String prov = provider.code();
		assertExternalCustomerNotOwnedByOtherTenant(tenantId, prov, ext);
		Instant now = Instant.now();
		BillingProviderLinkEntity row = billingProviderLinkRepository.findByTenantIdAndProvider(tenantId, prov).orElseGet(() -> {
			BillingProviderLinkEntity n = new BillingProviderLinkEntity();
			n.setId(UUID.randomUUID());
			n.setTenantId(tenantId);
			n.setProvider(prov);
			n.setCreatedAt(now);
			return n;
		});
		row.setExternalCustomerId(ext);
		row.setUpdatedAt(now);
		try {
			billingProviderLinkRepository.saveAndFlush(row);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "BILLING_EXTERNAL_CUSTOMER_IN_USE");
		}
		return new BillingProviderLinkDto(tenantId, prov, ext);
	}

	private void requireTenant(UUID tenantId) {
		tenantRepository.findById(tenantId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UNKNOWN_TENANT"));
	}

	private static String normalizeExternalCustomerId(String externalCustomerId) {
		if (!StringUtils.hasText(externalCustomerId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EXTERNAL_CUSTOMER_ID_REQUIRED");
		}
		String t = externalCustomerId.trim();
		if (t.length() > 255) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EXTERNAL_CUSTOMER_ID_TOO_LONG");
		}
		return t;
	}

	/**
	 * Avoids hitting the DB unique constraint for the common cross-tenant conflict case (cleaner errors and
	 * prevents marking the surrounding transaction rollback-only in tests). Races still surface as
	 * {@link DataIntegrityViolationException} → 409.
	 */
	private void assertExternalCustomerNotOwnedByOtherTenant(UUID tenantId, String provider, String externalCustomerId) {
		billingProviderLinkRepository.findByProviderAndExternalCustomerId(provider, externalCustomerId).ifPresent(existing -> {
			if (!existing.getTenantId().equals(tenantId)) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "BILLING_EXTERNAL_CUSTOMER_IN_USE");
			}
		});
	}
}
