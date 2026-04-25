package com.wagepayroll.plans;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.wagepayroll.api.dto.CommercialPlanDetailDto;
import com.wagepayroll.api.dto.CommercialPlanListItemDto;
import com.wagepayroll.api.dto.CreateCommercialPlanRequest;
import com.wagepayroll.api.dto.ReplaceCommercialPlanRequest;
import com.wagepayroll.domain.plan.CommercialPlanEntity;
import com.wagepayroll.domain.plan.CommercialPlanFeatureEntity;
import com.wagepayroll.domain.plan.CommercialPlanFeatureRepository;
import com.wagepayroll.domain.plan.CommercialPlanRepository;
import com.wagepayroll.domain.plan.PlanFeatureEntity;
import com.wagepayroll.domain.plan.PlanFeatureRepository;
import com.wagepayroll.domain.subscription.TenantSubscriptionRepository;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommercialPlanService {

	private final CommercialPlanRepository commercialPlanRepository;
	private final CommercialPlanFeatureRepository commercialPlanFeatureRepository;
	private final PlanFeatureRepository planFeatureRepository;
	private final TenantSubscriptionRepository tenantSubscriptionRepository;
	private final AuditService auditService;

	public CommercialPlanService(CommercialPlanRepository commercialPlanRepository,
			CommercialPlanFeatureRepository commercialPlanFeatureRepository, PlanFeatureRepository planFeatureRepository,
			TenantSubscriptionRepository tenantSubscriptionRepository, AuditService auditService) {
		this.commercialPlanRepository = commercialPlanRepository;
		this.commercialPlanFeatureRepository = commercialPlanFeatureRepository;
		this.planFeatureRepository = planFeatureRepository;
		this.tenantSubscriptionRepository = tenantSubscriptionRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<CommercialPlanListItemDto> listPlans() {
		return commercialPlanRepository.findAllByOrderBySortOrderAscCodeAsc().stream()
				.map(p -> new CommercialPlanListItemDto(p.getId(), p.getCode(), p.getSortOrder(), p.isActive(),
						commercialPlanFeatureRepository.countByCommercialPlanId(p.getId()), p.getStripeSubscriptionPriceId(),
						p.getPaypalBillingPlanId()))
				.toList();
	}

	/**
	 * Active commercial plans for tenant self-serve checkout (Stripe / PayPal). Same row shape as the platform list, without inactive plans.
	 */
	@Transactional(readOnly = true)
	public List<CommercialPlanListItemDto> listActivePlansForTenantCatalog() {
		return commercialPlanRepository.findAllByOrderBySortOrderAscCodeAsc().stream().filter(CommercialPlanEntity::isActive)
				.map(p -> new CommercialPlanListItemDto(p.getId(), p.getCode(), p.getSortOrder(), p.isActive(),
						commercialPlanFeatureRepository.countByCommercialPlanId(p.getId()), p.getStripeSubscriptionPriceId(),
						p.getPaypalBillingPlanId()))
				.toList();
	}

	@Transactional
	public void delete(UUID id, UUID actorUserId, String correlationId) {
		CommercialPlanEntity plan = commercialPlanRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UNKNOWN_COMMERCIAL_PLAN"));
		if (tenantSubscriptionRepository.existsByCommercialPlanId(id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "COMMERCIAL_PLAN_IN_USE");
		}
		auditService.append(null, actorUserId, AuditActionCodes.COMMERCIAL_PLAN_DELETED, AuditResourceTypes.COMMERCIAL_PLAN, id.toString(),
				correlationId, Map.of("code", plan.getCode()));
		commercialPlanRepository.delete(plan);
	}

	@Transactional(readOnly = true)
	public CommercialPlanDetailDto getPlan(UUID id) {
		CommercialPlanEntity plan = commercialPlanRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UNKNOWN_COMMERCIAL_PLAN"));
		return toDetail(plan);
	}

	@Transactional
	public CommercialPlanDetailDto create(CreateCommercialPlanRequest body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		final String code;
		try {
			code = CommercialPlanCode.normalizeAndValidate(body.code());
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PLAN_CODE");
		}
		if (commercialPlanRepository.existsByCodeIgnoreCase(code)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "DUPLICATE_PLAN_CODE");
		}
		LinkedHashSet<UUID> featureIds = validateNonEmptyFeatureIds(body.planFeatureIds());
		Instant now = Instant.now();
		CommercialPlanEntity plan = new CommercialPlanEntity();
		plan.setId(UUID.randomUUID());
		plan.setCode(code);
		plan.setSortOrder(body.sortOrder());
		plan.setActive(body.active() == null || body.active());
		plan.setCreatedAt(now);
		plan.setUpdatedAt(now);
		applyStripeSubscriptionPriceIdOnCreate(plan, body.stripeSubscriptionPriceId());
		applyPaypalBillingPlanIdOnCreate(plan, body.paypalBillingPlanId());
		commercialPlanRepository.save(plan);
		persistLinks(plan.getId(), new ArrayList<>(featureIds), now);
		return toDetail(plan);
	}

	@Transactional
	public CommercialPlanDetailDto replace(UUID id, ReplaceCommercialPlanRequest body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		CommercialPlanEntity plan = commercialPlanRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UNKNOWN_COMMERCIAL_PLAN"));
		LinkedHashSet<UUID> featureIds = validateNonEmptyFeatureIds(body.planFeatureIds());
		Instant now = Instant.now();
		commercialPlanFeatureRepository.deleteByCommercialPlanId(plan.getId());
		commercialPlanFeatureRepository.flush();
		plan.setSortOrder(body.sortOrder());
		plan.setActive(body.active());
		plan.setUpdatedAt(now);
		applyStripeSubscriptionPriceIdOnReplace(plan, body.stripeSubscriptionPriceId(), body.clearStripeSubscriptionPrice());
		applyPaypalBillingPlanIdOnReplace(plan, body.paypalBillingPlanId(), body.clearPaypalBillingPlanId());
		commercialPlanRepository.save(plan);
		persistLinks(plan.getId(), new ArrayList<>(featureIds), now);
		return toDetail(plan);
	}

	private LinkedHashSet<UUID> validateNonEmptyFeatureIds(List<UUID> raw) {
		if (raw == null || raw.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLAN_FEATURES_REQUIRED");
		}
		LinkedHashSet<UUID> unique = new LinkedHashSet<>(raw);
		if (unique.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLAN_FEATURES_REQUIRED");
		}
		for (UUID id : unique) {
			if (id == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_PLAN_FEATURE");
			}
		}
		if (planFeatureRepository.findAllById(unique).size() != unique.size()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_PLAN_FEATURE");
		}
		return unique;
	}

	private void persistLinks(UUID commercialPlanId, List<UUID> orderedFeatureIds, Instant now) {
		for (UUID featureId : orderedFeatureIds) {
			CommercialPlanFeatureEntity row = new CommercialPlanFeatureEntity();
			row.setId(UUID.randomUUID());
			row.setCommercialPlanId(commercialPlanId);
			row.setPlanFeatureId(featureId);
			row.setCreatedAt(now);
			row.setUpdatedAt(now);
			commercialPlanFeatureRepository.save(row);
		}
	}

	private CommercialPlanDetailDto toDetail(CommercialPlanEntity plan) {
		List<CommercialPlanFeatureEntity> links = commercialPlanFeatureRepository.findByCommercialPlanIdOrderByPlanFeatureId(
				plan.getId());
		List<UUID> ids = links.stream().map(CommercialPlanFeatureEntity::getPlanFeatureId).toList();
		Map<UUID, PlanFeatureEntity> byId = planFeatureRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(PlanFeatureEntity::getId, pf -> pf));
		List<PlanFeatureEntity> ordered = ids.stream().map(byId::get).sorted(Comparator.comparingInt(PlanFeatureEntity::getSortOrder)
				.thenComparing(PlanFeatureEntity::getCode)).toList();
		List<UUID> orderedIds = ordered.stream().map(PlanFeatureEntity::getId).toList();
		List<String> codes = ordered.stream().map(PlanFeatureEntity::getCode).toList();
		return new CommercialPlanDetailDto(plan.getId(), plan.getCode(), plan.getSortOrder(), plan.isActive(), orderedIds, codes,
				plan.getStripeSubscriptionPriceId(), plan.getPaypalBillingPlanId());
	}

	private void applyStripeSubscriptionPriceIdOnCreate(CommercialPlanEntity plan, String rawPriceId) {
		if (!StringUtils.hasText(rawPriceId)) {
			return;
		}
		String priceId = rawPriceId.trim();
		validateStripeSubscriptionPriceFormat(priceId);
		commercialPlanRepository.findByStripeSubscriptionPriceId(priceId).ifPresent(p -> {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "STRIPE_SUBSCRIPTION_PRICE_ID_IN_USE");
		});
		plan.setStripeSubscriptionPriceId(priceId);
	}

	private void applyStripeSubscriptionPriceIdOnReplace(CommercialPlanEntity plan, String rawPriceId, Boolean clearStripe) {
		if (Boolean.TRUE.equals(clearStripe)) {
			plan.setStripeSubscriptionPriceId(null);
			return;
		}
		if (!StringUtils.hasText(rawPriceId)) {
			return;
		}
		String priceId = rawPriceId.trim();
		validateStripeSubscriptionPriceFormat(priceId);
		if (commercialPlanRepository.existsByStripeSubscriptionPriceIdAndIdNot(priceId, plan.getId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "STRIPE_SUBSCRIPTION_PRICE_ID_IN_USE");
		}
		plan.setStripeSubscriptionPriceId(priceId);
	}

	private static void validateStripeSubscriptionPriceFormat(String priceId) {
		if (!priceId.startsWith("price_")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "STRIPE_SUBSCRIPTION_PRICE_ID_INVALID");
		}
	}

	private void applyPaypalBillingPlanIdOnCreate(CommercialPlanEntity plan, String rawPaypalPlanId) {
		if (!StringUtils.hasText(rawPaypalPlanId)) {
			return;
		}
		String paypalPlanId = rawPaypalPlanId.trim();
		validatePaypalBillingPlanFormat(paypalPlanId);
		commercialPlanRepository.findByPaypalBillingPlanId(paypalPlanId).ifPresent(p -> {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "PAYPAL_BILLING_PLAN_ID_IN_USE");
		});
		plan.setPaypalBillingPlanId(paypalPlanId);
	}

	private void applyPaypalBillingPlanIdOnReplace(CommercialPlanEntity plan, String rawPaypalPlanId, Boolean clearPaypal) {
		if (Boolean.TRUE.equals(clearPaypal)) {
			plan.setPaypalBillingPlanId(null);
			return;
		}
		if (!StringUtils.hasText(rawPaypalPlanId)) {
			return;
		}
		String paypalPlanId = rawPaypalPlanId.trim();
		validatePaypalBillingPlanFormat(paypalPlanId);
		if (commercialPlanRepository.existsByPaypalBillingPlanIdAndIdNot(paypalPlanId, plan.getId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "PAYPAL_BILLING_PLAN_ID_IN_USE");
		}
		plan.setPaypalBillingPlanId(paypalPlanId);
	}

	private static void validatePaypalBillingPlanFormat(String paypalPlanId) {
		if (paypalPlanId.length() > 128) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAYPAL_BILLING_PLAN_ID_INVALID");
		}
		if (!paypalPlanId.startsWith("P-")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAYPAL_BILLING_PLAN_ID_INVALID");
		}
	}
}
