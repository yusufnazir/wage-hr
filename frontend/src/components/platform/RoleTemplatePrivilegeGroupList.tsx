"use client";

import type { Dispatch, SetStateAction } from "react";

import type { PrivilegeCodeGroup } from "@/lib/privilegeCatalogGroups";

type Props = {
  groups: PrivilegeCodeGroup[];
  selected: Set<string>;
  setSelected: Dispatch<SetStateAction<Set<string>>>;
  busy: boolean;
};

export function RoleTemplatePrivilegeGroupList({ groups, selected, setSelected, busy }: Props) {
  return (
    <div className="mt-4 flex flex-col gap-4">
      {groups.map((g) => (
        <div
          key={g.key}
          role="group"
          aria-labelledby={`privilege-group-${g.key}`}
          className="overflow-hidden rounded-xl border border-border bg-background/60 shadow-sm ring-1 ring-border/60 dark:bg-muted/20 dark:ring-border/40"
        >
          <div
            id={`privilege-group-${g.key}`}
            className="flex items-center gap-3 border-b border-border/80 bg-muted/50 px-4 py-3 dark:bg-muted/35"
          >
            <span
              className="h-2.5 w-2.5 shrink-0 rounded-full bg-primary shadow-[0_0_0_3px] shadow-primary/25"
              aria-hidden
            />
            <h3 className="text-sm font-semibold tracking-tight text-foreground">{g.label}</h3>
            <span
              className="ml-auto rounded-md bg-background/80 px-2 py-0.5 font-mono text-xs tabular-nums text-muted dark:bg-background/40"
              title="Privileges in this group"
            >
              {g.codes.length}
            </span>
          </div>
          <div className="p-4 pt-3">
            <ul className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
              {g.codes.map((c) => (
                <li key={c}>
                  <label className="flex cursor-pointer items-center gap-3 rounded-md border border-transparent px-2 py-1.5 text-sm text-foreground hover:border-border/80 hover:bg-muted/30">
                    <input
                      type="checkbox"
                      className="h-4 w-4 shrink-0 rounded border-border text-primary focus:ring-primary"
                      checked={selected.has(c)}
                      disabled={busy}
                      onChange={(ev) =>
                        setSelected((prev) => {
                          const n = new Set(prev);
                          if (ev.target.checked) n.add(c);
                          else n.delete(c);
                          return n;
                        })
                      }
                    />
                    <span className="font-mono text-xs">{c}</span>
                  </label>
                </li>
              ))}
            </ul>
          </div>
        </div>
      ))}
    </div>
  );
}
