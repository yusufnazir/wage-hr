package com.wagepayroll.domain.payrollbase;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "platform_wage_component_base_effect")
public class PlatformWageComponentBaseEffectEntity extends WageComponentBaseEffectColumns {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "platform_wage_component_id", length = 36, nullable = false)
	private UUID platformWageComponentId;

	public UUID getPlatformWageComponentId() {
		return platformWageComponentId;
	}

	public void setPlatformWageComponentId(UUID platformWageComponentId) {
		this.platformWageComponentId = platformWageComponentId;
	}
}
