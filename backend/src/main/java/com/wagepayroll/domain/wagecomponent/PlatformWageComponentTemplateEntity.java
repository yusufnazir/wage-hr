package com.wagepayroll.domain.wagecomponent;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.AbstractUuidEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "platform_wage_component_template")
public class PlatformWageComponentTemplateEntity extends AbstractUuidEntity {

	@Column(name = "country_code", nullable = false, length = 2, columnDefinition = "CHAR(2)")
	private String countryCode;

	@Column(name = "template_code", nullable = false, length = 64)
	private String templateCode;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "definition_defaults_json", nullable = false, length = 4000)
	private String definitionDefaultsJson;

	/** Sort key for template and wage-component lists (lower first); not payroll engine execution order. */
	@Column(name = "processing_order_hint")
	private Integer processingOrderHint;

	@Column(name = "phase_hint", length = 20)
	private String phaseHint;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "debit_platform_ledger_template_id", length = 36)
	private UUID debitPlatformLedgerTemplateId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "credit_platform_ledger_template_id", length = 36)
	private UUID creditPlatformLedgerTemplateId;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Column(name = "duplicable", nullable = false)
	private boolean duplicable = true;

	@Column(name = "print_on_payslip", nullable = false)
	private boolean printOnPayslip = true;

	@Column(name = "auxiliary", nullable = false)
	private boolean auxiliary;

	@Column(name = "apply_in_payroll", nullable = false)
	private boolean applyInPayroll = true;

	@Column(name = "recurrence", length = 20)
	private String recurrence;

	@Column(name = "country_rule_key", length = 64)
	private String countryRuleKey;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "platform_country_tax_rule_id", length = 36)
	private UUID platformCountryTaxRuleId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public String getCountryCode() {
		return countryCode;
	}

	public void setCountryCode(String countryCode) {
		this.countryCode = countryCode;
	}

	public String getTemplateCode() {
		return templateCode;
	}

	public void setTemplateCode(String templateCode) {
		this.templateCode = templateCode;
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

	public String getDefinitionDefaultsJson() {
		return definitionDefaultsJson;
	}

	public void setDefinitionDefaultsJson(String definitionDefaultsJson) {
		this.definitionDefaultsJson = definitionDefaultsJson;
	}

	public Integer getProcessingOrderHint() {
		return processingOrderHint;
	}

	public void setProcessingOrderHint(Integer processingOrderHint) {
		this.processingOrderHint = processingOrderHint;
	}

	public String getPhaseHint() {
		return phaseHint;
	}

	public void setPhaseHint(String phaseHint) {
		this.phaseHint = phaseHint;
	}

	public UUID getDebitPlatformLedgerTemplateId() {
		return debitPlatformLedgerTemplateId;
	}

	public void setDebitPlatformLedgerTemplateId(UUID debitPlatformLedgerTemplateId) {
		this.debitPlatformLedgerTemplateId = debitPlatformLedgerTemplateId;
	}

	public UUID getCreditPlatformLedgerTemplateId() {
		return creditPlatformLedgerTemplateId;
	}

	public void setCreditPlatformLedgerTemplateId(UUID creditPlatformLedgerTemplateId) {
		this.creditPlatformLedgerTemplateId = creditPlatformLedgerTemplateId;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean isDuplicable() {
		return duplicable;
	}

	public void setDuplicable(boolean duplicable) {
		this.duplicable = duplicable;
	}

	public boolean isPrintOnPayslip() {
		return printOnPayslip;
	}

	public void setPrintOnPayslip(boolean printOnPayslip) {
		this.printOnPayslip = printOnPayslip;
	}

	public boolean isAuxiliary() {
		return auxiliary;
	}

	public void setAuxiliary(boolean auxiliary) {
		this.auxiliary = auxiliary;
	}

	public boolean isApplyInPayroll() {
		return applyInPayroll;
	}

	public void setApplyInPayroll(boolean applyInPayroll) {
		this.applyInPayroll = applyInPayroll;
	}

	public String getRecurrence() {
		return recurrence;
	}

	public void setRecurrence(String recurrence) {
		this.recurrence = recurrence;
	}

	public String getCountryRuleKey() {
		return countryRuleKey;
	}

	public void setCountryRuleKey(String countryRuleKey) {
		this.countryRuleKey = countryRuleKey;
	}

	public UUID getPlatformCountryTaxRuleId() {
		return platformCountryTaxRuleId;
	}

	public void setPlatformCountryTaxRuleId(UUID platformCountryTaxRuleId) {
		this.platformCountryTaxRuleId = platformCountryTaxRuleId;
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
