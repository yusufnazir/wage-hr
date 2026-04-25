package com.wagepayroll.billing;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.domain.billing.TenantResolutionState;
import com.wagepayroll.domain.plan.CommercialPlanEntity;
import com.wagepayroll.domain.plan.CommercialPlanRepository;
import com.wagepayroll.subscription.TenantSubscriptionService;
import com.wagepayroll.subscription.TenantSubscriptionStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Minimal M3 reconciliation: after a Stripe webhook is accepted idempotently, applies **narrow** side effects for
 * subscription checkout completion, subscription lifecycle updates, and subscription deletion. Unknown shapes → log + no-op
 * (webhook still returned 200).
 */
@Service
public class BillingStripeSubscriptionWebhookReconciler {

	private static final Logger log = LoggerFactory.getLogger(BillingStripeSubscriptionWebhookReconciler.class);

	private final CommercialPlanRepository commercialPlanRepository;
	private final TenantSubscriptionService tenantSubscriptionService;
	private final AuditService auditService;

	public BillingStripeSubscriptionWebhookReconciler(CommercialPlanRepository commercialPlanRepository,
			TenantSubscriptionService tenantSubscriptionService, AuditService auditService) {
		this.commercialPlanRepository = commercialPlanRepository;
		this.tenantSubscriptionService = tenantSubscriptionService;
		this.auditService = auditService;
	}

	@Transactional
	public void maybeReconcileAfterReceipt(Event event, TenantResolutionResult resolution,
			BillingWebhookReceiptService.InsertOutcome outcome) {
		if (outcome == BillingWebhookReceiptService.InsertOutcome.DUPLICATE) {
			return;
		}
		if (resolution.state() != TenantResolutionState.RESOLVED || resolution.tenantId() == null) {
			return;
		}
		String type = event.getType() != null ? event.getType() : "";
		try {
			if ("checkout.session.completed".equals(type)) {
				reconcileCheckoutSessionCompleted(event, resolution.tenantId());
			}
			else if ("customer.subscription.updated".equals(type)) {
				reconcileSubscriptionUpdated(event, resolution.tenantId());
			}
			else if ("customer.subscription.deleted".equals(type)) {
				reconcileSubscriptionDeleted(event, resolution.tenantId());
			}
		}
		catch (RuntimeException ex) {
			log.warn("event=billing_stripe.reconcile_failed providerEventId={} eventType={} tenantId={}", event.getId(), type,
					resolution.tenantId(), ex);
		}
	}

