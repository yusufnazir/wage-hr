/** v2 contract for `platform_country_tax_rule.parameters_json` — see docs/modules/payroll-wage-component-engine.md §4.1 */

export const TAX_RULE_PARAMETER_KINDS = [
  "MARGINAL_RATES",
  "FLAT_RATE",
  "THRESHOLD_AMOUNT",
  "AMOUNT_BAND",
  "LEGACY_SERVICE_YEAR_TABLE",
  "PLACEHOLDER",
] as const;

export type TaxRuleParameterKind = (typeof TAX_RULE_PARAMETER_KINDS)[number];

export const TAX_RULE_PARAMETER_FREQS = ["YEAR", "MONTH"] as const;

export type TaxRuleParameterFreq = (typeof TAX_RULE_PARAMETER_FREQS)[number];

export type MarginalRateRow = {
  i: number;
  pct: number;
  min: number;
  max?: number | null;
};

export type ServiceYearRow = {
  i: number;
  pct: number;
  lo: number;
  hi?: number | null;
};

export type TaxRuleParametersForm = {
  v: number;
  kind: TaxRuleParameterKind;
  freq: TaxRuleParameterFreq | "";
  legacyTariffTypeId: number | null;
  source: string;
  note: string;
  pct: number | null;
  amount: number | null;
  min: number | null;
  max: number | null;
  marginalRows: MarginalRateRow[];
  serviceYearRows: ServiceYearRow[];
};

const KNOWN_ROOT_KEYS = new Set([
  "v",
  "kind",
  "freq",
  "legacyTariffTypeId",
  "source",
  "note",
  "pct",
  "amount",
  "min",
  "max",
  "rows",
]);

export type ParseTaxRuleParametersResult =
  | { ok: true; form: TaxRuleParametersForm; extras: Record<string, unknown> }
  | { ok: false; error: string };

export function defaultTaxRuleParametersForm(kind: TaxRuleParameterKind = "MARGINAL_RATES"): TaxRuleParametersForm {
  return {
    v: 2,
    kind,
    freq: "YEAR",
    legacyTariffTypeId: null,
    source: "",
    note: "",
    pct: null,
    amount: null,
    min: null,
    max: null,
    marginalRows: [{ i: 1, pct: 0, min: 0, max: null }],
    serviceYearRows: [{ i: 1, pct: 0, lo: 0, hi: null }],
  };
}

function asObject(value: unknown): Record<string, unknown> | null {
  if (value == null || typeof value !== "object" || Array.isArray(value)) {
    return null;
  }
  return value as Record<string, unknown>;
}

function readNumber(value: unknown): number | null {
  if (value == null || value === "") {
    return null;
  }
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : null;
}

function readKind(value: unknown): TaxRuleParameterKind | null {
  if (typeof value !== "string") {
    return null;
  }
  return TAX_RULE_PARAMETER_KINDS.includes(value as TaxRuleParameterKind)
    ? (value as TaxRuleParameterKind)
    : null;
}

function readFreq(value: unknown): TaxRuleParameterFreq | "" {
  if (value === "YEAR" || value === "MONTH") {
    return value;
  }
  return "";
}

function parseMarginalRows(rows: unknown): MarginalRateRow[] {
  if (!Array.isArray(rows)) {
    return [];
  }
  const out: MarginalRateRow[] = [];
  for (const raw of rows) {
    const row = asObject(raw);
    if (!row) continue;
    const i = readNumber(row.i);
    const pct = readNumber(row.pct);
    const min = readNumber(row.min);
    if (i == null || pct == null || min == null) continue;
    const max = readNumber(row.max);
    out.push({ i: Math.trunc(i), pct, min, max: max ?? null });
  }
  return out;
}

function parseServiceYearRows(rows: unknown): ServiceYearRow[] {
  if (!Array.isArray(rows)) {
    return [];
  }
  const out: ServiceYearRow[] = [];
  for (const raw of rows) {
    const row = asObject(raw);
    if (!row) continue;
    const i = readNumber(row.i);
    const pct = readNumber(row.pct);
    const lo = readNumber(row.lo);
    if (i == null || pct == null || lo == null) continue;
    const hi = readNumber(row.hi);
    out.push({ i: Math.trunc(i), pct, lo: Math.trunc(lo), hi: hi != null ? Math.trunc(hi) : null });
  }
  return out;
}

