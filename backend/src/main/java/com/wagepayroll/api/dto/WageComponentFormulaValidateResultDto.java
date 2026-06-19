package com.wagepayroll.api.dto;

import java.math.BigDecimal;

public record WageComponentFormulaValidateResultDto(boolean ok, BigDecimal amount) {
}
