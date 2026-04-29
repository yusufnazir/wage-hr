import Image from "next/image";

import { authGlassCardClassName } from "@/components/shell/AuthShell";
import { brandLogoWordmarkSmallSrc } from "@/lib/brand-assets";

const highlights = [
  "Multi-tenant payroll management",
  "Secure document storage",
  "Role-based access control",
  "Subscription-based features",
  "Session-based security with CSRF-safe BFF",
];

/**
 * Left column for auth split layout — glass panel + value bullets (UX contract v1).
 */
export function AuthMarketingPanel({
  className,
  applicationName = "Wage Payroll",
}: {
  className?: string;
  applicationName?: string;
}) {
  return (
    <div
      className={`relative flex flex-col justify-center border-border/30 bg-gradient-to-br from-primary/[0.08] via-background to-accent-soft px-8 py-12 lg:border-r lg:border-border/40 lg:px-12 ${className ?? ""}`}
    >
      <div className={`mx-auto w-full max-w-md space-y-6 p-8 ${authGlassCardClassName}`}>
        <div>
          <Image
            src={brandLogoWordmarkSmallSrc}
            alt={applicationName}
            width={240}
            height={52}
            className="h-9 w-auto max-w-full object-contain object-left"
            priority
          />
          <p className="mt-4 text-[11px] font-semibold uppercase tracking-[0.2em] text-primary">Why teams choose us</p>
          <h2 className="mt-3 text-balance text-3xl font-semibold tracking-tight text-foreground sm:text-[1.75rem] sm:leading-tight">
            Operate payroll with <span data-testid="auth-marketing-product-name">{applicationName}</span>
          </h2>
          <p className="mt-2 text-sm leading-relaxed text-muted">
            A modern multi-tenant foundation for payroll, billing, and workforce data — built for teams that need clear
            roles, strong security, and room to grow.
          </p>
        </div>
        <ul className="space-y-2.5 text-sm text-foreground">
          {highlights.map((line) => (
            <li key={line} className="flex gap-2">
              <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-primary" aria-hidden />
              <span>{line}</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
