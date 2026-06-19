package com.wagepayroll.payroll.base;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.wagepayroll.domain.payrollbase.PlatformPayrollBaseEntity;
import com.wagepayroll.domain.payrollbase.PlatformPayrollBaseRepository;
import com.wagepayroll.domain.payrollbase.TenantWageComponentBaseEffectEntity;
import com.wagepayroll.domain.payrollbase.TenantWageComponentBaseEffectRepository;
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;
import com.wagepayroll.payroll.model.PayrollBaseEffectCalculationType;
import com.wagepayroll.payroll.model.PayrollBaseEffectDirection;

@Service
public class PayrollBaseAccumulator {

	private static final MathContext MC = MathContext.DECIMAL64;
	private static final BigDecimal HUNDRED = new BigDecimal("100");

	private final TenantWageComponentBaseEffectRepository tenantEffectRepository;
	private final PlatformPayrollBaseRepository payrollBaseRepository;

	public PayrollBaseAccumulator(TenantWageComponentBaseEffectRepository tenantEffectRepository,
			PlatformPayrollBaseRepository payrollBaseRepository) {
		this.tenantEffectRepository = tenantEffectRepository;
		this.payrollBaseRepository = payrollBaseRepository;
	}

	public Map<UUID, Map<String, BigDecimal>> accumulateForEmployees(UUID tenantId,
			List<EvaluatedComponentAmount> evaluatedAmounts) {
		return accumulateDetailed(tenantId, evaluatedAmounts).totalsByEmployee();
	}

	public PayrollBaseAccumulationResult accumulateDetailed(UUID tenantId,
			List<EvaluatedComponentAmount> evaluatedAmounts) {
		if (evaluatedAmounts == null || evaluatedAmounts.isEmpty()) {
			return PayrollBaseAccumulationResult.empty();
		}
		Map<UUID, String> baseCodeById = payrollBaseRepository.findByActiveIsTrueOrderByCodeAsc().stream()
				.collect(Collectors.toMap(PlatformPayrollBaseEntity::getId, PlatformPayrollBaseEntity::getCode));
		List<UUID> componentIds = evaluatedAmounts.stream().map(EvaluatedComponentAmount::tenantWageComponentId).distinct()
				.toList();
		List<TenantWageComponentBaseEffectEntity> effects = tenantEffectRepository
				.findByTenantIdAndTenantWageComponentIdInAndActiveIsTrue(tenantId, componentIds);
		Map<UUID, List<TenantWageComponentBaseEffectEntity>> effectsByComponent = effects.stream()
				.collect(Collectors.groupingBy(TenantWageComponentBaseEffectEntity::getTenantWageComponentId));

		Map<UUID, Map<String, BigDecimal>> totalsByEmployee = new HashMap<>();
		Map<UUID, Map<String, List<PayrollBaseContribution>>> contributionsByEmployee = new HashMap<>();
		for (EvaluatedComponentAmount ev : evaluatedAmounts) {
			if (ev.evaluatedAmount() == null) {
				continue;
			}
			List<TenantWageComponentBaseEffectEntity> componentEffects = effectsByComponent
					.getOrDefault(ev.tenantWageComponentId(), List.of());
			if (componentEffects.isEmpty()) {
				continue;
			}
			Map<String, BigDecimal> totals = totalsByEmployee.computeIfAbsent(ev.employeeId(), k -> new HashMap<>());
			Map<String, List<PayrollBaseContribution>> contributions = contributionsByEmployee
					.computeIfAbsent(ev.employeeId(), k -> new HashMap<>());
			BigDecimal componentAmount = ev.evaluatedAmount().setScale(4, RoundingMode.HALF_UP);
			for (TenantWageComponentBaseEffectEntity effect : componentEffects) {
				String baseCode = baseCodeById.get(effect.getPlatformPayrollBaseId());
				if (baseCode == null || effect.getEffectDirection() == PayrollBaseEffectDirection.IGNORE) {
					continue;
				}
				BigDecimal delta = resolveDelta(ev.evaluatedAmount(), effect);
				if (delta == null || delta.signum() == 0) {
					continue;
				}
				if (effect.getEffectDirection() == PayrollBaseEffectDirection.DECREASE) {
					delta = delta.negate();
				}
				BigDecimal signedDelta = delta.setScale(4, RoundingMode.HALF_UP);
				totals.merge(baseCode, signedDelta, BigDecimal::add);
				contributions.computeIfAbsent(baseCode, ignored -> new ArrayList<>())
						.add(new PayrollBaseContribution(ev.tenantWageComponentCode(), baseCode,
								effect.getEffectDirection(), componentAmount, signedDelta));
			}
		}
		totalsByEmployee.replaceAll((id, map) -> Collections.unmodifiableMap(scaleMap(map)));
		contributionsByEmployee.replaceAll((id, map) -> {
			Map<String, List<PayrollBaseContribution>> frozen = new HashMap<>();
			for (Map.Entry<String, List<PayrollBaseContribution>> entry : map.entrySet()) {
				frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
			}
			return Collections.unmodifiableMap(frozen);
		});
		return PayrollBaseAccumulationResult.of(totalsByEmployee, contributionsByEmployee);
	}

	private static BigDecimal resolveDelta(BigDecimal componentAmount, TenantWageComponentBaseEffectEntity effect) {
		return switch (effect.getEffectCalculationType()) {
			case FULL -> {
				BigDecimal pct = effect.getEffectValue() != null ? effect.getEffectValue() : HUNDRED;
				yield componentAmount.multiply(pct, MC).divide(HUNDRED, MC);
			}
			case PERCENTAGE -> {
				if (effect.getEffectValue() == null) {
					yield BigDecimal.ZERO;
				}
				yield componentAmount.multiply(effect.getEffectValue(), MC).divide(HUNDRED, MC);
			}
			case FIXED -> effect.getEffectValue() != null ? effect.getEffectValue() : BigDecimal.ZERO;
			case FORMULA -> null;
		};
	}

	private static Map<String, BigDecimal> scaleMap(Map<String, BigDecimal> map) {
		Map<String, BigDecimal> scaled = new HashMap<>();
		for (Map.Entry<String, BigDecimal> e : map.entrySet()) {
			scaled.put(e.getKey(), e.getValue().setScale(4, RoundingMode.HALF_UP));
		}
		return scaled;
	}
}
