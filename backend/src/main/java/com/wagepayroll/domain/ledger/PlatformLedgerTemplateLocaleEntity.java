package com.wagepayroll.domain.ledger;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.AbstractUuidEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "platform_ledger_template_locale")
public class PlatformLedgerTemplateLocaleEntity extends AbstractUuidEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "platform_ledger_template_id", nullable = false, length = 36)
	private UUID platformLedgerTemplateId;

	@Column(name = "locale", nullable = false, length = 35)
	private String locale;

	@Column(name = "description", nullable = false, length = 500)
	private String description;

	public UUID getPlatformLedgerTemplateId() {
		return platformLedgerTemplateId;
	}

	public void setPlatformLedgerTemplateId(UUID platformLedgerTemplateId) {
		this.platformLedgerTemplateId = platformLedgerTemplateId;
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
