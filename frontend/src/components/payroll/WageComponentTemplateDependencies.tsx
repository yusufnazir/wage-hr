"use client";

import { useMemo } from "react";

import { WageComponentDependencyGraph } from "@/components/payroll/WageComponentDependencyGraph";
import { detectDependencyIssues } from "@/lib/dependency-graph";
import type {
  PlatformWageComponentTemplateDependencyPutItem,
  PlatformWageComponentTemplateDependencyRow,
  PlatformWageComponentTemplateRow,
} from "@/lib/api";

export type EditableDependencyRow = {
  dependsOnTemplateId: string;
  dependsOnTemplateCode: string;
  dependsOnTemplateName: string;
};

export function dependenciesFromTemplate(
  deps: PlatformWageComponentTemplateDependencyRow[] | undefined,
): EditableDependencyRow[] {
  if (!deps?.length) return [];
  return deps.map((d) => ({
    dependsOnTemplateId: d.dependsOnTemplateId,
    dependsOnTemplateCode: d.dependsOnTemplateCode,
    dependsOnTemplateName: d.dependsOnTemplateName,
  }));
}

export function toDependencyPutPayload(
  rows: EditableDependencyRow[],
): PlatformWageComponentTemplateDependencyPutItem[] {
  return rows.map((row) => ({ dependsOnTemplateId: row.dependsOnTemplateId }));
}

type Props = {
  rows: EditableDependencyRow[];
  onChange: (rows: EditableDependencyRow[]) => void;
  availableTemplates: PlatformWageComponentTemplateRow[];
  currentTemplateId: string;
  currentTemplateCode: string;
  t: (key: string) => string;
};

export function WageComponentTemplateDependenciesEditor({
  rows,
  onChange,
  availableTemplates,
  currentTemplateId,
  currentTemplateCode,
  t,
}: Props) {
  const candidates = availableTemplates.filter((tpl) => tpl.id !== currentTemplateId);
  const used = new Set(rows.map((r) => r.dependsOnTemplateId));
  const cycleIssue = useMemo(
    () => detectDependencyIssues(currentTemplateId, rows.map((r) => r.dependsOnTemplateId)),
    [currentTemplateId, rows],
  );

  function add(templateId: string) {
    const tpl = candidates.find((t) => t.id === templateId);
    if (!tpl || used.has(templateId)) return;
    onChange([
      ...rows,
      {
        dependsOnTemplateId: tpl.id,
        dependsOnTemplateCode: tpl.templateCode,
        dependsOnTemplateName: tpl.name,
      },
    ]);
  }

  function remove(templateId: string) {
    onChange(rows.filter((r) => r.dependsOnTemplateId !== templateId));
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted">
        Prerequisites must be evaluated before this template&apos;s formulas can reference{" "}
        <code className="text-xs">component(&quot;CODE&quot;).amount</code>.
      </p>
      <div className="flex flex-wrap items-end gap-2">
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-muted">Add prerequisite</span>
          <select
            className="min-w-[220px] rounded border border-border bg-surface px-2 py-1.5 text-sm"
            defaultValue=""
            onChange={(e) => {
              const v = e.target.value;
              if (v) add(v);
              e.target.value = "";
            }}
          >
            <option value="">Select template…</option>
            {candidates
              .filter((t) => !used.has(t.id))
              .map((t) => (
                <option key={t.id} value={t.id}>
                  {t.templateCode} — {t.name}
                </option>
              ))}
          </select>
        </label>
      </div>
      {rows.length > 0 ? (
        <ul className="divide-y divide-border rounded border border-border">
          {rows.map((row) => (
            <li key={row.dependsOnTemplateId} className="flex items-center justify-between px-3 py-2 text-sm">
              <span>
                <span className="font-mono text-xs">{row.dependsOnTemplateCode}</span>
                <span className="ml-2 text-muted">{row.dependsOnTemplateName}</span>
              </span>
              <button
                type="button"
                className="text-xs text-danger hover:underline"
                onClick={() => remove(row.dependsOnTemplateId)}
              >
                Remove
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-muted">{t("platformWageComponentTemplates.dependencies.empty")}</p>
      )}
      {cycleIssue.hasCycle ? (
        <p className="text-sm text-destructive">{cycleIssue.message ?? t("platformWageComponentTemplates.dependencies.cycleError")}</p>
      ) : null}
      <WageComponentDependencyGraph
        currentTemplateCode={currentTemplateCode}
        currentTemplateId={currentTemplateId}
        prerequisites={rows.map((r) => ({ id: r.dependsOnTemplateId, code: r.dependsOnTemplateCode }))}
        t={t}
      />
    </div>
  );
}
