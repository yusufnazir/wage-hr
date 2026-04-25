package com.wagepayroll.subscription;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.api.dto.CommercialPlanDetailDto;
import com.wagepayroll.domain.subscription.TenantSubscriptionEntity;
import com.wagepayroll.domain.subscription.TenantSubscriptionRepository;
import com.wagepayroll.plans.CommercialPlanService;
import com.wagepayroll.plans.PlanFeaturePrivilegeWiring;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionGatingService {

	private final TenantSubscriptionRepository tenantSubscriptionRepository;
	private final CommercialPlanService commercialPlanService;

	public SubscriptionGatingService(TenantSubscriptionRepository tenantSubscriptionRepository,
			CommercialPlanService commercialPlanService) {
		this.tenantSubscriptionRepository = tenantSubscriptionRepository;
		this.commercialPlanService = commercialPlanService;
	}

	/**
	 * Plan feature codes from the tenant's commercial plan when subscription status is {@code ACTIVE};
	 * empty when none or not active.
	 */
	@Transactional(readOnly = true)
	public List<String> activePlanFeatureCodesOrEmpty(UUID tenantId) {
		return activeCommercialPlanDetail(tenantId).map(p -> List.copyOf(p.planFeatureCodes())).orElse(List.of());
	}

	@Transactional(readOnly = true)
	public Set<String> subscriptionDerivedPrivilegeCodes(UUID tenantId) {
		return PlanFeaturePrivilegeWiring.privilegeCodesForPlanFeatures(activePlanFeatureCodesOrEmpty(tenantId));
	}

	@Transactional(readOnly = true)
	public boolean subscriptionCeilingContainsPrivilegeCode(UUID tenantId, String privilegeCode) {
		if (privilegeCode == null) {
			return false;
		}
		return subscriptionDerivedPrivilegeCodes(tenantId).contains(privilegeCode);
	}

	private Optional<CommercialPlanDetailDto> activeCommercialPlanDetail(UUID tenantId) {
		Optional<TenantSubscriptionEntity> opt = tenantSubscriptionRepository.findByTenantId(tenantId);
		if (opt.isEmpty() || !TenantSubscriptionStatus.ACTIVE.code().equals(opt.get().getStatus())) {
			return Optional.empty();
		}
		return Optional.of(commercialPlanService.getPlan(opt.get().getCommercialPlanId()));
	}
}
