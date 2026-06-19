/** Mirrors {@code TenantEmployeeCompensationService} derived salary columns. */

export type CompensationDerivedRates = {
  derivedYearlyAmount: number | null;
  derivedPeriodAmount: number | null;
  derivedMonthlyAmount: number | null;
  derivedHourlyAmount: number | null;
};

export function periodsPerYear(payrollFrequency: string | null | undefined): number {
  switch (payrollFrequency) {
    case "WEEKLY":
      return 52;
    case "BIWEEKLY":
      return 26;
    case "SEMIMONTHLY":
      return 24;
    default:
      return 12;
  }
}

function roundHalfUp(value: number, digits: number): number {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}

export function deriveCompensationRates(input: {
  wageType: "PER_HOUR" | "PER_PERIOD";
  wageAmount: number;
  hoursPerDay?: number | null;
  workDaysPerWeek?: number | null;
  payrollFrequency?: string | null;
}): CompensationDerivedRates {
  const amount = input.wageAmount;
  if (!Number.isFinite(amount) || amount <= 0) {
    return {
      derivedYearlyAmount: null,
      derivedPeriodAmount: null,
      derivedMonthlyAmount: null,
      derivedHourlyAmount: null,
    };
  }

  const hoursPerWeek =
    input.hoursPerDay != null &&
    input.workDaysPerWeek != null &&
    Number.isFinite(input.hoursPerDay) &&
    Number.isFinite(input.workDaysPerWeek) &&
    input.hoursPerDay > 0 &&
    input.workDaysPerWeek > 0
      ? input.hoursPerDay * input.workDaysPerWeek
      : null;

  const periods = periodsPerYear(input.payrollFrequency);

  let yearly: number | null = null;
  switch (input.wageType) {
    case "PER_HOUR":
      yearly = hoursPerWeek == null ? null : amount * hoursPerWeek * 52;
      break;
    case "PER_PERIOD":
      yearly = amount * periods;
      break;
    default:
      yearly = null;
  }

  if (yearly == null) {
    return {
      derivedYearlyAmount: null,
      derivedPeriodAmount: null,
      derivedMonthlyAmount: null,
      derivedHourlyAmount: null,
    };
  }

  const monthly = roundHalfUp(yearly / 12, 2);
  const period = roundHalfUp(yearly / periods, 2);
  const hourly =
    hoursPerWeek == null || hoursPerWeek <= 0
      ? null
      : roundHalfUp(yearly / (hoursPerWeek * 52), 4);

  return {
    derivedYearlyAmount: roundHalfUp(yearly, 2),
    derivedPeriodAmount: period,
    derivedMonthlyAmount: monthly,
    derivedHourlyAmount: hourly,
  };
}
