"use client";

type Step = {
  id: string;
  label: string;
};

export function HorizontalStepper({
  steps,
  currentIndex,
  completedThrough,
  onStepClick,
}: {
  steps: Step[];
  currentIndex: number;
  /** Highest step index the user may jump to (0-based). */
  completedThrough: number;
  onStepClick?: (index: number) => void;
}) {
  return (
    <nav className="overflow-x-auto pb-1" aria-label="Progress">
      <ol className="flex min-w-max items-center gap-0">
        {steps.map((step, index) => {
          const done = index < currentIndex || index <= completedThrough - 1;
          const active = index === currentIndex;
          const clickable = onStepClick != null && index <= completedThrough;
          const isLast = index === steps.length - 1;

          return (
            <li key={step.id} className="flex items-center">
              <button
                type="button"
                disabled={!clickable}
                onClick={() => clickable && onStepClick?.(index)}
                className={`group flex items-center gap-2 rounded-md px-1 py-1 text-left transition-colors ${
                  clickable ? "cursor-pointer hover:bg-surface-alt" : "cursor-default"
                }`}
                aria-current={active ? "step" : undefined}
              >
                <span
                  className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-semibold transition-colors ${
                    done && !active
                      ? "bg-primary/15 text-primary"
                      : active
                        ? "bg-primary text-primary-foreground shadow-sm"
                        : "border border-border bg-surface text-muted"
                  }`}
                  aria-hidden
                >
                  {done && !active ? "✓" : index + 1}
                </span>
                <span
                  className={`max-w-[9rem] text-xs font-medium leading-tight sm:max-w-none sm:text-sm ${
                    active ? "text-foreground" : done ? "text-foreground/80" : "text-muted"
                  }`}
                >
                  {step.label}
                </span>
              </button>
              {!isLast ? (
                <span
                  className={`mx-1 hidden h-px w-6 shrink-0 sm:block md:w-10 ${
                    index < currentIndex ? "bg-primary/40" : "bg-border"
                  }`}
                  aria-hidden
                />
              ) : null}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