	private void reconcileCheckoutSessionCompleted(Event event, UUID tenantIdFromResolution) {
		final StripeObject dataObject;
		try {
			dataObject = event.getDataObjectDeserializer().deserializeUnsafe();
		}
		catch (EventDataObjectDeserializationException ex) {
			log.warn("event=billing_stripe.checkout_reconcile_skip reason=deserialize_failed providerEventId={}", event.getId(), ex);
			return;
		}
		if (dataObject == null || !(dataObject instanceof Session session)) {
			log.debug("event=billing_stripe.checkout_reconcile_skip reason=not_a_session providerEventId={}", event.getId());
			return;
		}
		if (!"subscription".equals(session.getMode())) {
			return;
		}
		if (!StringUtils.hasText(session.getClientReferenceId())) {
			log.warn("event=billing_stripe.checkout_reconcile_skip reason=missing_client_reference_id providerEventId={}",
					event.getId());
			return;
		}
		final UUID tenantIdFromSession;
		try {
			tenantIdFromSession = UUID.fromString(session.getClientReferenceId().trim());
		}
		catch (IllegalArgumentException ex) {
			log.warn("event=billing_stripe.checkout_reconcile_skip reason=invalid_client_reference_id providerEventId={}",
					event.getId());
			return;
		}
		if (!tenantIdFromResolution.equals(tenantIdFromSession)) {
			log.warn(
					"event=billing_stripe.checkout_reconcile_skip reason=tenant_mismatch providerEventId={} resolutionTenant={} sessionTenant={}",
					event.getId(), tenantIdFromResolution, tenantIdFromSession);
			return;
		}
		String planRaw = session.getMetadata() != null ? session.getMetadata().get("commercial_plan_id") : null;
		if (!StringUtils.hasText(planRaw)) {
			log.warn("event=billing_stripe.checkout_reconcile_skip reason=missing_metadata_commercial_plan_id providerEventId={}",
					event.getId());
			return;
		}
		final UUID commercialPlanId;
		try {
			commercialPlanId = UUID.fromString(planRaw.trim());
		}
		catch (IllegalArgumentException ex) {
			log.warn("event=billing_stripe.checkout_reconcile_skip reason=invalid_metadata_commercial_plan_id providerEventId={}",
					event.getId());
			return;
		}
		CommercialPlanEntity plan = commercialPlanRepository.findById(commercialPlanId).orElse(null);
		if (plan == null || !plan.isActive()) {
			log.warn("event=billing_stripe.checkout_reconcile_skip reason=plan_missing_or_inactive providerEventId={} planId={}",
					event.getId(), commercialPlanId);
			return;
		}
		if (!StringUtils.hasText(plan.getStripeSubscriptionPriceId())) {
			log.warn("event=billing_stripe.checkout_reconcile_skip reason=plan_has_no_stripe_price providerEventId={} planId={}",
					event.getId(), commercialPlanId);
			return;
		}
		tenantSubscriptionService.upsert(tenantIdFromResolution, commercialPlanId, TenantSubscriptionStatus.ACTIVE);
		auditService.append(tenantIdFromResolution, null, AuditActionCodes.TENANT_SUBSCRIPTION_STRIPE_RECONCILED,
				AuditResourceTypes.TENANT_SUBSCRIPTION, tenantIdFromResolution.toString(), event.getId(),
				Map.of("commercialPlanId", commercialPlanId.toString(), "stripeEventType", "checkout.session.completed"));
		log.info("event=billing_stripe.checkout_reconciled providerEventId={} tenantId={} commercialPlanId={}", event.getId(),
				tenantIdFromResolution, commercialPlanId);
	}

