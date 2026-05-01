"use client";

import Link from "next/link";

export default function NotFound() {
  return (
    <div className="mx-auto flex min-h-[60vh] max-w-lg flex-col justify-center gap-4 px-6 py-16">
      <h1 className="text-lg font-semibold text-foreground">Page not found</h1>
      <p className="text-sm text-muted">The page you’re looking for doesn’t exist.</p>
      <Link href="/app" className="w-fit text-sm font-medium text-primary underline-offset-4 hover:underline">
        Go to app
      </Link>
    </div>
  );
}

