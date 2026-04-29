package com.wagepayroll.domain.billing;

import java.time.Instant;
import java.util.UUID;

import com.wagepayroll.domain.AbstractUuidEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "billing_webhook_receipt")
public class BillingWebhookReceiptEntity extends AbstractUuidEntity {

	@Column(name = "provider", nullable = false, length = 16)
	private String provider;

	@Column(name = "provider_event_id", nullable = false, length = 255)
	private String providerEventId;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	@Column(name = "processed_at", nullable = true)
	private Instant processedAt;

	@Column(name = "processing_error", nullable = true, length = 512)
	private String processingError;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "tenant_id", length = 36, nullable = true)
	private UUID tenantId;

	/** Matches Liquibase {@code CLOB} → MariaDB {@code LONGTEXT}; {@code @Lob} would validate as {@code TINYTEXT}. */
	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "raw_payload", nullable = true)
	private String rawPayload;

	@Column(name = "event_type", nullable = true, length = 128)
	private String eventType;

	@Enumerated(EnumType.STRING)
	@Column(name = "tenant_resolution_state", nullable = false, length = 40)
	private TenantResolutionState tenantResolutionState;

	@Column(name = "tenant_resolution_reason_code", nullable = true, length = 64)
	private String tenantResolutionReasonCode;

	@Column(name = "tenant_resolution_missing_field_path", nullable = true, length = 255)
	private String tenantResolutionMissingFieldPath;

	@Column(name = "tenant_resolution_resolver_version", nullable = true, length = 64)
	private String tenantResolutionResolverVersion;

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getProviderEventId() {
		return providerEventId;
	}

	public void setProviderEventId(String providerEventId) {
		this.providerEventId = providerEventId;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}

	public void setReceivedAt(Instant receivedAt) {
		this.receivedAt = receivedAt;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}

	public void setProcessedAt(Instant processedAt) {
		this.processedAt = processedAt;
	}

	public String getProcessingError() {
		return processingError;
	}

	public void setProcessingError(String processingError) {
		this.processingError = processingError;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public void setTenantId(UUID tenantId) {
		this.tenantId = tenantId;
	}

	public String getRawPayload() {
		return rawPayload;
	}

	public void setRawPayload(String rawPayload) {
		this.rawPayload = rawPayload;
	}

	public String getEventType() {
		return eventType;
	}

	public void setEventType(String eventType) {
		this.eventType = eventType;
	}

	public TenantResolutionState getTenantResolutionState() {
		return tenantResolutionState;
	}

	public void setTenantResolutionState(TenantResolutionState tenantResolutionState) {
		this.tenantResolutionState = tenantResolutionState;
	}

	public String getTenantResolutionReasonCode() {
		return tenantResolutionReasonCode;
	}

	public void setTenantResolutionReasonCode(String tenantResolutionReasonCode) {
		this.tenantResolutionReasonCode = tenantResolutionReasonCode;
	}

	public String getTenantResolutionMissingFieldPath() {
		return tenantResolutionMissingFieldPath;
	}

	public void setTenantResolutionMissingFieldPath(String tenantResolutionMissingFieldPath) {
		this.tenantResolutionMissingFieldPath = tenantResolutionMissingFieldPath;
	}

	public String getTenantResolutionResolverVersion() {
		return tenantResolutionResolverVersion;
	}

	public void setTenantResolutionResolverVersion(String tenantResolutionResolverVersion) {
		this.tenantResolutionResolverVersion = tenantResolutionResolverVersion;
	}
}
