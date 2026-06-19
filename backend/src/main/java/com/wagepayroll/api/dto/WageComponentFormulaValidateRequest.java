package com.wagepayroll.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WageComponentFormulaValidateRequest(
		@NotBlank String calculationMethod,
		String formulaExpression,
		String percentageBase,
		String roundingStrategy,
		@NotNull @Valid FormulaMockContextDto mockContext) {
}
