package com.wagepayroll.payroll.model;

/**
 * Display and processing-order bands (multiples of 1000). Within a band use offsets 10–990 so new
 * components can be inserted between existing ones.
 */
public enum WageComponentSortBand {

	GROSS_EARNINGS(1000),
	GROSS_DEDUCTIONS(2000),
	NON_TAXABLE_EARNINGS(3000),
	TAX_ADJUSTMENTS(4000),
	STATUTORY_DEDUCTIONS(5000),
	NET_DEDUCTIONS(6000),
	EMPLOYER_CONTRIBUTIONS(7000),
	SYSTEM_CALCULATIONS(8000);

	private final int base;

	WageComponentSortBand(int base) {
		this.base = base;
	}

	public int base() {
		return base;
	}

	public boolean contains(int processingOrder) {
		return processingOrder >= base && processingOrder < base + 1000;
	}
}
