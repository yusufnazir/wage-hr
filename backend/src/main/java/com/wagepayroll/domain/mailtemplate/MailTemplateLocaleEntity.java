package com.wagepayroll.domain.mailtemplate;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.AbstractUuidEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "mail_template_locale")
public class MailTemplateLocaleEntity extends AbstractUuidEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "mail_template_id", length = 36, nullable = false)
	private UUID mailTemplateId;

	@Column(name = "locale", nullable = false, length = 8)
	private String locale;

	@Column(name = "subject", nullable = false, length = 500)
	private String subject;

	/** Matches Liquibase LONGTEXT (see BillingWebhookReceiptEntity). */
	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "body_html", nullable = false)
	private String bodyHtml;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getMailTemplateId() {
		return mailTemplateId;
	}

	public void setMailTemplateId(UUID mailTemplateId) {
		this.mailTemplateId = mailTemplateId;
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getBodyHtml() {
		return bodyHtml;
	}

	public void setBodyHtml(String bodyHtml) {
		this.bodyHtml = bodyHtml;
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
