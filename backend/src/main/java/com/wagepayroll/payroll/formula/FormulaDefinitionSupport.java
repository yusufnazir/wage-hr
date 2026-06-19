package com.wagepayroll.payroll.formula;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wagepayroll.payroll.model.CalculationMethod;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class FormulaDefinitionSupport {

	public static final int MAX_RULES = 20;

	private final ObjectMapper objectMapper;
	private final WageComponentFormulaValidator wageComponentFormulaValidator;

	public FormulaDefinitionSupport(ObjectMapper objectMapper, WageComponentFormulaValidator wageComponentFormulaValidator) {
		this.objectMapper = objectMapper;
		this.wageComponentFormulaValidator = wageComponentFormulaValidator;
	}

	public FormulaDefinitionConfig parseStoredExpression(String raw) {
		if (!StringUtils.hasText(raw)) {
			return new FormulaDefinitionConfig(null, List.of(), null, null);
		}
		String trimmed = raw.trim();
		if (trimmed.startsWith("{")) {
			try {
				JsonNode root = objectMapper.readTree(trimmed);
				if (root.has("formulaMode") || root.has("formulaRules")) {
					return fromJsonNode(root);
				}
			}
			catch (Exception ex) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FORMULA_DEFINITION");
			}
		}
		return new FormulaDefinitionConfig(null, List.of(), null, trimmed);
	}

	public FormulaDefinitionConfig fromDefinitionFields(String formulaMode, List<FormulaRuleDefinition> rules,
			String defaultFormulaExpression, String legacyFormulaExpression) {
		return new FormulaDefinitionConfig(trimToNull(formulaMode), rules != null ? List.copyOf(rules) : List.of(),
				trimToNull(defaultFormulaExpression), trimToNull(legacyFormulaExpression));
	}

	public String toStoredExpression(FormulaDefinitionConfig config) {
		if (config == null) {
			return null;
		}
		if (config.isCriteriaRules()) {
			try {
				var node = objectMapper.createObjectNode();
				node.put("formulaMode", FormulaDefinitionConfig.MODE_CRITERIA_RULES);
				var rulesNode = objectMapper.createArrayNode();
				for (FormulaRuleDefinition rule : config.formulaRules()) {
					if (rule.formulaExpression() == null || rule.formulaExpression().isBlank()) {
						continue;
					}
					String ruleExpr = rule.formulaExpression().trim();
					String defaultExpr = config.effectiveDefaultFormula();
					if (defaultExpr != null && ruleExpr.equals(defaultExpr.trim())) {
						continue;
					}
					var r = objectMapper.createObjectNode();
					r.put("criteriaType", rule.criteriaType().name());
					r.put("itemKey", rule.itemKey());
					r.put("formulaExpression", ruleExpr);
					rulesNode.add(r);
				}
				node.set("formulaRules", rulesNode);
				if (config.defaultFormulaExpression() != null) {
					node.put("defaultFormulaExpression", config.defaultFormulaExpression());
				}
				return objectMapper.writeValueAsString(node);
			}
			catch (Exception ex) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FORMULA_DEFINITION", ex);
			}
		}
		String legacy = config.legacyFormulaExpression();
		if (legacy != null && !legacy.isBlank()) {
			return legacy.trim();
		}
		return config.effectiveDefaultFormula();
	}

	public void validate(CalculationMethod method, String percentageBase, FormulaDefinitionConfig config,
			Set<String> prerequisiteComponentCodes) {
		if (method != CalculationMethod.FORMULA) {
			wageComponentFormulaValidator.validate(method, percentageBase, null, prerequisiteComponentCodes);
			return;
		}
		if (config == null || (!config.isCriteriaRules()
				&& (config.legacyFormulaExpression() == null || config.legacyFormulaExpression().isBlank())
				&& (config.defaultFormulaExpression() == null || config.defaultFormulaExpression().isBlank()))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FORMULA_REQUIRED");
		}
		if (!config.isCriteriaRules()) {
			String expr = config.legacyFormulaExpression() != null ? config.legacyFormulaExpression()
					: config.defaultFormulaExpression();
			wageComponentFormulaValidator.validate(method, percentageBase, expr, prerequisiteComponentCodes);
			return;
		}
		if (config.formulaRules().size() > MAX_RULES) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FORMULA_RULES_TOO_MANY");
		}
		Set<String> seen = new HashSet<>();
		for (FormulaRuleDefinition rule : config.formulaRules()) {
			if (rule.criteriaType() == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FORMULA_CRITERIA_TYPE_REQUIRED");
			}
			if (rule.itemKey() == null || rule.itemKey().isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FORMULA_CRITERIA_ITEM_REQUIRED");
			}
			String dedupeKey = rule.criteriaType().name() + ":" + rule.itemKey().trim().toUpperCase(Locale.ROOT);
			if (!seen.add(dedupeKey)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FORMULA_RULE_DUPLICATE");
			}
			wageComponentFormulaValidator.validate(method, percentageBase, rule.formulaExpression(),
					prerequisiteComponentCodes);
		}
		String defaultExpr = config.effectiveDefaultFormula();
		if (defaultExpr == null || defaultExpr.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FORMULA_DEFAULT_REQUIRED");
		}
		wageComponentFormulaValidator.validate(method, percentageBase, defaultExpr, prerequisiteComponentCodes);
	}

	public FormulaDefinitionConfig configFrom(String formulaMode, List<FormulaRuleJson> rules,
			String defaultFormulaExpression, String legacyFormulaExpression) {
		List<FormulaRuleDefinition> parsed = new ArrayList<>();
		if (rules != null) {
			for (FormulaRuleJson r : rules) {
				if (r == null || r.criteriaType == null) {
					continue;
				}
				parsed.add(new FormulaRuleDefinition(FormulaCriteriaType.parse(r.criteriaType), r.itemKey, r.itemLabel,
						r.formulaExpression));
			}
		}
		return fromDefinitionFields(formulaMode, parsed, defaultFormulaExpression, legacyFormulaExpression);
	}

	public static List<FormulaRuleDefinition> baseSalaryWageTypeRules() {
		List<FormulaRuleDefinition> rules = new ArrayList<>();
		rules.add(new FormulaRuleDefinition(FormulaCriteriaType.WAGE_TYPE, "PER_HOUR", "Per hour",
				"transaction.quantity * transaction.rate"));
		rules.add(new FormulaRuleDefinition(FormulaCriteriaType.WAGE_TYPE, "PER_PERIOD", "Per period",
				"compensation.periodic_rate"));
		return List.copyOf(rules);
	}

	private FormulaDefinitionConfig fromJsonNode(JsonNode root) {
		String mode = root.path("formulaMode").asText(null);
		List<FormulaRuleDefinition> rules = new ArrayList<>();
		if (root.has("formulaRules") && root.get("formulaRules").isArray()) {
			for (JsonNode n : root.get("formulaRules")) {
				String typeRaw = n.path("criteriaType").asText(null);
				if (typeRaw == null) {
					continue;
				}
				rules.add(new FormulaRuleDefinition(FormulaCriteriaType.parse(typeRaw),
						n.path("itemKey").asText(null), n.path("itemLabel").asText(null),
						n.path("formulaExpression").asText(null)));
			}
		}
		String defaultExpr = root.path("defaultFormulaExpression").asText(null);
		String legacy = root.path("formulaExpression").asText(null);
		return new FormulaDefinitionConfig(trimToNull(mode), rules, trimToNull(defaultExpr), trimToNull(legacy));
	}

	private static String trimToNull(String s) {
		if (!StringUtils.hasText(s)) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class FormulaRuleJson {
		public String criteriaType;
		public String itemKey;
		public String itemLabel;
		public String formulaExpression;
	}
}
