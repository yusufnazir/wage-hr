package com.wagepayroll.payroll.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cycle detection and topological ordering for wage component dependency graphs.
 */
public final class ComponentDependencyValidation {

	private ComponentDependencyValidation() {
	}

	/**
	 * @param prerequisiteToDependents map prerequisite node → dependent nodes (edge P before D)
	 */
	public static boolean hasCycle(Map<UUID, Set<UUID>> prerequisiteToDependents, Set<UUID> allNodes) {
		return topologicalOrder(prerequisiteToDependents, allNodes, null).size() < allNodes.size();
	}

	/**
	 * Topological order with optional tie-breaker when multiple nodes are ready.
	 */
	public static List<UUID> topologicalOrder(Map<UUID, Set<UUID>> prerequisiteToDependents, Set<UUID> allNodes,
			Comparator<UUID> tieBreaker) {
		Map<UUID, Integer> inDegree = new HashMap<>();
		for (UUID node : allNodes) {
			inDegree.put(node, 0);
		}
		for (Map.Entry<UUID, Set<UUID>> e : prerequisiteToDependents.entrySet()) {
			UUID prerequisite = e.getKey();
			if (!allNodes.contains(prerequisite)) {
				continue;
			}
			for (UUID dependent : e.getValue()) {
				if (!allNodes.contains(dependent)) {
					continue;
				}
				inDegree.merge(dependent, 1, Integer::sum);
			}
		}
		Comparator<UUID> order = tieBreaker != null ? tieBreaker : Comparator.comparing(UUID::toString);
		List<UUID> ready = new ArrayList<>();
		for (UUID node : allNodes) {
			if (inDegree.getOrDefault(node, 0) == 0) {
				ready.add(node);
			}
		}
		ready.sort(order);
		Deque<UUID> queue = new ArrayDeque<>(ready);
		List<UUID> sorted = new ArrayList<>();
		while (!queue.isEmpty()) {
			UUID node = queue.removeFirst();
			sorted.add(node);
			Set<UUID> dependents = prerequisiteToDependents.getOrDefault(node, Set.of());
			List<UUID> unlocked = new ArrayList<>();
			for (UUID dependent : dependents) {
				if (!allNodes.contains(dependent)) {
					continue;
				}
				int remaining = inDegree.merge(dependent, -1, Integer::sum);
				if (remaining == 0) {
					unlocked.add(dependent);
				}
			}
			unlocked.sort(order);
			queue.addAll(unlocked);
		}
		return sorted;
	}
}
