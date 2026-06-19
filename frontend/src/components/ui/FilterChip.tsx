"use client";

import {
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type ReactNode,
} from "react";

type FilterChipProps<T> = {
  label: string;
  value: T | null | undefined;
  onApply: (value: T | null) => void;
  /**
   * Format the value for display next to the label when active.
   * Defaults to {@link String}. Return an empty string to hide the value text.
   */
  formatValue?: (value: T) => string;
  /**
   * Render the input(s) inside the popover. Receives the current draft value,
   * a setter, and an apply callback (useful for Enter-to-apply).
   */
  renderInput: (
    draft: T | null,
    setDraft: (next: T | null) => void,
    apply: () => void,
  ) => ReactNode;
};

function isPresent<T>(value: T | null | undefined): value is T {
  if (value === null || value === undefined) return false;
  if (typeof value === "string") return value.trim().length > 0;
  if (Array.isArray(value)) return value.length > 0;
  return true;
}

export function FilterChip<T>({ label, value, onApply, formatValue, renderInput }: FilterChipProps<T>) {
  const popoverId = useId();
  const wrapperRef = useRef<HTMLDivElement | null>(null);
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<T | null>(value ?? null);

  useEffect(() => {
    setDraft(value ?? null);
  }, [value]);

  useEffect(() => {
    if (!open) return;
    function onDown(e: MouseEvent) {
      if (!wrapperRef.current) return;
      if (!wrapperRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const apply = useCallback(() => {
    onApply(isPresent(draft) ? draft : null);
    setOpen(false);
  }, [draft, onApply]);

  const clear = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      setDraft(null);
      onApply(null);
      setOpen(false);
    },
    [onApply],
  );

  const active = isPresent(value);
  const displayValue = active ? (formatValue ? formatValue(value) : String(value)) : "";

  return (
    <div ref={wrapperRef} className="relative inline-flex">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-controls={popoverId}
        className={`inline-flex items-center gap-1.5 rounded-full border border-dashed px-2 py-0.5 text-xs font-medium transition-colors ${
          active
            ? "border-primary/60 bg-primary/5 text-foreground"
            : "border-border bg-surface text-foreground hover:bg-surface-alt"
        }`}
      >
        {active ? (
          <span
            role="button"
            aria-label={`Clear ${label} filter`}
            onClick={clear}
            className="inline-flex h-4 w-4 items-center justify-center rounded-full text-destructive hover:bg-destructive/10"
          >
            <svg viewBox="0 0 16 16" className="h-3 w-3" fill="currentColor" aria-hidden="true">
              <path d="M8 1a7 7 0 1 0 0 14A7 7 0 0 0 8 1Zm2.78 9.72-1.06 1.06L8 10.06l-1.72 1.72-1.06-1.06L6.94 9 5.22 7.28l1.06-1.06L8 7.94l1.72-1.72 1.06 1.06L9.06 9l1.72 1.72Z" />
            </svg>
          </span>
        ) : (
          <span aria-hidden="true" className="inline-flex h-4 w-4 items-center justify-center text-muted">
            <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" fill="currentColor">
              <path d="M8 1a7 7 0 1 0 0 14A7 7 0 0 0 8 1Zm.75 6.25H11.5v1.5H8.75V11.5h-1.5V8.75H4.5v-1.5h2.75V4.5h1.5v2.75Z" />
            </svg>
          </span>
        )}
        <span className="text-foreground">{label}</span>
        {active ? (
          <>
            <span aria-hidden="true" className="px-0.5 text-muted">|</span>
            <span className="max-w-[10rem] truncate text-primary">{displayValue}</span>
          </>
        ) : null}
      </button>

      {open ? (
        <div
          id={popoverId}
          role="dialog"
          aria-label={`Filter by ${label}`}
          className="absolute left-0 top-full z-30 mt-1 w-72 rounded-md border border-border bg-surface shadow-lg"
        >
          <div className="flex flex-col gap-2 px-3 pt-3">
            <span className="text-sm font-semibold text-foreground">Filter by {label}</span>
            <div className="py-1">{renderInput(draft, setDraft, apply)}</div>
          </div>
          <div className="flex gap-2 px-3 pb-3 pt-1">
            <button
              type="button"
              onClick={apply}
              className="flex-1 rounded bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90"
            >
              Apply
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
