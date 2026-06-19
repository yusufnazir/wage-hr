package com.wagepayroll.payroll.formula;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.wagepayroll.api.dto.FormulaMockContextDto;
import com.wagepayroll.api.dto.WageComponentFormulaValidateRequest;
import com.wagepayroll.api.dto.WageComponentFormulaValidateResultDto;
import com.wagepayroll.payroll.model.CalculationMethod;
import com.wagepayroll.payroll.model.RoundingStrategy;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stateless formula validate/preview using the same validator and evaluator as the payroll engine (ADR-PE-002).
 */
@Service
public class WageComponentFormulaValidateService {

	private static final MathContext MC = MathContext.DECIMAL64;

	private final WageComponentFormulaValidator wageComponentFormulaValidator;
	private final WageComponentFormulaEvaluator wageComponentFormulaEvaluator;

	public WageComponentFormulaValidateService(WageComponentFormulaValidator wageComponentFormulaValidator,
			WageComponentFormulaEvaluator wageComponentFormulaEvaluator) {
		this.wageComponentFormulaValidator = wageComponentFormulaValidator;
		this.wageComponentFormulaEvaluator = wageComponentFormulaEvaluator;
	}

	public WageComponentFormulaValidateResultDto validate(WageComponentFormulaValidateRequest request) {
		if (request == null || request.mockContext() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mockContext is required");
		}
		CalculationMethod method = parseCalculationMethod(request.calculationMethod());
		RoundingMode roundMode = parseRoundingStrategy(request.roundingStrategy());
		FormulaEvaluationContext ctx = toEvaluationContext(request.mockContext());
		Set<String> prerequisiteCodes = prerequisiteCodesFromMock(request.mockContext());
		wageComponentFormulaValidator.validate(method, request.percentageBase(), request.formulaExpression(),
				prerequisiteCodes);
		BigDecimal amount = evaluateAmount(method, request.formulaExpression(), ctx, roundMode);
		return new WageComponentFormulaValidateResultDto(true, amount);
	}

	private static CalculationMethod parseCalculationMethod(String raw) {
		if (!StringUtils.hasText(raw)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "calculationMethod is required");
		}
		try {
			return CalculationMethod.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_CALCULATION_METHOD");
		}
	}

	private static RoundingMode parseRoundingStrategy(String raw) {
		if (!StringUtils.hasText(raw)) {
			return PayrollRounding.toRoundingMode(RoundingStrategy.HALF_UP);
		}
		try {
			return PayrollRounding.toRoundingMode(RoundingStrategy.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ROUNDING_STRATEGY");
		}
	}

	private static Set<String> prerequisiteCodesFromMock(FormulaMockContextDto mock) {
		if (mock.componentAmounts() == null || mock.componentAmounts().isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(new LinkedHashSet<>(mock.componentAmounts().keySet()));
	}

	private static FormulaEvaluationContext toEvaluationContext(FormulaMockContextDto mock) {
		return new FormulaEvaluationContext(mock.compensationPeriodicRate(), mock.compensationIsHourly(),
				mock.compensationHourlyRate(), mock.transactionQuantity(), mock.transactionRate(),
				mock.transactionAmount(), mock.definitionDefaultAmount(),
				mock.componentAmounts() != null ? mock.componentAmounts() : Map.of());
	}

	private BigDecimal evaluateAmount(CalculationMethod method, String formulaExpression, FormulaEvaluationContext ctx,
			RoundingMode roundMode) {
		return switch (method) {
			case FORMULA -> {
				String formula = trimToNull(formulaExpression);
				if (formula == null) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FORMULA_REQUIRED");
				}
				try {
					yield wageComponentFormulaEvaluator.evaluate(formula, ctx, roundMode);
				}
				catch (IllegalArgumentException ex) {
					throw mapEvaluationError(ex);
				}
			}
			case HOURLY -> ctx.transactionQuantity().multiply(ctx.transactionRate(), MC).setScale(4, roundMode);
			case FIXED_AMOUNT -> ctx.definitionDefaultAmount().setScale(4, roundMode);
			case MANUAL_INPUT -> ctx.transactionAmount().setScale(4, roundMode);
			case PERCENTAGE -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"PERCENTAGE_VALIDATE_NOT_SUPPORTED");
		};
	}

	private static ResponseStatusException mapEvaluationError(IllegalArgumentException ex) {
		String msg = ex.getMessage();
		if (msg != null && (msg.startsWith("COMPONENT_AMOUNT_NOT_AVAILABLE:")
				|| msg.startsWith("UNKNOWN_REF:") || "DIVISION_BY_ZERO".equals(msg))) {
			return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg, ex);
		}
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FORMULA", ex);
	}

	private static String trimToNull(String s) {
		if (!StringUtils.hasText(s)) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}
}
