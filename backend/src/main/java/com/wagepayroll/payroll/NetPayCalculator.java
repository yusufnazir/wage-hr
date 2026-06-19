package com.wagepayroll.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.wagepayroll.domain.wagecomponent.PlatformWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;
import com.wagepayroll.payroll.engine.EvaluatedComponentSource;
import com.wagepayroll.payroll.engine.PayrollRunState;
import com.wagepayroll.payroll.model.NetEffect;

/**
 * Computes per-employee NET pay from evaluated tenant + statutory lines using each component's {@link NetEffect}.
 */
@Component
public class NetPayCalculator {

	private final TenantWageComponentRepository tenantWageComponentRepository;
	private final PlatformWageComponentRepository platformWageComponentRepository;

	public NetPayCalculator(TenantWageComponentRepository tenantWageComponentRepository,
			PlatformWageComponentRepository platformWageComponentRepository) {
		this.tenantWageComponentRepository = tenantWageComponentRepository;
		this.platformWageComponentRepository = platformWageComponentRepository;
	}

	public Map<UUID, BigDecimal> compute(PayrollRunState state) {
		List<EvaluatedComponentAmount> tenantLines = state.evaluatedComponentAmounts();
		List<EvaluatedComponentAmount> statutoryLines = state.statutoryEvaluatedAmounts();
		if (tenantLines.isEmpty() && statutoryLines.isEmpty()) {
			return Map.of();
		}
		Map<UUID, TenantWageComponentEntity> tenantById = loadTenantComponents(tenantLines);
		Map<UUID, PlatformWageComponentEntity> platformById = loadPlatformComponents(statutoryLines);
		Map<UUID, BigDecimal> netByEmployee = new HashMap<>();
		for (EvaluatedComponentAmount line : tenantLines) {
			if (line.evaluatedAmount() == null || line.tenantWageComponentId() == null) {
				continue;
			}
			TenantWageComponentEntity comp = tenantById.get(line.tenantWageComponentId());
			if (comp == null) {
				continue;
			}
			apply(netByEmployee, line.employeeId(), line.evaluatedAmount(), comp.getNetEffect());
		}
		for (EvaluatedComponentAmount line : statutoryLines) {
			if (line.evaluatedAmount() == null || line.platformWageComponentId() == null) {
				continue;
			}
			PlatformWageComponentEntity comp = platformById.get(line.platformWageComponentId());
			if (comp == null) {
				continue;
			}
			apply(netByEmployee, line.employeeId(), line.evaluatedAmount(), comp.getNetEffect());
		}
		netByEmployee.replaceAll((id, amount) -> amount.setScale(4, RoundingMode.HALF_UP));
		return Map.copyOf(netByEmployee);
	}

	private static void apply(Map<UUID, BigDecimal> netByEmployee, UUID employeeId, BigDecimal amount,
			NetEffect netEffect) {
		if (netEffect == null || netEffect == NetEffect.NO_EFFECT || amount == null) {
			return;
		}
		BigDecimal signed = netEffect == NetEffect.SUBTRACT_FROM_NET ? amount.negate() : amount;
		netByEmployee.merge(employeeId, signed, BigDecimal::add);
	}

	private Map<UUID, TenantWageComponentEntity> loadTenantComponents(List<EvaluatedComponentAmount> lines) {
		Set<UUID> ids = new HashSet<>();
		for (EvaluatedComponentAmount line : lines) {
			if (line.componentSource() == EvaluatedComponentSource.TENANT && line.tenantWageComponentId() != null) {
				ids.add(line.tenantWageComponentId());
			}
		}
		if (ids.isEmpty()) {
			return Map.of();
		}
		return tenantWageComponentRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(TenantWageComponentEntity::getId, Function.identity()));
	}

	private Map<UUID, PlatformWageComponentEntity> loadPlatformComponents(List<EvaluatedComponentAmount> lines) {
		Set<UUID> ids = new HashSet<>();
		for (EvaluatedComponentAmount line : lines) {
			if (line.componentSource() == EvaluatedComponentSource.PLATFORM && line.platformWageComponentId() != null) {
				ids.add(line.platformWageComponentId());
			}
		}
		if (ids.isEmpty()) {
			return Map.of();
		}
		return platformWageComponentRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(PlatformWageComponentEntity::getId, Function.identity()));
	}
}
