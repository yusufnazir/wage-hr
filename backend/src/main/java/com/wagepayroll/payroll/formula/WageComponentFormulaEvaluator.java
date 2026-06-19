package com.wagepayroll.payroll.formula;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

/**
 * Evaluates validated {@code formula_expression} payloads (DSL line or JSON v1) using {@link FormulaEvaluationContext}.
 */
@Component
public class WageComponentFormulaEvaluator {

	private static final MathContext MC = MathContext.DECIMAL64;

	private final ObjectMapper objectMapper;

	public WageComponentFormulaEvaluator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public BigDecimal evaluate(String raw, FormulaEvaluationContext ctx, RoundingMode roundingMode) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("EMPTY_FORMULA");
		}
		String t = raw.trim();
		BigDecimal v;
		if (t.startsWith("{")) {
			try {
				v = evaluateJson(t, ctx);
			}
			catch (IllegalArgumentException ex) {
				throw ex;
			}
			catch (Exception ex) {
				throw new IllegalArgumentException("INVALID_FORMULA", ex);
			}
		}
		else {
			v = WageComponentFormulaDsl.evaluate(t, ctx);
		}
		return v.setScale(4, roundingMode);
	}

	private BigDecimal evaluateJson(String json, FormulaEvaluationContext ctx) throws Exception {
		JsonNode root = objectMapper.readTree(json);
		if (!root.path("version").isIntegralNumber() || root.path("version").intValue() != 1) {
			throw new IllegalArgumentException("INVALID_FORMULA");
		}
		String kind = root.path("kind").asText(null);
		if ("dsl".equals(kind)) {
			return WageComponentFormulaDsl.evaluate(root.path("expression").asText(), ctx);
		}
		if ("expr".equals(kind)) {
			return evalExprNode(root.get("root"), ctx);
		}
		throw new IllegalArgumentException("INVALID_FORMULA");
	}

	private BigDecimal evalExprNode(JsonNode n, FormulaEvaluationContext ctx) {
		if (n == null || !n.isObject()) {
			throw new IllegalArgumentException("INVALID_FORMULA");
		}
		if (n.has("ref")) {
			return ctx.resolveReference(n.get("ref").asText());
		}
		if (n.has("num")) {
			JsonNode num = n.get("num");
			if (num.isNumber()) {
				return num.decimalValue().stripTrailingZeros();
			}
			if (num.isTextual()) {
				return new BigDecimal(num.asText(), MC);
			}
			throw new IllegalArgumentException("INVALID_FORMULA");
		}
		if (n.has("op")) {
			String op = n.path("op").asText(null);
			BigDecimal left = evalExprNode(n.get("left"), ctx);
			BigDecimal right = evalExprNode(n.get("right"), ctx);
			return switch (op) {
				case "add" -> left.add(right, MC);
				case "sub" -> left.subtract(right, MC);
				case "mul" -> left.multiply(right, MC);
				case "div" -> {
					if (right.compareTo(BigDecimal.ZERO) == 0) {
						throw new IllegalArgumentException("DIVISION_BY_ZERO");
					}
					yield left.divide(right, MC);
				}
				default -> throw new IllegalArgumentException("INVALID_FORMULA");
			};
		}
		throw new IllegalArgumentException("INVALID_FORMULA");
	}
}
