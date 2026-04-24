/**
 * Spring API origin — server-only. Never use NEXT_PUBLIC_* for this URL.
 */
export function getUpstreamApiBaseUrl(): string {
  const raw = process.env.API_BASE_URL?.trim();
  if (raw) {
    return raw.replace(/\/$/, "");
  }
  if (process.env.NODE_ENV === "development") {
    return "http://127.0.0.1:8300";
  }
  throw new Error(
    "API_BASE_URL must be set for the Next.js server to reach the Spring API (required outside development).",
  );
}
