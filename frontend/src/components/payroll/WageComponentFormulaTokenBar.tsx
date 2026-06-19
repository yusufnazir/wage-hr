"use client";

import { WAGE_COMPONENT_FORMULA_PRESETS, WAGE_COMPONENT_FORMULA_REFS } from "@/lib/wage-component-formula";

type Props = {
  componentCodes?: string[];
  onAppendRef: (ref: string) => void;
  onApplyPreset: (expression: string) => void;
  t: (key: string) => string;
};

export function WageComponentFormulaTokenBar({ componentCodes = [], onAppendRef, onApplyPreset, t }: Props) {
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {WAGE_COMPONENT_FORMULA_PRESETS.map((p) => (
          <button
            key={p.key}
            type="button"
            className="rounded border border-border bg-background px-2 py-1 text-xs font-medium text-foreground hover:bg-surface"
            onClick={() => onApplyPreset(p.expression)}
          >
            {t(`wageComponents.formula.${p.key}`)}
          </button>
        ))}
      </div>
      <div>
        <div className="mb-1 text-xs text-muted">{t("wageComponents.formula.insertRef")}</div>
        <div className="flex flex-wrap gap-1">
          {WAGE_COMPONENT_FORMULA_REFS.map((ref) => (
            <button
              key={ref}
              type="button"
              className="rounded bg-background px-2 py-0.5 font-mono text-[11px] text-foreground ring-1 ring-border hover:bg-surface"
              onClick={() => onAppendRef(ref)}
            >
              {ref}
            </button>
          ))}
          {componentCodes.map((code) => {
            const token = `component("${code}").amount`;
            return (
              <button
                key={code}
                type="button"
                className="rounded bg-primary/10 px-2 py-0.5 font-mono text-[11px] text-primary ring-1 ring-primary/30 hover:bg-primary/20"
                onClick={() => onAppendRef(token)}
              >
                {token}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
