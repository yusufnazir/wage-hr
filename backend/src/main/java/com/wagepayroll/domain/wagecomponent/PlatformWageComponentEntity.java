package com.wagepayroll.domain.wagecomponent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.wagepayroll.domain.AbstractUuidEntity;
import com.wagepayroll.payroll.model.CalculationMethod;
import com.wagepayroll.payroll.model.ComponentType;
import com.wagepayroll.payroll.model.NetEffect;
import com.wagepayroll.payroll.model.PayrollImpactSide;
import com.wagepayroll.payroll.model.PayrollPhase;
import com.wagepayroll.payroll.model.RoundingStrategy;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "platform_wage_component")
public class PlatformWageComponentEntity extends AbstractUuidEntity {

	@Column(name = "country_code", nullable = false, length = 2, columnDefinition = "CHAR(2)")
	private String countryCode;

	@Column(name = "code", nullable = false, length = 64)
	private String code;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "statutory", nullable = false)
	private boolean statutory;

	@Enumerated(EnumType.STRING)
	@Column(name = "component_type", nullable = false, length = 30)
	private ComponentType componentType;

	@Column(name = "category", nullable = false, length = 40)
	private String category;

	@Enumerated(EnumType.STRING)
	@Column(name = "net_effect", nullable = false, length = 20)
	private NetEffect netEffect;

	@Enumerated(EnumType.STRING)
	@Column(name = "impact_side", nullable = false, length = 20)
	private PayrollImpactSide impactSide = PayrollImpactSide.EMPLOYEE;

	@Column(name = "taxable_wage_tax", nullable = false)
	private boolean taxableWageTax;

	@Column(name = "taxable_social_security", nullable = false)
	private boolean taxableSocialSecurity;

	@Column(name = "taxable_pension", nullable = false)
	private boolean taxablePension;

	@Column(name = "taxable_vacation_reserve", nullable = false)
	private boolean taxableVacationReserve;

	@Enumerated(EnumType.STRING)
	@Column(name = "calculation_method", nullable = false, length = 30)
	private CalculationMethod calculationMethod;

	@Column(name = "percentage_base", length = 40)
	private String percentageBase;

	@Column(name = "formula_expression", length = 500)
	private String formulaExpression;

	@Column(name = "default_amount", precision = 19, scale = 4)
	private BigDecimal defaultAmount;

	@Enumerated(EnumType.STRING)
	@Column(name = "rounding_strategy", nullable = false, length = 30)
	private RoundingStrategy roundingStrategy = RoundingStrategy.HALF_UP;

	@Column(name = "processing_order", nullable = false)
	private int processingOrder;

	@Enumerated(EnumType.STRING)
	@Column(name = "phase", nullable = false, length = 20)
	private PayrollPhase phase;

	@Column(name = "maintains_balance", nullable = false)
	private boolean maintainsBalance;

	@Column(name = "balance_type", length = 20)
	private String balanceType;

	@Column(name = "balance_direction", length = 10)
	private String balanceDirection;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "counter_component_id", length = 36)
	private UUID counterComponentId;

	@Column(name = "debit_ledger_account_code", length = 64)
	private String debitLedgerAccountCode;

	@Column(name = "credit_ledger_account_code", length = 64)
	private String creditLedgerAccountCode;

	@Column(name = "posting_strategy", length = 40)
	private String postingStrategy;

	@Column(name = "country_specific_settings_json", length = 4000)
	private String countrySpecificSettingsJson;

	@Column(name = "effective_from")
	private LocalDate effectiveFrom;

	@Column(name = "effective_to")
	private LocalDate effectiveTo;

	@Column(name = "active", nullable = false)
	private boolean active = true;

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

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
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

	public boolean isStatutory() {
		return statutory;
	}

	public void setStatutory(boolean statutory) {
		this.statutory = statutory;
	}

	public ComponentType getComponentType() {
		return componentType;
	}

	public void setComponentType(ComponentType componentType) {
		this.componentType = componentType;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public NetEffect getNetEffect() {
		return netEffect;
	}

	public void setNetEffect(NetEffect netEffect) {
		this.netEffect = netEffect;
	}

	public PayrollImpactSide getImpactSide() {
		return impactSide;
	}

	public void setImpactSide(PayrollImpactSide impactSide) {
		this.impactSide = impactSide;
	}

	public boolean isTaxableWageTax() {
		return taxableWageTax;
	}

	public void setTaxableWageTax(boolean taxableWageTax) {
		this.taxableWageTax = taxableWageTax;
	}

	public boolean isTaxableSocialSecurity() {
		return taxableSocialSecurity;
	}

	public void setTaxableSocialSecurity(boolean taxableSocialSecurity) {
		this.taxableSocialSecurity = taxableSocialSecurity;
	}

	public boolean isTaxablePension() {
		return taxablePension;
	}

	public void setTaxablePension(boolean taxablePension) {
		this.taxablePension = taxablePension;
	}

	public boolean isTaxableVacationReserve() {
		return taxableVacationReserve;
	}

	public void setTaxableVacationReserve(boolean taxableVacationReserve) {
		this.taxableVacationReserve = taxableVacationReserve;
	}

	public CalculationMethod getCalculationMethod() {
		return calculationMethod;
	}

	public void setCalculationMethod(CalculationMethod calculationMethod) {
		this.calculationMethod = calculationMethod;
	}

	public String getPercentageBase() {
		return percentageBase;
	}

	public void setPercentageBase(String percentageBase) {
		this.percentageBase = percentageBase;
	}

	public String getFormulaExpression() {
		return formulaExpression;
	}

	public void setFormulaExpression(String formulaExpression) {
		this.formulaExpression = formulaExpression;
	}

	public BigDecimal getDefaultAmount() {
		return defaultAmount;
	}

	public void setDefaultAmount(BigDecimal defaultAmount) {
		this.defaultAmount = defaultAmount;
	}

	public RoundingStrategy getRoundingStrategy() {
		return roundingStrategy;
	}

	public void setRoundingStrategy(RoundingStrategy roundingStrategy) {
		this.roundingStrategy = roundingStrategy;
	}

	public int getProcessingOrder() {
		return processingOrder;
	}

	public void setProcessingOrder(int processingOrder) {
		this.processingOrder = processingOrder;
	}

	public PayrollPhase getPhase() {
		return phase;
	}

	public void setPhase(PayrollPhase phase) {
		this.phase = phase;
	}

	public boolean isMaintainsBalance() {
		return maintainsBalance;
	}

	public void setMaintainsBalance(boolean maintainsBalance) {
		this.maintainsBalance = maintainsBalance;
	}

	public String getBalanceType() {
		return balanceType;
	}

	public void setBalanceType(String balanceType) {
		this.balanceType = balanceType;
	}

	public String getBalanceDirection() {
		return balanceDirection;
	}

	public void setBalanceDirection(String balanceDirection) {
		this.balanceDirection = balanceDirection;
	}

	public UUID getCounterComponentId() {
		return counterComponentId;
	}

	public void setCounterComponentId(UUID counterComponentId) {
		this.counterComponentId = counterComponentId;
	}

	public String getDebitLedgerAccountCode() {
		return debitLedgerAccountCode;
	}

	public void setDebitLedgerAccountCode(String debitLedgerAccountCode) {
		this.debitLedgerAccountCode = debitLedgerAccountCode;
	}

	public String getCreditLedgerAccountCode() {
		return creditLedgerAccountCode;
	}

	public void setCreditLedgerAccountCode(String creditLedgerAccountCode) {
		this.creditLedgerAccountCode = creditLedgerAccountCode;
	}

	public String getPostingStrategy() {
		return postingStrategy;
	}

	public void setPostingStrategy(String postingStrategy) {
		this.postingStrategy = postingStrategy;
	}

	public String getCountrySpecificSettingsJson() {
		return countrySpecificSettingsJson;
	}

	public void setCountrySpecificSettingsJson(String countrySpecificSettingsJson) {
		this.countrySpecificSettingsJson = countrySpecificSettingsJson;
	}

	public LocalDate getEffectiveFrom() {
		return effectiveFrom;
	}

	public void setEffectiveFrom(LocalDate effectiveFrom) {
		this.effectiveFrom = effectiveFrom;
	}

	public LocalDate getEffectiveTo() {
		return effectiveTo;
	}

	public void setEffectiveTo(LocalDate effectiveTo) {
		this.effectiveTo = effectiveTo;
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
