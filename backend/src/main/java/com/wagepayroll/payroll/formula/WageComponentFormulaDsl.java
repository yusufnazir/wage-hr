package com.wagepayroll.payroll.formula;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Parses and evaluates the payroll formula DSL (identifiers, component refs, literals, + - * / parentheses).
 */
final class WageComponentFormulaDsl {

	private static final MathContext MC = MathContext.DECIMAL64;

	private final String s;
	private int pos;
	private final FormulaEvaluationContext ctx;
	private final boolean evaluate;

	private WageComponentFormulaDsl(String input, FormulaEvaluationContext ctx, boolean evaluate) {
		this.s = input;
		this.pos = 0;
		this.ctx = ctx != null ? ctx : FormulaEvaluationContext.empty();
		this.evaluate = evaluate;
	}

	static void parse(String input) {
		run(input, FormulaEvaluationContext.empty(), false);
	}

	static BigDecimal evaluate(String input, FormulaEvaluationContext ctx) {
		return run(input, ctx, true);
	}

	private static BigDecimal run(String input, FormulaEvaluationContext ctx, boolean evaluate) {
		if (input == null || input.isBlank()) {
			throw new IllegalArgumentException("EMPTY_FORMULA");
		}
		var p = new WageComponentFormulaDsl(input.trim(), ctx, evaluate);
		p.skipWs();
		if (evaluate) {
			BigDecimal result = p.parseExprValue();
			p.skipWs();
			if (p.pos < p.s.length()) {
				throw new IllegalArgumentException("TRAILING_INPUT");
			}
			return result;
		}
		p.parseExpr();
		p.skipWs();
		if (p.pos < p.s.length()) {
			throw new IllegalArgumentException("TRAILING_INPUT");
		}
		return null;
	}

