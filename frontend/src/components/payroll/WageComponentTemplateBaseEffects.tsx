"use client";

import type {
  PlatformPayrollBaseRow,
  PlatformWageComponentTemplateBaseEffectPutItem,
  PlatformWageComponentTemplateBaseEffectRow,
} from "@/lib/api";

export type BaseEffectDirection = "INCREASE" | "DECREASE" | "IGNORE";
export type BaseEffectCalculationType = "FULL" | "PERCENTAGE" | "FIXED";

export type EditableBaseEffectRow = {
  payrollBaseId: string;
  payrollBaseCode: string;
  payrollBaseName: string;
  effectDirection: BaseEffectDirection;
  effectCalculationType: BaseEffectCalculationType;
  effectValue: string;
  priority: string;
};

const DIRECTIONS: BaseEffectDirection[] = ["INCREASE", "DECREASE", "IGNORE"];
const CALC_TYPES: BaseEffectCalculationType[] = ["FULL", "PERCENTAGE", "FIXED"];

function directionSymbol(direction: string): string {
  switch (direction) {
    case "INCREASE":
      return "↑";
    case "DECREASE":
      return "↓";
    default:
      return "—";
  }
}

export function summarizeBaseEffects(effects: PlatformWageComponentTemplateBaseEffectRow[] | undefined): string {
  if (!effects?.length) return "—";
  const meaningful = effects.filter((e) => e.effectDirection !== "IGNORE");
  if (!meaningful.length) return "—";
  return meaningful.map((e) => `${e.payrollBaseCode}${directionSymbol(e.effectDirection)}`).join(" ");
}

export function baseEffectsFromTemplate(
  effects: PlatformWageComponentTemplateBaseEffectRow[] | undefined,
): EditableBaseEffectRow[] {
  if (!effects?.length) return [];
  return effects.map((e) => ({
    payrollBaseId: e.payrollBaseId,
    payrollBaseCode: e.payrollBaseCode,
    payrollBaseName: e.payrollBaseName,
    effectDirection: e.effectDirection as BaseEffectDirection,
    effectCalculationType: e.effectCalculationType as BaseEffectCalculationType,
    effectValue: String(e.effectValue ?? ""),
    priority: String(e.priority ?? 0),
  }));
}

export function toBaseEffectPutPayload(rows: EditableBaseEffectRow[]): PlatformWageComponentTemplateBaseEffectPutItem[] {
  return rows.map((row) => {
    const valueTrim = row.effectValue.trim();
    let effectValue: number | null = null;
    if (valueTrim !== "") {
      const n = Number.parseFloat(valueTrim);
      effectValue = Number.isNaN(n) ? null : n;
    }
    const priorityTrim = row.priority.trim();
    let priority: number | null = null;
    if (priorityTrim !== "") {
      const p = Number.parseInt(priorityTrim, 10);
      priority = Number.isNaN(p) ? null : p;
    }
    return {
      payrollBaseId: row.payrollBaseId,
      effectDirection: row.effectDirection,
      effectCalculationType: row.effectCalculationType,
      effectValue,
      priority,
    };
  });
}

type SummaryProps = {
  effects: PlatformWageComponentTemplateBaseEffectRow[] | undefined;
  className?: string;
};

export function WageComponentTemplateBaseEffectsSummary({ effects, className }: SummaryProps) {
  const text = summarizeBaseEffects(effects);
  if (text === "—") {
    return <span className={className ?? "text-muted"}>—</span>;
  }
  return (
    <span className={className ?? "font-mono text-xs text-foreground"} title={text}>
      {text}
    </span>
  );
}

type EditorProps = {
  rows: EditableBaseEffectRow[];
  onChange: (rows: EditableBaseEffectRow[]) => void;
  payrollBases: PlatformPayrollBaseRow[];
  t: (key: string) => string;
};