export function parseTaxRuleParametersJson(raw: string): ParseTaxRuleParametersResult {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw.trim());
  } catch {
    return { ok: false, error: "INVALID_JSON" };
  }
  const root = asObject(parsed);
  if (!root) {
    return { ok: false, error: "ROOT_MUST_BE_OBJECT" };
  }
  const kind = readKind(root.kind);
  if (!kind) {
    return { ok: false, error: "MISSING_OR_UNKNOWN_KIND" };
  }
  const extras: Record<string, unknown> = {};
  for (const [key, val] of Object.entries(root)) {
    if (!KNOWN_ROOT_KEYS.has(key)) {
      extras[key] = val;
    }
  }
  const v = readNumber(root.v);
  const form: TaxRuleParametersForm = {
    v: v != null && v >= 1 ? Math.trunc(v) : 2,
    kind,
    freq: readFreq(root.freq),
    legacyTariffTypeId: readNumber(root.legacyTariffTypeId),
    source: typeof root.source === "string" ? root.source : "",
    note: typeof root.note === "string" ? root.note : "",
    pct: readNumber(root.pct),
    amount: readNumber(root.amount),
    min: readNumber(root.min),
    max: readNumber(root.max),
    marginalRows: [],
    serviceYearRows: [],
  };
  if (kind === "MARGINAL_RATES") {
    const rows = parseMarginalRows(root.rows);
    form.marginalRows = rows.length > 0 ? rows : [{ i: 1, pct: 0, min: 0, max: null }];
  }
  if (kind === "LEGACY_SERVICE_YEAR_TABLE") {
    const rows = parseServiceYearRows(root.rows);
    form.serviceYearRows = rows.length > 0 ? rows : [{ i: 1, pct: 0, lo: 0, hi: null }];
  }
  return { ok: true, form, extras };
}

function omitEmptyMax(max: number | null | undefined): number | undefined {
  if (max == null || !Number.isFinite(max)) {
    return undefined;
  }
  return max;
}

export function serializeTaxRuleParameters(form: TaxRuleParametersForm, extras: Record<string, unknown> = {}): string {
  const root: Record<string, unknown> = {
    ...extras,
    v: form.v >= 1 ? form.v : 2,
    kind: form.kind,
  };
  if (form.freq) {
    root.freq = form.freq;
  }
  if (form.legacyTariffTypeId != null) {
    root.legacyTariffTypeId = form.legacyTariffTypeId;
  }
  if (form.source.trim()) {
    root.source = form.source.trim();
  }

  switch (form.kind) {
    case "MARGINAL_RATES":
      root.rows = form.marginalRows.map((row) => {
        const entry: Record<string, unknown> = {
          i: row.i,
          pct: row.pct,
          min: row.min,
        };
        const max = omitEmptyMax(row.max);
        if (max !== undefined) {
          entry.max = max;
        }
        return entry;
      });
      break;
    case "FLAT_RATE":
      if (form.pct != null) {
        root.pct = form.pct;
      }
      break;
    case "THRESHOLD_AMOUNT":
      if (form.amount != null) {
        root.amount = form.amount;
      }
      break;
    case "AMOUNT_BAND":
      if (form.min != null) {
        root.min = form.min;
      }
      if (form.max != null) {
        root.max = form.max;
      }
      break;
    case "LEGACY_SERVICE_YEAR_TABLE":
      root.rows = form.serviceYearRows.map((row) => {
        const entry: Record<string, unknown> = {
          i: row.i,
          pct: row.pct,
          lo: row.lo,
        };
        if (row.hi != null) {
          entry.hi = row.hi;
        }
        return entry;
      });
      break;
    case "PLACEHOLDER":
      if (form.note.trim()) {
        root.note = form.note.trim();
      }
      break;
    default:
      break;
  }

  return JSON.stringify(root);
}

export function nextMarginalIndex(rows: MarginalRateRow[]): number {
  if (rows.length === 0) return 1;
  return Math.max(...rows.map((r) => r.i)) + 1;
}

export function nextServiceYearIndex(rows: ServiceYearRow[]): number {
  if (rows.length === 0) return 1;
  return Math.max(...rows.map((r) => r.i)) + 1;
}
