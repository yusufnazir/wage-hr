"use client";

import Link from "next/link";

export function NoCompanyEmptyState({
  title = "Create a company to continue",
  body = "This section requires a company. Create one first, then come back here to continue setup.",
  returnTo = "/app",
  showViewCompanies = true,
}: {
  title?: string;
  body?: string;
  returnTo?: string;
  showViewCompanies?: boolean;
}) {
  const safeReturnTo = returnTo.startsWith("/app") ? returnTo : "/app";

  return (
    <section className="rounded-xl border border-border bg-surface p-7 shadow-sm" data-testid="no-company-empty-state">
      <h1 className="text-lg font-semibold text-foreground">{title}</h1>
      <p className="mt-2 text-sm text-muted">{body}</p>
      <div className="mt-5 flex flex-wrap items-center gap-3">
        <Link
          href={`/app/companies/new?returnTo=${encodeURIComponent(safeReturnTo)}`}
          className="inline-flex items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90"
        >
          Create company
        </Link>
        {showViewCompanies ? (
          <Link
            href="/app/companies"
            className="inline-flex items-center justify-center rounded-md border border-border bg-background px-4 py-2 text-sm font-medium text-foreground hover:bg-surface-alt"
          >
            View companies
          </Link>
        ) : null}
      </div>
    </section>
  );
}

