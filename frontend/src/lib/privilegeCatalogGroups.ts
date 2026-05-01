import type { PlatformPrivilegeCatalogEntry } from "@/lib/api";

export function buildPrivilegeCatalogByCode(catalog: PlatformPrivilegeCatalogEntry[]): Map<string, PlatformPrivilegeCatalogEntry> {
  return new Map(catalog.map((e) => [e.code, e]));
}

/** Group key: catalog `resource` when set, else first segment of `CODE_*` before `_`, else `OTHER`. */
export function privilegeResourceGroupKey(
  entry: PlatformPrivilegeCatalogEntry | undefined,
  code: string,
): string {
  const r = entry?.resource?.trim();
  if (r) return r;
  const i = code.indexOf("_");
  if (i > 0) return code.slice(0, i);
  return "OTHER";
}

/** Human-readable heading for a resource group key (e.g. `TENANT_SETTINGS` → `Tenant Settings`). */
export function formatPrivilegeResourceGroupLabel(key: string): string {
  if (key === "OTHER") return "Other";
  return key
    .split("_")
    .map((w) => (w.length ? w.charAt(0) + w.slice(1).toLowerCase() : ""))
    .join(" ");
}

export type PrivilegeCodeGroup = { key: string; label: string; codes: string[] };

/** Sorted groups (by label), codes sorted within each group. */
export function groupPrivilegeCodesByResource(
  codes: string[],
  catalog: PlatformPrivilegeCatalogEntry[],
): PrivilegeCodeGroup[] {
  const byCode = buildPrivilegeCatalogByCode(catalog);
  const bucket = new Map<string, string[]>();
  for (const code of codes) {
    const k = privilegeResourceGroupKey(byCode.get(code), code);
    if (!bucket.has(k)) bucket.set(k, []);
    bucket.get(k)!.push(code);
  }
  return [...bucket.entries()]
    .map(([key, list]) => ({
      key,
      label: formatPrivilegeResourceGroupLabel(key),
      codes: list.sort((a, b) => a.localeCompare(b)),
    }))
    .sort((a, b) => a.label.localeCompare(b.label));
}
