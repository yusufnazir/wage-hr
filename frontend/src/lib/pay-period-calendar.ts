import type { TenantCompanyItem, TenantPayPeriodItem } from "@/lib/api";

export type PayPeriodDateRange = { startDate: string; endDate: string };

/** Calendar periods for a company year (1-based index matches {@code currentPeriod} on the company). */
export function computePeriodsForYear(company: TenantCompanyItem, year: number): PayPeriodDateRange[] {
  if (!company.payPeriodEndDate) return [];
  const [ay, am, ad] = company.payPeriodEndDate.split("-").map(Number) as [number, number, number];
  const freq = company.payrollFrequency;
  const anchorIsEOM = ad === new Date(ay, am, 0).getDate();
  let end = new Date(ay, am - 1, ad);
  const jan1 = new Date(year, 0, 1);
  while (end >= jan1) end = prevEnd(end, freq, ad, anchorIsEOM);
  end = nextEnd(end, freq, ad, anchorIsEOM);
  const results: PayPeriodDateRange[] = [];
  const dec31 = new Date(year, 11, 31);
  while (end <= dec31) {
    const prev = prevEnd(end, freq, ad, anchorIsEOM);
    const start = new Date(prev);
    start.setDate(start.getDate() + 1);
    results.push({ startDate: iso(start), endDate: iso(end) });
    end = nextEnd(end, freq, ad, anchorIsEOM);
  }
  return results;
}

export type ActivePeriodResolution =
  | { kind: "incomplete" }
  | { kind: "outOfRange"; year: number; period: number; maxPeriods: number }
  | { kind: "notFound"; year: number; period: number; expected: PayPeriodDateRange }
  | {
      kind: "found";
      year: number;
      period: number;
      expected: PayPeriodDateRange;
      payPeriod: TenantPayPeriodItem;
    };

export function resolveActivePayPeriod(
  company: TenantCompanyItem,
  payPeriods: TenantPayPeriodItem[],
): ActivePeriodResolution {
  const year = company.currentYear;
  const period = company.currentPeriod;
  if (year == null || period == null || period < 1) {
    return { kind: "incomplete" };
  }
  const ranges = computePeriodsForYear(company, year);
  if (period > ranges.length) {
    return { kind: "outOfRange", year, period, maxPeriods: ranges.length };
  }
  const expected = ranges[period - 1]!;
  const payPeriod = payPeriods.find(
    (p) => p.startDate === expected.startDate && p.endDate === expected.endDate,
  );
  if (!payPeriod) {
    return { kind: "notFound", year, period, expected };
  }
  return { kind: "found", year, period, expected, payPeriod };
}

function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function nextEnd(cur: Date, freq: string, anchorDay: number, anchorIsEOM: boolean): Date {
  if (freq === "WEEKLY") {
    const d = new Date(cur);
    d.setDate(d.getDate() + 7);
    return d;
  }
  if (freq === "BIWEEKLY") {
    const d = new Date(cur);
    d.setDate(d.getDate() + 14);
    return d;
  }
  if (freq === "SEMIMONTHLY") {
    if (cur.getDate() === 15) return new Date(cur.getFullYear(), cur.getMonth(), new Date(cur.getFullYear(), cur.getMonth() + 1, 0).getDate());
    const nm = (cur.getMonth() + 1) % 12;
    const ny = nm === 0 ? cur.getFullYear() + 1 : cur.getFullYear();
    return new Date(ny, nm, 15);
  }
  const nm = (cur.getMonth() + 1) % 12;
  const ny = nm === 0 ? cur.getFullYear() + 1 : cur.getFullYear();
  const dim = new Date(ny, nm + 1, 0).getDate();
  return new Date(ny, nm, anchorIsEOM ? dim : Math.min(anchorDay, dim));
}

function prevEnd(cur: Date, freq: string, anchorDay: number, anchorIsEOM: boolean): Date {
  if (freq === "WEEKLY") {
    const d = new Date(cur);
    d.setDate(d.getDate() - 7);
    return d;
  }
  if (freq === "BIWEEKLY") {
    const d = new Date(cur);
    d.setDate(d.getDate() - 14);
    return d;
  }
  if (freq === "SEMIMONTHLY") {
    if (cur.getDate() !== 15) return new Date(cur.getFullYear(), cur.getMonth(), 15);
    const pm = cur.getMonth() === 0 ? 11 : cur.getMonth() - 1;
    const py = cur.getMonth() === 0 ? cur.getFullYear() - 1 : cur.getFullYear();
    return new Date(py, pm, new Date(py, pm + 1, 0).getDate());
  }
  const pm = cur.getMonth() === 0 ? 11 : cur.getMonth() - 1;
  const py = cur.getMonth() === 0 ? cur.getFullYear() - 1 : cur.getFullYear();
  const dim = new Date(py, pm + 1, 0).getDate();
  return new Date(py, pm, anchorIsEOM ? dim : Math.min(anchorDay, dim));
}
