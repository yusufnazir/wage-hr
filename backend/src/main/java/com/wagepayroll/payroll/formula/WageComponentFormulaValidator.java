package com.wagepayroll.payroll.formula;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.payroll.model.CalculationMethod;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Validates {@code formula_expression} and {@code percentage_base} for tenant wage components.
 */
@Component
public class WageComponentFormulaValidator {

	public static final int MAX_FORMULA_LENGTH = 500;
	public static final int MAX_PERCENTAGE_BASE_LENGTH = 40;

	private static final Pattern COMPONENT_AMOUNT_REF = Pattern
			.compile("component\\s*\\(\\s*\"([^\"]+)\"\\s*\\)\\s*\\.amount");

	private static final Set<String> REFERENCE_ALLOWLIST = Set.of(
			"compensation.periodic_rate",
			"compensation.hourly_rate",
			"compensation.is_hourly",
			"transaction.quantity",
			"transaction.rate",
			"transaction.amount",
			"definition.default_amount");

	private final ObjectMapper objectMapper;

	public WageComponentFormulaValidator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public static Set<String> allowedReferences() {
		return Collections.unmodifiableSet(REFERENCE_ALLOWLIST);
	}

	public void validate(CalculationMethod method, String percentageBase, String formulaExpression) {
		validate(method, percentageBase, formulaExpression, Set.of());
	}

	public void validate(CalculationMethod method, String percentageBase, String formulaExpression,
			Set<String> prerequisiteComponentCodes) {
		String pct = trimToNull(percentageBase);
		String formula = trimToNull(formulaExpression);

		if (method == CalculationMethod.PERCENTAGE) {
			if (pct == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PERCENTAGE_BASE_REQUIRED");
			}
			if (pct.length() > MAX_PERCENTAGE_BASE_LENGTH) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PERCENTAGE_BASE_TOO_LONG");
			}
		}

		if (method == CalculationMethod.FORMULA) {
			if (formula == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FORMULA_REQUIRED");
			}
		}

		if (formula != null) {
			if (formula.length() > MAX_FORMULA_LENGTH) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FORMULA_TOO_LONG");
			}
			validateFormulaPayload(formula, prerequisiteComponentCodes != null ? prerequisiteComponentCodes : Set.of());
		}
	}

	public static Set<String> extractComponentCodes(String formulaExpression) {
		if (formulaExpression == null || formulaExpression.isBlank()) {
			return Set.of();
		}
		Set<String> codes = new LinkedHashSet<>();
		Matcher m = COMPONENT_AMOUNT_REF.matcher(formulaExpression);
		while (m.find()) {
			String code = m.group(1).trim();
			if (!code.isEmpty()) {
				codes.add(code);
			}
		}
		return Set.copyOf(codes);
	}

	private void validateFormulaPayload(String raw, Set<String> prerequisiteComponentCodes) {
		String t = raw.trim();
		if (t.startsWith("{")) {
			try {
				validateJsonPayload(t, prerequisiteComponentCodes);
			}
			catch (ResponseStatusException ex) {
				throw ex;
			}
			catch (Exception ex) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FORMULA", ex);
			}
		}
		else {
			try {
				WageComponentFormulaDsl.parse(t);
				assertComponentRefsDeclared(t, prerequisiteComponentCodes);
			}
			catch (IllegalArgumentException ex) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FORMULA", ex);
			}
		}
	}

	private void validateJsonPayload(String json, Set<String> prerequisiteComponentCodes) throws Exception {
		JsonNode root = objectMapper.readTree(json);
		if (!root.path("version").isIntegralNumber() || root.path("version").intValue() != 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FORMULA");
		}
		String kind = root.path("kind").asText(null);
		if ("dsl".equals(kind)) {
			if (!root.path("expression").isTextual()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FORMULA");
			}
			String expr = root.path("expression").asText();
			WageComponentFormulaDsl.parse(expr);
			assertComponentRefsDeclared(expr, prerequisiteComponentCodes);
			return;
		}
		if ("expr".equals(kind)) {
			validateExprNode(root.get("root"));
			return;
		}
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FORMULA");
	}

	private static void assertComponentRefsDeclared(String expression, Set<String> prerequisiteComponentCodes) {
		Set<String> refs = extractComponentCodes(expression);
		for (String ref : refs) {
			if (!prerequisiteComponentCodes.contains(ref)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FORMULA_MISSING_DEPENDENCY");
			}
		}
	}

	private void validateExprNode(JsonNode n) {
		if (n == null || !n.isObject()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FORMULA");
		}
		if (n.has("ref")) {
			if (!n.get("ref").isTextual()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FORMULA");
			}
			String ref = n.get("ref").asText();
			if (!REFERENCE_ALLOWLIST.contains(ref)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FORMULA");
			}
			return;
		}
		if (n.has("num")) {
			JsonNode num = n.get("num");
			if (num.isNumber()) {
				return;
			}
			if (num.isTextual()) {
				try {
					new BigDecimal(num.asText());
					return;
				}
				catch (NumberFormatException ex) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FORMULA", ex);
				}
			}
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FORMULA");
		}
		if (n.has("op")) {
			String op = n.path("op").asText(null);
			if (op == null || !(op.equals("add") || op.equals("sub") || op.equals("mul") || op.equals("div"))) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FORMULA");
			}
			validateExprNode(n.get("left"));
			validateExprNode(n.get("right"));
			return;
		}
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FORMULA");
	}

	private static String trimToNull(String s) {
		if (!StringUtils.hasText(s)) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}
}
