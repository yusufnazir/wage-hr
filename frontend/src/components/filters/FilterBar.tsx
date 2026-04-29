"use client";

import { useEffect, useId, useMemo, useRef, useState, type ReactNode } from "react";

type FilterChipBaseProps = {
  label: string;
  /** Empty string means not active. */
  value: string;
  /** Short display label for the active value. */
  valueLabel?: string;
  onClear: () => void;
  children: (args: { close: () => void }) => ReactNode;
};

function useOnClickOutside(ref: React.RefObject<HTMLElement | null>, onOutside: () => void, enabled: boolean) {
  useEffect(() => {
    if (!enabled) return;
    function onDocMouseDown(e: MouseEvent) {
      const t = e.target as Node | null;
      if (!t) return;
      const el = ref.current;
      if (el && !el.contains(t)) {
        onOutside();
      }
    }
    document.addEventListener("mousedown", onDocMouseDown);
    return () => document.removeEventListener("mousedown", onDocMouseDown);
  }, [ref, onOutside, enabled]);
}

export function FilterBar({ children, onClearAll, showClearAll }: { children: ReactNode; onClearAll: () => void; showClearAll: boolean }) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      {children}
      {showClearAll ? (
        <button
          type="button"
          className="ml-2 text-sm font-semibold text-primary underline-offset-4 hover:underline"
          onClick={onClearAll}
        >
          Clear filters
        </button>
      ) : null}
    </div>
  );
}

/**
 * Stripe-like filter chip:
 * - Suggested state (value empty): "+" button that opens a menu.
 * - Active state (value set): shows selected value + "×" clear button.
 */
export function FilterChip({ label, value, valueLabel, onClear, children }: FilterChipBaseProps) {
  const popId = useId();
  const ref = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const active = value.trim().length > 0;
  const display = useMemo(() => (valueLabel && valueLabel.trim() ? valueLabel.trim() : value.trim()), [value, valueLabel]);

  useOnClickOutside(
    ref,
    () => {
      setOpen(false);
    },
    open,
  );

  return (
    <div className="relative" ref={ref}>
      {!active ? (
        <button
          type="button"
          className="inline-flex items-center gap-2 rounded-full border border-border bg-background px-3 py-1.5 text-sm font-medium text-foreground shadow-sm hover:bg-muted/30"
          aria-haspopup="dialog"
          aria-expanded={open}
          aria-controls={popId}
          onClick={() => setOpen((v) => !v)}
        >
          <span className="text-muted">+</span>
          <span>{label}</span>
        </button>
      ) : (
        <div className="inline-flex items-center gap-2 rounded-full border border-border bg-surface px-3 py-1.5 text-sm font-medium text-foreground shadow-sm">
          <span className="text-muted">{label}:</span>
          <span className="max-w-[12rem] truncate">{display}</span>
          <button
            type="button"
            className="ml-1 inline-flex h-5 w-5 items-center justify-center rounded-full border border-border bg-background text-xs text-muted hover:bg-muted/30"
            aria-label={`Clear ${label} filter`}
            onClick={() => onClear()}
          >
            ×
          </button>
        </div>
      )}

      {open ? (
        <div
          id={popId}
          role="dialog"
          aria-label={`${label} filter`}
          className="absolute left-0 top-full z-50 mt-2 w-[18rem] rounded-lg border border-border bg-surface p-3 shadow-lg"
        >
          {children({ close: () => setOpen(false) })}
        </div>
      ) : null}
    </div>
  );
}

