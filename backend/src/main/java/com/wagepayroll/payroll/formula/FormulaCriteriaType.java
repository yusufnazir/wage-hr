package com.wagepayroll.payroll.formula;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum FormulaCriteriaType {
	WAGE_TYPE,
	DEPARTMENT,
	JOB;

	public static FormulaCriteriaType parse(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FORMULA_CRITERIA_TYPE_REQUIRED");
		}
		try {
			return FormulaCriteriaType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_FORMULA_CRITERIA_TYPE");
		}
	}
}