export function WageComponentTemplateBaseEffectsEditor({ rows, onChange, payrollBases, t }: EditorProps) {
  const usedBaseIds = new Set(rows.map((r) => r.payrollBaseId));
  const availableBases = payrollBases.filter((b) => b.active && !usedBaseIds.has(b.id));

  function updateRow(index: number, patch: Partial<EditableBaseEffectRow>) {
    onChange(rows.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  }

  function removeRow(index: number) {
    onChange(rows.filter((_, i) => i !== index));
  }

  function addBase(baseId: string) {
    const base = payrollBases.find((b) => b.id === baseId);
    if (!base || usedBaseIds.has(baseId)) return;
    onChange([
      ...rows,
      {
        payrollBaseId: base.id,
        payrollBaseCode: base.code,
        payrollBaseName: base.name,
        effectDirection: "INCREASE",
        effectCalculationType: "FULL",
        effectValue: "100",
        priority: "0",
      },
    ]);
  }

  return (
    <div className="space-y-3">
      <p className="text-xs text-muted">{t("platformWageComponentTemplates.helper.baseEffects")}</p>
      {rows.length > 0 ? (
        <div className="overflow-x-auto rounded border border-border">
          <table className="w-full text-sm">
            <thead className="bg-surface text-left text-xs font-medium uppercase text-muted">
              <tr>
                <th className="px-3 py-2">{t("platformWageComponentTemplates.baseEffects.col.base")}</th>
                <th className="px-3 py-2">{t("platformWageComponentTemplates.baseEffects.col.direction")}</th>
                <th className="px-3 py-2">{t("platformWageComponentTemplates.baseEffects.col.calculation")}</th>
                <th className="px-3 py-2">{t("platformWageComponentTemplates.baseEffects.col.value")}</th>
                <th className="px-3 py-2">{t("platformWageComponentTemplates.baseEffects.col.priority")}</th>
                <th className="px-3 py-2 text-right">{t("platformWageComponentTemplates.col.actions")}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, index) => (
                <tr key={row.payrollBaseId} className="border-t border-border">
                  <td className="px-3 py-2">
                    <span className="font-mono text-xs">{row.payrollBaseCode}</span>
                    <span className="ml-2 text-muted">{row.payrollBaseName}</span>
                  </td>
                  <td className="px-3 py-2">
                    <select
                      className="w-full min-w-[7rem] rounded border border-border bg-background px-2 py-1 text-foreground"
                      value={row.effectDirection}
                      onChange={(e) =>
                        updateRow(index, { effectDirection: e.target.value as BaseEffectDirection })
                      }
                    >
                      {DIRECTIONS.map((d) => (
                        <option key={d} value={d}>
                          {t(`platformWageComponentTemplates.baseEffects.direction.${d}`)}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className="px-3 py-2">
                    <select
                      className="w-full min-w-[7rem] rounded border border-border bg-background px-2 py-1 text-foreground"
                      value={row.effectCalculationType}
                      disabled={row.effectDirection === "IGNORE"}
                      onChange={(e) =>
                        updateRow(index, {
                          effectCalculationType: e.target.value as BaseEffectCalculationType,
                        })
                      }
                    >
                      {CALC_TYPES.map((c) => (
                        <option key={c} value={c}>
                          {t(`platformWageComponentTemplates.baseEffects.calculation.${c}`)}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className="px-3 py-2">
                    <input
                      type="number"
                      step="any"
                      className="w-24 rounded border border-border bg-background px-2 py-1 text-foreground disabled:opacity-50"
                      value={row.effectValue}
                      disabled={row.effectDirection === "IGNORE" || row.effectCalculationType === "FULL"}
                      onChange={(e) => updateRow(index, { effectValue: e.target.value })}
                    />
                  </td>
                  <td className="px-3 py-2">
                    <input
                      type="number"
                      className="w-16 rounded border border-border bg-background px-2 py-1 text-foreground"
                      value={row.priority}
                      onChange={(e) => updateRow(index, { priority: e.target.value })}
                    />
                  </td>
                  <td className="px-3 py-2 text-right">
                    <button
                      type="button"
                      className="text-destructive underline-offset-4 hover:underline"
                      onClick={() => removeRow(index)}
                    >
                      {t("platformWageComponentTemplates.baseEffects.action.remove")}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="text-sm text-muted">{t("platformWageComponentTemplates.baseEffects.empty")}</p>
      )}
      {availableBases.length > 0 ? (
        <label className="flex flex-wrap items-center gap-2 text-sm">
          <span className="text-muted">{t("platformWageComponentTemplates.baseEffects.action.add")}</span>
          <select
            className="rounded border border-border bg-background px-2 py-1 text-foreground"
            defaultValue=""
            onChange={(e) => {
              const id = e.target.value;
              if (id) addBase(id);
              e.target.value = "";
            }}
          >
            <option value="">—</option>
            {availableBases.map((b) => (
              <option key={b.id} value={b.id}>
                {b.code} — {b.name}
              </option>
            ))}
          </select>
        </label>
      ) : null}
    </div>
  );
}
