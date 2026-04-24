import Link from "next/link";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import { AuthShell, authGlassCardClassName } from "@/components/shell/AuthShell";
import { defaultTenantAppUrl } from "@/lib/web-origins";

export default function HomePage() {
  return (
    <AuthShell>
      <main className="mx-auto flex min-h-screen max-w-lg flex-col justify-center gap-6 px-6 py-16">
        <div className="flex items-center justify-between gap-4">
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">wage-payroll</h1>
          <ThemeToggle />
        </div>
        <div className={`p-6 ${authGlassCardClassName}`}>
          <p className="text-muted">
            Minimal auth shell scaffold. Use{" "}
            <code className="rounded bg-background px-1.5 py-0.5 text-sm text-foreground ring-1 ring-border">
              auth.lvh.me:3007
            </code>{" "}
            for login flows per architecture.
          </p>
          <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:items-center">
            <Link
              href="/login"
              className="inline-flex w-fit items-center justify-center rounded-md bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground shadow-sm ring-offset-background transition hover:opacity-90 focus-visible:outline focus-visible:ring-2 focus-visible:ring-primary"
            >
              Sign in
            </Link>
            <a
              href={defaultTenantAppUrl()}
              className="inline-flex w-fit items-center justify-center rounded-md border border-border bg-background px-4 py-2.5 text-sm font-medium text-foreground shadow-sm transition hover:opacity-90 focus-visible:outline focus-visible:ring-2 focus-visible:ring-primary"
            >
              Demo tenant app
            </a>
          </div>
        </div>
      </main>
    </AuthShell>
  );
}
