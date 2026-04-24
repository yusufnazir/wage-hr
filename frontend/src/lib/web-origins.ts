/**
 * Browser-visible web origins for multi-host local dev (*.lvh.me + port 3007).
 * Override with NEXT_PUBLIC_* when ports or hosts differ.
 */

export function getAuthWebOrigin(): string {
  return (process.env.NEXT_PUBLIC_AUTH_WEB_ORIGIN ?? "http://auth.lvh.me:3007").replace(/\/$/, "");
}

/** Seeded demo tenant UI origin (tenant handle `demo`). */
export function getDefaultTenantWebOrigin(): string {
  return (process.env.NEXT_PUBLIC_DEFAULT_TENANT_WEB_ORIGIN ?? "http://demo.lvh.me:3007").replace(/\/$/, "");
}

export function authLoginUrl(): string {
  return `${getAuthWebOrigin()}/login`;
}

export function defaultTenantAppUrl(): string {
  return `${getDefaultTenantWebOrigin()}/app`;
}

/**
 * Build `/app` URL for another tenant handle using the same scheme/port and **first DNS label**
 * as {@link getDefaultTenantWebOrigin} (intended for `{handle}.lvh.me` dev hosts).
 */
export function tenantWebAppUrlForHandle(handle: string): string {
  const template = getDefaultTenantWebOrigin();
  const u = new URL(template);
  const labels = u.hostname.split(".");
  labels[0] = handle;
  u.hostname = labels.join(".");
  return `${u.origin}/app`;
}
