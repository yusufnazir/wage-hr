package com.wagepayroll.domain.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.TenantScopedEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification")
public class NotificationEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "recipient_user_id", length = 36, nullable = true)
	private UUID recipientUserId;

	@Column(name = "notification_type", nullable = false, length = 64)
	private String notificationType;

	@Column(name = "template_version", nullable = false, length = 32)
	private String templateVersion;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "correlation_id", length = 36, nullable = false)
	private UUID correlationId;

	@Column(name = "external_message_id", length = 128, nullable = true)
	private String externalMessageId;

	@Column(name = "status", nullable = false, length = 32)
	private String status;

	@Column(name = "read_at", nullable = true)
	private Instant readAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getRecipientUserId() {
		return recipientUserId;
	}

	public void setRecipientUserId(UUID recipientUserId) {
		this.recipientUserId = recipientUserId;
	}

	public String getNotificationType() {
		return notificationType;
	}

	public void setNotificationType(String notificationType) {
		this.notificationType = notificationType;
	}

	public String getTemplateVersion() {
		return templateVersion;
	}

	public void setTemplateVersion(String templateVersion) {
		this.templateVersion = templateVersion;
	}

	public UUID getCorrelationId() {
		return correlationId;
	}

	public void setCorrelationId(UUID correlationId) {
		this.correlationId = correlationId;
	}

	public String getExternalMessageId() {
		return externalMessageId;
	}

	public void setExternalMessageId(String externalMessageId) {
		this.externalMessageId = externalMessageId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Instant getReadAt() {
		return readAt;
	}

	public void setReadAt(Instant readAt) {
		this.readAt = readAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
