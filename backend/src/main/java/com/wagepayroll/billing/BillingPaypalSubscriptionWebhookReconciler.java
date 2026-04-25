package com.wagepayroll.billing;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Minimal PayPal subscription reconciliation after idempotent webhook receipt (parity with Stripe reconciler).
 *
 * <p>Handled types: {@code BILLING.SUBSCRIPTION.ACTIVATED}, {@code RE-ACTIVATED} (entitlement on); {@code CANCELLED},
 * {@code EXPIRED}, {@code SUSPENDED} (entitlement off via {@code CANCELLED} row).</p>
 */
@Service
public class BillingPaypalSubscriptionWebhookReconciler {

	private static final Logger log = LoggerFactory.getLogger(BillingPaypalSubscriptionWebhookReconciler.class);

	private final CommercialPlanRepository commercialPlanRepository;
	private final TenantSubscriptionService tenantSubscriptionService;
	private final AuditService auditService;

	public BillingPaypalSubscriptionWebhookReconciler(CommercialPlanRepository commercialPlanRepository,
			TenantSubscriptionService tenantSubscriptionService, AuditService auditService) {
		this.commercialPlanRepository = commercialPlanRepository;
		this.tenantSubscriptionService = tenantSubscriptionService;
		this.auditService = auditService;
	}

	@Transactional
	public void maybeReconcileAfterReceipt(JsonNode root, String transmissionId, TenantResolutionResult resolution,
			BillingWebhookReceiptService.InsertOutcome outcome) {
		if (outcome == BillingWebhookReceiptService.InsertOutcome.DUPLICATE) {
			return;
		}
		if (resolution.state() != TenantResolutionState.RESOLVED || resolution.tenantId() == null) {
			return;
		}
		JsonNode eventTypeNode = root.get("event_type");
		String eventType = eventTypeNode != null && eventTypeNode.isTextual() ? eventTypeNode.asText().trim() : "";
		try {
			if ("BILLING.SUBSCRIPTION.ACTIVATED".equals(eventType) || "BILLING.SUBSCRIPTION.RE-ACTIVATED".equals(eventType)) {
				reconcileSubscriptionActivated(root.path("resource"), resolution.tenantId(), transmissionId, eventType);
			}
			else if (isSubscriptionNotEntitledEvent(eventType)) {
				reconcileSubscriptionCancelled(resolution.tenantId(), transmissionId, eventType);
			}
		}
		catch (RuntimeException ex) {
			log.warn("event=billing_paypal.reconcile_failed providerEventId={} eventType={} tenantId={}", transmissionId, eventType,
					resolution.tenantId(), ex);
		}
	}

	private void reconcileSubscriptionActivated(JsonNode resource, UUID tenantIdFromResolution, String transmissionId, String eventType) {
		if (resource == null || !resource.isObject()) {
			log.warn("event=billing_paypal.activated_reconcile_skip reason=resource_missing providerEventId={}", transmissionId);
			return;
		}
		JsonNode customNode = resource.get("custom_id");
		if (customNode == null || !customNode.isTextual() || !StringUtils.hasText(customNode.asText())) {
			log.warn("event=billing_paypal.activated_reconcile_skip reason=missing_custom_id providerEventId={}", transmissionId);
			return;
		}
		Optional<PaypalSubscriptionCustomId.Decode> decode = PaypalSubscriptionCustomId.decode(customNode.asText());
		if (decode.isEmpty()) {
			log.warn("event=billing_paypal.activated_reconcile_skip reason=invalid_custom_id providerEventId={}", transmissionId);
			return;
		}
		UUID tenantIdFromCustom = decode.get().tenantId();
		UUID commercialPlanId = decode.get().commercialPlanId();
		if (!tenantIdFromResolution.equals(tenantIdFromCustom)) {
			log.warn(
					"event=billing_paypal.activated_reconcile_skip reason=tenant_mismatch providerEventId={} resolutionTenant={} customTenant={}",
					transmissionId, tenantIdFromResolution, tenantIdFromCustom);
			return;
		}
		CommercialPlanEntity plan = commercialPlanRepository.findById(commercialPlanId).orElse(null);
		if (plan == null || !plan.isActive()) {
			log.warn("event=billing_paypal.activated_reconcile_skip reason=plan_missing_or_inactive providerEventId={} planId={}",
					transmissionId, commercialPlanId);
			return;
		}
		tenantSubscriptionService.upsert(tenantIdFromResolution, commercialPlanId, TenantSubscriptionStatus.ACTIVE);
		auditService.append(tenantIdFromResolution, null, AuditActionCodes.TENANT_SUBSCRIPTION_PAYPAL_RECONCILED,
				AuditResourceTypes.TENANT_SUBSCRIPTION, tenantIdFromResolution.toString(), transmissionId,
				Map.of("commercialPlanId", commercialPlanId.toString(), "paypalEventType", eventType));
		log.info("event=billing_paypal.activated_reconciled providerEventId={} tenantId={} commercialPlanId={}", transmissionId,
				tenantIdFromResolution, commercialPlanId);
	}

	private void reconcileSubscriptionCancelled(UUID tenantId, String transmissionId, String eventType) {
		tenantSubscriptionService.markCancelledByProviderIfPresent(tenantId);
		auditService.append(tenantId, null, AuditActionCodes.TENANT_SUBSCRIPTION_PAYPAL_RECONCILED, AuditResourceTypes.TENANT_SUBSCRIPTION,
				tenantId.toString(), transmissionId, Map.of("paypalEventType", eventType));
		log.info("event=billing_paypal.subscription_marked_cancelled providerEventId={} tenantId={} paypalEventType={}", transmissionId,
				tenantId, eventType);
	}

	/** PayPal lifecycle events where we revoke subscription entitlement (same as Stripe {@code customer.subscription.deleted}). */
	private static boolean isSubscriptionNotEntitledEvent(String eventType) {
		return "BILLING.SUBSCRIPTION.CANCELLED".equals(eventType) || "BILLING.SUBSCRIPTION.EXPIRED".equals(eventType)
				|| "BILLING.SUBSCRIPTION.SUSPENDED".equals(eventType);
	}
}
