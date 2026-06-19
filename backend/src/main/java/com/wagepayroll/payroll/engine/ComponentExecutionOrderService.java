package com.wagepayroll.payroll.engine;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.wagepayroll.domain.wagecomponent.TenantWageComponentDependencyEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;

@Service
public class ComponentExecutionOrderService {

	public List<TenantWageComponentEntity> sortForExecution(List<TenantWageComponentEntity> components,
			List<TenantWageComponentDependencyEntity> edges) {
		if (components == null || components.isEmpty()) {
			return List.of();
		}
		Set<UUID> componentIds = components.stream().map(TenantWageComponentEntity::getId).collect(Collectors.toSet());
		List<TenantWageComponentDependencyEntity> relevant = edges == null ? List.of() : edges.stream()
				.filter(e -> componentIds.contains(e.getTenantWageComponentId())
						&& componentIds.contains(e.getDependsOnTenantWageComponentId()))
				.toList();
		if (relevant.isEmpty()) {
			return components.stream().sorted(processingOrderTieBreak()).toList();
		}
		Map<UUID, Set<UUID>> prerequisiteToDependents = new HashMap<>();
		for (TenantWageComponentDependencyEntity edge : relevant) {
			prerequisiteToDependents
					.computeIfAbsent(edge.getDependsOnTenantWageComponentId(), k -> new HashSet<>())
					.add(edge.getTenantWageComponentId());
		}
		Map<UUID, TenantWageComponentEntity> byId = components.stream()
				.collect(Collectors.toMap(TenantWageComponentEntity::getId, c -> c, (a, b) -> a));
		List<UUID> orderedIds = ComponentDependencyValidation.topologicalOrder(prerequisiteToDependents, componentIds,
				Comparator.comparing((UUID id) -> byId.get(id).getProcessingOrder())
						.thenComparing(id -> byId.get(id).getCode(), String.CASE_INSENSITIVE_ORDER));
		if (orderedIds.size() < componentIds.size()) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "PAYROLL_DEPENDENCY_CYCLE");
		}
		return orderedIds.stream().map(byId::get).toList();
	}

	private static Comparator<TenantWageComponentEntity> processingOrderTieBreak() {
		return Comparator.comparing(TenantWageComponentEntity::getProcessingOrder)
				.thenComparing(TenantWageComponentEntity::getCode, String.CASE_INSENSITIVE_ORDER);
	}
}
