package com.wagepayroll.payroll.country;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Suriname tax rules in force for {@link #asOf()}, keyed by {@code rule_code}.
 */
public record SurinameTaxRulesSnapshot(LocalDate asOf, Map<String, ResolvedSurinameTaxRule> rulesByCode) {

	public SurinameTaxRulesSnapshot {
		rulesByCode = Map.copyOf(new TreeMap<>(rulesByCode));
	}

	public ObjectNode toJsonObject(ObjectMapper mapper) {
		ObjectNode root = mapper.createObjectNode();
		root.put("asOf", asOf.toString());
		ObjectNode rules = mapper.createObjectNode();
		for (var e : rulesByCode.entrySet()) {
			ResolvedSurinameTaxRule r = e.getValue();
			ObjectNode o = mapper.createObjectNode();
			o.put("id", r.id().toString());
			o.put("ruleCode", r.ruleCode());
			o.put("name", r.name());
			o.put("effectiveFrom", r.effectiveFrom().toString());
			if (r.effectiveTo() != null) {
				o.put("effectiveTo", r.effectiveTo().toString());
			}
			else {
				o.putNull("effectiveTo");
			}
			o.set("parameters", r.parameters());
			rules.set(e.getKey(), o);
		}
		root.set("rules", rules);
		return root;
	}

	public String toJsonString(ObjectMapper mapper) {
		try {
			return mapper.writeValueAsString(toJsonObject(mapper));
		}
		catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
			throw new IllegalStateException("failed to serialize Suriname tax rules snapshot", ex);
		}
	}
}
