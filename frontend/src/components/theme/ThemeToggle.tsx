"use client";

import { useTheme } from "next-themes";
import { useEffect, useState } from "react";

function nextAppearance(current: string | undefined): "light" | "dark" | "system" {
  if (current === "light") {
    return "dark";
  }
  if (current === "dark") {
    return "system";
  }
  return "light";
}

export function ThemeToggle() {
  const { theme, setTheme, resolvedTheme } = useTheme();
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  if (!mounted) {
    return (
      <span
        className="inline-flex h-9 min-w-[8.5rem] rounded-md border border-border bg-surface/80 px-3 py-2 text-sm text-muted backdrop-blur-sm"
        data-testid="theme-toggle"
        aria-hidden
      >
        …
      </span>
    );
  }
  const label =
    theme === "system" ? `System (${resolvedTheme ?? "…"})` : theme === "dark" ? "Dark" : "Light";
  return (
    <button
      type="button"
      data-testid="theme-toggle"
      className="inline-flex h-9 min-w-[8.5rem] items-center justify-center rounded-md border border-border bg-surface px-3 text-sm font-medium text-foreground shadow-sm ring-offset-background transition hover:bg-background focus-visible:outline focus-visible:ring-2 focus-visible:ring-primary"
      onClick={() => setTheme(nextAppearance(theme))}
      aria-label={`Color theme: ${label}. Click to cycle light, dark, and system.`}
    >
      {label}
    </button>
  );
}
