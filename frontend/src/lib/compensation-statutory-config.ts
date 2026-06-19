import type { TenantEmployeeCompensationPayload } from "@/lib/api";

export type CompensationStatutoryFieldKey = "applyTaxes" | "applyTaxExempt" | "applyAov";

export type CompensationStatutoryFieldDef = {
  key: CompensationStatutoryFieldKey;
  label: string;
  description: string;
  /** When true, the toggle is on if the value is strictly true; otherwise on unless explicitly false. */
  checkedWhenTrue: boolean;
};

export type CompensationStatutoryPanel = {
  title: string;
  countryCode: string;
  fields: CompensationStatutoryFieldDef[];
};

const SURINAME_STATUTORY_PANEL: CompensationStatutoryPanel = {
  title: "Tax and premiums",
  countryCode: "SR",
  fields: [
    {
      key: "applyTaxes",
      label: "Apply taxes",
      description: "Apply wage-tax (loonbelasting) components when running payroll.",
      checkedWhenTrue: false,
    },
    {
      key: "applyTaxExempt",
      label: "Apply tax exempt",
      description: "Apply the local tax-exempt allowance (belastingvrij).",
      checkedWhenTrue: true,
    },
    {
      key: "applyAov",
      label: "Apply AOV",
      description: "Apply the AOV (Algemene Ouderdomsvoorziening) employee premium.",
      checkedWhenTrue: false,
    },
  ],
};

const PANELS_BY_COUNTRY: Record<string, CompensationStatutoryPanel> = {
  SR: SURINAME_STATUTORY_PANEL,
};

export function getCompensationStatutoryPanel(
  payrollCountry: string | null | undefined,
): CompensationStatutoryPanel | null {
  if (!payrollCountry) return null;
  return PANELS_BY_COUNTRY[payrollCountry.trim().toUpperCase()] ?? null;
}

export function isCompensationStatutoryFieldChecked(
  form: TenantEmployeeCompensationPayload,
  field: CompensationStatutoryFieldDef,
): boolean {
  const value = form[field.key];
  return field.checkedWhenTrue ? value === true : value !== false;
}

export function patchCompensationStatutoryField(
  form: TenantEmployeeCompensationPayload,
  field: CompensationStatutoryFieldDef,
  checked: boolean,
): TenantEmployeeCompensationPayload {
  return { ...form, [field.key]: checked };
}
