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
@Table(name = "tenant_job")
public class TenantJobEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "company_id", length = 36, nullable = false)
	private UUID companyId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "department_id", length = 36, nullable = false)
	private UUID departmentId;

	@Column(name = "title", nullable = false, length = 140)
	private String title;

	@Column(name = "code", nullable = false, length = 40)
	private String code;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "salary_type", nullable = false, length = 20)
	private String salaryType;

	@Column(name = "default_salary", precision = 18, scale = 2)
	private BigDecimal defaultSalary;

	@Column(name = "default_hourly_rate", precision = 18, scale = 2)
	private BigDecimal defaultHourlyRate;

	@Column(name = "standard_hours_per_week", precision = 5, scale = 2)
	private BigDecimal standardHoursPerWeek;

	@Column(name = "job_level", length = 40)
	private String jobLevel;

	@Column(name = "job_category", length = 60)
	private String jobCategory;

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

	public UUID getDepartmentId() {
		return departmentId;
	}

	public void setDepartmentId(UUID departmentId) {
		this.departmentId = departmentId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getSalaryType() {
		return salaryType;
	}

	public void setSalaryType(String salaryType) {
		this.salaryType = salaryType;
	}

	public BigDecimal getDefaultSalary() {
		return defaultSalary;
	}

	public void setDefaultSalary(BigDecimal defaultSalary) {
		this.defaultSalary = defaultSalary;
	}

	public BigDecimal getDefaultHourlyRate() {
		return defaultHourlyRate;
	}

	public void setDefaultHourlyRate(BigDecimal defaultHourlyRate) {
		this.defaultHourlyRate = defaultHourlyRate;
	}

	public BigDecimal getStandardHoursPerWeek() {
		return standardHoursPerWeek;
	}

	public void setStandardHoursPerWeek(BigDecimal standardHoursPerWeek) {
		this.standardHoursPerWeek = standardHoursPerWeek;
	}

	public String getJobLevel() {
		return jobLevel;
	}

	public void setJobLevel(String jobLevel) {
		this.jobLevel = jobLevel;
	}

	public String getJobCategory() {
		return jobCategory;
	}

	public void setJobCategory(String jobCategory) {
		this.jobCategory = jobCategory;
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
