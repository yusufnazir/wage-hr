package com.wagepayroll.payroll.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ComponentDependencyValidationTest {

	@Test
	void detectsCycle() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		Map<UUID, Set<UUID>> edges = Map.of(a, Set.of(b), b, Set.of(a));
		assertThat(ComponentDependencyValidation.hasCycle(edges, Set.of(a, b))).isTrue();
	}

	@Test
	void topologicalOrderRespectsPrerequisite() {
		UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
		UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
		Map<UUID, Set<UUID>> edges = Map.of(a, Set.of(b));
		var order = ComponentDependencyValidation.topologicalOrder(edges, Set.of(a, b), null);
		assertThat(order.indexOf(a)).isLessThan(order.indexOf(b));
	}
}
