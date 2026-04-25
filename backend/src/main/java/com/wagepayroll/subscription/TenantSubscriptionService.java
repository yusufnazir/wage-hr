package com.wagepayroll.subscription;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.wagepayroll.api.dto.CommercialPlanDetailDto;
import com.wagepayroll.api.dto.TenantSubscriptionPayloadDto;
import com.wagepayroll.domain.subscription.TenantSubscriptionEntity;
import com.wagepayroll.domain.subscription.TenantSubscriptionRepository;
import com.wagepayroll.domain.tenant.TenantRepository;
import com.wagepayroll.plans.CommercialPlanService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantSubscriptionService {

	private final TenantSubscriptionRepository tenantSubscriptionRepository;
	private final TenantRepository tenantRepository;
	private final CommercialPlanService commercialPlanService;

	public TenantSubscriptionService(TenantSubscriptionRepository tenantSubscriptionRepository,
			TenantRepository tenantRepository, CommercialPlanService commercialPlanService) {
		this.tenantSubscriptionRepository = tenantSubscriptionRepository;
		this.tenantRepository = tenantRepository;
		this.commercialPlanService = commercialPlanService;
	}

	@Transactional(readOnly = true)
	public Optional<TenantSubscriptionPayloadDto> findByTenantId(UUID tenantId) {
		return tenantSubscriptionRepository.findByTenantId(tenantId).map(this::toPayload);
	}

	@Transactional
	public TenantSubscriptionPayloadDto upsert(UUID tenantId, UUID commercialPlanId, TenantSubscriptionStatus status) {
		tenantRepository.findById(tenantId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UNKNOWN_TENANT"));
		CommercialPlanDetailDto plan = commercialPlanService.getPlan(commercialPlanId);
		if (status == TenantSubscriptionStatus.ACTIVE && !plan.active()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INACTIVE_PLAN_FOR_ACTIVE_SUBSCRIPTION");
		}
		Instant now = Instant.now();
		TenantSubscriptionEntity entity = tenantSubscriptionRepository.findByTenantId(tenantId).orElseGet(() -> {
			TenantSubscriptionEntity row = new TenantSubscriptionEntity();
			row.setId(UUID.randomUUID());
			row.setTenantId(tenantId);
			row.setCreatedAt(now);
			return row;
		});
		entity.setCommercialPlanId(commercialPlanId);
		entity.setStatus(status.code());
		entity.setUpdatedAt(now);
		tenantSubscriptionRepository.save(entity);
		return toPayload(entity);
	}

	/**
	 * Sets subscription status to {@link TenantSubscriptionStatus#CANCELLED} when a row exists (e.g. Stripe
	 * {@code customer.subscription.deleted}). Preserves {@code commercial_plan_id}.
	 */
	@Transactional
	public void markCancelledByProviderIfPresent(UUID tenantId) {
		tenantSubscriptionRepository.findByTenantId(tenantId).ifPresent(entity -> {
			Instant now = Instant.now();
			entity.setStatus(TenantSubscriptionStatus.CANCELLED.code());
			entity.setUpdatedAt(now);
			tenantSubscriptionRepository.save(entity);
		});
	}

	private TenantSubscriptionPayloadDto toPayload(TenantSubscriptionEntity entity) {
		CommercialPlanDetailDto plan = commercialPlanService.getPlan(entity.getCommercialPlanId());
		return new TenantSubscriptionPayloadDto(entity.getTenantId(), entity.getCommercialPlanId(), plan.code(),
				entity.getStatus(), plan.planFeatureIds(), plan.planFeatureCodes());
	}
}
