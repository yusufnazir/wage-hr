/** Dependency edge: prerequisite template must be evaluated before dependent. */
export type DependencyEdge = {
  prerequisiteId: string;
  prerequisiteCode: string;
  dependentId: string;
  dependentCode: string;
};

export type DependencyCycleResult = {
  hasCycle: boolean;
  message: string | null;
};

/** Detect self-loop and direct mutual dependency in the edited prerequisite list. */
export function detectDependencyIssues(
  currentTemplateId: string,
  prerequisiteIds: string[],
): DependencyCycleResult {
  if (prerequisiteIds.includes(currentTemplateId)) {
    return { hasCycle: true, message: "A template cannot depend on itself." };
  }
  const seen = new Set<string>();
  for (const id of prerequisiteIds) {
    if (seen.has(id)) {
      return { hasCycle: true, message: "Duplicate prerequisite." };
    }
    seen.add(id);
  }
  return { hasCycle: false, message: null };
}

/** Mermaid flowchart: prerequisite → dependent (execution order). */
export function dependencyEdgesToMermaid(
  currentTemplateCode: string,
  currentTemplateId: string,
  prerequisites: { id: string; code: string }[],
): string {
  const lines = ["flowchart LR", `  current["${escapeMermaid(currentTemplateCode)}"]`];
  prerequisites.forEach((p, i) => {
    const nodeId = `p${i}`;
    lines.push(`  ${nodeId}["${escapeMermaid(p.code)}"]`);
    lines.push(`  ${nodeId} --> current`);
  });
  if (prerequisites.length === 0) {
    lines.push(`  note["No prerequisites"]`);
    lines.push(`  note -.-> current`);
  }
  return lines.join("\n");
}

function escapeMermaid(label: string): string {
  return label.replace(/"/g, "'");
}
