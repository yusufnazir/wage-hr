package com.wagepayroll.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.payroll.country.SurinameCountryRuleKeys;
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;
import com.wagepayroll.payroll.engine.PayrollRunState;
import com.wagepayroll.payroll.model.CalculationMethod;

/**
 * Writes computed per-employee net pay onto informational {@code SUR_NET_WAGE} lines (template 1026)
 * so previews and payslips show the same figure as {@link NetPayCalculator}.
 */
@Component
public class NetWageLineSynchronizer {

	private static final RoundingMode ROUND = RoundingMode.HALF_UP;

	private final TenantWageComponentRepository tenantWageComponentRepository;

	public NetWageLineSynchronizer(TenantWageComponentRepository tenantWageComponentRepository) {
		this.tenantWageComponentRepository = tenantWageComponentRepository;
	}

	public void sync(PayrollRunState state, Map<UUID, BigDecimal> netByEmployee) {
		if (netByEmployee == null || netByEmployee.isEmpty()) {
			return;
		}
		var context = state.context();
		List<TenantWageComponentEntity> netComponents = tenantWageComponentRepository
				.findByTenantIdAndCompanyIdAndActiveIsTrueOrderByProcessingOrderAsc(context.tenantId(),
						context.companyId())
				.stream()
				.filter(c -> SurinameCountryRuleKeys.isNetWageDisplayKey(c.getCountryRuleKey()))
				.toList();
		if (netComponents.isEmpty()) {
			return;
		}
		Map<String, EvaluatedComponentAmount> byKey = new HashMap<>();
		List<EvaluatedComponentAmount> others = new ArrayList<>();
		for (EvaluatedComponentAmount line : state.evaluatedComponentAmounts()) {
			if (line.tenantWageComponentId() != null && isNetWageComponent(line.tenantWageComponentId(), netComponents)) {
				byKey.put(lineKey(line.employeeId(), line.tenantWageComponentId()), line);
			}
			else {
				others.add(line);
			}
		}
		for (UUID employeeId : context.employeeIds()) {
			BigDecimal net = netByEmployee.getOrDefault(employeeId, BigDecimal.ZERO).setScale(4, ROUND);
			for (TenantWageComponentEntity comp : netComponents) {
				byKey.put(lineKey(employeeId, comp.getId()),
						EvaluatedComponentAmount.tenant(employeeId, comp.getId(), comp.getCode(),
								CalculationMethod.FIXED_AMOUNT.name(), net, comp.getFormulaExpression()));
			}
		}
		others.addAll(byKey.values());
		state.evaluatedComponentAmounts().clear();
		state.evaluatedComponentAmounts().addAll(others);
	}

	private static boolean isNetWageComponent(UUID componentId, List<TenantWageComponentEntity> netComponents) {
		for (TenantWageComponentEntity comp : netComponents) {
			if (comp.getId().equals(componentId)) {
				return true;
			}
		}
		return false;
	}

	private static String lineKey(UUID employeeId, UUID componentId) {
		return employeeId + ":" + componentId;
	}

}
