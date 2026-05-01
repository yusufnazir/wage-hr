import Link from "next/link";

export default function PrivacyPolicyPage() {
  return (
    <main className="mx-auto max-w-2xl px-6 py-12 text-foreground">
      <h1 className="text-2xl font-semibold">Privacy policy</h1>
      <p className="mt-4 text-sm text-muted">
        Placeholder page. Replace this content with your organization&apos;s privacy policy before production use.
      </p>
      <p className="mt-8 text-sm">
        <Link href="/register" className="text-primary underline-offset-4 hover:underline">
          Back to registration
        </Link>
      </p>
    </main>
  );
}
