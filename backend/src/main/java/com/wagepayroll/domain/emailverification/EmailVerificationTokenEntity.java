package com.wagepayroll.domain.emailverification;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.wagepayroll.domain.AbstractUuidEntity;
import com.wagepayroll.domain.user.UserAccountEntity;

@Entity
@Table(name = "email_verification_token")
public class EmailVerificationTokenEntity extends AbstractUuidEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_account_id", nullable = false, updatable = false)
	private UserAccountEntity userAccount;

	@Column(name = "token_sha256", nullable = false, unique = true, length = 64, updatable = false)
	private String tokenSha256;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public UserAccountEntity getUserAccount() {
		return userAccount;
	}

	public void setUserAccount(UserAccountEntity userAccount) {
		this.userAccount = userAccount;
	}

	public String getTokenSha256() {
		return tokenSha256;
	}

	public void setTokenSha256(String tokenSha256) {
		this.tokenSha256 = tokenSha256;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

	public void setUsedAt(Instant usedAt) {
		this.usedAt = usedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
