package com.wagepayroll.domain.org;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.TenantScopedEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant_work_time")
public class TenantWorkTimeEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "company_id", length = 36, nullable = false)
	private UUID companyId;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "code", nullable = false, length = 40)
	private String code;

	@Column(name = "hours_per_day", nullable = false, precision = 4, scale = 2)
	private BigDecimal hoursPerDay;

	@Column(name = "work_days_per_week", nullable = false)
	private int workDaysPerWeek;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getCompanyId() {
		return companyId;
	}

	public void setCompanyId(UUID companyId) {
		this.companyId = companyId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public BigDecimal getHoursPerDay() {
		return hoursPerDay;
	}

	public void setHoursPerDay(BigDecimal hoursPerDay) {
		this.hoursPerDay = hoursPerDay;
	}

	public int getWorkDaysPerWeek() {
		return workDaysPerWeek;
	}

	public void setWorkDaysPerWeek(int workDaysPerWeek) {
		this.workDaysPerWeek = workDaysPerWeek;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
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
