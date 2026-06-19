"use client";

import { useMemo, useState } from "react";

import { dependencyEdgesToMermaid } from "@/lib/dependency-graph";

type Props = {
  currentTemplateCode: string;
  currentTemplateId: string;
  prerequisites: { id: string; code: string }[];
  t: (key: string) => string;
};

export function WageComponentDependencyGraph({
  currentTemplateCode,
  currentTemplateId,
  prerequisites,
  t,
}: Props) {
  const [copied, setCopied] = useState(false);
  const mermaid = useMemo(
    () => dependencyEdgesToMermaid(currentTemplateCode, currentTemplateId, prerequisites),
    [currentTemplateCode, currentTemplateId, prerequisites],
  );

  async function copyMermaid() {
    try {
      await navigator.clipboard.writeText(mermaid);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
    catch {
      setCopied(false);
    }
  }

  return (
    <div className="space-y-2 rounded border border-border bg-surface-alt/50 p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-xs font-medium text-foreground">{t("platformWageComponentTemplates.dependencies.graphTitle")}</span>
        <button
          type="button"
          className="rounded border border-border px-2 py-0.5 text-[11px] hover:bg-surface"
          onClick={() => void copyMermaid()}
        >
          {copied ? t("platformWageComponentTemplates.dependencies.copiedMermaid") : t("platformWageComponentTemplates.dependencies.copyMermaid")}
        </button>
      </div>
      <pre className="overflow-x-auto rounded bg-background p-2 font-mono text-[10px] leading-relaxed text-muted">{mermaid}</pre>
      <ul className="text-xs text-muted">
        {prerequisites.length === 0 ? (
          <li>{t("platformWageComponentTemplates.dependencies.graphEmpty")}</li>
        ) : (
          prerequisites.map((p) => (
            <li key={p.id}>
              <span className="font-mono text-foreground">{p.code}</span>
              <span> → </span>
              <span className="font-mono text-foreground">{currentTemplateCode}</span>
            </li>
          ))
        )}
      </ul>
    </div>
  );
}
