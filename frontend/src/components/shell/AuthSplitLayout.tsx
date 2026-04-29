"use client";

import type { ReactNode } from "react";
import Image from "next/image";
import { useEffect, useState } from "react";

import { AuthShell } from "@/components/shell/AuthShell";
import { AuthMarketingPanel } from "@/components/shell/AuthMarketingPanel";
import { brandLogoWordmarkSmallSrc } from "@/lib/brand-assets";
import { fetchPublicSurface } from "@/lib/api";
import { ThemeToggle } from "@/components/theme/ThemeToggle";

type AuthSplitLayoutProps = {
  /** Right column: form + links */
  children: ReactNode;
  /** Heading on the form side (e.g. Sign in) */
  title: string;
  /** Optional subtitle under title */
  subtitle?: string;
};

/**
 * Twin-column auth layout: marketing glass panel + form column (desktop); stacked on small screens.
 */
export function AuthSplitLayout({ children, title, subtitle }: AuthSplitLayoutProps) {
  const [applicationName, setApplicationName] = useState("Wage Payroll");

  useEffect(() => {
    void fetchPublicSurface().then((r) => {
      if (r.ok) {
        setApplicationName(r.surface.applicationName);
      }
    });
  }, []);

  return (
    <AuthShell>
      <div className="grid min-h-screen lg:grid-cols-2">
        <AuthMarketingPanel className="order-2 hidden lg:order-1 lg:flex" applicationName={applicationName} />
        <div className="order-1 flex min-h-screen flex-col justify-center px-6 py-10 sm:px-10 lg:order-2 lg:min-h-0 lg:px-14 lg:py-12">
          <div className="mx-auto w-full max-w-md space-y-6">
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0">
                <Image
                  src={brandLogoWordmarkSmallSrc}
                  alt={applicationName}
                  width={200}
                  height={44}
                  className="h-8 w-auto max-w-[11rem] object-contain object-left lg:hidden"
                  priority
                />
                <h1 className="mt-2 text-xl font-semibold text-foreground lg:mt-0">{title}</h1>
                {subtitle ? <p className="mt-1 text-sm text-muted">{subtitle}</p> : null}
              </div>
              <ThemeToggle />
            </div>
            {children}
          </div>
        </div>
        <div className="order-3 border-t border-border/50 px-6 py-8 lg:hidden">
          <div className="mx-auto max-w-md rounded-xl border border-border/60 bg-surface/80 p-5 shadow-sm backdrop-blur-md dark:bg-surface/70">
            <p className="text-xs font-semibold uppercase tracking-wider text-primary">Why teams choose us</p>
            <p className="mt-2 text-sm text-muted">
              <span data-testid="auth-application-name" className="font-medium text-foreground">
                {applicationName}
              </span>{" "}
              — multi-tenant payroll, secure documents, RBAC, and subscription-aware navigation in one cohesive web experience.
            </p>
          </div>
        </div>
      </div>
    </AuthShell>
  );
}
