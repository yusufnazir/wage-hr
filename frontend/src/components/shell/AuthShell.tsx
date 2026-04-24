import type { ReactNode } from "react";

/** Shared card surface for auth forms (glass on gradient — see WEB-THEMING-AND-DESIGN-SYSTEM.md). */
export const authGlassCardClassName =
  "rounded-xl border border-border/60 bg-surface/85 shadow-md backdrop-blur-md dark:border-border/50 dark:bg-surface/70";

type AuthShellProps = {
  children: ReactNode;
};

/**
 * Auth / marketing-adjacent layout: soft mesh + glass-ready content area.
 * Tenant signed-in shell uses {@code data-layout="app"} instead (see tenant app page).
 */
export function AuthShell({ children }: AuthShellProps) {
  return (
    <div
      data-layout="auth"
      className="relative min-h-screen overflow-hidden bg-background text-foreground"
    >
      <div className="pointer-events-none absolute inset-0 opacity-90 dark:opacity-70" aria-hidden>
        <div className="absolute -left-1/4 top-0 h-[min(28rem,70vw)] w-[min(28rem,70vw)] rounded-full bg-primary/20 blur-3xl" />
        <div className="absolute -right-1/4 top-1/3 h-[min(22rem,55vw)] w-[min(22rem,55vw)] rounded-full bg-accent/15 blur-3xl" />
        <div className="absolute bottom-0 left-1/3 h-[min(20rem,50vw)] w-[min(20rem,50vw)] rounded-full bg-primary/10 blur-3xl" />
      </div>
      <div className="relative">{children}</div>
    </div>
  );
}
