"use client";

import { useRef } from "react";

import { formatUserFacingDate } from "@/lib/user-date-format";

/**
 * A date picker button that displays the date in the platform-configured format
 * (`me.dateFormat`) while keeping the underlying `<input type="date">` value
 * in `yyyy-MM-dd` (required by the HTML spec).
 */
export function PlatformDateInput({
  id,
  value,
  dateFormat,
  onChange,
  className,
  disabled,
}: {
  id?: string;
  value: string;
  dateFormat: string;
  onChange: (value: string) => void;
  className?: string;
  disabled?: boolean;
}) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const displayValue = value ? formatUserFacingDate(value, dateFormat) : "";
  const placeholder = formatUserFacingDate("2026-12-31", dateFormat);

  const openPicker = () => {
    if (disabled) return;
    const input = inputRef.current;
    if (!input) return;
    if (typeof input.showPicker === "function") {
      input.showPicker();
      return;
    }
    input.focus();
    input.click();
  };

  return (
    <div className={`relative ${className ?? ""}`}>
      <button
        id={id}
        type="button"
        onClick={openPicker}
        disabled={disabled}
        className="flex w-full items-center justify-between rounded border border-border bg-background px-3 py-2 text-left text-sm text-foreground disabled:cursor-not-allowed disabled:opacity-50"
      >
        <span className={displayValue ? "" : "text-muted"}>{displayValue || placeholder}</span>
        <svg className="h-4 w-4 shrink-0 text-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <rect x="3" y="5" width="18" height="16" rx="2" />
          <path d="M16 3v4M8 3v4M3 9h18" strokeLinecap="round" />
        </svg>
      </button>
      <input
        ref={inputRef}
        type="date"
        tabIndex={-1}
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 opacity-0"
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    </div>
  );
}