	private void reconcileSubscriptionUpdated(Event event, UUID tenantIdFromResolution) {
		final StripeObject dataObject;
		try {
			dataObject = event.getDataObjectDeserializer().deserializeUnsafe();
		}
		catch (EventDataObjectDeserializationException ex) {
			log.warn("event=billing_stripe.subscription_updated_reconcile_skip reason=deserialize_failed providerEventId={}",
					event.getId(), ex);
			return;
		}
		if (dataObject == null || !(dataObject instanceof Subscription subscription)) {
			log.debug("event=billing_stripe.subscription_updated_reconcile_skip reason=not_a_subscription providerEventId={}",
					event.getId());
			return;
		}
		String status = subscription.getStatus();
		if (!StringUtils.hasText(status)) {
			return;
		}
		if ("canceled".equals(status) || "unpaid".equals(status) || "incomplete_expired".equals(status)) {
			tenantSubscriptionService.markCancelledByProviderIfPresent(tenantIdFromResolution);
			auditService.append(tenantIdFromResolution, null, AuditActionCodes.TENANT_SUBSCRIPTION_STRIPE_RECONCILED,
					AuditResourceTypes.TENANT_SUBSCRIPTION, tenantIdFromResolution.toString(), event.getId(),
					Map.of("stripeEventType", "customer.subscription.updated", "stripeSubscriptionStatus", status));
			log.info("event=billing_stripe.subscription_updated_marked_cancelled providerEventId={} tenantId={} status={}",
					event.getId(), tenantIdFromResolution, status);
			return;
		}
		if (!"active".equals(status) && !"trialing".equals(status)) {
			return;
		}
		String priceId = firstSubscriptionPriceId(subscription);
		if (!StringUtils.hasText(priceId)) {
			log.warn("event=billing_stripe.subscription_updated_reconcile_skip reason=missing_price_on_subscription providerEventId={}",
					event.getId());
			return;
		}
		String planRaw = subscription.getMetadata() != null ? subscription.getMetadata().get("commercial_plan_id") : null;
		if (StringUtils.hasText(planRaw)) {
			try {
				UUID commercialPlanIdFromMeta = UUID.fromString(planRaw.trim());
				CommercialPlanEntity planByMeta = commercialPlanRepository.findById(commercialPlanIdFromMeta).orElse(null);
				if (planByMeta != null && planByMeta.isActive() && StringUtils.hasText(planByMeta.getStripeSubscriptionPriceId())
						&& planByMeta.getStripeSubscriptionPriceId().trim().equals(priceId.trim())) {
					finishSubscriptionUpdatedActive(tenantIdFromResolution, commercialPlanIdFromMeta, event, status, "metadata");
					return;
				}
				if (planByMeta != null) {
					log.warn(
							"event=billing_stripe.subscription_updated_reconcile_skip reason=metadata_plan_price_mismatch providerEventId={} planId={} subscriptionPrice={}",
							event.getId(), commercialPlanIdFromMeta, priceId);
					return;
				}
			}
			catch (IllegalArgumentException ex) {
				log.warn("event=billing_stripe.subscription_updated_reconcile_skip reason=invalid_metadata_commercial_plan_id providerEventId={}",
						event.getId());
			}
		}
		Optional<CommercialPlanEntity> planByPrice = commercialPlanRepository.findByStripeSubscriptionPriceId(priceId.trim());
		if (planByPrice.isEmpty() || !planByPrice.get().isActive()) {
			log.debug(
					"event=billing_stripe.subscription_updated_reconcile_skip reason=no_active_plan_for_stripe_price providerEventId={} priceId={}",
					event.getId(), priceId);
			return;
		}
		finishSubscriptionUpdatedActive(tenantIdFromResolution, planByPrice.get().getId(), event, status, "stripe_price_id");
	}

	private void finishSubscriptionUpdatedActive(UUID tenantIdFromResolution, UUID commercialPlanId, Event event, String status,
			String reconcileSource) {
		tenantSubscriptionService.upsert(tenantIdFromResolution, commercialPlanId, TenantSubscriptionStatus.ACTIVE);
		auditService.append(tenantIdFromResolution, null, AuditActionCodes.TENANT_SUBSCRIPTION_STRIPE_RECONCILED,
				AuditResourceTypes.TENANT_SUBSCRIPTION, tenantIdFromResolution.toString(), event.getId(),
				Map.of("commercialPlanId", commercialPlanId.toString(), "stripeEventType", "customer.subscription.updated",
						"stripeSubscriptionStatus", status, "stripeSubscriptionReconcileSource", reconcileSource));
		log.info("event=billing_stripe.subscription_updated_reconciled providerEventId={} tenantId={} commercialPlanId={} source={}",
				event.getId(), tenantIdFromResolution, commercialPlanId, reconcileSource);
	}

	private static String firstSubscriptionPriceId(Subscription subscription) {
		if (subscription.getItems() == null || subscription.getItems().getData() == null) {
			return null;
		}
		for (SubscriptionItem item : subscription.getItems().getData()) {
			if (item != null && item.getPrice() != null && StringUtils.hasText(item.getPrice().getId())) {
				return item.getPrice().getId();
			}
		}
		return null;
	}

	private void reconcileSubscriptionDeleted(Event event, UUID tenantId) {
		tenantSubscriptionService.markCancelledByProviderIfPresent(tenantId);
		auditService.append(tenantId, null, AuditActionCodes.TENANT_SUBSCRIPTION_STRIPE_RECONCILED,
				AuditResourceTypes.TENANT_SUBSCRIPTION, tenantId.toString(), event.getId(),
				Map.of("stripeEventType", "customer.subscription.deleted"));
		log.info("event=billing_stripe.subscription_deleted_reconciled tenantId={}", tenantId);
	}
}
