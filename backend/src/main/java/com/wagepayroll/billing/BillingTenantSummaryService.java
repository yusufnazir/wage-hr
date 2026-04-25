package com.wagepayroll.billing;

import java.util.Optional;
import java.util.UUID;

import com.wagepayroll.api.dto.TenantBillingSubscriptionSnapshotDto;
import com.wagepayroll.api.dto.TenantBillingSummaryDto;
import com.wagepayroll.domain.billing.BillingProviderLinkRepository;
import com.wagepayroll.domain.plan.CommercialPlanEntity;
import com.wagepayroll.domain.plan.CommercialPlanRepository;
import com.wagepayroll.domain.setting.PlatformSettingEntity;
import com.wagepayroll.domain.setting.PlatformSettingRepository;
import com.wagepayroll.domain.subscription.TenantSubscriptionEntity;
import com.wagepayroll.domain.subscription.TenantSubscriptionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingTenantSummaryService {

	public static final String PLATFORM_KEY_STRIPE_ENABLED = "billing.stripe.enabled";
	public static final String PLATFORM_KEY_PAYPAL_ENABLED = "billing.paypal.enabled";

	private final PlatformSettingRepository platformSettingRepository;
	private final BillingProviderLinkRepository billingProviderLinkRepository;
	private final TenantSubscriptionRepository tenantSubscriptionRepository;
	private final CommercialPlanRepository commercialPlanRepository;

	public BillingTenantSummaryService(PlatformSettingRepository platformSettingRepository,
			BillingProviderLinkRepository billingProviderLinkRepository, TenantSubscriptionRepository tenantSubscriptionRepository,
			CommercialPlanRepository commercialPlanRepository) {
		this.platformSettingRepository = platformSettingRepository;
		this.billingProviderLinkRepository = billingProviderLinkRepository;
		this.tenantSubscriptionRepository = tenantSubscriptionRepository;
		this.commercialPlanRepository = commercialPlanRepository;
	}

	@Transactional(readOnly = true)
	public TenantBillingSummaryDto summarize(UUID tenantId) {
		boolean stripeOn = flagIsOne(platformSettingRepository.findByKey(PLATFORM_KEY_STRIPE_ENABLED));
		boolean paypalOn = flagIsOne(platformSettingRepository.findByKey(PLATFORM_KEY_PAYPAL_ENABLED));
		boolean stripeLinked = billingProviderLinkRepository.findByTenantIdAndProvider(tenantId, BillingProvider.STRIPE.code())
				.isPresent();
		boolean paypalLinked = billingProviderLinkRepository.findByTenantIdAndProvider(tenantId, BillingProvider.PAYPAL.code())
				.isPresent();
		TenantBillingSubscriptionSnapshotDto subscription = tenantSubscriptionRepository.findByTenantId(tenantId).map(this::toSubscriptionSnapshot)
				.orElse(null);
		return new TenantBillingSummaryDto(stripeOn, paypalOn, stripeLinked, paypalLinked, subscription);
	}

	private TenantBillingSubscriptionSnapshotDto toSubscriptionSnapshot(TenantSubscriptionEntity entity) {
		String code = commercialPlanRepository.findById(entity.getCommercialPlanId()).map(CommercialPlanEntity::getCode).orElse(null);
		return new TenantBillingSubscriptionSnapshotDto(entity.getStatus(), entity.getCommercialPlanId(), code);
	}

	private static boolean flagIsOne(Optional<PlatformSettingEntity> row) {
		return row.map(PlatformSettingEntity::getValueText).map(v -> "1".equals(v.trim())).orElse(false);
	}
}
