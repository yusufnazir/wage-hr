/** HttpOnly cookie: selected tenant UUID for platform superadmin lens (BFF forwards as {@code X-Tenant-Id}). */
export const LENS_TENANT_COOKIE = "wp_lens_tenant";

/** Canonical 8-4-4-4-12 hex (case-insensitive). Not RFC-variant strict — matches Java/Spring UUID strings including seeded test ids. */
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isUuidString(s: string): boolean {
  return UUID_RE.test(s.trim());
}

/** Upstream paths that must not receive {@code X-Tenant-Id} from the lens cookie. */
export function shouldAttachLensTenantHeader(upstreamPath: string): boolean {
  if (upstreamPath.startsWith("/api/v1/platform/")) {
    return false;
  }
  if (upstreamPath.startsWith("/api/v1/auth/")) {
    return false;
  }
  return true;
}

export function readLensTenantIdFromCookieStore(cookieStore: { get(name: string): { value: string } | undefined }): string | null {
  const raw = cookieStore.get(LENS_TENANT_COOKIE)?.value?.trim();
  if (!raw || !isUuidString(raw)) {
    return null;
  }
  return raw.toLowerCase();
}
