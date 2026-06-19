package com.wagepayroll.domain.componentgroup;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.AbstractUuidEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant_component_item_locale")
public class TenantComponentItemLocaleEntity extends AbstractUuidEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "tenant_component_item_id", nullable = false, length = 36)
	private UUID tenantComponentItemId;

	@Column(name = "locale", nullable = false, length = 35)
	private String locale;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "description", length = 500)
	private String description;

	public UUID getTenantComponentItemId() {
		return tenantComponentItemId;
	}

	public void setTenantComponentItemId(UUID tenantComponentItemId) {
		this.tenantComponentItemId = tenantComponentItemId;
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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
