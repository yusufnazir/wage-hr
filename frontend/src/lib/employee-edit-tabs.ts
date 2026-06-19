export type EmployeeEditTabId =
  | "basic"
  | "employment"
  | "compensation"
  | "payment"
  | "payrollInput"
  | "contact"
  | "documents";

export const DEFAULT_EMPLOYEE_EDIT_TAB_SLUG = "employee";

const TAB_TO_SLUG: Record<EmployeeEditTabId, string> = {
  basic: "employee",
  employment: "employment",
  compensation: "compensation",
  payment: "payment",
  payrollInput: "payroll-input",
  contact: "contact",
  documents: "documents",
};

const SLUG_TO_TAB: Record<string, EmployeeEditTabId> = Object.fromEntries(
  Object.entries(TAB_TO_SLUG).map(([id, slug]) => [slug, id as EmployeeEditTabId]),
) as Record<string, EmployeeEditTabId>;

export function employeeEditTabSlug(tabId: EmployeeEditTabId): string {
  return TAB_TO_SLUG[tabId];
}

export function employeeEditTabFromSlug(slug: string | undefined): EmployeeEditTabId | null {
  if (!slug) {
    return null;
  }
  return SLUG_TO_TAB[slug.trim().toLowerCase()] ?? null;
}

export function employeeEditHref(employeeId: string, tabId: EmployeeEditTabId): string {
  return `/app/employees/${encodeURIComponent(employeeId)}/edit/${employeeEditTabSlug(tabId)}`;
}