	private void skipWs() {
		while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
			pos++;
		}
	}

	private void parseExpr() {
		parseTerm();
		while (true) {
			skipWs();
			if (pos >= s.length()) {
				return;
			}
			char c = s.charAt(pos);
			if (c == '+' || c == '-') {
				pos++;
				parseTerm();
			}
			else {
				return;
			}
		}
	}

	private BigDecimal parseExprValue() {
		BigDecimal v = parseTermValue();
		while (true) {
			skipWs();
			if (pos >= s.length()) {
				return v;
			}
			char c = s.charAt(pos);
			if (c == '+') {
				pos++;
				v = v.add(parseTermValue(), MC);
			}
			else if (c == '-') {
				pos++;
				v = v.subtract(parseTermValue(), MC);
			}
			else {
				return v;
			}
		}
	}

	private void parseTerm() {
		parseFactor();
		while (true) {
			skipWs();
			if (pos >= s.length()) {
				return;
			}
			char c = s.charAt(pos);
			if (c == '*' || c == '/') {
				pos++;
				parseFactor();
			}
			else {
				return;
			}
		}
	}

	private BigDecimal parseTermValue() {
		BigDecimal v = parseFactorValue();
		while (true) {
			skipWs();
			if (pos >= s.length()) {
				return v;
			}
			char c = s.charAt(pos);
			if (c == '*') {
				pos++;
				v = v.multiply(parseFactorValue(), MC);
			}
			else if (c == '/') {
				pos++;
				BigDecimal d = parseFactorValue();
				if (d.compareTo(BigDecimal.ZERO) == 0) {
					throw new IllegalArgumentException("DIVISION_BY_ZERO");
				}
				v = v.divide(d, MC);
			}
			else {
				return v;
			}
		}
	}

	private void parseFactor() {
		skipUnary();
		skipWs();
		if (pos >= s.length()) {
			throw new IllegalArgumentException("INCOMPLETE");
		}
		char c = s.charAt(pos);
		if (c == '(') {
			pos++;
			parseExpr();
			skipWs();
			if (pos >= s.length() || s.charAt(pos) != ')') {
				throw new IllegalArgumentException("UNBALANCED_PARENS");
			}
			pos++;
			return;
		}
		if (isDigit(c) || c == '.') {
			readNumberValue();
			return;
		}
		if (isIdentStart(c)) {
			int mark = pos;
			String id = parseIdentifier();
			if ("if".equals(id)) {
				parseIfCallSyntax();
				return;
			}
			if ("component".equals(id)) {
				parseComponentRefSyntax();
				return;
			}
			pos = mark;
			id = parseIdentifier();
			if (!WageComponentFormulaValidator.allowedReferences().contains(id)) {
				throw new IllegalArgumentException("UNKNOWN_REF:" + id);
			}
			return;
		}
		throw new IllegalArgumentException("UNEXPECTED_CHAR");
	}

	private BigDecimal parseFactorValue() {
		int sign = skipUnary();
		skipWs();
		if (pos >= s.length()) {
			throw new IllegalArgumentException("INCOMPLETE");
		}
		char c = s.charAt(pos);
		BigDecimal v;
		if (c == '(') {
			pos++;
			v = parseExprValue();
			skipWs();
			if (pos >= s.length() || s.charAt(pos) != ')') {
				throw new IllegalArgumentException("UNBALANCED_PARENS");
			}
			pos++;
		}
		else if (isDigit(c) || c == '.') {
			v = readNumberValue();
		}
		else if (isIdentStart(c)) {
			int mark = pos;
			String id = parseIdentifier();
			if ("if".equals(id)) {
				v = parseIfCallValue();
			}
			else if ("component".equals(id)) {
				String code = parseComponentRefSyntax();
				v = evaluate ? ctx.resolveComponentAmount(code) : BigDecimal.ZERO;
			}
			else {
				pos = mark;
				id = parseIdentifier();
				if (!WageComponentFormulaValidator.allowedReferences().contains(id)) {
					throw new IllegalArgumentException("UNKNOWN_REF:" + id);
				}
				v = evaluate ? ctx.resolveReference(id) : BigDecimal.ZERO;
			}
		}
		else {
			throw new IllegalArgumentException("UNEXPECTED_CHAR");
		}
		return sign < 0 ? v.negate(MC) : v;
	}

	private int skipUnary() {
		int sign = 1;
		while (true) {
			skipWs();
			if (pos >= s.length()) {
				break;
			}
			char c = s.charAt(pos);
			if (c == '+') {
				pos++;
			}
			else if (c == '-') {
				pos++;
				sign = -sign;
			}
			else {
				break;
			}
		}
		return sign;
	}

	/** {@code if(condition, thenExpr, elseExpr)} — condition is true when non-zero. */
	private void parseIfCallSyntax() {
		skipWs();
		if (pos >= s.length() || s.charAt(pos) != '(') {
			throw new IllegalArgumentException("IF_OPEN_PAREN");
		}
		pos++;
		parseExpr();
		expectComma();
		parseExpr();
		expectComma();
		parseExpr();
		skipWs();
		if (pos >= s.length() || s.charAt(pos) != ')') {
			throw new IllegalArgumentException("IF_CLOSE_PAREN");
		}
		pos++;
	}

	private BigDecimal parseIfCallValue() {
		skipWs();
		if (pos >= s.length() || s.charAt(pos) != '(') {
			throw new IllegalArgumentException("IF_OPEN_PAREN");
		}
		pos++;
		BigDecimal condition = parseExprValue();
		expectComma();
		BigDecimal thenValue = parseExprValue();
		expectComma();
		BigDecimal elseValue = parseExprValue();
		skipWs();
		if (pos >= s.length() || s.charAt(pos) != ')') {
			throw new IllegalArgumentException("IF_CLOSE_PAREN");
		}
		pos++;
		if (!evaluate) {
			return BigDecimal.ZERO;
		}
		return condition.compareTo(BigDecimal.ZERO) != 0 ? thenValue : elseValue;
	}

	private void expectComma() {
		skipWs();
		if (pos >= s.length() || s.charAt(pos) != ',') {
			throw new IllegalArgumentException("IF_COMMA");
		}
		pos++;
	}

	private String parseComponentRefSyntax() {
		skipWs();
		if (pos >= s.length() || s.charAt(pos) != '(') {
			throw new IllegalArgumentException("COMPONENT_REF_OPEN_PAREN");
		}
		pos++;
		skipWs();
		String code = parseStringLiteral();
		skipWs();
		if (pos >= s.length() || s.charAt(pos) != ')') {
			throw new IllegalArgumentException("COMPONENT_REF_CLOSE_PAREN");
		}
		pos++;
		skipWs();
		if (pos + 7 > s.length() || !s.regionMatches(pos, ".amount", 0, 7)) {
			throw new IllegalArgumentException("COMPONENT_REF_AMOUNT_SUFFIX");
		}
		pos += 7;
		return code;
	}

	private String parseStringLiteral() {
		skipWs();
		if (pos >= s.length() || s.charAt(pos) != '"') {
			throw new IllegalArgumentException("COMPONENT_CODE_STRING");
		}
		pos++;
		StringBuilder sb = new StringBuilder();
		while (pos < s.length() && s.charAt(pos) != '"') {
			char c = s.charAt(pos++);
			if (c == '\\') {
				if (pos >= s.length()) {
					throw new IllegalArgumentException("COMPONENT_CODE_STRING");
				}
				sb.append(s.charAt(pos++));
			}
			else {
				sb.append(c);
			}
		}
		if (pos >= s.length() || s.charAt(pos) != '"') {
			throw new IllegalArgumentException("COMPONENT_CODE_STRING");
		}
		pos++;
		String code = sb.toString().trim();
		if (code.isEmpty()) {
			throw new IllegalArgumentException("COMPONENT_CODE_EMPTY");
		}
		return code;
	}

	private BigDecimal readNumberValue() {
		int start = pos;
		boolean sawDigit = false;
		while (pos < s.length() && isDigit(s.charAt(pos))) {
			sawDigit = true;
			pos++;
		}
		if (pos < s.length() && s.charAt(pos) == '.') {
			pos++;
			while (pos < s.length() && isDigit(s.charAt(pos))) {
				sawDigit = true;
				pos++;
			}
		}
		if (!sawDigit) {
			throw new IllegalArgumentException("BAD_NUMBER");
		}
		if (pos - start > 64) {
			throw new IllegalArgumentException("NUMBER_TOO_LONG");
		}
		return new BigDecimal(s.substring(start, pos), MC);
	}

	private String parseIdentifier() {
		int start = pos;
		pos++;
		while (pos < s.length()) {
			char c = s.charAt(pos);
			if (isIdentPart(c)) {
				pos++;
			}
			else {
				break;
			}
		}
		return s.substring(start, pos);
	}

	private static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	private static boolean isIdentStart(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
	}

	private static boolean isIdentPart(char c) {
		return isIdentStart(c) || isDigit(c) || c == '.';
	}
}
