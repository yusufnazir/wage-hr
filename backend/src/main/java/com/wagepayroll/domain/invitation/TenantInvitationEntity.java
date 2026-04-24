package com.wagepayroll.domain.invitation;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.TenantScopedEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant_invitation")
public class TenantInvitationEntity extends TenantScopedEntity {

	@Column(name = "invited_email", nullable = false, length = 320)
	private String invitedEmail;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "inviter_user_id", length = 36, nullable = false)
	private UUID inviterUserId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "role_id", length = 36, nullable = false)
	private UUID roleId;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "status", nullable = false, length = 32)
	private String status;

	/**
	 * When {@code status=PENDING}, equals {@code tenant_id + ':' + invited_email} (email already lowercased) for at-most-one active invite per tenant+email.
	 * Cleared when the invitation is no longer pending.
	 */
	@Column(name = "pending_dedup_key", length = 400)
	private String pendingDedupKey;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "accepted_user_id", length = 36, nullable = true)
	private UUID acceptedUserId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public String getInvitedEmail() {
		return invitedEmail;
	}

	public void setInvitedEmail(String invitedEmail) {
		this.invitedEmail = invitedEmail;
	}

	public UUID getInviterUserId() {
		return inviterUserId;
	}

	public void setInviterUserId(UUID inviterUserId) {
		this.inviterUserId = inviterUserId;
	}

	public UUID getRoleId() {
		return roleId;
	}

	public void setRoleId(UUID roleId) {
		this.roleId = roleId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public void setTokenHash(String tokenHash) {
		this.tokenHash = tokenHash;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getPendingDedupKey() {
		return pendingDedupKey;
	}

	public void setPendingDedupKey(String pendingDedupKey) {
		this.pendingDedupKey = pendingDedupKey;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public UUID getAcceptedUserId() {
		return acceptedUserId;
	}

	public void setAcceptedUserId(UUID acceptedUserId) {
		this.acceptedUserId = acceptedUserId;
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
