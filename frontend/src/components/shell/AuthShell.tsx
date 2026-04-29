import type { ReactNode } from "react";

/** Shared card surface for auth forms — glass elevation (see WEB-THEMING-AND-DESIGN-SYSTEM.md). */
export const authGlassCardClassName =
  "rounded-2xl border border-border/50 bg-surface/90 shadow-glass backdrop-blur-xl ring-1 ring-foreground/[0.04] dark:border-border/40 dark:bg-surface/75 dark:ring-white/[0.06]";

type AuthShellProps = {
  children: ReactNode;
};

/**
 * Auth / marketing-adjacent layout: layered mesh using brand blues (see `public/wage/` marks).
 */
export function AuthShell({ children }: AuthShellProps) {
  return (
    <div
      data-layout="auth"
      className="relative min-h-screen overflow-hidden bg-background text-foreground"
    >
      <div className="pointer-events-none absolute inset-0" aria-hidden>
        <div
          className="absolute -left-[20%] -top-[10%] h-[min(36rem,85vw)] w-[min(36rem,85vw)] rounded-full blur-[4.5rem]"
          style={{ background: "var(--color-mesh-a)" }}
        />
        <div
          className="absolute -right-[15%] top-[20%] h-[min(28rem,70vw)] w-[min(28rem,70vw)] rounded-full blur-[4rem]"
          style={{ background: "var(--color-mesh-b)" }}
        />
        <div
          className="absolute bottom-[-10%] left-[25%] h-[min(26rem,65vw)] w-[min(26rem,65vw)] rounded-full blur-[3.5rem]"
          style={{ background: "var(--color-mesh-c)" }}
        />
        <div className="absolute inset-0 bg-gradient-to-b from-transparent via-background/30 to-background dark:via-background/50" />
      </div>
      <div className="relative">{children}</div>
    </div>
  );
}
