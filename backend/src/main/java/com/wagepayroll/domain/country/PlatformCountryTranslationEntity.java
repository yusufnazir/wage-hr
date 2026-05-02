package com.wagepayroll.domain.country;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.AbstractUuidEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "platform_country_translation")
public class PlatformCountryTranslationEntity extends AbstractUuidEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "country_id", nullable = false, length = 36)
	private UUID countryId;

	@Column(name = "locale", nullable = false, length = 35)
	private String locale;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	public UUID getCountryId() {
		return countryId;
	}

	public void setCountryId(UUID countryId) {
		this.countryId = countryId;
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
